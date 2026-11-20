# 临床放行 · clinical-facts 设计

| 项 | 内容 |
| --- | --- |
| 状态 | **已终冻（方案 A）** |
| 评审 | 架构 / 数据流 / 错误与配置分段确认 |
| 日期 | 2026-11-20 |
| 基线 | `feature-V1.0-20261120`（含 real-parse 合入） |
| 版本线 | `feature-V1.0-20261120` |
| 任务分支 | `feature-V1.0-20261120-clinical-facts` |
| 上位方案 | `docs/superpowers/specs/2026-11-18-AI报告解读企业级完整方案-design.md` §7 |
| 规范 | `docs/工程/企业规范对齐.md` |
| 前序 | `docs/superpowers/specs/2026-11-20-real-parse-design.md` |
| 计划 | `docs/superpowers/plans/feature-V1.0-20261120-clinical-facts.md` |

## 0. 企业级平衡原则

1. **架构不大改**：在既有 `ReportRuleService` / `RuleContextResolver` / 危急与参考区间引擎上接端口；不重写规则主链。  
2. **Fake 先闭环**：契约与 fail-closed 不绑死身份服务；换真实身份 HTTP 不改业务求值语义。  
3. **边界清晰**：人口学来自身份服务；危急/参考目录可版本化加载；**病情主数据仍归 clinical-record / HIS**。  
4. **不做一次做满**：报告侧人口学补缺与冲突合并、真实 LLM、KB 审定、危急通道、HIS 档案、影子运行——本分支不做。  
5. **clinical-facts ≠ 临床放行**：本分支只证明规则链能吃到真实/可切换的人口学与版本化目录，且 `UNKNOWN` 仍 fail-closed。

---

## 1. 背景与目标

现状：`ReportRuleService` 写死 `PatientFacts.UNKNOWN`；危急表主要为 `CriticalRuleTable.builtIn()`。分层参考区间与人群适用性无法用真实人口学验证。

本分支目标：规则执行前经端口取得人口学与版本化临床目录，缺必要条件仍走既有 4106 / 4109 路径，**禁止**因缺失而假 `NOT_CRITICAL` / 「安全正常」。

## 2. 范围

### 2.1 本分支做

- `PatientFactsPort` + `FakePatientFactsAdapter` + `HttpIdentityPatientFactsAdapter`。  
- `ClinicalCatalogPort` + Fake（及可切换 `active-version`）；危急规则表 / 参考区间表由目录供给，替换「仅内置不可切换」。  
- `ReportRuleService`：去掉写死 `UNKNOWN`，经端口取数后再 `RuleContextResolver`。  
- 规则视图 / 落库摘要增加强类型审计字段：`factsSource`、`catalogVersion`、`factsIncomplete`。  
- 验收：Fake 分层命中；`UNKNOWN` 安全禁令；Http 身份失败降级；Catalog 缺版本/缺条目明确失败或待复核；全量 `mvn verify` 绿。

### 2.2 本分支明确不做

| 项 | 处理 |
| --- | --- |
| 报告原文人口学补缺 / 与身份冲突合并 | **不做**；身份优先，失败即 `UNKNOWN` |
| 病情 / 诊断主数据 | **不做**；归 clinical-record / HIS |
| 新平台 ErrorCode 号段扩展 | **默认不做**；复用 4106 / 4109；不够再单列 PR |
| real-llm / kb-release / critical-channel / archive-his / ops / shadow-gate | 后续分支 |

### 2.3 仍禁止

`Map` 契约；SQL 非 XML；成功非 `code=200`；适用性不足时推导安全；未识别危急项当「未命中=正常」。

---

## 3. 架构

```text
规则链入口（parse active 后，既有事务边界内）
  → PatientFactsPort.resolve(patientId[, asOf])
       ├─ FakePatientFactsAdapter          （测试 / 默认 verify）
       └─ HttpIdentityPatientFactsAdapter  （可配；超时/4xx/5xx → UNKNOWN）
  → ClinicalCatalogPort.load(activeVersion)
       ├─ FakeClinicalCatalog
       └─ （本版至少 Fake + 版本切换；classpath/db 可后置实装）
  → CriticalValueEngine / ReferenceRangeEngine 使用目录规则
  → RuleContextResolver.resolve(indicator, facts)
  → 适用性 / 危急 / 参考区间 / SafetyGate（既有 fail-closed）
  → 持久化 RuleExecution / Summary（含 factsSource、catalogVersion、factsIncomplete）
```

人口学：**身份服务优先**；缺字段或调用失败 → `PatientFacts.UNKNOWN`，**不编造**。

---

## 4. 数据流

1. 入参已有 `patientId`（及可选 `patientVersion` / 就诊时点）→ `PatientFactsPort.resolve`。  
2. 身份服务返回出生日期/性别等 → 计算年龄（整数岁，按 asOf 日）；妊娠等本版可空。  
3. `ClinicalCatalogPort.load(catalogVersion)` → 危急值表 + 参考区间表（与现有引擎接口对齐）。  
4. `RuleContextResolver` 组装上下文 → 现有规则求值。  
5. 结果带：`factsSource` / `catalogVersion` / `factsIncomplete`（便于审计，不改 `Result` 外壳）。

