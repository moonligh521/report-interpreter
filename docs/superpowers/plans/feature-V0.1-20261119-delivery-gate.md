# feature-V0.1-20261119-delivery-gate

> 依赖 `rag-guard`。合入后打通 V0.1 **P0 技术闭环**（≠ 临床放行）。规范：`docs/工程/企业规范对齐.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.1-20261119-delivery-gate` |
| 对应方案 | 1.3 Final §10～§13、P0-D |
| 目标 | SSE 事件落库与回放、三生命周期隔离、档案 Outbox、补偿、观测、门禁包 |

---

## Global Constraints

禁 Map；事件 payload 用类型化 `*Payload` record，禁止 `payload: {}`/`Map`；SQL→xml；档案 Outbox ≠ 危急 Outbox；身份非 MATCHED 不投递档案；`Result.code=200`。

---

## 1. SDD

1. 重要 SSE 事件持久化 `report_event`；Redis 仅加速。  
2. `INTERPRETATION_DELTA` 仅已校验块。  
3. `(jobId, sequence)` 唯一；拒旧 epoch；重连再鉴权。  
4. 解读 COMPLETED ≠ 档案成功 ≠ 危急已读。  
5. 本分支关门 = 技术闭环 ≠ 真实患者放行。

---

## 2. 业务规则

| ID | 规则 |
| --- | --- |
| D-R1 | Last-Event-ID 续传已落库事件 |
| D-R2 | DELTA 必须 validationStatus=VALID |
| D-R3 | job COMPLETED 时 archive 可未 SUCCESS |
| D-R4 | MATCHED 且解读完成 → archive outbox 同事务写入 |
| D-R5 | CONFLICT/MISSING → 不写可投递 archive |
| D-R6 | 同 eventId 消费幂等 |
| D-R7 | 补偿可将 DEAD 拉回（策略允许时） |
| D-R8 | 旧 epoch 丢弃 |
| D-R9 | 日志无指标全文/证件号 |

---

## 3. 接口与强类型

| 方法 | 路径 |
| --- | --- |
| `GET` | `/api/report/reports/{reportId}/interpretation/stream` |
| `POST` | `/internal/report/jobs/{id}/compensate` |
| `GET` | `/api/report/reports/{reportId}/events` |

**`ReportSseEventRes`：** `eventId`、`reportId`、`sequence`、`eventType`、`timestamp`、**按类型分发的强类型 payload 字段**（可用 sealed 层次：`StructuredResultPayload`、`CriticalAlertPayload`、`InterpretationDeltaPayload`、`CorrectionPayload`、`DonePayload`、`ErrorPayload`），**禁止** `Map payload`。

**`CompensateReq`：** `outboxId`、`force`  
**`CompensateRes`：** `outboxId`、`status`、`attemptCount`  
**`ArchiveOutboxMessage`：** `eventId`、`schemaVersion`、`reportId`、`patientId`、`parseVersion`、`interpretationId`、`List<IndicatorDto> indicators`、`identityStatus`

---

## 4. Mapper.xml

### ReportEventMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_event
  (event_id, report_id, job_id, sequence, event_type, epoch, payload_json, created_at)
  VALUES
  (#{eventId}, #{reportId}, #{jobId}, #{sequence}, #{eventType}, #{epoch}, #{payloadJson},
   CURRENT_TIMESTAMP(3))
</insert>
<select id="listAfterSequence" resultType="...ReportEventRow">
  SELECT event_id AS eventId, report_id AS reportId, job_id AS jobId, sequence,
         event_type AS eventType, epoch, payload_json AS payloadJson, created_at AS createdAt
  FROM report_event
  WHERE report_id = #{reportId} AND sequence &gt; #{afterSequence}
  ORDER BY sequence ASC
</select>
```

### ReportArchiveOutboxMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_archive_outbox
  (outbox_id, report_id, event_id, schema_version, status, attempt_count,
   payload_json, created_at, updated_at)
  VALUES
  (#{outboxId}, #{reportId}, #{eventId}, #{schemaVersion}, #{status}, #{attemptCount},
   #{payloadJson}, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
</insert>
<update id="updateStatus">
  UPDATE report_archive_outbox
  SET status = #{status}, attempt_count = #{attemptCount},
      last_error_code = #{lastErrorCode}, updated_at = CURRENT_TIMESTAMP(3)
  WHERE outbox_id = #{outboxId}
</update>
<select id="findByEventId" resultType="...ArchiveOutboxRow">
  SELECT outbox_id AS outboxId, report_id AS reportId, event_id AS eventId,
         schema_version AS schemaVersion, status, attempt_count AS attemptCount,
         payload_json AS payloadJson
  FROM report_archive_outbox WHERE event_id = #{eventId}
</select>
```

应用层将 `*Payload` / `ArchiveOutboxMessage` 序列化为 JSON 列；读写经强类型，禁止业务 `Map`。

---

## 5. 验收 / 6. TDD

AC-D1～D10 + **AC-D11** 无 Map payload。  
测试：SseReplay、DeltaMustBeValid、JobCompletedVsArchive、ArchiveOutboxTx、ArchiveBlockedByIdentity、OutboxIdempotency、EpochReject、CompensateApi、GateSuiteIT、MapperXml*。

---

## 7. 完成定义

- [ ] SDD；TDD+AC；版本分支达 V0.1 技术完成；可打 `V0.1.0`（非临床放行）  
