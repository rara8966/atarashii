# Atarashii Handoff Memory

更新时间：2026-05-09

用途：这是给下一位接手者（Claude 或其他 agent）的完整交接文档。目标是不用重新摸索，就能继续维护 `F:\atarashii` 工作区，尤其是 `policy-report-demo` 项目。

重要安全说明：DeepSeek API Key 只允许运行时输入或请求参数传入，不要写入源码、配置、文档、日志、截图或提交记录。此前已搜索过完整 key，项目内 0 匹配。

---

## 1. 工作区总览

当前工作区根目录：`F:\atarashii`

当前 Git 仓库：
- owner/repo：`rara8966/atarashii`
- 当前分支：`main`
- 默认分支：`main`

根目录主要内容：
- `policy-report-demo/`：当前核心项目，建设用地报批审查报告 Demo，React + Spring Boot。
- `土地用地标准/`：土地用地标准资料库，含 DOCX/PDF 大文件。
- `上传文件分类/`：材料分类相关资料。
- `启动.bat`：根目录启动脚本之一。
- `policy-report-demo.zip`、`policy-report-demo-client-deepseek.zip`、`policy-report-demo-client-exe-deepseek.zip`：历史打包产物或客户包。
- `20250508新问题.md`、`建设用地报批审查报告智能生成系统建设问题0508.docx`：需求/问题资料。
- `.venv/`：当前终端激活过的 Python venv，不是这个 Java/React 项目的核心依赖。
- `.copilot-temp/`：临时目录，已在 `.gitignore`。

当前重点项目路径：`F:\atarashii\policy-report-demo`

---

## 2. 用户偏好记忆

用户沟通风格：
- 中文沟通。
- 直接干脆，常说 `doitbro`、`两个都做`、`先写代码，配置我后面填`。
- 不喜欢冗长解释，偏好直接实现、直接给结果。
- 很重视客户可看的效果，属于 demo 驱动。
- 如果能先做出可演示版本，优先先写框架和可运行功能，后续配置再补。

通用开发环境记忆：
- OS：Windows。
- 系统默认 JDK 可能是 JDK 25，但部分项目需要 JDK 17。
- 系统默认 Node 可能是 Node 24，但一些 Vite/esbuild 项目需要 Node 18 LTS。
- 已安装 `fnm`，新终端切 Node 18：
  ```powershell
  fnm env | Invoke-Expression; fnm use 18
  ```
- 此机器存在 esbuild 原生 binary 兼容问题：Go <= 1.22 编译的二进制通过管道输出可能失败，Vite 项目常用 `esbuild-wasm` 规避。
- PowerShell 中直接用 `curl.exe -d` 传 JSON 容易丢双引号；接口自测优先写 `.json` 文件，再用 `--data-binary @file`。
- 写 PowerShell 启动器不要用 `$home`、`$pid` 这类内置变量名。
- 客户 Windows 无依赖场景，优先考虑 JDK `jpackage --type app-image` 打自带 runtime 的包。

其他项目记忆：
- 校园跑跑猫：校园外卖 + 跑腿 + 论坛 + 工单平台；4 端：用户小程序、骑手小程序、商户小程序、管理后台 Web；技术栈 uni-app Vue3 + Java Spring Boot 3.2 + MySQL + Redis。
- `F:\kunrenn`：YOLO 训练平台；`.venv` 原始路径迁移导致 `pip.exe` 启动器坏，必须用 `f:\kunrenn\.venv\Scripts\python.exe -m pip`；RTX 4060 Laptop 8GB，torch 2.11.0+cu126。
- 大文件临时副本、发布快照、打包中间目录不要落 C 盘，优先放 F 盘项目目录下。

---

## 3. policy-report-demo 当前定位

项目名称：`policy-report-demo`

业务目标：建设用地报批审查报告智能生成 Demo。用户上传项目报批材料、政策明白卡、土地用地标准等文件后，系统自动解析、OCR、归档到八个审查步骤、抽取字段、做规则检查，并通过本地模型或 DeepSeek 生成审查意见。

