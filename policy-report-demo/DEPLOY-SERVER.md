# 服务器部署指南（宝塔面板）

把服务器版后端跑到你自己的服务器上，作为「云端授权 + 功能区判定」服务。
客户端只是空壳，核心判定和授权校验都在这台机器上。

前提：宝塔已装 MySQL、JDK17+、Nginx，且有一个已解析到本机的域名。
下文域名以 `api.xieyuchen.xyz` 代指，替换成你自己的。

---

## 1. 建数据库

宝塔面板 → 「数据库」→「添加数据库」：

| 项 | 值 |
|---|---|
| 数据库名 | `atarashii_prod` |
| 用户名 | `atarashii_prod` |
| 密码 | 点「生成」取一个强密码，**记下来** |
| 字符集 | `utf8mb4` |
| 访问权限 | 本地服务器（不要选「所有人」） |

> 服务器这套只需要「标准表」和「授权表」两类数据，项目/文件数据仍在客户机本地。
> 应用启动时 `ddl-auto: update` 会自动建表，无需手动建表。

## 2. 迁移标准库数据

服务器要做功能区判定，必须有你本地那套「标准表」数据（standard_tables）。
在你**本地开发机**导出，再传到服务器导入。

本地（PowerShell，确认本地 MySQL 库名是 `atarashii`）：

```powershell
mysqldump -u atarashii -p atarashii standard_tables project_types > standards-dump.sql
```

把 `standards-dump.sql` 上传到服务器 `/www/wwwroot/policy-report/`，然后在宝塔
「数据库」→ 对应库 →「导入」选这个文件；或在服务器 SSH 终端：

```bash
mysql -u atarashii_prod -p atarashii_prod < /www/wwwroot/policy-report/standards-dump.sql
```

## 3. 上传 jar

宝塔「文件」→ 建目录 `/www/wwwroot/policy-report/`，上传：

```
policy-report-demo/backend/target/policy-report-demo-0.0.1-SNAPSHOT.jar
```

## 4. 准备启动配置

在 `/www/wwwroot/policy-report/` 下新建 `start.sh`，内容如下（占位符全部替换）：

```bash
#!/bin/bash
cd /www/wwwroot/policy-report

export SERVER_PORT=18080
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/atarashii_prod?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&connectionCollation=utf8mb4_unicode_ci'
export DB_USERNAME='atarashii_prod'
export DB_PASSWORD='第1步那个数据库密码'

# JWT 密钥：≥48 位随机串。生成：openssl rand -base64 48
export JWT_SECRET='SkcQmCdJcHt9z8ElGbFnSFDjRcqbopTWmwfDpiRi/SzQAxhe55dQBXSauqpgcvhu'

# 授权开关：服务器上必须 true，授权校验才生效
export LICENSE_ENABLED=true
# 管理员令牌：签发/吊销授权用，≥32 位随机串。生成：openssl rand -hex 24
export LICENSE_ADMIN_TOKEN='b044d1470729a0be3cfe5e69698cb4894f78dbfe25aa4f9f'

# 服务器不做上传/OCR，关掉省心
export OCR_ENABLED=false

exec java -Xms512m -Xmx1536m -jar policy-report-demo-0.0.1-SNAPSHOT.jar
```

给执行权限：`chmod +x /www/wwwroot/policy-report/start.sh`

> 两个随机串生成命令（服务器 SSH 跑）：
> - `openssl rand -base64 48` → 填 JWT_SECRET
> - `openssl rand -hex 24`   → 填 LICENSE_ADMIN_TOKEN
> 把 LICENSE_ADMIN_TOKEN **单独记到安全的地方**，之后签发授权要用。

## 5. 用宝塔托管这个服务

宝塔「Java 项目管理器」（没有就在「软件商店」搜安装）→「添加 Java 项目」：

- 项目方式：选「Shell 命令 / jar 包」
- 启动命令：`bash /www/wwwroot/policy-report/start.sh`
- 项目端口：`18080`
- 开机自启：开

启动后看日志，出现 `Started PolicyReportDemoApplication` 即就绪。
本机自测：`curl http://127.0.0.1:18080/api/v2/projects` 能返回 JSON。

## 6. Nginx 反向代理 + HTTPS

宝塔「网站」→「添加站点」，域名填 `api.xieyuchen.xyz`（你的真实域名/子域名，
**不要填 example.com 这种占位示例**，Let's Encrypt 拒发保留域名），PHP 选「纯静态」。

> 用子域名 `api.xieyuchen.xyz` 需先在域名商加一条 A 记录指向本服务器 IP；
> 也可直接用主域名 `xieyuchen.xyz`（已解析则零等待）。

进站点设置：

1. 「反向代理」→ 添加：
   - 代理名称：`policy-report`
   - 目标 URL：`http://127.0.0.1:18080`
   - 发送域名：`$host`
2. 「SSL」→「Let's Encrypt」→ 勾选域名 → 申请（选普通证书，不要选通配符）→
   开启「强制 HTTPS」。

客户端今后连的就是 `https://api.xieyuchen.xyz`。

## 7. 收口防火墙

- 宝塔「安全」：放行 `80`、`443`；**不要**对公网放行 `18080`。
- 云服务商安全组：同样只放 `80`、`443`。
- 18080 只允许 `127.0.0.1` 访问，外部一律走 Nginx + HTTPS。

## 8. 验收

浏览器开 `https://api.xieyuchen.xyz/api/v2/projects` —— 返回 JSON、地址栏是小锁，即部署成功。

此时授权校验已生效（LICENSE_ENABLED=true）：直接访问功能区判定接口会被
`LicenseFilter` 拦截返回 403，这是预期行为——要等步骤 5 签发授权后才放行。

---

## 签发第一张授权（步骤 5 预告）

服务器跑起来后，用 LICENSE_ADMIN_TOKEN 签发授权：

```bash
curl -X POST https://api.xieyuchen.xyz/api/admin/licenses \
  -H "X-Admin-Token: 你的LICENSE_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerName":"某某客户","validDays":365,"note":"首批"}'
```

返回里的 `licenseKey` 就是发给客户、填进客户端的授权码。
