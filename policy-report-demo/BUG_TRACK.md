# Bug & Feature 跟踪

## 核心问题诊断

### Bug 1 — 解析字段核对不更新（已确认根因）
**根因**: 字段提取是对 Tika 文本跑正则。扫描件/图片 PDF Tika 提取 < 100 字，正则匹配不到任何字段，`extractedFields` 为空，`mergeAnalysis` 没有内容可写入工作区，字段一直停在"待解析"。
**AI 返回的自然语言里有字段，但没有解析回结构化字段**。
- **Fix**: 豆包先提取文字 → 合并后再跑字段正则（见架构重设计）

### Bug 2 — Step5 文件上传一个顶掉一个（已确认根因）
**根因**: `WorkspaceStateService.mergeAnalysis()` 限制每步最多 6 条分析记录。Step5 需要上传≥6份材料，第7份上传后第1份被截断。
- **Fix**: 把上限从 6 改到 20

### Bug 3 — 情形选择 / 校验结果不跟 AI 输出
**根因**: 情形选择只在 Step6 有自动推导（`deriveSituations`），其他步骤不自动。校验结果依赖 checklist，而 checklist 更新需要 `policyChecks`，AI 提取的自然语言字段没有触发。
- **Fix**: 短期：豆包提取文字 → 正则字段 → checklist 自动更新。长期：synthesis 第三轮解析后推 `updateSituation`

### Bug 4 — 模糊扫描/工程图/流程图无法解析
**根因**: Ollama 本地模型视觉能力弱，对模糊扫描图（歪斜、低分辨率）识别效果差。
- **Fix**: 豆包视觉作为第一优先，Ollama 降为可选备用

---

## 架构重设计：三轮 Pipeline（每文件）

### 当前流程（问题版）
```
Tika → [OCR if needed] → 字段正则 → DeepSeek/Ollama 文本分析 → [Doubao if visual]
```

### 新流程（豆包优先）
```
Round 1: Tika → 生成首页缩略图 → 如果配置了豆包:
           ├─ 文本文件（Tika >= 100字）: 豆包补充图像识别
           └─ 图片/扫描/Tika < 100字: 豆包提取全文 + 描述

Round 2: 合并文本（Tika + 豆包）→ 字段正则 → DeepSeek/Ollama 分析

Round 3: 所有文件上传完后 → DeepSeek 综合分析所有 AI 输出
         → 输出字段建议 + 情形建议 → 展示在解析面板底部
```

---

## 待实现清单

- [x] DoubaoClient.java 创建 (ARK API)
- [x] OcrService 缩略图生成（ocrPdf/ocrImage）
- [x] DocumentAnalysisService 调豆包（视觉内容）
- [x] synthesize 端点
- [x] 前端豆包配置 + 缩略图 + 综合分析面板
- [x] **OcrService.generateFileThumbnail()** — 独立于 OCR 的缩略图，对所有文件生成
- [x] **DocumentAnalysisService** — 豆包优先 pipeline：每文件先调豆包，合并文本再提字段
- [x] **WorkspaceStateService** — 分析上限 6 → 20
- [x] **normalizeState 去重 bug** — 服务重启后 label 相同 key 不同导致字段重复（mergeFields 新增按 label 去重）
- [ ] **synthesis 结果写入工作区字段** — 第三轮 AI 输出的字段建议自动填入（可选）

---

## 已知限制
- 豆包 Endpoint: ep-20260126190029-bbd2h
- 豆包 API Key: ark-bd8b1526-fb47-43dc-a8c8-ab1fef9acfc2-31cb4
- `max_tokens: 600`（严格限制输出）
