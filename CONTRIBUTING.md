# 参与贡献

感谢你关注 `report-interpreter`。当前以架构定稿与分支实施规格为先，业务代码按 V0.1 任务分支落地。

## 开始之前

先看：

- [README.md](README.md)
- 产品/架构基线：[docs/superpowers/specs/2026-11-18-AI报告解读企业级完整方案-design.md](docs/superpowers/specs/2026-11-18-AI报告解读企业级完整方案-design.md)（**1.3 Final**）
- 企业规范对齐：[docs/工程/企业规范对齐.md](docs/工程/企业规范对齐.md)
- 分支规划：[docs/工程/分支与版本.md](docs/工程/分支与版本.md)

已经有人在做的事，请先到对应 Issue 留言。较大改动先开 Issue 再提交代码；小的文档修正可直接提 PR。

账号、JWT、就诊人归属属于同产品体系的 `registration-assistant`。病情主数据属于 `clinical-record`。本仓只做报告解析、规则危急、白话解读与投递；**不要**自建登录或病情主表。

统一返回、通用错误码、异常、traceId、账号头鉴权、内部令牌使用独立仓 `moonligh-platform` 的 `platform-*`，不要在本仓库再实现一套。网关限流与 JWT 验签复用挂号入口上的 `platform-gateway`。业务仓之间不要互相依赖 jar；与挂号 / 营养 / 临床的协作走 HTTP。

## 开发约定

对齐挂号《基础设施》与菜品推荐 Global Constraints：

- 运行时：JDK 21、Spring Boot 4.1.1、MySQL 8.0.46、Redis 8.2.10、RocketMQ 5.5.1；版本以 `platform-bom` 为准。
- 包名：`com.moonligh.report`。分层：`controller` / `service` / `domain` / `infrastructure.*`。
- 用到哪个工具，才把依赖写进构建文件，并在 `infrastructure` 封装；业务代码只依赖本仓库端口。
- 接口与领域对象使用明确的 `*Req` / `*Res` / `*Command` / `*Result` / `*Row`，**禁止** `Map<String,Object>` 传递业务数据；手工映射，无 BeanUtils / MapStruct / Lombok。
- SQL 只写在 `src/main/resources/mapper/*.xml`；不用 MyBatis-Plus。
- 写接口必须支持 `Idempotency-Key`；敏感健康数据不得写入日志、Issue 或提交说明。
- 业务成功 `Result.code=200`；错误码 41xx 须先合入平台 `ErrorCode`。
- 示例只使用合成数据或可公开引用文本。
- 仓库归属写 moonligh，主页是 <https://moonligh.cn/>。

## 提交说明

本仓与菜品推荐一致，使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```text
feat: 增加报告上传幂等建单
fix: 修复身份 CONFLICT 仍写入档案 Outbox
docs: 更新分支实施规格
test: 补充危急覆盖假阴性用例
refactor: 拆分 RuleContext 解析
chore: 调整忽略规则
```

标题用现在时，说清这次提交做了什么。若向 `registration-assistant` 提交改动，按其 CONTRIBUTING 使用中文类型（`功能:` / `修复:` …）。

## 分支与 Pull Request

与挂号、菜品推荐同一套命名：

- 版本开发：`feature-V主.次-YYYYMMDD`（日期用实际创建当天）
- 并行任务：`feature-V主.次-YYYYMMDD-短英文名`（合回版本开发分支）
- 发布冻结：`release-V主.次-YYYYMMDD`
- 线上修复：`hotfix-V主.次.修订-YYYYMMDD`

禁止 `feat/`、`feature/V0.1`、小写 `v`。当前 V0.1 计划分支见 [docs/工程/分支与版本.md](docs/工程/分支与版本.md)。

每个任务分支（foundation → … → delivery-gate）代码写完后，必须完成本分支独立验收，以及与此前已合入分支的累计验收；两项都通过才可进入下一分支。

Pull Request 请写明：

- 解决了哪个问题
- 行为有什么变化
- 怎样验证
- 有没有新增示例数据
- 是否依赖 `registration-assistant` / `clinical-record` / 平台错误码变更

维护者可能直接修改提交说明，或请你补测试。还不能运行的阶段，文档和目录改动可以在说明里写「当前仓库尚无可执行测试」。

本地可先执行：

```bash
bash .github/scripts/check-branch-name.sh feature-V0.1-20261119-foundation
```

## 版本

版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。项目版本从 `0.1.0-SNAPSHOT` 开始。对外可见变化写入 [CHANGELOG.md](CHANGELOG.md) 的 `Unreleased` 一节，发布时再归入具体版本。

## 许可

提交到本仓库的贡献，按 [Apache-2.0](LICENSE) 授权给本项目。除非贡献者在提交中明确标明不属于贡献，否则适用该许可证第 5 条。
