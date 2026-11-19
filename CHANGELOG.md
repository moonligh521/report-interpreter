# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

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