核心演示点：
- 一次性拖入多个材料，自动识别并归档到八步审查。
- 支持 Word、PDF、图片、扫描件。
- Tika 解析文本型 PDF/DOCX。
- Tesseract OCR 解析图片和扫描 PDF。
- DeepSeek 生成完整审查意见。
- 用地标准库文件可识别、可匹配，但不污染项目事实字段。
- 项目库/标准库可在前端页面展示。
- 支持导出 `.md` / `.docx`。
- 支持客户本地输入 DeepSeek Key，不写源码。

八步审查：
1. 项目基本情况
2. 申请用地现状
3. 农用地转用
4. 补充耕地
5. 土地征收
6. 土地利用
7. 地灾压矿
8. 信访违法

本轮重点验证的是第 6 步“土地利用”：OCR 图片、风电用地标准 DOCX、90MB PDF 上传、DeepSeek 审查意见。

---

## 4. 技术栈

后端：
- Java / Spring Boot `3.3.6`
- Maven
- Spring MVC multipart 上传
- Spring Data JPA
- Hibernate ORM `6.5.3.Final`
- H2 文件库：`jdbc:h2:file:./data/policy-report-demo-db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE`
- Apache Tika `2.9.2`
- Apache PDFBox `2.0.31`
- Apache POI `5.2.5`
- Tesseract OCR
- Ollama 本地模型（视觉 OCR / AI 兜底）
- DeepSeek API

前端：
- React `18.3.1`
- TypeScript `5.6.3`
- Vite `5.4.11`
- `lucide-react` `0.468.0`
- `esbuild-wasm` `0.24.0`

端口：
- 后端：`8080`
- 前端：`5173`
- Vite 代理：`/api -> http://localhost:8080`

关键配置：
- 后端配置：`policy-report-demo/backend/src/main/resources/application.yml`
- 前端代理：`policy-report-demo/frontend/vite.config.ts`
- Maven：`policy-report-demo/backend/pom.xml`
- 前端依赖：`policy-report-demo/frontend/package.json`

---

## 5. 运行方式

最省事方式：

```powershell
Set-Location F:\atarashii
.\启动.bat all
```

手动启动后端：

```powershell
Set-Location F:\atarashii\policy-report-demo\backend
mvn spring-boot:run
```

如果只想限制 OCR 页数，适合演示和测试：

```powershell
Set-Location F:\atarashii\policy-report-demo\backend
mvn spring-boot:run '-Dspring-boot.run.arguments=--app.ocr-max-pages=2'
```

手动启动前端：

```powershell
Set-Location F:\atarashii\policy-report-demo\frontend
fnm env | Invoke-Expression; fnm use 18
npm run dev
```

访问地址：
- 前端：`http://127.0.0.1:5173/`
- 另一个 Vite 页面可能在 `http://127.0.0.1:5174/`，但主要演示用 `5173`。
- 工作流静态页：`file:///F:/atarashii/policy-report-demo/ocr-deepseek-workflow.html`

构建验证命令：

```powershell
Set-Location F:\atarashii\policy-report-demo\backend
mvn -DskipTests compile
```

```powershell
Set-Location F:\atarashii\policy-report-demo\frontend
npm run build
```

最近验证结果：
- 后端：`BUILD SUCCESS`，总耗时约 `0.977 s`。
- 前端：`tsc -b && vite build` 成功，`1582 modules transformed`，`built in 1.95s`。

---

## 6. OCR / AI 配置

`application.yml` 当前关键配置：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 300MB
      max-request-size: 320MB

app:
  ollama-base-url: http://localhost:11434
  ollama-model: qwen25vl-7b-local:latest
  ollama-fallback-model: qwen25vl-3b-local:latest
  ollama-enabled: true
  ollama-timeout-seconds: 120
  ai-full-text-chunk-size: 12000
  ai-full-text-max-chunks: 80
  ocr-enabled: true
  ocr-engine: auto
  ocr-max-pages: 120
  ocr-dpi: 160
  ocr-min-useful-chars: 1500
  ocr-tesseract-command: C:/Program Files/Tesseract-OCR/tesseract.exe
  ocr-tesseract-language: chi_sim+eng
  ocr-tesseract-page-seg-mode: 6
  ocr-tesseract-timeout-seconds: 90
