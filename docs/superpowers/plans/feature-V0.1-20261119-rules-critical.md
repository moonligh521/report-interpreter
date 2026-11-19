# feature-V0.1-20261119-rules-critical

> 依赖 `parse`。规范：`docs/工程/企业规范对齐.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.1-20261119-rules-critical` |
| 对应方案 | 1.3 Final §6.4～§7、P0-B |
| 目标 | 身份一致性、可验证 Coverage、RuleContext、危急事件与通知 Outbox、待复核出口 |

---

## Global Constraints

禁 Map；SQL→xml；`Result.code=200`；41xx 平台枚举；危急与解读生命周期隔离；日志不写指标全文。

---

## 1. SDD

1. `ReferenceRangeResult` ≠ `CriticalValueResult` ≠ 解读。  
2. Coverage `UNKNOWN` / 身份 `MISSING|CONFLICT` → 不得「安全正常」；默认阻断档案。  
3. `COMPLETE` 必须带 evidence。  
4. 通知状态机：DETECTED / PENDING_REVIEW / PENDING / SENDING / ACCEPTED / FAILED / RETRYING（ACK 可 P1）。  
5. 必要条件缺失 → UNKNOWN/待复核，不得 NOT_CRITICAL。  
6. 报告自带区间优先，且 refRange 置信度不合格不得强用。

---

## 2. 业务规则（必要规则写入本文）

| ID | 规则 |
| --- | --- |
| R-R1 | 身份 CONFLICT → 4108；不写可投递 archive |
| R-R2 | 身份 MISSING → 4110；默认不归档 |
| R-R3 | Coverage COMPLETE 无 evidence → 非法 |
| R-R4 | 关键页失败未命中危急 → 不得判正常 |
| R-R5 | 适用性失败 → UNKNOWN/待复核，不是 NOT_CRITICAL |
| R-R6 | 无 RAG/LLM 也可持久化 critical_event |
| R-R7 | ACCEPTED ≠ 已读；FAILED 可 RETRYING |
| R-R8 | `report_rule_execution` 含 contextSnapshot + 三类结果 |
| R-R9 | 低置信原文区间不得作 P0 判定依据 |
| R-R10 | RuleContext 必要：canonical、value、unit、specimenType、age、sex、pregnancyStatus?、institution?、method?、specimenTime、fieldConfidence、referenceRangeEvidence |

参考区间优先级：原文可信区间 → 已审核匹配规则 → 有条件标准区间 → UNKNOWN。

---

## 3. 接口与强类型

| 方法 | 路径 |
| --- | --- |
| `GET` | `/api/report/reports/{reportId}` 扩展字段 |
| `GET` | `/api/report/reports/{reportId}/critical-events` |
| `POST` | `/internal/report/reviews` |

**扩展 Res（嵌套对象，非 Map）：**  
`IdentityCheckRes`：`status`、`detail`  
`CriticalCoverageRes`：`status`、`CriticalCoverageEvidence evidence`  
`CriticalCoverageEvidence`：`pagesProcessed: List<Integer>`、`failedPages`、`unmappedFlags`、`unrecognizedRegions`（类型化列表/记录，不用 Map）  
`AbnormalHitRes`：`canonicalCode`、`referenceResult`、`ruleId`、`ruleVersion`  
`CriticalHitRes`：`eventId`、`canonicalCode`、`criticalResult`、`eventStatus`、`notificationStatus`

**`CreateReviewReq`：** `reportId`、`reason`、`priority`  
**`CreateReviewRes`：** `reviewTaskId`、`status`

领域：`ReportIdentityConsistencyChecker`、`CriticalCoverageChecker`、`RuleContextResolver`、`ReferenceRangeEngine`、`CriticalValueEngine`、`SafetyGate`、`CriticalNotificationManager` — 入参出参均为强类型。

---

## 4. Mapper.xml

### ReportRuleExecutionMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_rule_execution
  (id, report_id, parse_version, rule_version,
   context_snapshot_json, reference_result, critical_result, applicability_result,
   created_at)
  VALUES
  (#{id}, #{reportId}, #{parseVersion}, #{ruleVersion},
   #{contextSnapshotJson}, #{referenceResult}, #{criticalResult}, #{applicabilityResult},
   CURRENT_TIMESTAMP(3))
</insert>
<select id="listByReport" resultType="...ReportRuleExecutionRow">
  SELECT id, report_id AS reportId, parse_version AS parseVersion, rule_version AS ruleVersion,
         context_snapshot_json AS contextSnapshotJson,
         reference_result AS referenceResult, critical_result AS criticalResult,
         applicability_result AS applicabilityResult, created_at AS createdAt
  FROM report_rule_execution WHERE report_id = #{reportId} ORDER BY created_at DESC
