# feature-V0.1-20261119-foundation

> **For agentic workers:** 先完成本文件 SDD，再 TDD。合入 `feature-V0.1-20261119`。全仓规范见 `docs/工程/企业规范对齐.md`。

| 项 | 内容 |
| --- | --- |
| 分支全名 | `feature-V0.1-20261119-foundation` |
| 从哪里拉 | `feature-V0.1-20261119`（若无则从 `master` 先建） |
| 合回哪里 | `feature-V0.1-20261119` |
| 对应方案 | 1.3 Final §3；企业架构规范 |
| 目标 | 可编译启动、平台对齐、错误码与鉴权可用；不实现业务解读 |

**Architecture:** `report-interpreter-api` + `report-interpreter-service`；`platform-bom`；无业务解析/规则引擎。

**Tech Stack:** JDK 21、Spring Boot 4.1.1（BOM）、MyBatis XML、`platform-common` / `security` / `observability`

---

## Global Constraints（对齐挂号 + 菜品）

- 只依赖 `platform-*`；禁止兄弟业务仓 jar。  
- 禁止 `Map<String,Object>` 契约；`*Req`/`*Res`/`*Row` record；手工映射；无 Lombok/MapStruct/BeanUtils。  
- SQL 只在 `mapper/*.xml`；`@MapperScan(annotationClass=Mapper.class)`。  
- `Result` 成功 `code=200`；业务 HTTP 常 200；鉴权失败不得假装业务成功。  
- 写接口 `Idempotency-Key`（本分支占位也要校验存在）。  
- 配置 `report.*`；鉴权 `consult.auth`。  
- 浏览器 `/api/report/**`；内部 `/internal/report/**` + `X-Internal-Token`。  
- 41xx 须先合入平台 `ErrorCode` 再引用。

---

## 1. SDD

1. 双模块骨架可 `mvn -B verify`。  
2. 路径与 Result 形状固定；业务可返回未实现，但类型固定。  
3. 本分支不暴露真实解析/危急/解读。

**产出物：** 本文件；OpenAPI/路径登记；`docs/对齐` 已有企业架构副本。

---

## 2. 业务规则

| ID | 规则 |
| --- | --- |
| F-R1 | 无可信身份 → 拒绝写接口，不写库 |
| F-R2 | 无 `Idempotency-Key` → 校验失败 |
| F-R3 | 内部无令牌 → 拒绝 |
| F-R4 | 依赖树无 nutritionist / registration-assistant / clinical-record jar |
| F-R5 | 响应必须 `Result<CreateReportRes>`，禁止 `Result<Map>` |
| F-R6 | 成功码为 **200**，不是 0 |

---

## 3. 接口与强类型

| 方法 | 路径 |
| --- | --- |
| `GET` | `/actuator/health` |
| `POST` | `/api/report/reports` |
| `GET` | `/api/report/reports/{reportId}` |
| `POST` | `/internal/report/kb/reseed` |

**头：** 身份头；`Idempotency-Key`（写）。

**`CreateReportReq`：** `patientId: String`；`file: MultipartFile`；`reportType: String?`  
**`CreateReportRes`：** `reportId: String`；`status: String`  
**`Result<CreateReportRes>`：** `code/message/data/traceId`

---

## 4. Mapper.xml

**`ReportJobMapper`** → `mapper/ReportJobMapper.xml`

```xml
<insert id="insert">
  INSERT INTO report_job
  (report_id, account_id, patient_id, idempotency_key, status, epoch, created_at, updated_at)
  VALUES
  (#{reportId}, #{accountId}, #{patientId}, #{idempotencyKey}, #{status}, #{epoch},
   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
</insert>

<select id="findById" resultType="com.moonligh.report.infrastructure.persistence.ReportJobRow">
  SELECT report_id AS reportId, account_id AS accountId, patient_id AS patientId,
         idempotency_key AS idempotencyKey, status, epoch,
         created_at AS createdAt, updated_at AS updatedAt
  FROM report_job WHERE report_id = #{reportId}
</select>

<select id="findByIdempotency" resultType="com.moonligh.report.infrastructure.persistence.ReportJobRow">
  SELECT report_id AS reportId, account_id AS accountId, patient_id AS patientId,
         idempotency_key AS idempotencyKey, status, epoch,
         created_at AS createdAt, updated_at AS updatedAt
  FROM report_job
  WHERE account_id = #{accountId} AND idempotency_key = #{idempotencyKey}
</select>
```

禁止 `resultType="java.util.HashMap"`。

---

## 5. 验收标准

| ID | 标准 |
| --- | --- |
| AC-F1 | `mvn -B verify` 通过 |
| AC-F2 | 应用可启动；`report.*` 无致命错误 |
| AC-F3 | 无身份写接口被拒 |
| AC-F4 | 内部无令牌被拒 |
| AC-F5 | 平台可引用 4101～4110 |
| AC-F6 | 无兄弟业务仓 jar |
| AC-F7 | `/api/report/**` 已登记 |
| AC-F8 | 无 Map 契约；SQL 仅 xml |
| AC-F9 | 成功示例 `code=200` |

---

## 6. TDD

| 测试 | 断言 |
| --- | --- |
| `AuthGuardTest` | 无身份拒绝 |
| `InternalTokenTest` | 无令牌拒绝 |
| `ResultContractTest` | 强类型 + code=200 形状 |
| `ReportJobMapperXmlTest` | insert/find |
| `BomDependencyTest` | 禁兄弟仓包名 |

---

## 7. 完成定义

- [x] SDD 冻结；TDD 绿（`mvn -B verify` 13 tests）；AC-F1～F9 本分支代码侧达标  
- [x] 合入版本开发分支 `feature-V0.1-20261119`  
- [x] 平台仓 41xx 已本地合入分支 `feature-V1.0-20261119-report-error-codes`（远端 PR 可另开）  