```

OCR 记忆：
- 本机已安装 Tesseract：`C:\Program Files\Tesseract-OCR\tesseract.exe`
- 语言：`chi_sim+eng`
- `app.ocr-engine=auto`：优先 Tesseract，必要时可走本地视觉模型兜底。
- 演示时建议用 `--app.ocr-max-pages=2`，避免大 PDF OCR 太久。
- 大 PDF 只验证上传/Tika 时可临时关闭 OCR：`--app.ocr-enabled=false`

AI 记忆：
- 本地 Ollama 默认模型：`qwen25vl-7b-local:latest`
- fallback：`qwen25vl-3b-local:latest`
- DeepSeek 参数通过接口请求传入：
  - `aiProvider=deepseek`
  - `deepseekModel=deepseek-chat`
  - `deepseekApiKey=运行时传入，不要落盘`

---

## 7. 后端核心文件与职责

路径：`policy-report-demo/backend/src/main/java/com/atarashii/policyreport`

入口：
- `PolicyReportDemoApplication.java`：Spring Boot 启动类。

配置：
- `config/AppProperties.java`：统一读取 app 配置，包含政策文件路径、Ollama、AI 分块、OCR 配置。
- `application.yml`：端口、H2、multipart、OCR、Ollama 等配置。

Controller：
- `controller/DemoController.java`
  - Demo 主 API。
  - 关键接口：
    - `GET /api/workspace/state?projectId=...`
    - `PUT /api/workspace/fields`
    - `PUT /api/workspace/situations`
    - `POST /api/documents/analyze`
  - `POST /documents/analyze` 接收：`file`、`targetStep`、`fallbackStep`、`aiProvider`、`deepseekApiKey`、`deepseekModel`、`projectId`。
- `controller/ProjectLibraryController.java`
  - 项目库/标准库相关接口，当前为新增未跟踪文件。

模型：
- `model/DemoModels.java`
  - Demo 前后端共享 DTO。
  - 包含 workspace、step、field、analysis、AI advice、project record、standard 等模型。

文件解析与 OCR：
- `service/TikaDocumentParser.java`
  - Apache Tika 文本解析。
  - 用于 DOCX/PDF 等文件抽取文本。
- `service/OcrService.java`
  - 新增 OCR 服务。
  - 支持 Tesseract OCR。
  - 支持图片、扫描 PDF 页面 OCR。
  - 根据配置控制页数、DPI、语言、超时等。
- `service/UploadedFileService.java`
  - 保存上传原件到 `backend/data/uploads`。
  - 返回 `sourceFileId`，前端可通过 `/api/documents/source/{fileId}` 打开原件。

AI：
- `service/OllamaClient.java`
  - 调用本地 Ollama 模型。
  - 当前增加/调整了图文分析和 fallback 相关逻辑。
- `service/DeepSeekClient.java`
  - 调用 DeepSeek API。
  - 不持久化 key。

文档分析主流程：
- `service/DocumentAnalysisService.java`
  - 最核心文件。
  - 流程：上传文件 -> Tika 文本解析 -> 保存原件 -> OCR 兜底/增强 -> 文件类型识别 -> 自动归档步骤 -> 字段抽取 -> 政策校验 -> AI advice -> 合并到 workspace state。
  - 关键保护：若识别为“用地标准库文件”，字段列表强制为空，避免标准条文污染项目事实字段。
  - 重要逻辑形态：
    ```java
    List<ExtractedField> fields = isStandardLibraryDocument(documentType)
        ? List.of()
        : attachSourceFile(extractFields(text), fileId);
    ```

工作区状态：
- `service/WorkspaceStateService.java`
  - 保存八步工作台状态和上传分析结果。
  - 默认状态：`backend/data/workspace-state.json`
  - 项目状态：`backend/data/projects/{projectId}/workspace-state.json`
  - 重要坑：前端/API 使用数据库 UUID 作为 `projectId`，不是 `PRJ-...` 项目编号。

项目库：
- `service/DemoProjectService.java`
  - Demo 项目服务。
- `service/ProjectRecordService.java`
  - 项目记录服务，当前新增。
- `service/ProjectTypeCatalog.java`
  - 项目类型目录，当前新增。
- `persistence/`
  - JPA entity/repository 包，当前新增。
  - 用于项目库、标准库、持久化记录。

用地标准库：
- `service/LandUseStandardService.java`
  - 标准库导入/查询服务，当前新增。
- `service/LandUseStandardMatchService.java`
  - 第六步“土地利用”标准匹配。
  - 支持风电单位和面积换算：`m²/台`、`hm²/km`、`hm²/MW`、绝对面积估算。
- `backend/src/main/resources/land-standards/`
  - 启动导入 DOCX 标准来源。

---

## 8. 前端核心文件与职责

路径：`policy-report-demo/frontend/src`

入口与页面：
- `App.tsx`
  - 主工作台页面。
  - 支持项目库、项目创建/打开、八步导航、材料上传、字段展示、政策检查、AI 完整意见、标准库匹配。
  - 关键渲染：`ParsedFileCards` 展示解析卡片；`AiAdvicePanel` 展示 AI 完整审查意见。
  - Step 6 页面会显示标准库匹配结果和复核项。

接口封装：
- `api.ts`
  - `fetchWorkspaceState(projectId)`
  - `fetchProjectDashboard()`
  - `createProject(payload)`
  - `fetchLandStandards()`
  - `fetchStandardMatches()`
  - `refreshStandardMatches()`
  - `updateField()`
  - `withProject(path, projectId)` 会把 UUID 作为 query 参数拼到 API。

类型：
- `types.ts`
  - 前端 DTO 类型，已扩充项目库、标准库、AI advice、OCR/analysis 等类型。

样式：
- `styles.css`
  - 工作台、卡片、上传区、项目库、标准库、AI 面板等样式。

配置：
- `vite.config.ts`
  - Vite 端口 `5173`。
  - `/api` 代理到 `http://localhost:8080`。

