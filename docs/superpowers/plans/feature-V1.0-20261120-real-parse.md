# feature-V1.0-20261120-real-parse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让合法 PDF/图片经 NativePdf / 外置 OCR 端口进入既有解析→质量门禁→规则链路，不再静默停在 `CREATED`。

**Architecture:** domain 端口 `DocumentExtractionPort` + 强类型 `ExtractedDocument`；Fake/Http 适配器；`NativePdfParser` / `OcrLayoutParser` 单路匹配；结构化与 `ParseQualityGate` 仍在本仓；外置服务失败 fail-closed（`4102` / 待复核），禁止双引擎串行。

**Tech Stack:** JDK 21、Spring Boot 4.1.1、platform-bom、MyBatis XML、JUnit + MockMvc、WireMock（或等效）测 HTTP 适配器；PDF 文本层用轻量库（优先已有 BOM 内可用库，否则最小依赖如 PDFBox，须经平台引入规范评估）。

**Spec:** `docs/superpowers/specs/2026-11-20-real-parse-design.md`  
**Branch:** `feature-V1.0-20261120-real-parse`（已从 `V0.3.0` 拉出）  
**Norm:** `docs/工程/企业规范对齐.md`

## Global Constraints

- 禁 `Map` 作 API/domain/Mapper 契约；SQL 只在 `mapper/*.xml`
- `Result` 成功 `code=200`；解析失败用 `ErrorCode.REPORT_PARSE_FAILED`（4102）
- P0～P2 不变量不回退；`IMAGING_FILM` → 4101；无条件双引擎串行禁止
- `mvn -B verify` 默认不连真 OCR；`report.parse.allow-text-fixture` 测试可开、生产 false
- 日志不写指标全文 / 姓名 / 证件号
- Conventional Commits；JAVA_HOME 指向 JDK 21

---

## File map

| Path | Responsibility |
| --- | --- |
| `.../domain/parse/DocumentExtractionPort.java` | 抽取端口 |
| `.../domain/parse/ExtractedDocument.java` 等 | 抽取结果强类型 |
| `.../domain/parse/PdfTextProbe.java` | 是否具备可用文本层 |
| `.../infrastructure/extraction/FakeDocumentExtractionAdapter.java` | 测试 Fake |
| `.../infrastructure/extraction/HttpDocumentExtractionAdapter.java` | HTTP 适配 |
| `.../infrastructure/extraction/ExtractionHttpDtos.java` | HTTP 请求/响应 record |
| `.../infrastructure/parser/NativePdfParser.java` | 文本层 PDF → ParseResult |
| `.../infrastructure/parser/OcrLayoutParser.java` | 扫描/图片 → 端口 → 结构化 |
| `.../infrastructure/parser/ExtractedDocumentStructurer.java` | ExtractedDocument → ParseResult（LAB_PANEL 首期） |
| `.../config/ReportProperties.java` | `parse.extraction.*` |
| `.../config/ExtractionConfig.java` | Bean 装配 Fake/Http |
| `.../service/ReportParseService.java` | 去掉「无解析器则 CREATED」静默成功 |
| `.../domain/parse/ParseQualityGate.java` | 真实路径强制 totalPages |
| tests under `report-interpreter-service/src/test/java/...` | RP-1～RP-6 |
| `src/test/resources/golden/pdf/` | 最小 PDF 夹具 |

---

### Task 1: 抽取端口与 Fake（类型地基）

**Files:**
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/DocumentExtractionPort.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/ExtractedDocument.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/ExtractedPage.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/ExtractedBlock.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/ExtractedTable.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/extraction/FakeDocumentExtractionAdapter.java`
- Test: `report-interpreter-service/src/test/java/com/moonligh/report/FakeDocumentExtractionTest.java`

**Interfaces:**
- Produces:
  - `DocumentExtractionPort.extract(ExtractionRequest request) → ExtractedDocument`
  - `ExtractionRequest(String reportId, String contentType, byte[] content, String fileName)`
  - `ExtractedDocument(int totalPages, List<ExtractedPage> pages, double ocrConfidence, double structureConfidence)`
  - `ExtractedPage(int pageIndex, List<ExtractedBlock> blocks, List<ExtractedTable> tables)`
  - `ExtractedBlock(String text, BoundingBox region, int readingOrder, double confidence)`
  - `ExtractedTable(int pageIndex, List<String> headers, List<List<String>> rows, BoundingBox region)`

- [ ] **Step 1: Write the failing test**

```java
@Test
void fakeReturnsLabPanelLikeBlocks() {
    var port = new FakeDocumentExtractionAdapter();
    ExtractedDocument doc = port.extract(new ExtractionRequest(
            "rpt_1", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "scan.png"));
    assertTrue(doc.totalPages() >= 1);
    assertFalse(doc.pages().get(0).blocks().isEmpty());
}
```

- [ ] **Step 2: Run test — expect FAIL (types/port missing)**

```bash
mvn -pl report-interpreter-service -Dtest=FakeDocumentExtractionTest -B test
```

Expected: compilation failure or test not found until types exist.

- [ ] **Step 3: Implement records + port + Fake**

Fake 对已知魔数/夹具名返回固定 LAB 风格块（含 `K+|6.8|mmol/L` 原文行与 pageIndex=0）；未知内容抛 `BusinessException(REPORT_PARSE_FAILED)`。

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/ \
  report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/extraction/FakeDocumentExtractionAdapter.java \
  report-interpreter-service/src/test/java/com/moonligh/report/FakeDocumentExtractionTest.java
git commit -m "feat: 增加 DocumentExtractionPort 与 Fake 适配器"
```