参考区间优先级仍遵循上位方案 §7.3（报告原文区间优先，再目录分层，否则 `UNKNOWN`）。

---

## 5. 端口契约（强类型，禁 Map）

### 5.1 PatientFactsPort

```text
PatientFactsPort
  resolve(patientId, asOfInstant?) → PatientFactsResolution

PatientFactsResolution
  facts: PatientFacts          （age / sex / pregnancyStatus；均可空）
  source: FactsSource          （FAKE | IDENTITY | IDENTITY_FAILED | NONE）
  incomplete: boolean          （任一规则必需字段缺失则为 true）
  detailCode: String?          （失败时，如 TIMEOUT / HTTP_4XX / CONTRACT）
```

`PatientFacts.UNKNOWN` = 全空；`incomplete=true` 时适用性不得当作「已满足」。

### 5.2 ClinicalCatalogPort

```text
ClinicalCatalogPort
  load(version) → ClinicalCatalog

ClinicalCatalog
  version: String
  criticalRules: CriticalRuleTable   （或等价只读视图）
  referenceRules: …                 （分层参考区间条目，与引擎对齐）
```

- 版本不存在 / 必填条目缺失 → 抛业务错或返回明确失败，由规则编排映射 **4106**。  
- 历史判定钉死当时 `catalogVersion`；**不**因新目录改写旧结果。

---

## 6. 错误与配置

### 6.1 错误（复用 41xx）

| 场景 | 对外 | 对内 |
| --- | --- | --- |
| 身份 HTTP 超时 / 4xx / 5xx / 契约坏 | **不**整单解析失败；Facts → `UNKNOWN` | `factsSource=IDENTITY_FAILED` + detail；适用性不足 → **4106** |
| 人口学缺失导致危急覆盖算不清 | **4109** | 禁止「安全正常」/ 假 `NOT_CRITICAL` |
| Catalog 版本不存在 / 必填条目缺失 | **4106** | `catalogVersion` + `missingKey` |
| 启用 Http 但 baseUrl 空等非法配置 | 启动失败或明确业务错 | 与 real-parse Http 适配同风格 |

身份失败**不**映射 4102（解析域）。

### 6.2 配置

```yaml
report:
  clinical-facts:
    patient-facts-adapter: fake   # fake | http-identity
    identity:
      base-url: ""
      connect-timeout-ms: 1000
      read-timeout-ms: 2000
    catalog:
      adapter: fake               # fake | classpath | db（本版至少 fake）
      active-version: "builtin-v1"
```

测试 `application-test.yml` 默认 Fake，verify **不**连真网。

---

## 7. 与既有组件衔接

| 组件 | 变化 |
| --- | --- |
| `ReportRuleService` | 注入端口；`resolver.resolve(..., facts)`；引擎用 catalog 表 |
| `RuleConfig` | 按配置装配 Fake/Http；`CriticalValueEngine` 表来自 catalog |
| `PatientFacts` | 语义不变；注释改为「来自身份端口，失败 UNKNOWN」 |
| `SafetyGate` / Coverage | 行为不变；补齐人口学后 Fake 用例可命中分层规则 |
| 解析 / Manifest | **不改** real-parse 契约 |

---

## 8. 验收（CF）

| ID | 内容 |
| --- | --- |
| CF-1 | Fake：给定年龄/性别可命中分层参考区间与危急阈值 |
| CF-2 | `UNKNOWN` / `factsIncomplete` → 不得误判非危急或「安全正常」（4106/4109 路径） |
| CF-3 | Http 身份：契约测；超时/4xx/5xx → `UNKNOWN` + `IDENTITY_FAILED`；不炸解析 |
| CF-4 | Catalog：版本可切换；缺版本/缺条目 → 4106 或待复核，不静默错表 |
| CF-5 | 审计字段写入规则视图/摘要；无 Map；SQL 仅 XML |
| CF-6 | P0～P2 + real-parse 回归不回退；`mvn -B verify` 全绿 |

## 9. 实施顺序

1. 冻结 `PatientFactsResolution` / `FactsSource` / Catalog 版本字段  
2. `PatientFactsPort` + Fake；改 `ReportRuleService` 接线  
3. Fake 分层参考区间 + 危急目录用例（CF-1/CF-2）  
4. `ClinicalCatalogPort` + 版本切换（CF-4）  
5. `HttpIdentityPatientFactsAdapter`（超时/4xx/5xx/契约）（CF-3）  
6. 审计字段落库/视图（CF-5）  
7. 全量回归（CF-6）  

**先 Fake 跑通 1～4，再接 5。**

## 10. 完成定义

- [x] 本文终冻（方案 A 分段确认）  
- [x] 计划与本文顺序一致  
- [x] CF-1～CF-6 + `mvn -B verify` 全绿（342）  
- [x] 合入 `feature-V1.0-20261120`；**不**打 `V1.0.0`；不宣称临床放行  

## 11. 后续临床放行

`real-llm` → `kb-release` → `critical-channel` → `archive-his` → `ops-hardening` → `shadow-gate`