---

## 9. 当前已完成验证

### 9.1 真实风电标准 DOCX + DeepSeek

测试文件：

`F:\atarashii\土地用地标准\土地用地标准（文件提取）\2.电力工程项目建设用地指标（风电场）\电力工程项目建设用地指标（风电场）.docx`

结果：
- 后端识别为：`用地标准库文件`
- 字段数：`0`
- DeepSeek 能给出审查意见。
- 标准条文没有写入项目事实字段。

意义：标准库隔离目标验证通过。

### 9.2 模拟扫描图片 + Tesseract OCR + DeepSeek

测试文件：

`F:\atarashii\policy-report-demo\.copilot-temp\ocr-test.png`

OCR 结果抽取字段：
- `申报面积: 12.3公顷`
- `供地方式: 划拨`

结果：
- OCR 引擎：`tesseract-chi_sim+eng`
- 文本长度：约 `62` 字。
- 字段数：`2`
- DeepSeek 基于 OCR 文本生成五段式审查意见。

意义：没有真实扫描材料时，也可以用假扫描图跑完整 OCR + AI 链路。

### 9.3 90.5MB PDF 上传验证

测试文件：

`F:\atarashii\土地用地标准\土地用地标准（文件提取）\（已压缩）土地使用标准汇编（上册）_1-500.pdf`

验证方式：临时启动关闭 OCR 的后端，只验证 multipart 上传上限和 Tika 路径。

结果：
- HTTP：`200`
- 文件大小：`90.5 MB`
- DocType：`用地标准库文件`
- TextLength：`13412`
- Fields：`0`
- Checks：`已完成Tika文本解析 | 政策明白卡加载 | 标准库文件识别`

意义：`300MB` 上传限制生效。

注意：曾尝试 90MB PDF 带 OCR，命令没有生成响应文件且退出码 1，未深挖；建议后续大 PDF 先拆成“上传/Tika”和“OCR 页数”分别测。

### 9.4 前端 Step 6 展示验证

测试项目：
- 项目名称：`OCR与DeepSeek测试项目`
- 建设单位：`Copilot测试建设单位`
- 建设地点：`测试县测试镇`
- 项目编号：`PRJ-20260509-0007`
- 实际数据库 UUID：`f04ed0d9-ab13-4eb9-a050-178537990586`

重要：前端用 UUID，不用 `PRJ-...` 编号。

Step 6 页面验证：
- 能看到风电标准 DOCX 解析卡片。
- 能看到 OCR 图片解析卡片。
- 卡片显示 `deepseek · deepseek-chat`。
- 能看到 `AI完整审查意见`。
- 材料清单中显示已上传 OCR 与标准库文件。