---

### Task 2: 配置与装配

**Files:**
- Modify: `report-interpreter-service/src/main/java/com/moonligh/report/config/ReportProperties.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/config/ExtractionConfig.java`
- Modify: `report-interpreter-service/src/test/resources/application-test.yml`（或 properties）

**Interfaces:**
- Produces: `ReportProperties.Parse.Extraction` with `enabled`（默认 false）、`baseUrl`、`connectTimeoutMs`、`readTimeoutMs`、`internalToken`（可空）
- Bean: 当 `enabled=false` → Fake；`enabled=true` → Http（Task 5 实现前可先只注册 Fake）

- [ ] **Step 1: Write failing test** that context loads `report.parse.extraction.enabled=false` and injects `DocumentExtractionPort` as Fake

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add nested `Extraction` under `Parse` + `@Bean DocumentExtractionPort`**

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit** `feat: 装配 report.parse.extraction 配置与 Fake Bean`

---

### Task 3: NativePdfParser（RP-1）

**Files:**
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/PdfTextProbe.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/parser/NativePdfParser.java`
- Create: `report-interpreter-service/src/test/resources/golden/pdf/lab-panel-native.pdf`（最小文本层 PDF，正文含 LAB 管道行或可映射行）
- Test: `report-interpreter-service/src/test/java/com/moonligh/report/NativePdfParseTest.java`

**Interfaces:**
- Consumes: `ReportParser`, `ParseContext`, existing `LabNormalizer` patterns
- Produces: `NativePdfParser.supports` = `application/pdf` **且** `PdfTextProbe.hasUsableText(bytes)==true` **且** reportType 为已支持类型（首期 `LAB_PANEL`）
- `parse` → `ParseResult` with indicators + evidence.pageIndex/rawText + meta.totalPages

- [ ] **Step 1: Write failing MockMvc/unit test RP-1**

```java
@Test
void nativePdfProducesIndicatorsWithEvidence() throws Exception {
    // multipart LAB_PANEL + golden PDF bytes
    // assert code==200, parseVersion>=1, indicators non-empty, pageIndex+rawText present
}
```

- [ ] **Step 2: Run — expect FAIL**（仍 CREATED / parseVersion=0）

- [ ] **Step 3: Implement PdfTextProbe + NativePdfParser + wire as `@Component`**

文本抽取后复用夹具行解析逻辑（可抽 `LabPanelLineParser` 供 TextParser 与 Native 共用，避免复制）；无文本层时 `supports=false`（交给 Ocr）。

- [ ] **Step 4: Run NativePdfParseTest — PASS**；既有文本夹具测不挂

- [ ] **Step 5: Commit** `feat: 实现 NativePdfParser 文本层 PDF 解析`

---

### Task 4: OcrLayoutParser + Structurer（RP-2）

**Files:**
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/parser/ExtractedDocumentStructurer.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/parser/OcrLayoutParser.java`
- Test: `report-interpreter-service/src/test/java/com/moonligh/report/OcrLayoutParseTest.java`
- Modify: `UploadGuard` 若需允许 `.discharge` 以外图片已支持则不动

**Interfaces:**
- Consumes: `DocumentExtractionPort`
- `OcrLayoutParser.supports` = IMAGE，或 PDF 且 `!PdfTextProbe.hasUsableText`
- `ExtractedDocumentStructurer.toParseResult(ReportType, ExtractedDocument) → ParseResult`

- [ ] **Step 1: Failing test** — PNG 魔数字节 + Fake 端口 → parseVersion≥1、有证据

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement Structurer + OcrLayoutParser**

Structurer 从块/表格行识别 `name|value|unit|...` 或表格行列；写 `FieldConfidence`（ocr/structure 来自 ExtractedDocument，字段层按可解析性）。

- [ ] **Step 4: PASS**

- [ ] **Step 5: Commit** `feat: 实现 OcrLayoutParser 与抽取结果结构化`

---

### Task 5: HttpDocumentExtractionAdapter（契约 + fail-closed）

