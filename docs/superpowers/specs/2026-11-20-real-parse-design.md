# 临床放行 · real-parse 设计

| 项 | 内容 |
| --- | --- |
| 状态 | 已确认（方案 A） |
| 日期 | 2026-11-20 |
| 基线 | `V0.3.0` / `master` |
| 版本线 | `feature-V1.0-<创建日>` |
| 任务分支 | `feature-V1.0-<创建日>-real-parse` |
| 上位方案 | `docs/superpowers/specs/2026-11-18-AI报告解读企业级完整方案-design.md` §6 |
| 规范 | `docs/工程/企业规范对齐.md` |

## 1. 背景与目标

P0～P2 技术线已闭环，但合法 PDF/图片上传后仅建单为 `CREATED`（`parseVersion=0`），真实抽数未接入。临床放行清单第 2 项要求：真实 OCR / PDF / 图片解析与质量门槛。

本分支目标：在**不把 OCR 算力塞进 JVM**的前提下，使 PDF/图片进入既有「解析 → 质量门禁 → 规则」链路，fail-closed，且不回退 P0～P2 不变量。

## 2. 范围

### 2.1 做

- domain 端口 `DocumentExtractionPort` + 强类型 `ExtractedDocument`（页、块、表格、bbox、分层置信度）。
- 适配器：`FakeDocumentExtractionAdapter`（测试默认）、`HttpDocumentExtractionAdapter`（外置服务，可配置开关）。
- 解析器：`NativePdfParser`（文本层优先）、`OcrLayoutParser`（扫描 PDF/图片经端口）。
- 路由单路匹配；质量门禁强化（总页数、`pageIndex`+`rawText`、低置信 → 待复核 / `4102`）。
- 合法 PDF/图片不再停在空解析的 `CREATED`（能解析则落 `parseVersion`；不能则明确失败/待复核）。

### 2.2 不做

- 真实 LLM / Prompt 红队、危急值临床表、KB 审定、HIS 档案、影子运行、SSE live、生产压测门禁。
- 无条件串行双引擎；业务规则不进入 Python/OCR 仓。
- `IMAGING_FILM` 仍拒绝（`4101`）。

## 3. 架构

```text
上传 → UploadGuard → report_job
  → 路由（单路）
       ├─ NativePdfParser          （文本层可用）
       └─ OcrLayoutParser
            → DocumentExtractionPort
                 ├─ Fake（测试）
                 └─ Http（外置 OCR/版面）
  → 本仓结构化（指标 / structure / evidence / 分层置信度）
  → ParseQualityGate
  → 落库 parseVersion → 既有规则链
```

- **本仓**：任务、路由、结构化、质量门禁、规则、Outbox、SSE、鉴权与审计。
- **外置计算**：OCR / 版面 / 表格抽取；经 HTTP 契约接入；超时与 5xx fail-closed。

## 4. 数据流与错误处理

1. `UploadGuard`：MIME/魔数/大小；生产默认关闭文本夹具。
2. 建单后按文件种类与文本层探测选唯一解析器。
3. 抽取 → 结构化 → `ParseQualityGate`（有指标则必须 `pageIndex`+`rawText`；真实 OCR 路径必须声明总页数）。
4. 通过：落 active 解析版本并跑规则；身份非 MATCHED 等既有门禁不变。
5. 抽取失败、契约错误、超时、质量不达标：`4102` 或任务进入 `NEED_MANUAL_REVIEW`；**不**写可投递档案 Outbox；**不**自动改走第二引擎。

## 5. 类型与契约（原则）

- 禁止 `Map` 作端口/API/Mapper 契约；用 record。
- SQL 只在 `mapper/*.xml`。
- `Result.code=200` 成功语义不变；业务失败用平台 `ErrorCode`（解析失败 `4102`）。
- HTTP 请求/响应 DTO 定义在本仓 infrastructure（或独立 api 子包），与外置服务 OpenAPI 对齐后冻结。

`ExtractedDocument` 至少包含：总页数、每页块列表（文本、bbox、阅读顺序）、可选表格、OCR/结构分层置信度。字段级 name/value/unit/refRange 置信度仍在本仓结构化阶段写入 `FieldConfidence`。

## 6. 验收标准

| ID | 内容 |
| --- | --- |
| RP-1 | 文本层 PDF 能解析出指标且带页码/原文证据 |
| RP-2 | 扫描/图片经 Fake 端口可解析；HTTP 适配器可配置，默认测试不连真网 |
| RP-3 | 抽取失败或质量不达标 → `4102` 或待复核，非假成功 |
| RP-4 | 禁止无条件双引擎串行；`IMAGING_FILM` → `4101` |
| RP-5 | 无 Map 契约；SQL 仅 XML |
| RP-6 | P0～P2 回归（含 Gate / FullRegression）不回退 |

## 7. 测试策略

- Fake 端口 + 金标 PDF/图片夹具（仓库内小文件或合成字节）。
- HTTP 适配器：WireMock / 录制 JSON，覆盖超时与 5xx。
- 保留文本夹具路径（`allow-text-fixture`）供既有 297 测回归。
- `mvn -B verify` 默认不依赖真实 OCR 进程。

## 8. 配置

- `report.parse.extraction.enabled` / `report.parse.extraction.base-url` / 超时与令牌（若平台已有内部令牌则复用）。
- 生产：`allow-text-fixture=false`；抽取未配置时对 PDF/图片不得静默当成功。

## 9. 完成定义

- [x] SDD（本文）评审通过并据此写出实施计划（`docs/superpowers/plans/feature-V1.0-20261120-real-parse.md`）  
- [ ] TDD+AC（RP-1～RP-6）与 `mvn -B verify` 全绿  
- [ ] 合入 `feature-V1.0-*`（本分支完成定义）；临床总标签 `V1.0.0` 留给后续 shadow-gate

## 10. 后续临床放行（非本分支）

`clinical-facts` → `real-llm` → `kb-release` → `critical-channel` → `archive-his` → `ops-hardening` → `shadow-gate`。
