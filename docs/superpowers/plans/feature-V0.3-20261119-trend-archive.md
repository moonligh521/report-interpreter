# feature-V0.3-20261119-trend-archive

> 从 `V0.2.0` 拉 `feature-V0.3-20261119`。规范：`docs/工程/企业规范对齐.md`。临床边界见 `docs/对齐/公共HIS与临床快照边界.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.3-20261119-trend-archive` |
| 对应方案 | 1.3 Final P2 |
| 目标 | 历史趋势（可比性）、出院小结/内镜、clinical-record 深度集成、可选 FHIR 提示字段、压测放行 |

---

## Global Constraints

禁 Map；SQL→xml；Outbox 仍分离；clinical-record 为病情唯一主人；P0/P1 不变量不回退；`Result.code=200`。

---

## 1. SDD

1. 趋势自动比较仅当同 canonical + 标本/时间维可比 + 单位可换算；否则并列并声明不可比。  
2. 不做 AI 确诊式趋势病因推断。  
3. 档案深度集成仍经 Outbox。  
4. FHIR 映射为可选适配字段。  
5. 压测与放行清单通过才允许 `V0.3.0`。

---

## 2. 业务规则

| ID | 规则 |
| --- | --- |
| T-R1 | 同码不同标本默认不可自动比 |
| T-R2 | 单位不可换算 → comparable=false + 原因码 |
| T-R3 | 趋势接口无诊断话术 |
| T-R4 | schemaVersion=2 消费幂等 |
| T-R5 | 身份非 MATCHED 仍不投递 |

---

## 3. 接口与强类型

| 方法 | 路径 |
| --- | --- |
| `GET` | `/api/report/patients/{patientId}/indicators/{canonicalCode}/trend` |
| `GET` | `/internal/report/archive/outbox/{id}` |

**查询：** `specimenType`、`from`、`to`、`requireComparable`  
**`TrendRes`：** `canonicalCode`、`comparable`、`incomparableReason`、`List<TrendPoint> points`、`disclaimer`  
**`TrendPoint`：** `reportId`、`specimenTime`、`normalizedValue`、`normalizedUnit`、`parseVersion`  
**`ArchiveOutboxMessageV2`：** 在 V1 字段上增加 `reportType`、`List<SectionDto> sections`、`FhirHints fhirHints?`  
**`FhirHints`：** `resourceType`、`List<LoincMapping> loincMappings`（类型化，非 Map）

---

## 4. Mapper.xml

```xml
<select id="listTrendPoints" resultType="...TrendPointRow">
  SELECT i.report_id AS reportId, i.parse_version AS parseVersion,
         i.normalized_value AS normalizedValue, i.normalized_unit AS normalizedUnit,
         i.specimen_type AS specimenType, p.specimen_time AS specimenTime
  FROM report_indicator i
  INNER JOIN report_parse_result p ON p.id = i.parse_result_id AND p.active = 1
  INNER JOIN report_job j ON j.report_id = i.report_id
  WHERE j.patient_id = #{patientId}
    AND i.canonical_code = #{canonicalCode}
    <if test="specimenType != null">AND i.specimen_type = #{specimenType}</if>
    <if test="from != null">AND p.specimen_time &gt;= #{from}</if>
    <if test="to != null">AND p.specimen_time &lt;= #{to}</if>
  ORDER BY p.specimen_time ASC
</select>

<select id="findArchiveOutbox" resultType="...ArchiveOutboxRow">
  SELECT outbox_id AS outboxId, report_id AS reportId, event_id AS eventId,
         schema_version AS schemaVersion, status, attempt_count AS attemptCount,
         payload_json AS payloadJson
  FROM report_archive_outbox WHERE outbox_id = #{outboxId}
</select>
```

---

## 5. 验收 / 6. TDD

AC-T1～T9 + **AC-T10** 无 Map。  
测试：TrendComparable、TrendUnit、TrendDisclaimer、DischargeParserGolden、EndoscopyFindings、ArchiveSchemaV2、ArchiveCompensateIT、LoadSmokeIT、FullRegressionIT。

---

## 7. 完成定义

- [x] SDD；TDD+AC；`release-V0.3-*` → `master` → `V0.3.0`（`mvn -B verify` **297** 测全绿）  
