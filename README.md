# 建设用地报批审查报告智能生成系统

政策模板驱动的建设用地报批审查报告智能生成与合规判定系统。
材料上传 → Tika/OCR/视觉解析 → 标准库匹配 → AI 审查 → 八步工作台 + 功能区合规判定。

> 项目根目录下的非项目文件（个人简历、其他工作目录等）不纳入仓库；实际项目代码在 [`policy-report-demo/`](./policy-report-demo/)。

## 架构

```
                       ┌─────────────────────────────────────┐
                       │  云端服务器 (policy.xieyuchen.xyz)    │
                       │  · 在线授权校验 (LicenseFilter)        │
                       │  · 功能区合规判定算法                  │
                       │    (FunctionalZoneVerdictService)    │
                       │  · 标准库 (MySQL)                     │
                       └────────▲────────────────────────────┘
                                │ HTTPS + X-License-Key
                                │ X-Machine-Id
   ┌────────────────────────────┴──────┐
   │  客户端 Electron 应用              │
   │  · 每次启动需联网激活               │
   │  · 上传 / OCR / 预览 (本地 H2)      │
   │  · 功能区判定 → 远程调用云端        │
   └──────────────────────────────────┘
```

两种构建产物来自同一份后端代码：

- **服务器版**（默认 profile）：完整后端，含功能区判定算法，部署到自己的服务器。
- **客户端版**（`-P client`）：通过 Maven profile 物理剔除 `FunctionalZoneVerdictService.java`，替换为 `RemoteZoneVerdictService` 走云调用；数据源切到本地 H2。

## 授权模型

- 客户端每次启动弹激活窗，强制输入授权码 + 联网激活；不缓存。
- 服务器对授权码做机器指纹绑定（首次激活），后续每次判定 API 校验。
- 吊销授权 → 该客户软件**下次启动直接打不开**（不只是判定失效）。

## 技术栈

- 后端：Spring Boot 3.3.6 + Java 17、JPA、MySQL/H2、JJWT、Tika、PDFBox、Tesseract OCR
- 前端：React 18 + TypeScript 5.6 + Vite 5.4
- 客户端：Electron 33 + electron-builder + 内置 JRE（jlink）
- 大模型：DeepSeek / 豆包（视觉）/ Ollama（本地回退）

## 目录结构

```
policy-report-demo/
├── backend/                  Spring Boot 后端
│   ├── pom.xml               含 -P client profile（剔算法 + H2 依赖）
│   └── src/main/resources/
│       ├── application.yml          主配置
│       ├── application-client.yml   client profile 覆盖（H2 数据源）
│       └── application-local.yml.example   本地密钥模板（真实文件 gitignore）
├── frontend/                 React + Vite（build 直接落到 backend static）
├── client-electron/          Electron 外壳工程
│   ├── main.js               启动激活流程 + spawn jar
│   ├── activation.html       激活窗口
│   ├── preload.js            渲染进程桥接
│   ├── build.ps1             jlink JRE + electron-builder 一键出安装包
│   └── package.json
├── tools/
│   ├── 签发授权.bat            授权签发/吊销 GUI 入口
│   ├── issue-license.ps1     底层 PowerShell 脚本
│   └── admin-token.txt       管理令牌（gitignore）
└── DEPLOY-SERVER.md          宝塔服务器部署指南
```

## 构建

### 前置

- JDK 17、Maven 3.9+
- Node.js 20+
- 出 Windows 安装包还需要 Windows 系统（electron-builder 调 NSIS）

### 服务器版

```powershell
cd policy-report-demo/backend
mvn -DskipTests clean package
# 产物：target/policy-report-demo-0.0.1-SNAPSHOT.jar
```

部署见 [`policy-report-demo/DEPLOY-SERVER.md`](policy-report-demo/DEPLOY-SERVER.md)。

### 客户端版（Windows 安装包）

```powershell
# 1. 前端构建（vite 已配 outDir 到 backend static）
cd policy-report-demo/frontend
npm install
npm run build

# 2. 客户端 jar：-P client 物理剔除功能区判定算法
cd ../backend
mvn -P client -DskipTests clean package

# 3. Electron 外壳：jlink 裁剪 JRE + electron-builder
cd ../client-electron
powershell -ExecutionPolicy Bypass -File build.ps1
# 产物：
#   dist/建设用地报批审查系统-安装版-2.0.0.exe   （NSIS 安装包，约 230 MB）
#   dist/win-unpacked/                       （免安装版整目录，可压缩后发出）
```

## 授权管理

服务器启动后，在本机用 `tools/签发授权.bat`（双击）：

| 选项 | 作用 |
|---|---|
| 1 | 签发新授权，输入客户名 + 有效天数，得到 `PRD-XXXX-XXXX-XXXX-XXXX` |
| 2 | 列出所有授权（状态 / 是否绑定 / 到期时间） |
| 3 | 吊销授权（客户下次启动直接打不开） |

`admin-token.txt` 存放管理令牌，本仓库 gitignore，不会被提交。

## 本地开发

后端：

```powershell
# 拷模板填密钥
cp backend/src/main/resources/application-local.yml.example `
   backend/src/main/resources/application-local.yml
$env:JAVA_HOME="<JDK17 安装路径>"
mvn -f backend/pom.xml spring-boot:run
```

前端：

```powershell
cd frontend
npm run dev   # http://localhost:5173, /api 已代理到 8080
```