前端状态：`step6.analyses=2`

### 9.5 构建与安全检查

后端：

```text
mvn -DskipTests compile
BUILD SUCCESS
```

前端：

```text
npm run build
tsc -b && vite build
built successfully
```

DeepSeek Key 搜索：

```text
No matches found
```

---

## 10. 当前浏览器页面

VS Code browser 当前可交互页面：
- `789c7191-040e-4ba2-a592-6eaa2300be83`：建设用地报批审查报告 Demo，`http://127.0.0.1:5173/`
- `6551b1bf-0677-429f-a5a9-7d6c69d92b4e`：建设用地报批审查报告 Demo，`http://127.0.0.1:5174/`
- `70ad7f16-8137-440c-98cb-72911606ab6e`：`about:blank`，当前 active

优先使用：`http://127.0.0.1:5173/`

---

## 11. 当前终端/服务状态记忆

最近保留过的演示服务：
- 后端 `8080`
- 前端 `5173`

已经清理过的临时测试服务：
- `18080`：OCR/DeepSeek 临时 H2 内存库服务，已 kill。
- `18081`：上传-only 临时 H2 内存库服务，已 kill。

注意：终端列表里有多个历史 `mvn spring-boot:run`，有的 exit code 1 是端口冲突或旧进程被终止，不代表当前代码构建失败。最终以 `mvn -DskipTests compile` 成功为准。

2026-05-09 21:23 补充：终端 `dd2c7cf6-ba12-4602-976c-c5b6225c7bd7` 中的后端曾正常启动在 `8080` 并运行约 45 分钟，最后因进程被终止显示 Maven `BUILD FAILURE` / `Process terminated with exit code: -1`。这不是编译失败；同一轮随后健康检查结果为 `Backend8080=200`、`Frontend5173=200`。

同一终端曾出现两条 `JSON parse error: Invalid UTF-8 start byte 0xb2` 警告，通常是 PowerShell/curl 发送 JSON 编码或中文请求体编码不对；接口自测优先使用 UTF-8 文件和 `curl.exe --data-binary @file`。

---

## 12. 关键交接文件

已新增客户可看工作流：
- `policy-report-demo/ocr-deepseek-workflow.html`

打开方式：

```text
file:///F:/atarashii/policy-report-demo/ocr-deepseek-workflow.html
```

页面内容：
- OCR + DeepSeek 审查链路。
- 五步流程：材料输入、基础解析、OCR 分支、审查加工、结果输出。
- 本轮验证结果。
- 关键保护点。
- 接口与配置说明。

其他文档：
- `policy-report-demo/客户演示说明.md`
- `policy-report-demo/首页项目库与用地标准库实施方案.md`
- 注意：在 PowerShell/git 输出中中文文件名可能显示为 mojibake，例如 `瀹㈡埛...`，实际文件名在资源管理器/VS Code 里正常。

---

## 13. 当前 Git 改动摘要

`policy-report-demo` 当前有较多修改和新增。

