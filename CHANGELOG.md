# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- real-parse：`DocumentExtractionPort` + 版本化 `ExtractedDocument`；`PdfTextLayerProbe` / `NativePdfParser` / `OcrLayoutParser`（整份单路）。
- Fake（默认）与 Http（可配）抽取适配器；页/文档质量门禁；`report_parse_manifest`；仅 PASSED 可 active 并同事务跑规则。
- RP-1～RP-8 验收；损坏 PDF/图片不再静默 `CREATED`（对外 4102）。

### Notes

- 本变更仅为 **真实文档解析工程闭环**，不等于临床放行；不打 `V1.0.0` 临床总标签。

## [0.3.0] - 2026-11-19

### Added

- 历史趋势 API：`GET /api/report/patients/{patientId}/indicators/{canonicalCode}/trend`（可比性 T-R1/T-R2、固定免责声明 T-R3）。
- `report_parse_result.specimen_time` 与解析落库接线（meta / 指标 `reportTime`）。
- 报告类型：`DISCHARGE_SUMMARY` / `ENDOSCOPY_REPORT` 文本夹具解析（仍拒绝 `IMAGING_FILM` → 4101）。
- 档案 Outbox schemaVersion=`2`：`ArchiveOutboxMessageV2`（`reportType` / `sections` / 可选 `FhirHints`）；`GET /internal/report/archive/outbox/{id}`。

### Notes

- `0.3.0` 为 **P2 技术能力**（趋势与档案深度字段），不等于临床放行。
- `LoadSmokeIT` 仅为短并发冒烟，非正式压测门禁；clinical-record 真实联调仍经 Outbox，本仓不持有病情主数据。

## [0.2.0] - 2026-11-19

### Added

- P1 多类型文本夹具解析：`CHECKUP_BOOK` / `ECG_REPORT` / `US_REPORT`（仍拒绝 `IMAGING_FILM` 等不支持类型）。
- 详情结构增量：`SubReportDto` / `SectionDto` / `FindingDto` / `AssessmentDto`；指标可选 `subReportId` / `reportTime`。
- 追问 API：`POST /api/report/reports/{reportId}/questions`（解析结果 + Guard/Validator；改数拦截 4107）。
- 危急已读确认：`POST /api/report/reports/{reportId}/critical-events/{eventId}/ack`（仅 `ACCEPTED`→`ACKNOWLEDGED`）。
- 人工修订与复核结案：`POST /internal/report/corrections`、`POST /internal/report/reviews/{id}/resolve`。

### Notes

- `0.2.0` 为 **P1 技术能力**（多类型与人工协作），不等于临床放行。

## [0.1.0] - 2026-11-19

### Added

- 建立 `report-interpreter` 的仓库骨架、社区文件和 Apache-2.0 许可证。
- P0 技术闭环：建单与幂等、文本夹具解析、身份/覆盖/危急规则与 Outbox、Hybrid RAG fail-closed、OutputGuard、SSE `report_event`、双 Outbox 与交付门禁。
- 平台错误码 `41xx` 与企业规范对齐文档、分支计划与验收定义。

### Changed

- 确定运行时为 JDK 21、Spring Boot 4.1.1、Spring AI 2.0.1、MySQL 8.0.46、Redis 8.2.10、RocketMQ 5.5.1。
- 按报告版面解析、检索解读、SSE 和脱敏落档收紧功能范围。
- 发布线对齐规范为 `master`（不再以 `main` 为默认发布分支）。

### Notes

- `0.1.0` 为 **P0 技术闭环**，不等于临床放行；真实 OCR/患者数据未纳入本版本。