**Files:**
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/extraction/ExtractionHttpDtos.java`
- Create: `report-interpreter-service/src/main/java/com/moonligh/report/infrastructure/extraction/HttpDocumentExtractionAdapter.java`
- Modify: `ExtractionConfig.java` — enabled 时用 Http
- Test: `report-interpreter-service/src/test/java/com/moonligh/report/HttpDocumentExtractionAdapterTest.java`

**Interfaces:**
- HTTP `POST {baseUrl}/v1/extract`，body: `ExtractionHttpRequest(reportId, contentType, contentBase64, fileName)`
- response: `ExtractionHttpResponse` 映射到 `ExtractedDocument`（字段一一对应，禁 Map）
- 超时 / 非 2xx / 缺 totalPages → `BusinessException(REPORT_PARSE_FAILED)`

- [ ] **Step 1: WireMock failing tests** — 200 映射成功；500 → 4102；超时 → 4102

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement Http adapter + DTO records**

- [ ] **Step 4: PASS**（`enabled=true` + WireMock base-url）

- [ ] **Step 5: Commit** `feat: 增加 HTTP DocumentExtraction 适配器`

---

### Task 6: ReportParseService + QualityGate（RP-3）

**Files:**
- Modify: `report-interpreter-service/src/main/java/com/moonligh/report/service/ReportParseService.java`
- Modify: `report-interpreter-service/src/main/java/com/moonligh/report/domain/parse/ParseQualityGate.java`
- Test: `report-interpreter-service/src/test/java/com/moonligh/report/RealParseFailClosedTest.java`

**Behavior:**
- 删除「`parser.isEmpty()` → 直接 `CREATED`」静默成功。
- 若 PDF/IMAGE 且无可用解析器：抛 `REPORT_PARSE_FAILED`（或建单后标记失败——优先与现有 `BusinessException` 一致，事务回滚或显式失败状态；**选定：抛 4102，不留下假成功 parseVersion=0 当已解析**）。
- 若抽取/质量失败：4102；低置信但有部分结果：`NEED_MANUAL_REVIEW`（规则出口已有则复用）。
- `ParseQualityGate`：当 `meta.totalPages==null` 且非纯文本夹具路径 → 4102。

- [ ] **Step 1: Failing tests** — 损坏 PDF 魔数已拒；合法 PDF 但 Fake 抛错 → code=4102；低置信夹具 → 待复核

- [ ] **Step 2: FAIL**

- [ ] **Step 3: Implement service + gate changes**

- [ ] **Step 4: PASS**

- [ ] **Step 5: Commit** `fix: PDF/图片解析失败 fail-closed，禁止静默 CREATED`

---

### Task 7: 不变量与回归（RP-4～RP-6）

**Files:**
- Modify: `NoDualEngineTest.java` — 允许多个 parser，但同一 context 仍至多一个 `supports=true`
- Test: `RealParseNoDualEngineTest.java` 或扩展现有：对同一扫描 PDF，Native 与 Ocr 不得同时 supports
- Test: `ReportTypeRejectTest` 保持 IMAGING_FILM=4101
- Test: `NoMapExtractionTest` — Extraction DTO / port 无 Map
- Keep: `FullRegressionIT` / `GateSuiteIT` / `P0RegressionIT`

- [ ] **Step 1: Write/adjust failing dual-engine + no-map tests**

- [ ] **Step 2: Run targeted — FAIL if dual supports**

- [ ] **Step 3: Fix supports() 互斥（Native 文本层优先；Ocr 仅无文本层或图片）**

- [ ] **Step 4: `mvn -B verify` — Tests run ≥297，Failures=0**

- [ ] **Step 5: Commit** `test: 补齐 real-parse 不变量与回归`

---

### Task 8: 文档与合入版本线

**Files:**
- Modify: `docs/工程/分支与版本.md` — 增加 V1.0 / real-parse 状态
- Modify: `docs/README.md` — 计划表增加一行
- Modify: `CHANGELOG.md` — `[Unreleased]` 记 real-parse（未打临床放行总标签前不写 1.0.0）
- Modify: `docs/superpowers/specs/2026-11-20-real-parse-design.md` §9 勾选实施计划项
- Modify: 本计划完成定义勾选

- [ ] **Step 1: Update docs**

- [ ] **Step 2: Commit** `docs: 登记 V1.0 real-parse 进度`

- [ ] **Step 3: Merge into `feature-V1.0-20261120` and push both branches**

```bash
git checkout feature-V1.0-20261120
git merge --ff-only feature-V1.0-20261120-real-parse
git push -u origin feature-V1.0-20261120-real-parse
git push -u origin feature-V1.0-20261120
```

- [ ] **Step 4: 不在本任务打 `V1.0.0`**（临床放行总标签留给 shadow-gate）；本分支完成 = 合入版本线 + verify 绿

---

## 完成定义

- [ ] RP-1～RP-6 测试全绿
- [ ] `mvn -B verify` 全绿（默认不连真 OCR）
- [ ] 合入 `feature-V1.0-20261120`
- [ ] 设计规格 §9「实施计划」勾选完成

## Spec coverage checklist

| Spec | Task |
| --- | --- |
| DocumentExtractionPort + ExtractedDocument | T1 |
| Fake / Http adapters | T1, T5 |
| NativePdfParser / OcrLayoutParser | T3, T4 |
| 单路路由 / 禁双引擎 | T4 supports + T7 |
| 不再静默 CREATED / fail-closed | T6 |
| QualityGate totalPages | T6 |
| 配置 extraction.* | T2 |
| RP-1～RP-6 / 回归 | T3–T7 |
| 范围外不做 LLM/HIS 等 | 无对应任务（刻意） |
