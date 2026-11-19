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

- [x] SDD 冻结（事件类型 / sealed payload、三生命周期、档案门禁、补偿策略见代码与测试）
- [x] TDD+AC（`mvn -B verify` 通过，259 个测试全绿 = rag-guard 的 195 + 本分支 64；AC-D1～D11 均有对应测试，另加 D-R9 日志防泄漏测试）
- [x] 合入版本开发分支 `feature-V0.1-20261119`；V0.1 **技术闭环**完成，可打 `V0.1.0`（非临床放行）

> **V0.1 技术完成 ≠ 临床放行。** 本版本只证明「P0 技术闭环 + 安全门禁在测试夹具上成立」；
> 真实患者 / 真实报告放行必须另走临床验证（见下「临床放行前仍缺」）。

### AC 对照

| AC | 内容 | 测试 |
| --- | --- | --- |
| AC-D1 | 重要事件落库 `report_event`，流 = DB | `SseReplayTest` |
| AC-D2 | `Last-Event-ID` 从 DB 续传；重连再鉴权 | `SseReplayTest` |
| AC-D3 | `(jobId, sequence)` 唯一；序号连续 | `SseReplayTest`、`MapperXmlDeliveryTest` |
| AC-D4 | DELTA 仅 `validationStatus=VALID` | `DeltaMustBeValidTest` |
| AC-D5 | 解读 COMPLETED ≠ 档案 SUCCESS ≠ 危急已读 | `JobCompletedVsArchiveTest` |
| AC-D6 | MATCHED 且完成 → 档案 Outbox 同事务；失败整体回滚 | `ArchiveOutboxTxTest` |
| AC-D7 | 非 MATCHED / 规则阻断不写可投递档案 | `ArchiveBlockedByIdentityTest` |
| AC-D8 | 按 eventId 入队 / 消费幂等 | `OutboxIdempotencyTest` |
| AC-D9 | 旧 epoch 拒绝、不落库、不回放 | `EpochRejectTest` |
| AC-D10 | `POST /internal/report/jobs/{id}/compensate` | `CompensateApiTest` |
| AC-D11 | payload / 消息 / 契约无 Map（sealed + record） | `NoMapPayloadTest` |
| 门禁包 | deterministic / adversarial / critical / coverage 冒烟 + 交付不变量 | `GateSuiteIT` |
| D-R9 | 日志无指标全文 / 解读文本 / 姓名 / 证件号（运行期捕获 + 源码规则） | `NoSensitiveLogTest` |

### 实现备注

- **jobId = reportId**（`report_job.report_id`）。`epoch` 起始为 1，**重解析（reparse）+1**；事件写入 `epoch != job.epoch` 一律拒绝（`StaleEpochException`），回放只返回当前 epoch 的事件，序号跨代连续。
- **SSE**：`GET /api/report/reports/{reportId}/interpretation/stream` 返回 `text/event-stream`（`id:`=eventId，`event:`=类型，`data:`=单行 JSON）。当前是「回放已落库事件后结束」的续传语义（解读同步完成，事件已全部落库），客户端按 `Last-Event-ID` 重连即可；**尚无 live tail / Redis 加速**。
- **事件**：`STRUCTURED_RESULT` → （重解读时 `CORRECTION` 先行）→ `CRITICAL_ALERT` / `INTERPRETATION_DELTA`（仅 VALID）→ `DONE`（通过）或 `ERROR`（4107）。`GET /api/report/reports/{reportId}/events` 返回事件列表 + 三条生命周期。
- **档案**：`ArchiveGate`（MATCHED + 规则未阻断归档 + 解读完成）放行才在**解读同一事务**里写 `report_archive_outbox`（`event_id = arc_<interpretationId>`）；`ArchiveConsumer` 按 eventId 幂等落 `report_archive_record`，并二次拒绝非 MATCHED。状态机 `PENDING/FAILED/SUCCESS/DEAD`，默认最多 5 次（`report.archive.max-attempts`）。与危急通知 Outbox 分表、分状态机、分服务。
- **补偿**：DEAD → PENDING（重置次数）；FAILED 需 `force`；SUCCESS 永不；**身份门禁不可被 force 越过**（CONFLICT 4108 / MISSING 4110 / 规则阻断 4106）。
- **三生命周期**：解读（`COMPLETED/FAILED/NONE`，来自 `report_interpretation`）、档案（`PENDING/FAILED/SUCCESS/DEAD/BLOCKED/NONE`，来自档案 Outbox）、危急已读（`NONE/UNREAD/READ`，仅用户 `ACKNOWLEDGED` 才算 READ，`ACCEPTED` 不算）。`report_job.status` 保持规则出口状态不被覆盖，避免丢失 NEED_MANUAL_REVIEW。
- 新表 DDL 仅在 `schema-test.sql`（生产 DDL 随部署脚本）：`report_event`、`report_archive_outbox`、`report_archive_record`。
- 重构：`IndicatorDtoAssembler` 抽出，报告详情与档案消息共用同一份 `IndicatorDto` 口径。
- `maven-surefire-plugin` 追加 `**/*IT.java`，`GateSuiteIT` 随 `mvn test` / `verify` 运行。

### 临床放行前仍缺（非本分支范围）

1. 真实 LLM 适配器与线上 Prompt 红队；目前仅 `FakeInterpretationModel` 驱动。
2. 真实 OCR / PDF / 图片解析（P0 仅文本夹具 `LAB_PANEL`）；多页、手写、扫描质量门槛。
3. 临床签署的危急值表与参考区间表（目前内置样例，仅 K 等少量项目），含年龄 / 性别 / 标本时间等必要条件的真实来源（`PatientFacts` 目前恒为 UNKNOWN → 相关规则 fail-closed）。
4. 临床审定的知识库（KB 仅 `lab-min` 夹具），版本发布 / 回滚流程。
5. 危急通知真实通道（短信 / 电话 / 院内 IM）与 SLA、人工升级（ESCALATED）处置流程；用户确认（ACKNOWLEDGED）入口目前未暴露接口。
6. 档案真实下游（HIS / 临床数据仓库）适配器与对账；目前是本服务内的幂等落地桩。
7. SSE live tail / Redis 加速 / 多实例一致性；Outbox 调度器与告警（当前仅有可调用的派发服务，无定时任务）。
8. 生产 DDL / 迁移脚本、鉴权网关路由登记、审计日志与合规（等保 / 个保）评审、压测与故障演练。
9. 真实患者数据小样本影子运行（shadow mode）与临床专家抽检，作为放行门槛。
