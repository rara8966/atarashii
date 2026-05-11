# 建设用地报批审查报告系统 — 重构计划

> 来源：2026-05-10 多方会议结论 + `admin-project-workflow.html` 流程设计稿  
> 当前系统：Spring Boot 3.3.6 + React 18 + H2  
> 目标：MySQL 8.4（库名 `atarashii`）+ 完整用户/项目/文件状态机 + 异步 AI 管道

---

## 什么不动

| 模块 | 理由 |
|---|---|
| 文档解析引擎（Tika + OCR + Doubao + Ollama/DeepSeek） | 核心能力，全部复用 |
| 标准库数据（476 条，9 类项目） | 数据迁移到 MySQL，接口不变 |
| 八步审查规则与材料清单定义 | 业务逻辑不变，只改展示层 |
| 报告生成（Markdown → DOCX） | 直接复用 |
| 前端基础 UI 组件（卡片、表格、标签） | 样式沿用，只新增/重组页面 |

---

## 数据库建设（MySQL 8.4，库名 atarashii）

### 建库 SQL

```sql
CREATE DATABASE IF NOT EXISTS atarashii
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'atarashii'@'localhost'
  IDENTIFIED BY '123456';

GRANT ALL PRIVILEGES ON atarashii.* TO 'atarashii'@'localhost';
FLUSH PRIVILEGES;
```

> Spring Boot 连接串：
> `spring.datasource.url=jdbc:mysql://localhost:3306/atarashii?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai`  
> `spring.datasource.username=atarashii`  
> `spring.datasource.password=123456`

### 表结构

