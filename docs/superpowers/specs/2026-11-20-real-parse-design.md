# 临床放行 · real-parse 设计

| 项 | 内容 |
| --- | --- |
| 状态 | **已终冻（企业级平衡修订）** |
| 评审 | 方案 A 维持；补质量治理与可追溯，不做过度工程 |
| 日期 | 2026-11-20 |
| 基线 | `V0.3.0` / `master` |
| 版本线 | `feature-V1.0-20261120` |
| 任务分支 | `feature-V1.0-20261120-real-parse` |
| 上位方案 | `docs/superpowers/specs/2026-11-18-AI报告解读企业级完整方案-design.md` §6 |
| 规范 | `docs/工程/企业规范对齐.md` |
| 计划 | `docs/superpowers/plans/feature-V1.0-20261120-real-parse.md` |

## 0. 企业级平衡原则

1. **架构不大改**：SPI + 外置抽取 + 本仓结构化/门禁/规则；禁止无条件双引擎全量串行。  
2. **治理要够用**：探针可解释、页级状态、契约可追溯、Manifest、仅门禁通过才 active——这些本分支要落地。  
3. **不做一次做满**：混合页局部 OCR、异步 OCR、对象存储实装、机房级网络隔离——**写明本版不做或部署后置**，避免假完整。  
4. **先 Fake 闭环再真 OCR**：契约与门禁不绑死某一引擎；换 Paddle/MinerU/商业 OCR 不改业务主链。  
5. **real-parse ≠ 临床放行**：本分支只证明真实文档解析工程闭环。

---

## 1. 背景与目标

P0～P2 已闭环，合法 PDF/图片仍可静默停在 `CREATED`。本分支目标：PDF/图片进入「解析 → 质量门禁 → 规则」，fail-closed，P0～P2 不回退。

## 2. 范围

### 2.1 本分支做

- `DocumentExtractionPort` + 版本化 `ExtractedDocument`（页/块/表/bbox/分层置信度）。
- `PdfTextLayerProbe`（可解释路由，非整页「有字即合格」）。
- `NativePdfParser` / `OcrLayoutParser`（整份文档单路；见 §4）。
- Fake + Http 适配器（默认测试 Fake；Http 可配）。
- 页级 + 文档级质量状态与门禁。
- `DocumentParseManifest`（库表摘要 + 产物引用字段，引用可空）。
- 资源限额与 HTTP 契约要点（§7～§8）。
- 仅文档级门禁通过的版本可 `active` 并触发规则链。

### 2.2 本分支明确不做

| 项 | 处理 |
| --- | --- |
| 页级混合：部分页 Native、部分页 OCR | **不支持**；整份走 Native 或整份走 OCR |
| 异步 OCR / 回调任务 | 契约可预留字段；**实现仅同步 HTTP + 超时** |
| 对象存储实装与生命周期策略 | Manifest 留 `*ArtifactRef`；**实装归 ops-hardening** |
| 机房级 OCR 网络隔离 | **部署要求**，不绑 `mvn verify` |
| 真实 LLM / KB 审定 / 危急通道 / HIS / 影子运行 | 后续临床分支 |

### 2.3 仍禁止

无条件双引擎串行；业务规则进 OCR 仓；`IMAGING_FILM` → `4101`；`Map` 契约。

---

## 3. 架构

```text
上传 → UploadGuard（格式/大小/页数等限额）
  → report_job + documentHash
  → PdfTextLayerProbe（PDF）→ ProbeReport（可审计）
  → 单路路由
       ├─ NativePdfParser     （探针判定 USE_NATIVE）
       └─ OcrLayoutParser     （图片，或探针判定 USE_OCR / 探针失败待复核）
            → DocumentExtractionPort（Fake | Http）
  → Structurer → ParseResult
  → ParseQualityGate（页级汇总 → 文档级）
  → 通过：落 parseVersion + Manifest + active + 规则链
  → 未通过：4102 或 NEED_MANUAL_REVIEW；不写可投递档案 Outbox；不自动换引擎
```

新增组件：**DocumentParseManifest**（§9）。

---

## 4. PdfTextLayerProbe（文本层可用性）