已修改：
- `policy-report-demo/README-EXE.md`
- `policy-report-demo/backend/pom.xml`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/config/AppProperties.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/controller/DemoController.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/model/DemoModels.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/DeepSeekClient.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/DemoProjectService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/DocumentAnalysisService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/OllamaClient.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/TikaDocumentParser.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/UploadedFileService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/WorkspaceStateService.java`
- `policy-report-demo/backend/src/main/resources/application.yml`
- `policy-report-demo/frontend/src/App.tsx`
- `policy-report-demo/frontend/src/api.ts`
- `policy-report-demo/frontend/src/styles.css`
- `policy-report-demo/frontend/src/types.ts`

新增未跟踪：
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/controller/ProjectLibraryController.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/persistence/`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/LandUseStandardMatchService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/LandUseStandardService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/OcrService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/ProjectRecordService.java`
- `policy-report-demo/backend/src/main/java/com/atarashii/policyreport/service/ProjectTypeCatalog.java`
- `policy-report-demo/backend/src/main/resources/land-standards/`
- `policy-report-demo/ocr-deepseek-workflow.html`
- `policy-report-demo/客户演示说明.md`
- `policy-report-demo/首页项目库与用地标准库实施方案.md`

diff stat 最近摘要：
- `17 files changed`
- 约 `1518 insertions`，`160 deletions`
- stat 未包含未跟踪新增文件内容。

注意：仓库根目录还有大量土地标准 DOCX/PDF 可能在 Git changed files 中出现，很多是用户资料/标准库，不一定是本轮代码改动。提交前务必筛选，避免误提交巨大文件或临时数据。

---

## 14. 临时文件和本地数据

`.gitignore` 已忽略：
- `.copilot-temp/`
- `.runtime/`
- `*.zip`
- `*.exe`
- `*.msi`
- `**/target/`
- `**/node_modules/`
- `**/dist/`
- `**/data/`
- `report-*.json` 等测试输出。

重要本地数据：
- 上传原件：`policy-report-demo/backend/data/uploads`
- 默认 workspace：`policy-report-demo/backend/data/workspace-state.json`
- 项目 workspace：`policy-report-demo/backend/data/projects/{uuid}/workspace-state.json`
- H2 数据库：`policy-report-demo/backend/data/policy-report-demo-db*`

这些属于本地演示状态，一般不要提交。

当前已知临时测试文件：
- `policy-report-demo/.copilot-temp/ocr-test.png`
- `policy-report-demo/.copilot-temp/ui-uuid-project-ocr-deepseek.json`
- `policy-report-demo/.copilot-temp/ui-uuid-project-standard-deepseek.json`
- `policy-report-demo/.copilot-temp/land-standard-pdf-upper-90mb-upload-only.json`

错误 project state 提醒：
- 曾误用 `PRJ-20260509-0007` 作为 `projectId`，导致生成：
  `policy-report-demo/backend/data/projects/PRJ-20260509-0007/workspace-state.json`
- 前端实际不用这个目录。
- 正确项目 UUID：`f04ed0d9-ab13-4eb9-a050-178537990586`

---

## 15. 重要坑点

1. `projectId` 坑：
   - 前端/API 使用数据库 UUID，不是项目编号。
   - `PRJ-20260509-0007` 是展示编号；真实 ID 是 `f04ed0d9-ab13-4eb9-a050-178537990586`。
   - 用编号写入 state，前端页面不会显示。

2. DeepSeek Key 坑：
   - 不能写入项目文件。
   - 页面可本地浏览器保存，或请求参数临时传入。
   - 交接、截图、日志都不要包含完整 key。

3. 大 PDF + OCR 坑：
   - 90MB PDF 带 OCR 很慢且容易因为命令/连接超时失败。
   - 先关闭 OCR 验证上传/Tika，再限制 OCR 页数单测。

4. VS Code Java Problems 坑：
   - 有时会显示 `non-project file` 或 package mismatch 噪声。
   - Maven 编译已成功，优先相信 `mvn -DskipTests compile`。

5. PowerShell 中文编码坑：
   - 终端里中文文件名或项目名可能乱码。
   - 浏览器/VS Code 文件树通常正常。

6. 点击自动化坑：
   - 浏览器自动化点击“创建项目并进入工作台”或“打开”时可能等 stable 超时。
   - 可以用 force click 或 DOM click。

7. `rg` 不可用：
   - 当前 PowerShell 环境执行 `rg` 报“无法将 rg 项识别”。
   - 用 `Get-ChildItem` / VS Code search / grep_search 工具代替。

8. Node/esbuild 坑：
   - 本机 Node 默认可能不是 18。
   - 前端如果安装依赖后 Vite/esbuild 异常，检查 `esbuild-wasm` postinstall 和 Node 版本。

---

## 16. 后续建议任务

高优先级：
- 整理 Git 改动，区分代码、文档、标准库资料、临时数据。
- 不要提交 `backend/data/`、`.copilot-temp/`、`.runtime/`、`target/`、`dist/`、zip/exe 等生成物。
- 确认 DeepSeek Key 仍未落盘。
- 若准备提交，先跑：
  ```powershell
  Set-Location F:\atarashii\policy-report-demo\backend
  mvn -DskipTests compile
  ```
  ```powershell
  Set-Location F:\atarashii\policy-report-demo\frontend
  npm run build
  ```

可选清理：
- 删除错误编号 state：`backend/data/projects/PRJ-20260509-0007/`，但清理前确认用户不需要调试痕迹。
- 清理 `.copilot-temp` 中不需要的响应 JSON。

可选增强：
- 在前端/后端文档中明确 `projectId` 必须用 UUID。
- 给客户演示说明补充 `ocr-deepseek-workflow.html` 打开方式。
- 将截图真正导出到项目目录，便于发客户。
- 对 90MB PDF OCR 做异步任务/进度条，避免同步请求超时。

---

## 17. 可用 Skills 记忆

这些是当前 Copilot 环境可用的 skills。Claude 不一定能直接调用，但可按职责理解。

1. `get-search-view-results`
   - 获取 VS Code Search 视图当前搜索结果。

2. `agent-customization`
   - 创建、更新、审查、修复 VS Code agent customization 文件。
   - 适用于 `.instructions.md`、`.prompt.md`、`.agent.md`、`SKILL.md`、`copilot-instructions.md`、`AGENTS.md`。

3. `summarize-github-issue-pr-notification`
   - 总结 GitHub issue、PR 或通知。

4. `suggest-fix-issue`
   - 根据 issue 详情建议修复方案。

5. `form-github-search-query`
   - 根据自然语言需求生成 GitHub issue/PR 搜索 query。

6. `show-github-search-result`
   - 用表格总结 GitHub 搜索结果。

7. `address-pr-comments`
   - 处理当前 PR 的 review comments，包括 Copilot comments。

8. `create-pull-request`
   - 从当前或指定分支创建 GitHub PR。

9. `typescript-upgrade`
   - 升级 TypeScript 项目的 npm dependencies，并处理 breaking changes。

10. `modernization-integration-tests`
    - Java 现代化项目集成测试技能。
    - 支持 Layer 1 TestContainers、Layer 2 Smoke、Layer 3 Azure Integration、Layer 4 Behavioral Comparison。

---

## 18. 可用 Agents 记忆

这些是当前 Copilot 环境可调 subagents：

- `modernize-azure-java`：Java 应用现代化。
- `modernize-java-assessment`：基于证据评估 Java 代码库。
- `modernize-azure-dotnet`：.NET 应用现代化。
- `modernize-java-upgrade`：Java/Spring Boot 等升级，Java 升级请求应优先交给它。
- `modernize-java-security`：扫描并修复 Java 依赖 CVE、废弃 API 等安全问题。
- `Explore`：只读代码库探索和问答；适合快速搜索、读文件、建立上下文。

---

## 19. 当前成功标准

如果 Claude 接手后要判断“当前状态是否健康”，看这些：

- `mvn -DskipTests compile` 通过。
- `npm run build` 通过。
- 打开 `http://127.0.0.1:5173/` 能看到建设用地报批审查报告 Demo。
- Step 6 能展示 OCR 图片和风电标准 DOCX 的解析卡片。
- OCR 图片卡片字段数为 2 左右，包含申报面积、供地方式。
- 风电标准 DOCX 被识别为“用地标准库文件”，字段数为 0。
- DeepSeek 卡片显示 `deepseek · deepseek-chat`。
- `ocr-deepseek-workflow.html` 能打开并正常展示流程图。
- 搜索完整 DeepSeek Key 应该 0 匹配。

---

## 20. 给 Claude 的接手建议

1. 先不要大改，先跑构建确认状态。
2. 先看 `policy-report-demo/ocr-deepseek-workflow.html`，理解用户要给客户看的效果。
3. 如果页面没有解析卡片，先检查 `projectId` 是不是 UUID。
4. 如果 OCR 慢，启动时加 `--app.ocr-max-pages=2`。
5. 如果要提交，先过滤掉大文件、临时文件、本地数据和 API Key。
6. 用户偏好是直接做、少废话、要能演示，所以回复尽量给结论、文件、命令、验证结果。

---

Copilot 留言：这个项目现在最重要的链路已经跑通了：上传材料 -> Tika/OCR -> 标准库隔离 -> DeepSeek 审查 -> 前端 Step 6 展示 -> 工作流 HTML 给客户看。后面主要是收尾、清理、打包和把体验再磨顺。