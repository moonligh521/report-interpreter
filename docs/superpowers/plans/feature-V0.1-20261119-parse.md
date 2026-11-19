# feature-V0.1-20261119-parse

> 依赖 `foundation` 已合入。规范：`docs/工程/企业规范对齐.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.1-20261119-parse` |
| 对应方案 | 1.3 Final §4～§6、P0-A |
| 目标 | LAB_PANEL：上传、四阶段解析、raw/normalized、分层置信度、原文证据、parseVersion |

---

## Global Constraints

同 foundation：禁 Map；SQL→mapper.xml；`Result.code=200`；`Idempotency-Key`；只依赖 platform-*；手工映射无 Lombok。

---

## 1. SDD

1. 仅 `LAB_PANEL`（其它 `4101`）。  
2. `ReportParser` SPI：`supports`/`parse`；禁止无条件多引擎串行。  
3. raw 与 normalized 双轨；规范化不得覆盖 raw。  
4. 置信度分层 + 字段级。  
5. 关键字段 `pageIndex`+`rawText` 必填。  
6. 重解析只增 `parseVersion`。  
7. 可不做规则/RAG；状态可停 `PARSED`。

---

## 2. 业务规则

| ID | 规则 |
| --- | --- |
| P-R1 | 非 LAB/片子 → 4101 |
| P-R2 | MIME+魔数/超页超大 → 拒绝 |
| P-R3 | 同 account+Idempotency-Key → 同一 reportId；同键不同哈希 → 冲突码 |
| P-R4 | 规范化失败保留 raw，不得 silent 改 raw |
| P-R5 | 缺 pageIndex/rawText → 质量门禁失败 |
| P-R6 | 重解析 version=max+1；旧 active=false |
| P-R7 | 禁止单一 model score 放行 |

---

## 3. 接口与强类型

**`CreateReportReq` / `CreateReportRes` / `ReportDetailRes` / `IndicatorDto` / `FieldConfidence` / `SourceEvidence` / `BoundingBox`** — 全为 record/明确字段，禁止 Map。

`CreateReportRes`：`reportId`、`status`、`parseVersion`  
`ReportDetailRes`：`reportId`、`reportType`、`status`、`parseVersion`、`activeParse`、`parseConfidence`、`List<IndicatorDto> indicators`

成功：`Result.code=200`。

SPI：`ReportParser#supports` / `#parse(ParseContext): ParseResult` — 强类型。

---

## 4. Mapper.xml

### ReportParseResultMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_parse_result
  (id, report_id, parse_version, active, report_type, parse_confidence, status, created_at)
  VALUES (#{id}, #{reportId}, #{parseVersion}, #{active}, #{reportType}, #{parseConfidence}, #{status},
          CURRENT_TIMESTAMP(3))
</insert>
<update id="deactivatePrevious">
  UPDATE report_parse_result SET active = 0, updated_at = CURRENT_TIMESTAMP(3)
  WHERE report_id = #{reportId} AND active = 1
</update>
<select id="findActive" resultType="...ReportParseResultRow">
  SELECT id, report_id AS reportId, parse_version AS parseVersion, active,
         report_type AS reportType, parse_confidence AS parseConfidence, status
  FROM report_parse_result WHERE report_id = #{reportId} AND active = 1
</select>
```

### ReportIndicatorMapper.xml

```xml
<insert id="batchInsert">
  INSERT INTO report_indicator
  (id, parse_result_id, report_id, parse_version,
   raw_name, raw_value, raw_unit, canonical_code, specimen_type,
   normalized_value, normalized_unit, normalization_status, ref_low, ref_high,
   conf_ocr, conf_structure, conf_extraction, conf_normalization,
   conf_name, conf_value, conf_unit, conf_ref_range)
  VALUES
  <foreach collection="rows" item="r" separator=",">
    (#{r.id}, #{r.parseResultId}, #{r.reportId}, #{r.parseVersion},
     #{r.rawName}, #{r.rawValue}, #{r.rawUnit}, #{r.canonicalCode}, #{r.specimenType},
     #{r.normalizedValue}, #{r.normalizedUnit}, #{r.normalizationStatus}, #{r.refLow}, #{r.refHigh},
     #{r.confOcr}, #{r.confStructure}, #{r.confExtraction}, #{r.confNormalization},
     #{r.confName}, #{r.confValue}, #{r.confUnit}, #{r.confRefRange})
  </foreach>
</insert>
<select id="listByParseResult" resultType="...ReportIndicatorRow">
  SELECT id, parse_result_id AS parseResultId, report_id AS reportId, parse_version AS parseVersion,
         raw_name AS rawName, raw_value AS rawValue, raw_unit AS rawUnit,
         canonical_code AS canonicalCode, specimen_type AS specimenType,
         normalized_value AS normalizedValue, normalized_unit AS normalizedUnit,
         normalization_status AS normalizationStatus, ref_low AS refLow, ref_high AS refHigh,
         conf_ocr AS confOcr, conf_structure AS confStructure,
         conf_extraction AS confExtraction, conf_normalization AS confNormalization,
         conf_name AS confName, conf_value AS confValue, conf_unit AS confUnit,
         conf_ref_range AS confRefRange
  FROM report_indicator WHERE parse_result_id = #{parseResultId} ORDER BY id ASC
</select>
```

### ReportSourceEvidenceMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_source_evidence (id, indicator_id, page_index, raw_text, region_json)
  VALUES (#{id}, #{indicatorId}, #{pageIndex}, #{rawText}, #{regionJson})
</insert>
<select id="findByIndicator" resultType="...ReportSourceEvidenceRow">
  SELECT id, indicator_id AS indicatorId, page_index AS pageIndex,
         raw_text AS rawText, region_json AS regionJson
  FROM report_source_evidence WHERE indicator_id = #{indicatorId}
</select>
```

---

## 5. 验收 / 6. TDD

| AC | 标准 |
| --- | --- |
| AC-P1～P9 | 同前：金标、证据、幂等、拒片子、无双引擎串行等 |
| AC-P10 | 无 Map；SQL 仅 xml；成功码 200 |

测试：`IdempotencyTest`、`FileSecurityTest`、`LabParserGoldenTest`、`RawNormalizedTest`、`SourceEvidenceTest`、`ParseVersionTest`、`ReportTypeRejectTest`、`NoMapContractTest`、`IndicatorMapperXmlTest`。

---

## 7. 完成定义

- [x] SDD  
- [x] TDD+AC（`mvn -B verify` 通过，44 个测试全绿）  
- [x] 合入版本开发分支 `feature-V0.1-20261119`  

### 实现说明与遗留

- AC-P1～P10 对应测试：`LabParserGoldenTest`、`SourceEvidenceTest`、`IdempotencyTest`、`ReportTypeRejectTest`、`NoDualEngineTest`、`FileSecurityTest`、`ParseVersionTest`、`RawNormalizedTest`、`NoMapContractTest`、`IndicatorMapperXmlTest`。  
- P0 未接真实 OCR：解析仅覆盖文本金标夹具（`text/plain` / `.lab.txt`，由 `report.parse.allow-text-fixture` 控制，生产默认 false）；合法 PDF/图片仅建单，状态 `CREATED`、`parseVersion=0`。  
- 重解析使用建单时保存的 `source_text`（仅夹具场景），重解析以 `Idempotency-Key` 回放。  
- 生产 MySQL DDL 尚未入库（仓内仅有 `schema-test.sql`），需随 delivery-gate 补充。  
- 未做超页数（P-R2 页数部分）校验，待真实 OCR/PDF 解析接入。  