**原则：** 有文本层 ≠ 质量合格；路由依据可解释、可写入 Manifest；**不**用不可测的「玄学总分」偷偷切 OCR。

### 4.1 探测输出（强类型）

`ProbeReport`：`decision`（`USE_NATIVE` | `USE_OCR` | `NEED_REVIEW`）、各检测项原始值与是否达标、简要 `reasons: List<String>`（禁 Map）。

### 4.2 本分支必测项（够用即可）

| 检测项 | 目的 | 默认倾向 |
| --- | --- | --- |
| 页面文本覆盖率 / 非空页比例 | 是否真有文本 | 过低 → `USE_OCR` |
| 可提取字符比例（乱码启发） | 识别明显乱码 | 过低 → `USE_OCR` 或 `NEED_REVIEW` |
| 页面文本密度 | 空白页过多 | 过低 → `NEED_REVIEW` |
| 总页数字符数下限 | 防「只有页脚」 | 过低 → `USE_OCR` |

阈值放配置（`report.parse.probe.*`），带默认值；变更可审计。

### 4.3 本分支弱化项（不作为路由硬条件）

- **阅读顺序完整性、表格可恢复性**：进入质量门禁 / 结构化失败原因，**不**单独驱动 Native↔OCR 自动切换（缺稳定评分器时过拟合）。后续可增强。

### 4.4 混合型 PDF

**本版不支持局部 OCR。** 探针给出整份决策；混合页若 Native 结构化后文档级门禁失败 → `NEED_MANUAL_REVIEW` 或整份改走 OCR（人工/配置策略二选一写死为：门禁失败不自动二次引擎，避免隐式双跑；需要 OCR 时由探针直接 `USE_OCR`）。

---

## 5. 置信度统一语义

| 名称 | 阶段 | 含义 |
| --- | --- | --- |
| `ocrConfidence` | 外置抽取 | 字符识别可靠程度 |
| `layoutConfidence` | 外置抽取 | 块/表/阅读顺序可靠程度 |
| `structureConfidence` | 本仓结构化 | 指标/章节结构识别可靠程度 |
| `FieldConfidence` | 本仓字段 | name/value/unit/refRange 等 |

**硬规则：** OCR 高分 ≠ 医学指标正确。门禁以 **证据完整性**（页码、rawText、总页数、页状态）为主，阈值置信为辅；不得仅凭单一 OCR 分数放行。

---

## 6. 页面级与文档级质量

```text
enum PageParseStatus {
  SUCCESS, LOW_CONFIDENCE, EMPTY, FAILED, NEED_MANUAL_REVIEW
}
```

文档级 `DocumentQualityStatus`：`PASSED` | `NEED_MANUAL_REVIEW` | `FAILED`。

**平衡规则：**

- 页级状态用于定位与 Manifest 统计（`successfulPageCount` / `failedPageCount`）。  
- **任一页 `FAILED`，或无法证明该页不含关键指标时不得 `PASSED`。**  
- 存在 `LOW_CONFIDENCE` / `NEED_MANUAL_REVIEW` 页 → 文档多为 `NEED_MANUAL_REVIEW`（可保留非 active 快照供人工，**不**自动当临床可用）。  
- 不因「大部分页成功」就跳过坏页放行。

---

## 7. OCR HTTP 契约（同步、够用）

### 7.1 必有字段

请求：`requestId`、`documentHash`、`schemaVersion`、`contentType`、`contentBase64`（或等价）、`fileName`、幂等键。  
响应：`requestId`、`schemaVersion`、`processingStatus`、`pageCount`、`pages`、`ocrConfidence`、`layoutConfidence`、`engineVersion`、`errorCode`（失败时）、可选 `serverTaskId`（预留）。

### 7.2 工程语义（本分支落地）

- 连接/读取超时；最大请求/响应体；响应 JSON 强类型反序列化校验。  
- 超时 / 5xx / 契约错误 → 失败阶段码入 Manifest；对外可映射 `4102`。  
- **不做**异步回调闭环；`serverTaskId` 仅预留。

### 7.3 安全与资源（代码侧限额 + 配置）

