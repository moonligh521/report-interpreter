# feature-V0.2-20261119-multi-type

> 从 `V0.1.0` 标签的 `master` 拉 `feature-V0.2-20261119`，再建本任务分支。规范：`docs/工程/企业规范对齐.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.2-20261119-multi-type` |
| 对应方案 | 1.3 Final P1 |
| 目标 | CHECKUP_BOOK / ECG_REPORT / US_REPORT、追问、bbox 渐进、人工工作台、危急 ACK |

---

## Global Constraints

P0 安全不变量不得回退；禁 Map；SQL→xml；写接口幂等；追问不信任前端改数；`Result.code=200`。

---

## 1. SDD

1. 心电不将波形图作主输入。  
2. 体检册必须 `subReportId`/页归属。  
3. 追问基于已解析结果 + Guard/Validator。  
4. 危急复核与普通字段修订分离。  
5. ACK ≠ 医院危急制度完成。

---

## 2. 业务规则

| ID | 规则 |
| --- | --- |
| M-R1 | CHECKUP_BOOK 子报告归属可测；禁止整册强绑同一 reportTime |
| M-R2 | ECG 路径不依赖波形文件 |
| M-R3 | 追问改数 → 4107 拦截 |
| M-R4 | ACK 仅患者端已读语义 |
| M-R5 | 人工修订 → 新 parseVersion + 重跑规则 |
| M-R6 | 危急复核与普通修订不同接口/权限 |

---

## 3. 接口与强类型

| 方法 | 路径 |
| --- | --- |
| `POST` | `/api/report/reports/{reportId}/questions` |
| `POST` | `/api/report/reports/{reportId}/critical-events/{eventId}/ack` |
| `POST` | `/internal/report/corrections` |
| `POST` | `/internal/report/reviews/{id}/resolve` |

**`AskQuestionReq`：** `question`、`focusCanonicalCodes: List<String>`  
**`AskQuestionRes`：** `answerId`、`blocks: List<InterpretationBlockRes>`、`evidence: List<EvidenceRes>`、`pinnedVersions: PinnedVersions`  
**`AckCriticalReq`：** `acknowledgedAt: Instant?`  
**`AckCriticalRes`：** `eventId`、`notificationStatus`  
**`CorrectionReq`：** `reportId`、`parseVersionBase`、`List<CorrectionPatch> patches`、`reviewerId`  
**`CorrectionPatch`：** `indicatorId`、`normalizedValue`、`reason`（明确字段）  
**`CorrectionRes`：** `newParseVersion`、`reRuleRequired`

详情增量：`List<SectionDto>` / `FindingDto` / `AssessmentDto` / `SubReportDto` — 非 Map。

---

## 4. Mapper.xml

```xml
<!-- ReportQuestionMapper.xml -->
<insert id="insert">
  INSERT INTO report_question
  (answer_id, report_id, question, focus_codes_json, created_at)
  VALUES (#{answerId}, #{reportId}, #{question}, #{focusCodesJson}, CURRENT_TIMESTAMP(3))
</insert>

<!-- ReportCorrectionMapper.xml -->
<insert id="insert">
  INSERT INTO report_correction
  (id, report_id, parse_version_base, new_parse_version, reviewer_id, patches_json, created_at)
  VALUES
  (#{id}, #{reportId}, #{parseVersionBase}, #{newParseVersion}, #{reviewerId}, #{patchesJson},
   CURRENT_TIMESTAMP(3))
</insert>

<!-- Critical ACK -->
<update id="ack">
  UPDATE report_critical_notification_outbox
  SET notification_status = 'ACKNOWLEDGED',
      acknowledged_at = #{acknowledgedAt},
      updated_at = CURRENT_TIMESTAMP(3)
  WHERE event_id = #{eventId}
</update>
```

---

## 5. 验收 / 6. TDD

AC-M1～M10 + **AC-M11** 无 Map。  
测试：CheckupSubReport、EcgNoWaveform、UsFindings、QuestionAuth、QuestionGuard、CriticalAck、CorrectionVersion、P0RegressionIT。

---

## 7. 完成定义

- [ ] SDD；TDD+AC；`release-V0.2-*` → `master` → `V0.2.0`  
