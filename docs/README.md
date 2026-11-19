# report-interpreter 文档索引

## 最终架构

| 文档 | 说明 |
| --- | --- |
| [AI 报告解读企业级完整方案](superpowers/specs/2026-11-18-AI报告解读企业级完整方案-design.md) | **最终文档 · 1.3 Final** |

## 工程规范（对齐挂号 + 菜品推荐 + 平台）

| 文档 | 说明 |
| --- | --- |
| [企业规范对齐](工程/企业规范对齐.md) | **全仓强制清单**；冲突取舍顺序 |
| [分支与版本](工程/分支与版本.md) | 与挂号/菜品同一套分支名 |
| [基础设施](工程/基础设施.md) | 用到再引入；infrastructure 封装 |
| [应用结构](工程/应用结构.md) | api/service、包分层、Result |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | 贡献入口 |

## 对齐副本

见 [对齐/README.md](对齐/README.md)。

## 分支实施规格

命名：`feature-V主.次-年月日[-短英文名]`（禁止 `feat/`）。文件名 = 分支全名。

| 分支全名 | 分期 | 文档 |
| --- | --- | --- |
| `feature-V0.1-20261119-foundation` | P0 底座 | [plans/…](superpowers/plans/feature-V0.1-20261119-foundation.md) |
| `feature-V0.1-20261119-parse` | P0-A | [plans/…](superpowers/plans/feature-V0.1-20261119-parse.md) |
| `feature-V0.1-20261119-rules-critical` | P0-B | [plans/…](superpowers/plans/feature-V0.1-20261119-rules-critical.md) |
| `feature-V0.1-20261119-rag-guard` | P0-C | [plans/…](superpowers/plans/feature-V0.1-20261119-rag-guard.md) |
| `feature-V0.1-20261119-delivery-gate` | P0-D | [plans/…](superpowers/plans/feature-V0.1-20261119-delivery-gate.md) |
| `feature-V0.2-20261119-multi-type` | P1 | [plans/…](superpowers/plans/feature-V0.2-20261119-multi-type.md) |
| `feature-V0.3-20261119-trend-archive` | P2 | [plans/…](superpowers/plans/feature-V0.3-20261119-trend-archive.md) |

每份含：Global Constraints、业务规则、强类型接口、Mapper.xml SQL、AC、SDD、TDD。  
合入顺序：foundation → parse → rules-critical → rag-guard → delivery-gate。

## 过程材料

| 文档 | 说明 |
| --- | --- |
| [方案审核意见](方案审核意见) | 审核原稿（已取舍并入 Final） |