- PDF 最大页数、图片最大像素、单文件大小（已有则对齐）、单次抽取总超时。  
- OCR 输出**不当系统指令**（防提示注入）。  
- 日志脱敏；临时文件及时清理。  
- 网络隔离、文件保留期限：规格写清，验收归部署/ops。

---

## 8. 任务状态与事务边界

| 场景 | 处理 |
| --- | --- |
| OCR 超时 / 5xx | 解析失败；对外 4102；Manifest 记阶段；**不** active |
| 契约错误 | 失败；不允许落有效 active 版本 |
| 质量不达标 / 关键页失败 | `NEED_MANUAL_REVIEW` 或 FAILED；**不** active；不写可投递档案 Outbox |
| 门禁 PASSED | 落 active `parseVersion` + Manifest → **同事务**跑既有规则链 |

并发：沿用幂等键、`parse_version` 唯一、active 指针；同报告解析用行锁/CAS，防止旧结果覆盖新版本。  
**4102** = 对外语义；内部用 `failureStage` + `detailCode`（进 Manifest），不另造对外码段。

---

## 9. DocumentParseManifest

记录「原始文件 → 抽取 → 结构化 → 门禁」摘要，支撑复现与审计。

```text
DocumentParseManifest
├── documentHash
├── parseVersion
├── parserType / parserVersion
├── extractionEngine / extractionEngineVersion / extractionSchemaVersion
├── totalPageCount / successfulPageCount / failedPageCount
├── qualityStatus
├── qualityIssues[]          （强类型条目：code + message，禁 Map）
├── extractedArtifactRef     （可空；本分支可空字符串）
├── structuredArtifactRef    （可空）
├── probeDecision / probeReasons（可审计）
├── failureStage / detailCode（失败时）
└── createdAt
```

- 库表存 Manifest；**不**把 OCR 全文塞库。  
- 产物 ref 指向对象存储时由 ops 接线；本分支允许 ref 为空，但字段与写入路径要有。

---

## 10. 开源参考（拆开借鉴）

| 项目 | 用法 |
| --- | --- |
| PaddleOCR / PP-Structure | 首选外置抽取 |
| MinerU | 复杂 PDF 对照；同端口，不双跑 |
| Docling | `Extracted*` 形状参考 |
| PyMuPDF | 文本层探针思路；JVM 用等价库实现 |

---

## 11. 验收（RP）

| ID | 内容 |
| --- | --- |
| RP-1 | Native：合格文本层 PDF → 指标 + 证据 + Manifest |
| RP-2 | Fake 端口扫描/图片 → 闭环；Http 可配，默认 verify 不连真网 |
| RP-3 | 失败/不达标 → 4102 或待复核；非假成功 active |
| RP-4 | 单路路由；IMAGING_FILM→4101；无双引擎串行 |
| RP-5 | 无 Map；SQL 仅 XML；置信度分层语义有测试断言 |
| RP-6 | 页级状态影响文档级门禁；坏页不静默跳过放行 |
| RP-7 | Manifest 含 hash/engine/schema/页统计/质量状态 |
| RP-8 | P0～P2 回归不回退 |

## 12. 实施顺序（平衡版）

1. 冻结 `ExtractedDocument` + 置信度字段（版本化契约）  
2. `PdfTextLayerProbe` + `NativePdfParser`  
3. `FakeDocumentExtractionAdapter`  
4. `OcrLayoutParser` + Structurer  
5. 页级/文档级 `ParseQualityGate`  
6. `DocumentParseManifest` + active 事务边界  
7. `HttpDocumentExtractionAdapter`（超时/5xx/契约）  
8. 全量回归  

**先 Fake 跑通 1～6，再接 7。**

## 13. 完成定义

- [x] 本文终冻（平衡修订获确认）  
- [x] 计划与本文顺序一致  
- [x] RP-1～RP-8 + `mvn -B verify` 全绿（327）  
- [ ] 合入 `feature-V1.0-20261120`；**不**宣称临床放行；`V1.0.0` 留给 shadow-gate  

## 14. 后续临床放行

`clinical-facts` → `real-llm` → `kb-release` → `critical-channel` → `archive-his` → `ops-hardening` → `shadow-gate`