</select>
```

### ReportCriticalEventMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_critical_event
  (event_id, report_id, parse_version, canonical_code, critical_level,
   event_status, detection_time, idempotency_key)
  VALUES
  (#{eventId}, #{reportId}, #{parseVersion}, #{canonicalCode}, #{criticalLevel},
   #{eventStatus}, #{detectionTime}, #{idempotencyKey})
</insert>
<select id="listByReport" resultType="...ReportCriticalEventRow">
  SELECT event_id AS eventId, report_id AS reportId, parse_version AS parseVersion,
         canonical_code AS canonicalCode, critical_level AS criticalLevel,
         event_status AS eventStatus, detection_time AS detectionTime,
         idempotency_key AS idempotencyKey
  FROM report_critical_event WHERE report_id = #{reportId} ORDER BY detection_time ASC
</select>
<select id="findByIdempotency" resultType="...ReportCriticalEventRow">
  SELECT event_id AS eventId, report_id AS reportId, parse_version AS parseVersion,
         canonical_code AS canonicalCode, critical_level AS criticalLevel,
         event_status AS eventStatus, detection_time AS detectionTime,
         idempotency_key AS idempotencyKey
  FROM report_critical_event WHERE idempotency_key = #{idempotencyKey}
</select>
```

### ReportCriticalNotificationOutboxMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_critical_notification_outbox
  (outbox_id, event_id, notification_status, delivery_channel,
   idempotency_key, attempt_count, next_retry_at, last_error_code,
   created_at, updated_at)
  VALUES
  (#{outboxId}, #{eventId}, #{notificationStatus}, #{deliveryChannel},
   #{idempotencyKey}, #{attemptCount}, #{nextRetryAt}, #{lastErrorCode},
   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
</insert>
<update id="updateStatus">
  UPDATE report_critical_notification_outbox
  SET notification_status = #{notificationStatus},
      attempt_count = #{attemptCount},
      next_retry_at = #{nextRetryAt},
      last_error_code = #{lastErrorCode},
      delivered_at = #{deliveredAt},
      updated_at = CURRENT_TIMESTAMP(3)
  WHERE outbox_id = #{outboxId}
</update>
<select id="listRetryDue" resultType="...CriticalNotificationOutboxRow">
  SELECT outbox_id AS outboxId, event_id AS eventId,
         notification_status AS notificationStatus, delivery_channel AS deliveryChannel,
         idempotency_key AS idempotencyKey, attempt_count AS attemptCount,
         next_retry_at AS nextRetryAt, last_error_code AS lastErrorCode
  FROM report_critical_notification_outbox
  WHERE notification_status IN ('PENDING','RETRYING','FAILED')
    AND (next_retry_at IS NULL OR next_retry_at &lt;= #{now})
  ORDER BY created_at ASC
  LIMIT #{limit}
</select>
```

### ReportReviewTaskMapper.xml

```xml
<insert id="insert">
  INSERT INTO report_review_task
  (review_task_id, report_id, reason, priority, status, created_at)
  VALUES (#{reviewTaskId}, #{reportId}, #{reason}, #{priority}, #{status}, CURRENT_TIMESTAMP(3))
</insert>
```

`context_snapshot_json` 由应用层将 `RuleContext` record 序列化写入；读回反序列化为 `RuleContext`，**禁止**业务层以 `Map` 传递。

---

## 5. 验收 / 6. TDD

AC-R1～R10 同前，另 **AC-R11**：无 Map；SQL 仅 xml。  
测试：Identity*、Coverage*、ApplicabilityUnknown、RefRangeConfidence、CriticalIndependent、NotificationRetry、RuleExecutionSnapshot、MapperXml*。

---

## 7. 完成定义

- [x] SDD 状态机/矩阵冻结（通知状态机、身份/覆盖/规则出口矩阵见代码 `NotificationStatus` / `SafetyGate`）
- [x] TDD+AC（`mvn -B verify` 通过，92 个测试全绿；AC-R1～R11 均有对应测试）
- [ ] 合入版本开发分支（待评审合并）

### 实现约定（落地时补充）

- 身份：`expectedPatientName` 为建单可选表单参数，存 `report_job.expected_patient_name`；报告无身份、或未提供期望姓名 → `MISSING`（保守，不自动绑定）。
- GET 详情：存在阻断时 `Result.code` 取 4108 / 4110 / 4109 / 4106（优先级依次），`data` 仍返回；`CONFLICT` 时不返回指标明细。
- 规则出口状态：`BLOCKED`（身份冲突）/ `NEED_MANUAL_REVIEW`（身份缺失、覆盖 UNKNOWN/PARTIAL、危急适用性 UNKNOWN）/ `RULE_CHECKED`。
- 新增表 `report_rule_summary`（身份、覆盖证据 JSON、报告级结论）；`report_rule_execution` 增 `indicator_id` / `canonical_code` / `reference_rule_id` / `critical_rule_id`；`report_review_task` 增 `idempotency_key`。
- 内置危急规则仅 K（HIGH >= 6.0、LOW < 2.5 mmol/L），规则集版本 `rules-v1.0.0`；其它项目 `NO_RULE`（≠ NOT_CRITICAL），上线前须机构审核。  