```sql
-- 用户表
CREATE TABLE users (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,           -- BCrypt
  role        ENUM('ADMIN','REPORTER','VIEWER') NOT NULL DEFAULT 'REPORTER',
  enabled     TINYINT(1)   NOT NULL DEFAULT 1,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 项目表
CREATE TABLE projects (
  id              CHAR(36)     NOT NULL PRIMARY KEY,     -- UUID
  display_no      VARCHAR(32)  NOT NULL UNIQUE,          -- 系统编号，如 PRJ-20260001
  name            VARCHAR(255),
  construction_unit VARCHAR(255),
  construction_site VARCHAR(500),
  project_type    VARCHAR(64),                           -- 公路/铁路/风电... 等 9 类
  standard_set    VARCHAR(64),                           -- 命中的标准包 key
  status          ENUM(
                    'DRAFT',            -- 草稿（未上传）
                    'UPLOADING',        -- 上传中
                    'ANALYZING',        -- AI 后台分析中
                    'TYPE_CONFIRM',     -- 待用户确认项目类型
                    'INFO_CONFIRM',     -- 待补三项基本信息
                    'IN_PROGRESS',      -- 八步工作台进行中
                    'PREVIEWING',       -- 预览报告已生成
                    'EXPORTED'          -- 已导出正式报告
                  ) NOT NULL DEFAULT 'DRAFT',
  created_by      BIGINT UNSIGNED,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 文件表
CREATE TABLE project_files (
  id              CHAR(36)     NOT NULL PRIMARY KEY,     -- UUID
  project_id      CHAR(36)     NOT NULL,
  original_name   VARCHAR(500) NOT NULL,
  storage_path    VARCHAR(1000) NOT NULL,
  mime_type       VARCHAR(128),
  page_count      INT,
  upload_batch    VARCHAR(36),                           -- 同一次拖入的批次号
  ai_suggested_step TINYINT,                             -- AI 建议归属步骤 1-8，NULL=待确认
  current_step    TINYINT,                               -- 用户当前指定步骤，NULL=待确认区
  confirmed_by_user TINYINT(1) NOT NULL DEFAULT 0,       -- 用户是否手动确认过归属
  analysis_status ENUM('PENDING','RUNNING','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- 文件分析结果表（与 project_files 1:1）
CREATE TABLE file_analyses (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  file_id         CHAR(36) NOT NULL UNIQUE,
  project_id      CHAR(36) NOT NULL,
  extracted_text  LONGTEXT,
  extracted_fields JSON,      -- [{key, value, source_page, confidence}]
  ocr_used        TINYINT(1) NOT NULL DEFAULT 0,
  doubao_used     TINYINT(1) NOT NULL DEFAULT 0,
  ai_summary      TEXT,
  ai_advice       TEXT,
  analyzed_at     DATETIME,
  FOREIGN KEY (file_id) REFERENCES project_files(id),
  FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- 步骤判定结果表
CREATE TABLE step_verdicts (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id  CHAR(36) NOT NULL,
  step_no     TINYINT NOT NULL,                         -- 1-8
  verdict     ENUM('PASS','WARN','FAIL','PENDING') NOT NULL DEFAULT 'PENDING',
  pass_items  JSON,           -- [{rule_key, matched_file_id, matched_field, desc}]
  warn_items  JSON,
  fail_items  JSON,           -- [{rule_key, missing_type, desc}]
  generated_at DATETIME,
  FOREIGN KEY (project_id) REFERENCES projects(id),
  UNIQUE KEY uq_project_step (project_id, step_no)
);

-- 操作日志表（撤回/改挂/补传的审计链）
CREATE TABLE operation_logs (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id  CHAR(36) NOT NULL,
  file_id     CHAR(36),
  action      ENUM(
                'FILE_UPLOAD',
                'STEP_ASSIGN',      -- AI 自动分配
                'STEP_REASSIGN',    -- 用户手动改挂
                'STEP_RETRACT',     -- 用户撤回到待确认区
                'TYPE_CONFIRM',     -- 用户确认项目类型
                'TYPE_CHANGE',      -- 用户更改项目类型
                'PREVIEW_GEN',      -- 预览报告生成
                'EXPORT'            -- 正式导出
              ) NOT NULL,
  from_step   TINYINT,
  to_step     TINYINT,
  note        VARCHAR(500),
  operated_by BIGINT UNSIGNED,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- 预览快照表
CREATE TABLE preview_snapshots (
  id              CHAR(36) NOT NULL PRIMARY KEY,
  project_id      CHAR(36) NOT NULL UNIQUE,             -- 一个项目最新快照
  snapshot_data   LONGTEXT NOT NULL,                     -- 冻结的 JSON 报告内容
  status          ENUM('GENERATING','READY','CONFIRMED') NOT NULL DEFAULT 'GENERATING',
  generated_at    DATETIME,
  confirmed_at    DATETIME,
  FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- 标准库表（从 H2 迁移，结构不变）
CREATE TABLE land_use_standards (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  document_type   VARCHAR(128) NOT NULL,
  project_type_key VARCHAR(64) NOT NULL,
  step_no         TINYINT,
  standard_key    VARCHAR(128),
  standard_name   VARCHAR(500),
  requirement     TEXT,
  threshold_value DECIMAL(18,4),
  threshold_unit  VARCHAR(32),
  source_document VARCHAR(255),
  source_page     INT,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 分阶段实施计划

### 阶段一：基础设施 + 认证 + 项目壳

**目标**：新版首页（登录）+ 项目总览页跑通，数据库切换到 MySQL。

**后端任务**
- [ ] 切换 `pom.xml`：删 H2 依赖，加 `mysql-connector-j` + `spring-security-starter` + `jjwt`
- [ ] 修改 `application.yml`：数据源换 MySQL，`ddl-auto=validate`（Schema 手动建）
- [ ] 新建 `User` 实体 + `UserRepository`
- [ ] 实现 JWT 登录接口：`POST /api/auth/login` → 返回 token
- [ ] 实现注册接口：`POST /api/auth/register`（管理员初期可直接建账号）
- [ ] `SecurityConfig` 放开 `/api/auth/**`，其他接口要求 Bearer token
- [ ] `ProjectRecord` 实体改造：加 `displayNo`、`status`（状态机）、`createdBy`、`standardSet`
- [ ] 实现项目列表接口：`GET /api/projects` → 支持草稿/历史过滤
- [ ] 实现创建项目接口：`POST /api/projects` → 生成 UUID + displayNo，状态 `DRAFT`

**前端任务**
- [ ] 新建页面 `pages/Login.tsx`：账号密码输入 + 角色 chip（当前只做管理员）
- [ ] 新建页面 `pages/ProjectList.tsx`：项目列表（草稿箱 + 历史），新建项目按钮
- [ ] 前端加 `AuthContext`，token 存 `localStorage`，`axios` 拦截器带 `Authorization`
- [ ] `App.tsx` 加路由守卫：未登录跳 `/login`

**测试完成标准**：能注册/登录，登录后看到项目列表（可能为空），能创建项目拿到系统编号。

---

### 阶段二：批量上传 + 异步 AI 分析管道

**目标**：上传完立即触发后台分析，前台不用等，能实时看文件分析状态。

**后端任务**
- [ ] 新建 `ProjectFile` 实体 + `ProjectFileRepository`
- [ ] 新建 `FileAnalysis` 实体 + `FileAnalysisRepository`
- [ ] 新建 `OperationLog` 实体 + `OperationLogRepository`
- [ ] 上传接口改造：`POST /api/projects/{id}/files/batch`  
  - 接收多文件，写入 `project_files` 表（status=`PENDING`）  
  - 同步返回文件清单（不等分析完）  
  - 异步触发分析任务（`@Async` + Spring `TaskExecutor`）
- [ ] 新建 `AsyncAnalysisService`：
  - 从现有 `DocumentAnalysisService` 复用 Tika/OCR/Doubao/DeepSeek 流程
  - 分析完后写 `file_analyses` 表，更新 `project_files.analysis_status = DONE`
  - 分析完后对项目所有文件做文件名聚合，推断 `ai_suggested_step` 并写回
- [ ] 新建轮询接口：`GET /api/projects/{id}/analysis-status` → 返回每个文件的分析进度

**前端任务**
- [ ] 新建页面 `pages/ProjectUpload.tsx`：全量拖拽上传区（不按步骤分，先全部丢进来）
- [ ] 上传完成后轮询 `/analysis-status`，卡片实时显示 `分析中 / 已完成`
- [ ] 文件卡片展示：文件名、类型、AI 建议步骤（分析完后显示）、分析状态徽章

**测试完成标准**：拖入 10 个文件，前台立即显示上传成功，后台异步分析，前台卡片状态从"分析中"变"已完成"，AI 建议步骤回填到卡片。

---

### 阶段三：项目类型确认 + 标准库路由

**目标**：AI 给出项目分类建议，用户确认后系统装载对应标准包，填写三项基本信息后进入八步。

**后端任务**
- [ ] 新建接口：`GET /api/projects/{id}/type-suggestion` → 聚合文件分析结果，返回推荐的 `projectType`
- [ ] 新建接口：`PUT /api/projects/{id}/confirm-type` → 用户确认或更换类型，写 `project_type` + `standard_set`，记操作日志
- [ ] 标准路由表（枚举或配置）：9 类项目 key → 对应 `land_use_standards.project_type_key`
- [ ] 新建接口：`PUT /api/projects/{id}/confirm-info` → 写项目名称/建设单位/建设地点，状态推进到 `IN_PROGRESS`

**前端任务**
- [ ] 新建页面 `pages/TypeConfirm.tsx`：
  - 展示 AI 推荐类型（可展开抽屉查看推理依据）  
  - 下拉可更换类型（9 类 + 通用兜底）
- [ ] 新建页面 `pages/InfoConfirm.tsx`：
  - 三个输入框：项目名称、建设单位、建设地点
  - 确认后跳转八步工作台

**测试完成标准**：确认类型后 `project.standard_set` 写库正确，确认信息后进入八步，工作台顶部能看到"当前使用规则：公路工程项目建设用地指标"。

---

### 阶段四：八步工作台重构

**目标**：文件有"待确认区"，可撤回改挂，AI 分类结果可纠错，步骤内展示结构化判定卡片。

**后端任务**
- [ ] 新建接口：`GET /api/projects/{id}/workspace` → 返回八步各自的文件列表 + 待确认区文件列表 + 每步判定状态
- [ ] 新建接口：`POST /api/projects/{id}/files/{fileId}/assign` → 用户指定步骤（改挂），记日志
- [ ] 新建接口：`POST /api/projects/{id}/files/{fileId}/retract` → 撤回到待确认区，记日志
- [ ] 新建 `VerdictEngine`（复用 `PolicyKnowledgeService` 逻辑）：
  - 输入：步骤 N 的文件列表 + 该项目标准包  
  - 输出：通过/注意/不通过 + 命中规则 + 证据来源（文件+字段）  
  - 写入 `step_verdicts` 表
- [ ] 新建接口：`GET /api/projects/{id}/steps/{stepNo}/verdict` → 返回某步判定卡片数据

**前端任务**
- [ ] 改造 `pages/Workspace.tsx`（现有八步工作台大改）：
  - 顶部：项目编号 + 名称 + 分析进度 + 当前使用标准包
  - 左侧：八步导航（沿用现有，加步骤判定徽章 通过/注意/不通过）
  - 中间：
    - 区块 1：**待确认区**（AI 没把握或用户撤回的文件）
    - 区块 2：**AI 已归类**（AI 自动分到本步的文件，可改挂/撤回）
    - 区块 3：**用户手动放置**（用户自己指定归属到本步的文件）
  - 右侧：展开抽屉看判定卡片（原始文件 + 抽取字段 + 命中规则 + 通过/注意/不通过）
- [ ] 判定卡片组件 `VerdictCard.tsx`：
  - 绿色通过区：字段命中 + 证据文件
  - 黄色注意区：模糊字段 + 人工复核提示
  - 红色不通过区：缺失必传字段/文件 + 具体说明

**测试完成标准**：能把文件从"待确认区"拖/指定到某步；能从步骤撤回；判定卡片展示通过/注意/不通过三栏；改挂后判定自动刷新。

---

### 阶段五：预览快照 + 导出

**目标**：用户确认八步后触发预览，系统冻结快照，二次调用 AI 生成预览报告，确认后导出。

**后端任务**
- [ ] 新建接口：`POST /api/projects/{id}/preview` → 
  - 冻结当前字段/步骤判定/文件归属为 `preview_snapshots` 一条记录（status=`GENERATING`）  
  - 异步调用 AI 组织成预览报告正文  
  - 完成后 status 改 `READY`，推送给前端
- [ ] 新建接口：`GET /api/projects/{id}/preview` → 返回预览报告内容（Markdown）
- [ ] 新建接口：`POST /api/projects/{id}/preview/confirm` → 快照 status 改 `CONFIRMED`
- [ ] 新建接口：`POST /api/projects/{id}/export` → 基于已确认快照生成 DOCX（复用现有报告生成逻辑），状态推进到 `EXPORTED`

**前端任务**
- [ ] 新建页面 `pages/Preview.tsx`：
  - 展示预览报告（Markdown 渲染）
  - 状态：生成中 loading → 就绪 → 已确认
  - 报告内容：项目摘要 + 八步判定汇总 + 通过项 + 注意项 + 不通过项 + 证据引用
  - 发现问题可返回八步继续修正（快照作废，重新生成）
- [ ] 导出按钮：仅在 `CONFIRMED` 后可点，点击下载 DOCX

**测试完成标准**：点击"生成预览"后出现 loading，报告生成完成展示正文；确认后导出 DOCX；返回修改后重新生成新快照，旧快照作废。

---

## 前端页面路由规划

```
/login                       → 首页登录
/projects                    → 项目总览（草稿箱 + 历史）
/projects/new                → 创建项目向导（含第一次全量上传）
/projects/:id/type           → 项目类型确认
/projects/:id/info           → 项目基本信息确认
/projects/:id/workspace      → 八步工作台
/projects/:id/preview        → 预览报告
```

---

## 阶段优先级与顺序

```
阶段一（基础设施）→ 阶段二（上传管道）→ 阶段三（类型确认）
                                         ↓
                        阶段四（工作台重构）→ 阶段五（预览导出）
```

每完成一个阶段后可单独上线/演示，不需要等全部写完。  
阶段一和阶段二是最重要的基础，阶段四是工作量最大的部分。

---

## 注意事项

1. **H2 → MySQL 迁移**：`land_use_standards` 476 条数据需要导出后导入 MySQL，写一次性迁移脚本。
2. **WorkspaceState JSON 文件**：原来存在 `data/projects/{uuid}/workspace-state.json`，新版改为数据库存储（`step_verdicts` + `project_files` 表），旧文件不再使用。
3. **JWT secret**：放 `application-local.yml`（不提交 git）。
4. **异步线程池**：`@Async` 需要配置 `ThreadPoolTaskExecutor`，并发控制避免 AI 接口被打爆（建议每项目最多 3 个并发分析任务）。
5. **文件类型确认后标准切换**：用户改类型后，`step_verdicts` 全部作废，重新触发 `VerdictEngine`，前台给出"标准已切换，正在重新分析"的提示。
6. **`projectId` 全程用 UUID**：展示给用户看的是 `displayNo`（PRJ-YYYYXXXX），接口参数是 UUID，不要混用。
