# feature-V1.0-20261120-clinical-facts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 规则链经端口取得人口学与版本化临床目录；Fake 闭环后再接身份 HTTP；`UNKNOWN` 仍 fail-closed。

**Architecture:** 方案 A：`PatientFactsPort`（Fake / HttpIdentity）+ `ClinicalCatalogPort`（Fake + 可切换版本）；`ReportRuleService` 去掉写死 `PatientFacts.UNKNOWN`；审计字段 `factsSource` / `catalogVersion` / `factsIncomplete`；复用 4106/4109。

**Tech Stack:** JDK 21、Spring Boot 4.1.1、platform-bom、MyBatis XML、JUnit/MockMvc、WireMock（身份 HTTP）。

**Spec:** `docs/superpowers/specs/2026-11-20-clinical-facts-design.md`

## Global Constraints

- 禁 Map；SQL→xml；成功 `code=200`
- 身份失败 → Facts UNKNOWN，**不**映射 4102；缺适用性 → 4106；覆盖不明 → 4109
- 病情主数据不进本仓；报告人口学补缺本版不做
- verify 默认 Fake，不连真身份服务
- 先 Fake（Task 1～4）再 Http（Task 5）
- clinical-facts ≠ 临床放行；不打 `V1.0.0`

---

## File map（增量）

| Path | 职责 |
| --- | --- |
| `domain/rule/FactsSource.java` | 枚举 FAKE / IDENTITY / IDENTITY_FAILED / NONE |
| `domain/rule/PatientFactsResolution.java` | facts + source + incomplete + detailCode |
| `domain/rule/PatientFactsPort.java` | 人口学端口 |
| `domain/rule/ClinicalCatalog.java` + `ClinicalCatalogPort.java` | 版本化危急/参考目录 |
| `domain/rule/ReferenceRangeRule*.java`（若需） | 分层参考区间条目（年龄/性别） |
| `infrastructure/facts/FakePatientFactsAdapter.java` | 测试人口学 |
| `infrastructure/facts/HttpIdentityPatientFactsAdapter.java` | 身份 HTTP |
| `infrastructure/catalog/FakeClinicalCatalogAdapter.java` | Fake 目录 + 多版本 |
| `config/ClinicalFactsConfig.java` + `ReportProperties.ClinicalFacts` | 装配与配置 |
| `ReportRuleService` / `RuleConfig` | 接线端口与 catalog 表 |
| `ReportRuleSummaryRow` + Mapper XML + 迁移 | 审计三字段 |
| 测试 `ClinicalFacts*Test` / `HttpIdentityPatientFacts*` | CF-1～CF-6 |

---

### Task 1: 冻结 Facts / Catalog 契约

**Produces:** `FactsSource`、`PatientFactsResolution`、`PatientFactsPort`、`ClinicalCatalog`、`ClinicalCatalogPort`。

- [x] 单测：Resolution 往返字段；`UNKNOWN` ⇒ `incomplete=true`；禁 Map
- [x] 实现 record / enum / port；更新 `PatientFacts` 注释
- [x] Commit: `feat: 冻结 clinical-facts 人口学与目录契约`

### Task 2: Fake PatientFacts + ReportRuleService 接线

**Produces:** Fake 按 `patientId` 返回固定年龄/性别；规则服务不再写死 UNKNOWN。

- [x] 金标：Fake 有年龄/性别时 `RuleContext.age/sex` 非空；未知 patientId → UNKNOWN + incomplete
- [x] `ReportRuleService` 注入 `PatientFactsPort`，按 job.patientId 解析
- [x] Commit: `feat: 规则链经 PatientFactsPort 取人口学`

### Task 3: Fake 分层参考区间 + 危急目录用例（CF-1/CF-2）

**Produces:** Catalog 内含需 AGE/SEX 的参考规则；可选危急规则 `required` 含 AGE（演示适用性）。

- [x] Fake：成人/儿童不同参考区间命中（CF-1）
- [x] Facts UNKNOWN → 适用性 UNKNOWN / 不得假 NOT_CRITICAL；覆盖路径仍 4109（CF-2）
- [x] Commit: `feat: Fake 目录支持分层参考与 UNKNOWN 安全禁令`

### Task 4: ClinicalCatalogPort 版本切换（CF-4）

**Produces:** `active-version` 可配；缺版本 → 4106；引擎表来自 catalog。

- [x] `RuleConfig`：`CriticalValueEngine` / 参考引擎消费 `ClinicalCatalogPort.load(activeVersion)`
- [x] 缺版本/缺必填条目测试 → 4106 或待复核，不静默 builtIn 错表
- [x] Commit: `feat: 版本化 ClinicalCatalog 接入规则引擎`

### Task 5: HttpIdentityPatientFactsAdapter（CF-3）

**Produces:** WireMock 契约；超时/4xx/5xx/坏 JSON → UNKNOWN + IDENTITY_FAILED。

- [x] `ReportProperties.ClinicalFacts` + HttpClient Bean（风格对齐 ExtractionConfig）
- [x] WireMock：200 有出生日期/性别；超时；5xx；坏体
- [x] Commit: `feat: 增加 HTTP 身份 PatientFacts 适配器`

### Task 6: 审计字段落库（CF-5）

**Produces:** `report_rule_summary`（或并列）增加 `facts_source` / `catalog_version` / `facts_incomplete`；Mapper XML。

- [x] Flyway/Liquibase 或既有 SQL 迁移风格增量
- [x] RuleView / Detail 可读出三字段；无 Map
- [x] Commit: `feat: 规则摘要落盘 clinical-facts 审计字段`

### Task 7: 全量回归（CF-6）

- [x] CF-1～CF-6 命名测试齐
- [x] `mvn -B verify` 全绿（自 reactor 根，342）
- [x] 更新 `docs/工程/分支与版本.md`、CHANGELOG Unreleased；设计 §10 勾选
- [ ] 合入 `feature-V1.0-20261120` 并推送（不打 V1.0.0）
- [ ] Commit: `test: clinical-facts 回归与文档状态`

---

## 完成定义

- [x] 规格终冻确认  
- [x] Task 1～7 完成；verify 绿（342）  
- [ ] 合入版本线；**不**宣称临床放行  

## 执行入口

实现时用 **subagent-driven-development** 或 **executing-plans**，按 Task 勾选推进。
