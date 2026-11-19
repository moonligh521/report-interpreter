# 公共 HIS 与临床快照边界

日期：2026-11-13。状态：规格。本轮只定边界，不接医院、不改三仓运行代码。

逻辑以 [GitHub 上的 HIS](2026-11-13-GitHub上的HIS.md) 为准。临床事实只追加在 HIS，不沿用营养库病情主表。下文若与该文件冲突，以该文件为准。

## 1. 三仓归属

```text
医院厂商
    → his（就诊、诊断、过敏、用药）
        → platform-his（ClinicalSnapshot / HospitalSystemPort）
            → nutritionist（配餐门禁与病种映射）
consult-identity 只提供 patientId，不提供诊断
```

| 放哪里 | 放什么 | 不放什么 |
| --- | --- | --- |
| `moonligh-platform` 新模块 `platform-his` | `ClinicalSnapshot`；`HospitalSystemPort`；无医院时的 `NoHospitalSystem`；只调用 HIS 内部快照接口的 HTTP 客户端 | 厂商报文、号源占号、患者表、营养规则、诊断是否算慢病 |
| `registration-assistant` / `consult-identity` | 就诊人主数据；账号是否拥有该就诊人；`patientVersion` | 诊断、过敏、用药、医院适配器、配餐 |
| 独立 `his` 服务 | 门诊就诊、诊断、过敏、用药；空快照；以后的某院适配器；`GET /internal/patients/{patientId}/clinical-snapshot` | 登录、号源、热量、菜谱、`mealPlanMode` |
| `nutritionist` | 读取快照；有医院诊断则走院内慢病路径并映射到已有 condition；无诊断按无慢病；缺身高体重仍只浏览 | 直连医院、复制 HIS 表 |

身份服务现有内部接口只核对就诊人归属和 `patientVersion`（`InternalPersonController`）。临床快照由独立 HIS 服务提供，不替代归属校验。

营养侧已有 `PatientGateway`，继续只校验账号是否拥有该就诊人。病情是否存在不由该端口决定。

## 2. 快照字段

`snapshotVersion` 与 `patientVersion` 分开。就诊人主数据变更不自动等于临床数据变更。

| 字段 | 要求 |
| --- | --- |
| `patientId` | 与身份服务就诊人标识一致 |
| `snapshotVersion` | 临床内容变化才递增 |
| `diagnoses[]` | `codeSystem`、`code`、`display`、`recordedAt`。不根据中文病名猜测编码 |
| `allergies[]` | 可空 |
| `medications[]` | 可空 |
| `source` | `NONE` 或 `HOSPITAL` |
| `asOf` | 快照生成时间 |

快照不含姓名、证件号、手机号。

营养侧把诊断编码映射到已有 condition code（如糖尿病、高血压）的表留在 nutritionist，不放进平台。

## 3. 和健康餐门禁的关系

病情不强制。快照为空，或 `source=NONE` 且营养库也没有手填病情，视为未申报慢病，按无慢病减重。人体测量不够时仍不能出个体化克数。

| 条件 | 渠道 | 配餐 |
| --- | --- | --- |
| `source=HOSPITAL` 且 `diagnoses` 非空 | 院内慢病 | 按已映射病种走现有 A/B/C，不要求用户再填一遍诊断 |
| `source=NONE` 且本地无手填病情 | 散户 | 按无慢病减重 |
| 本地有手填病情、医院快照为空 | 散户已补档案 | 以手填病情为准 |
| 缺性别、出生日期、身高或体重 | 任一渠道 | 只浏览已审核通用菜谱，不因缺病情拒绝 |

无医院连接时，HIS 服务固定返回 `source=NONE`、诊断列表为空。这不是「查询失败」。

## 4. 落地顺序

1. `platform-his`：端口、快照类型、`NoHospitalSystem`。不接厂商。
2. 独立 `his`：就诊、诊断、过敏、用药；无记录时返回空快照。不接号源。
3. `nutritionist`：能力查询读该快照。有医院诊断才标院内慢病；否则按无慢病。不在营养库假装同步诊断。

某院真实对接只增加 HIS 服务里的适配器。平台端口和营养门禁的调用方式不变。功能范围见 [独立 HIS 第一版范围](2026-11-13-独立HIS第一版范围.md)。

## 5. 本轮不做

- 实现 `platform-his` 模块或医院连接器
- 修改挂号号源、医保
- 把管理端「HIS」工作人员登录当成医院系统
- 营养仓直连医院或复制患者主数据
