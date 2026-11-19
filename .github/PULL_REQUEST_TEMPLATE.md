## 变更说明

<!-- 这个改动解决什么问题，行为有什么变化 -->

## 怎样验证

<!-- 跑了哪些检查；如果仓库还不能运行，请直接说明 -->

## 分支

<!-- 头分支须符合 feature-V0.1-20261119-foundation 这类名称 -->

## 检查项

- [ ] 头分支符合 `feature-V主.次-年月日`、带短英文名的并行任务分支、`release-V主.次-年月日` 或 `hotfix-V主.次.修订-年月日`
- [ ] 遵守 `docs/工程/企业规范对齐.md`（禁 Map、SQL 在 mapper.xml、只依赖 platform-*）
- [ ] 变更和 README / 相关规格一致
- [ ] 归属名称使用 moonligh，包名位于 `com.moonligh.report`
- [ ] 未加入密钥、令牌、真实病历、真实合同或客户材料
- [ ] 对外可见的变化已写入 CHANGELOG.md 的 Unreleased，或说明这次不需要写
- [ ] 若改平台错误码 / 挂号网关路由 / clinical-record，已在说明中标出跨仓依赖
