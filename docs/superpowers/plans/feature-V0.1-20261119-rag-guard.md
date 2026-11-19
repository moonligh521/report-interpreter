# feature-V0.1-20261119-rag-guard

> 依赖 `rules-critical`。规范：`docs/工程/企业规范对齐.md`。Hybrid RAG 同构对照：`docs/对齐/菜品推荐检索企业级闭环-design.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.1-20261119-rag-guard` |
| 对应方案 | 1.3 Final §8、P0-C |
| 目标 | 最小 KB + Hybrid RAG + 版本钉死 + 受控模板 + OutputGuard + 确定性 Validator |

---

## Global Constraints

禁 Map；SQL→xml；`Result.code=200`；检索 fail-closed（有过滤时空结果不退全库，对齐菜品）；纠正不删已 DETECTED 危急；KB 集合与营养隔离。

---

## 1. SDD

1. 仅 `PUBLISHED` KB；同次钉死 kb/rule/prompt/parser/schema。  
2. `INTERPRETATION_DELTA` 只能是 Guard 通过块。  
3. 确定性校验：数值/单位/区间/异常/危急/引用存在性。  
4. 失败策略：VALID / RETRYABLE / UNSUPPORTED / CONFLICT / SAFETY_VIOLATION。  
5. 不下确诊、不开方；不以另一 AI 作最终医学保证。  
6. 完整 SSE 落库在 `delivery-gate`。

---

## 2. 业务规则

| ID | 规则 |
| --- | --- |
| G-R1 | 无命中 KB → 降级话术，无伪造指南口吻 |
| G-R2 | 响应含完整 `PinnedVersions` 对象（非 Map） |
| G-R3 | 改数/降危急生成 → 拦截 |
| G-R4 | 假 evidenceId → 拦截 |
| G-R5 | 与 Java HIGH 冲突的「正常」→ CONFLICT |
| G-R6 | SAFETY_VIOLATION 立即终止 |
| G-R7 | 纠正不删 critical_event |
| G-R8 | 检索带 indicator/specialty 等**结构化过滤**；禁止只靠向量猜类型 |
| G-R9 | 有强制过滤时检索失败/空 → 空列表+空因，禁止 fail-open 退全库 |

---

## 3. 接口与强类型

| 方法 | 路径 |
| --- | --- |
| `POST` | `/api/report/reports/{reportId}/interpretation` |
| `GET` | `/api/report/reports/{reportId}/interpretation` |
| `POST` | `/internal/report/kb/reseed` |

**`TriggerInterpretationReq`：** `focusCanonicalCodes: List<String>`（可空）  
**`InterpretationRes`：** `interpretationId`、`status`、`PinnedVersions pinnedVersions`、`List<InterpretationBlockRes> blocks`、`List<EvidenceRes> evidence`  
**`PinnedVersions`：** `kbVersion`/`ruleVersion`/`promptVersion`/`parserVersion`/`schemaVersion`  
**`InterpretationBlockRes`：** `blockId`、`claimType`、`text`、`evidenceIds: List<String>`、`validationStatus`  
**`EvidenceRes`：** `evidenceId`、`docId`、`title`、`snippet`、`url`  
**`KbReseedReq`：** `source`、`dryRun`  
**`KbReseedRes`：** `documentsUpserted`、`chunksUpserted`、`kbVersion`

领域：`HybridRecall#search(RecallQuery, RecallFilter): List<KnowledgeChunk>`；`InterpretationOutputGuard`；`InterpretationValidator` — 全强类型。

---

## 4. Mapper.xml

### ReportKbDocumentMapper.xml / ReportKbChunkMapper.xml

```xml
<select id="listPublishedChunks" resultType="...KbChunkRow">
  SELECT c.chunk_id AS chunkId, c.doc_id AS docId, c.content, c.title,
         c.indicator_code AS indicatorCode, c.specialty, c.url,
         d.kb_version AS kbVersion, d.authority_level AS authorityLevel
  FROM report_kb_chunk c
  INNER JOIN report_kb_document d ON d.doc_id = c.doc_id
  WHERE d.status = 'PUBLISHED'
    AND d.effective_from &lt;= #{now} AND (d.effective_to IS NULL OR d.effective_to &gt; #{now})
    <if test="indicatorCode != null">AND c.indicator_code = #{indicatorCode}</if>
    <if test="specialty != null">AND c.specialty = #{specialty}</if>
</select>
```

### ReportInterpretationMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_interpretation
  (interpretation_id, report_id, status,
   kb_version, rule_version, prompt_version, parser_version, schema_version, created_at)
  VALUES
  (#{interpretationId}, #{reportId}, #{status},
   #{kbVersion}, #{ruleVersion}, #{promptVersion}, #{parserVersion}, #{schemaVersion},
   CURRENT_TIMESTAMP(3))
</insert>
```

### ReportInterpretationEvidenceMapper.xml

```xml
<insert id="batchInsert">
  INSERT INTO report_interpretation_evidence
  (id, interpretation_id, evidence_id, doc_id, title, snippet, url)
  VALUES
  <foreach collection="rows" item="r" separator=",">
    (#{r.id}, #{r.interpretationId}, #{r.evidenceId}, #{r.docId}, #{r.title}, #{r.snippet}, #{r.url})
  </foreach>
</insert>
```

---

## 5. 验收 / 6. TDD

AC-G1～G9 + **AC-G10** 无 Map/SQL 仅 xml。  
测试：KbEmptyDegrade、PinVersions、Validator*、FakeEvidence、ReportConsistency、GuardStrategy、NoDiagnosisLexicon、FailClosedNoFullCatalog、MapperXml*。  
先 Fake LLM 驱动 Validator/Guard。

---

## 7. 完成定义

- [x] SDD 冻结（`ValidationStatus` 严重度、Guard 失败策略、结果码 4104/4105/4107 见代码与测试）
- [x] TDD+AC（`mvn -B verify` 通过，195 个测试全绿；AC-G1～G10 均有对应测试；FakeInterpretationModel 驱动，无需真实 LLM）
- [x] 合入版本开发分支 `feature-V0.1-20261119`

### 实现备注

- 结果码：校验未通过 `4107`（data 仅含通过 Guard 的块）；报告含危急值 `4105`（data 仍返回，首块恒为 Java 模板 `CRITICAL_ALERT`）；`allow-degraded=false` 且 KB 无依据 `4104`；身份冲突 / 缺失沿用 `4108` / `4110`（不出解读）。
- Guard 严重度：`SAFETY_VIOLATION`（诊断/处方词、降低危急）立即终止 > `CONFLICT` > `UNSUPPORTED` > `RETRYABLE`（改数，最多 `report.interpretation.max-attempts` 次重生成）。
- 召回 fail-closed：存储异常 / 过滤后为空 / 模糊词无命中均返回空列表 + `RecallEmptyReason`；存储层漏过滤也由 `HybridRecall` 二次校验；`intersect(ids 空)` 返回空列表。
- 纠正不删 critical_event：解读与重解析不触碰事件表；当前版本不再危急但历史事件仍在时，输出 `NOTICE` 并保持 `4105`。
- 已知后续：真实 LLM 适配器与 SSE 落库在 `delivery-gate`；KB 生产 DDL 随部署脚本，本仓仅 `schema-test.sql`。  
