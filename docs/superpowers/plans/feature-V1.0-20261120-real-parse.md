# feature-V1.0-20261120-real-parse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 真实 PDF/图片进入解析→门禁→规则闭环；带探针、页级质量、Manifest 与可追溯契约；默认 Fake 闭环，Http 后接。

**Architecture:** 方案 A + 企业级平衡：`PdfTextLayerProbe` 整份路由；`DocumentExtractionPort`；页/文档质量门禁；`DocumentParseManifest`；仅 PASSED 可 active。本版不做局部 OCR、异步 OCR、对象存储实装。

**Tech Stack:** JDK 21、Spring Boot 4.1.1、platform-bom、MyBatis XML、JUnit/MockMvc、WireMock；PDF 探针/Native 用 JVM 库（如 PDFBox，按平台引入规范）。外置参考 PaddleOCR / MinerU / Docling / PyMuPDF（拆开借鉴）。

**Spec:** `docs/superpowers/specs/2026-11-20-real-parse-design.md`（平衡修订版）

## Global Constraints

- 禁 Map；SQL→xml；成功 `code=200`；解析失败对外 4102 + 对内 failureStage
- 禁双引擎全量串行；整份单路；IMAGING_FILM→4101
- verify 默认不连真 OCR；文本夹具测试可开
- 先 Fake 闭环（Task 1～6）再 Http（Task 7）
- real-parse ≠ 临床放行

---

## File map（增量）

| Path | 职责 |
| --- | --- |
| `domain/parse/Extracted*.java`、`ExtractionRequest` | 版本化抽取契约 |
| `domain/parse/PdfTextLayerProbe` + `ProbeReport` | 文本层可用性 |
| `domain/parse/PageParseStatus` / `DocumentQualityStatus` | 页/文档质量 |
| `domain/parse/DocumentParseManifest` + 持久化 | 可追溯清单 |
| `infrastructure/extraction/Fake*` / `Http*` | 端口适配 |
| `infrastructure/parser/NativePdfParser` / `OcrLayoutParser` / `*Structurer` | 解析与结构化 |
| `ParseQualityGate` / `ReportParseService` | 门禁与 active 事务 |
| `ReportProperties.Parse.probe|extraction|limits` | 配置 |

---

### Task 1: 冻结 ExtractedDocument 契约

**Produces:** `ExtractedDocument(totalPages, pages, ocrConfidence, layoutConfidence, schemaVersion)`；`ExtractedPage(..., PageParseStatus status)`；`ExtractedBlock`；`ExtractedTable`；`DocumentExtractionPort`。

- [x] 写契约单测（序列化往返、禁 Map、schemaVersion 必填）
- [x] 实现 record + port 接口
- [x] Commit: `feat: 冻结 ExtractedDocument 抽取契约`

### Task 2: PdfTextLayerProbe + NativePdfParser

**Produces:** `ProbeReport(decision, metrics..., reasons)`；Native 仅 `USE_NATIVE`。

- [x] 金标：合格文本 PDF → USE_NATIVE + 指标证据；乱码/过稀 → USE_OCR 或 NEED_REVIEW（按默认阈值）
- [x] 实现探针（配置默认阈值）+ NativePdfParser
- [x] Commit: `feat: 实现文本层探针与 NativePdfParser`

### Task 3: FakeDocumentExtractionAdapter

- [x] Fake 对扫描夹具返回稳定 ExtractedDocument（含页状态）
- [x] 未知/损坏输入 → 抽取失败（供门禁测）
- [x] Commit: `feat: 增加 Fake DocumentExtraction 适配器`

### Task 4: OcrLayoutParser + Structurer

- [x] 图片或 USE_OCR 的 PDF → 端口 → ParseResult
- [x] 与 Native supports 互斥（同 context 至多一个 true）
- [x] Commit: `feat: 实现 OcrLayoutParser 与结构化`

### Task 5: 页级/文档级 ParseQualityGate

- [x] 证据完整性优先；置信度辅
- [x] 任一关键 FAILED 页 → 文档不得 PASSED
- [x] LOW_CONFIDENCE → NEED_MANUAL_REVIEW
- [x] Commit: `feat: 强化页级与文档级解析质量门禁`

### Task 6: Manifest + active 事务边界

- [x] 表 `report_parse_manifest`（或并列行）+ Mapper XML
- [x] 仅 PASSED 落 active 并跑规则；失败写 Manifest + failureStage
- [x] 幂等/版本唯一/行锁防覆盖；artifactRef 可空
- [x] Commit: `feat: 增加 DocumentParseManifest 与 active 事务边界`

### Task 7: HttpDocumentExtractionAdapter

- [x] 契约字段：requestId、documentHash、schemaVersion、engineVersion、processingStatus、errorCode…
- [x] WireMock：200 / 5xx / 超时 / 坏 JSON
- [x] 超时与体积极限配置
- [x] Commit: `feat: 增加 HTTP DocumentExtraction 适配器`

### Task 8: 全量回归

- [x] RP-1～RP-8 命名测试齐
- [x] `mvn -B verify` 全绿
- [x] 更新 `docs/工程/分支与版本.md`、CHANGELOG Unreleased
- [x] 合入 `feature-V1.0-20261120` 并推送
- [x] Commit: `test: real-parse 回归与文档状态`

---

## 完成定义

- [x] 规格终冻确认  
- [x] Task 1～8 完成；verify 绿（327）  
- [x] 合入版本线；不打临床总标签 `V1.0.0`

## Spec coverage

| 规格要点 | Task |
| --- | --- |
| Extracted 契约 / 置信度分层 | 1, 5 |
| Probe + Native | 2 |
| Fake / Ocr | 3, 4 |
| 页/文档门禁 | 5 |
| Manifest + active 事务 | 6 |
| Http 契约与限额 | 7 |
| 回归 / 不做局部·异步·OSS 实装 | 8 + 规格 §2.2 |
