# 菜品推荐检索企业级闭环设计

状态：**一次性交付基线（不分期；按本文一次改完）**  
版本：1.1  
日期：2026-11-17  

继承与边界：

| 文档 | 关系 |
| --- | --- |
| `docs/方案` §3–5、§11 | 产品规则与降级总则 |
| `2026-11-16-菜谱向量化与检索-design.md` | 写入/集合/字段；**读路径以本文为准补强** |
| `2026-11-17-对话规划与增量推荐-design.md` | 对话任务与增量执行 |
| `2026-11-17-长短期记忆企业级闭环-design-V2.md` / `V2.1` | 记忆权威；本文只定义**记忆如何进入检索** |

原则：字段过滤不靠相似度；主料/克数/临床硬约束由 Java 锁定；模型不打分、不放宽；不上 Graph / Spring AI 接管检索。

---

## 0. 一句话结论

**结构化 `RecallContext`（话术点名 + 档案病情 + 长期偏好投影）→ 标量过滤必须进 Milvus/等价 query → 仅模糊词才 embed/ANN → 与已发布目录交集（禁止 fail-open）→ 主料门槛 ≤3 → 对话层硬过滤/软排/换菜。**  
全程带**空因枚举与指标**；有点名或病情时检索失败/空结果**绝不退全库**。

---

## 1. 目标与非目标

### 1.1 目标

1. 检索正确性与《方案》§5 一致：点名/病情精确过滤；模糊词才向量。  
2. 长期记忆中的菜系/偏好标签**结构化**进入过滤，禁止只靠拼进 `textHints` 再抠词。  
3. 降级可证明、可审计：每种空结果有稳定 `emptyReason`。  
4. 与记忆 V2.1 对齐：推荐只消费**已提交**偏好投影；客户端偏好不得当 ABSOLUTE。  
5. 单测门禁 + 真库冒烟清单可验收。

### 1.2 非目标

- 全量对账运维台、下架删向量后台。  
- 改主料分公式、求解器、临床规则引擎。  
- MQ 异步检索、多路召回融合算法升级（RRF 保持现状）。  
- 用向量「猜」菜系/病情。

---

## 2. 权威分层

| 数据 | 权威源 | 检索中的角色 |
| --- | --- | --- |
| 已发布菜谱目录 | MySQL（CatalogBrowse） | 主料/做法/营养的唯一可见集 |
| 标量过滤字段 + 向量 | Milvus `nutrition_recipe` | 缩小候选 id；不决定能不能做 |
| 关键词正文 | MySQL `recipe_search_chunk` | 模糊词 BM25 侧 |
| 病情/已确认过敏 | 临床档案（长期 A） | conditions → suitable/unsuitable；过敏在对话硬过滤 |
| 饮食偏好 | MySQL 偏好事实链（V2.1） | 菜系/标签进 `RecallContext`；AVOID 强度分流见 §5 |
| 会话过程态 | Redis Session | 缺料、已荐菜、hints；不替代长期偏好 |
| 菜卡缓存 | Redis `dish:{id}` | 仅缓存已过主料复核的展示卡；**判断能不能做不读它** |

JVM 内目录/L1 仅为加速；与记忆一致：**可丢，必须可从权威重建**。

---

## 3. 端到端流水线

```text
1 食材 → foodId（归一；未识别单列）
2 组装 RecallContext（§4）
3 生成 filter = RecipeVectorFilter.expr(...)
4 分支：
   A 无 filter 且无 fuzzy → 候选 = 全部已发布
   B 有 filter 且无 fuzzy → Milvus.query(filter)；空/失败 → [] + 空因
   C 有 fuzzy →（可选先 filter）embed + search / chunk 混合 ≤30；
        有 filter 且结果空/失败 → []；
        无 filter 且向量全失败 → 退全量已发布（唯一允许的退全库）
5 HybridRecall.intersect(published, ids)：
   ids 空 → []；
   有 ids 无交集 → []（禁止退全库）
6 IngredientDishRanker：主料齐全 + 规则分 → ≤3
7 对话层（Executor）：
   7a 硬过滤：临床过敏 + ABSOLUTE 忌口 + missing
   7b 软排：非 ABSOLUTE AVOID 降权；LIKE 加分
   7c 新 ABSOLUTE 命中已荐 → 换菜状态机
8 可选求解克数；写回 Session；返回
```

**顺序不变式**：硬约束与主料门槛不可被软偏好或向量分数逆转。

---

## 4. RecallContext（结构化检索上下文）

### 4.1 字段

```text
RecallContext
  cuisine: String?          // 至多一个点名菜系
  kind: String?             // 至多一个种类
  preferTags: List<String>  // 减肥/减脂/快手/家常等
  conditions: List<String>  // 控糖/控盐/低嘌呤等（档案映射后）
  fuzzyText: String?        // 「清淡一点」等对不上字段的残留
  sources: EnumSet          // UTTERANCE | MEMORY_PREFER | CLINICAL | CLIENT
```

### 4.2 组装规则（优先级）

| 来源 | 规则 |
| --- | --- |
| 本轮话术 | `RecipeIntent.parse(text)` → 点名/fuzzy |
| 长期记忆 preferCuisines | **写入 cuisine**（若话术未点名）；多菜系时取现行列表**第一条**注入 `cuisine`，其余忽略进 filter（LIKE 菜仍可 softRank 无此字段） |
| 长期记忆 preferTags | **并入 preferTags**（去重） |
| 长期记忆 preferLikes（FOOD） | **不进 Milvus filter**；仅对话 softRank |
| 长期记忆 avoid（非 ABSOLUTE） | **不进 filter**；softRank 降权 |
| 长期记忆 avoid ABSOLUTE | **不进 Milvus filter**；对话硬过滤（与过敏同级消费点） |
| 临床 conditions | 并入 conditions（映射：糖尿病→控糖 等，沿用 LongTermMemoryLoader） |
| 客户端 avoidFoods | 已降为 ≤MODERATE；**不得**当 ABSOLUTE；不进 suitable 过滤 |

禁止：把记忆列表 `String.join` 进 text 再依赖抠词作为唯一路径。话术解析与记忆投影**合并到 Context**，再生成 filter。

### 4.3 named / fuzzy 判定

- `named` = cuisine/kind/preferTags 任一非空（含记忆注入的标签/菜系）。  
- `hasClinical` = conditions 非空。  
- `hasFilter` = named \|\| hasClinical。  
- `fuzzy` = fuzzyText 非空。  
- **仅 fuzzy==true 时**调用 embed / ANN。

---

## 5. 过滤与降级矩阵（P0）

| 场景 | Milvus/向量 | 候选结果 | emptyReason（示例） |
| --- | --- | --- | --- |
| 无 filter 无 fuzzy | 不调用 | 全量已发布 | — |
| 有 filter 无 fuzzy | query(filter) | 空→[] | `SCALAR_EMPTY` / `VECTOR_STORE_UNAVAILABLE` |
| 有 filter + fuzzy | search(vec, filter) | 空→[] | `ANN_EMPTY` / `EMBED_FAILED` |
| 无 filter + fuzzy | search 或 chunk 混合 | 全失败→全量已发布 | 降级记 `FALLBACK_ALL_PUBLISHED` |
| ids 与目录无交集 | — | [] | `NO_PUBLISHED_INTERSECT` |
| 主料不齐 | — | 过滤掉 | 最终无菜 → `NO_FEASIBLE_MAINS` |

**禁止**：

1. 有 filter 时因异常或空召回退回全库。  
2. `pool(dishes, emptyIds)` 解释为「全库」（见 §6）。  
3. 向量分数把 unsuitable / 缺主料菜加回。

---

## 6. HybridRecall.pool 语义修正

现状问题：`recalledIds` 为空时返回全部 `dishes`，与「有过滤时空结果」冲突，存在 fail-open。

**新契约**：

```text
intersect(published, recalledIds):
  if published empty → []
  if recalledIds null or empty → []     // 调用方若要「全库」不得走本方法
  return published.filter(id in recalledIds)
  // 交集空 → []
```

「无 filter 且无召回」的全库路径：在 `DishRecommendService.narrow` **显式** `return published`，禁止经 `pool` 偷渡。

---

## 7. 对话层与检索层分工

| 关注点 | 检索层（DishRecommendService） | 对话层（DialogueExecutor） |
| --- | --- | --- |
| 菜系/种类/偏好标签/病情 | filter | 不重复标量过滤 |
| ABSOLUTE 忌口 / 过敏 / missing | 不负责 | 硬剔除 |
| 弱 AVOID / LIKE | 不负责 | softRank |
| 换菜 / NO_FEASIBLE | 提供候选 | 状态机 |
| 目录快照 | match 内一次 snapshot | softDelta 复用同回合 catalog 缓存（已有 RunState） |

Orchestrator 组装 `RecallContext`（或 Executor 从 `SessionMemory.longTerm` + text 组装），调用：

`matchIds(foodIds, unrecognized, origin, RecallContext)`。

废弃「仅靠 searchHints 字符串承载长期菜系」作为主路径；hints 可保留模糊话术原文。

---

## 8. 可观测性（企业级必选）

### 8.1 空因枚举 `RecallEmptyReason`

`NONE | SCALAR_EMPTY | ANN_EMPTY | EMBED_FAILED | VECTOR_STORE_UNAVAILABLE | NO_PUBLISHED_INTERSECT | NO_FEASIBLE_MAINS | FILTER_FAIL_CLOSED`

写入：

- `MatchReport` 增加 `emptyReason` + `filterExpr`（可截断）+ `recalledCount`。  
- 对话 `explanation` 对用户仍用业务话；**日志/指标用枚举**。

### 8.2 指标（进程内即可，可后接 Micrometer）

- `recall.filter_empty` / `recall.ann_empty` / `recall.fallback_all` / `recall.success`  
- `recall.embed_ms` / `recall.milvus_ms`（可选）  
- 按是否 `hasFilter`、是否 `fuzzy` 切片  

### 8.3 审计

不落聊天原文全文；可记 `eventId`/sessionId/filter 哈希。偏好侧仍走记忆三表。

---

## 9. 多实例与缓存

1. **目录**：允许请求内缓存；跨请求以 DB 为准。后续可加 `catalogVersion`（非本期必做）。  
2. **Milvus / 百炼**：无状态服务端；失败按 §5。  
3. **Session**：Redis 权威（V2.1）；换菜依赖的 foodIds/缺料从 Session 读。  
4. **菜卡 Redis**：仅展示；主料判定始终用 Catalog + owned ids。

---

## 10. API / 响应（最小增量）

`DialogueReply` 或内部 `MatchReport`：

- `emptyReason`（可选暴露给管理端；小程序可继续只看 explanation）  
- 保持 `dishes` ≤3、`fallback` 文案与现网兼容  

治理侧不新增检索 API。

---

## 11. 测试与验收

### 11.1 单测门禁（合并即测）

| ID | 用例 | 期望 |
| --- | --- | --- |
| T1 | 记忆 preferCuisine=川菜，话术无菜系 | filter 含 `cuisine == "川菜"`；query 被调用 |
| T2 | 有 filter，Milvus 返回空 | dishes 空；reason=`SCALAR_EMPTY`；不退全库 |
| T3 | 有 filter，Milvus 抛错 | dishes 空；reason=`VECTOR_STORE_UNAVAILABLE` |
| T4 | 无 filter，仅 fuzzy，embed 失败 | 退全量再主料；记 `FALLBACK_ALL_PUBLISHED` |
| T5 | recalledIds 与目录无交 | []；`NO_PUBLISHED_INTERSECT` |
| T6 | ABSOLUTE 忌口不进 filter，对话硬剔 | 检索候选可含该菜，Executor 后剔除或换菜 |
| T7 | 客户端 avoid 仅为 MODERATE | 不进绝对硬过滤 |
| T8 | `pool(empty)` | [] 而非全库 |

### 11.2 真库冒烟（沿用向量设计 §5，升为企业门禁）

1. 连 Milvus + 百炼。  
2. 上传含川菜/非川菜、suitable 控糖样例。  
3. 「川菜」→ 仅川菜 id。  
4. 病情控糖 → suitable 命中且非 unsuitable。  
5. 「清淡一点」→ 走 ANN，仍过主料。  
6. 有 filter 断 Milvus → 空，不退全库。

### 11.3 属性不变式

- 任意 RecallContext：有 filter ⇒ 结果 ⊆ query/search 命中 ⊆ 已发布。  
- 返回菜主料 ⊆ owned ∪ 常备。  
- softRank 不改变硬过滤集合，只重排。

---

## 12. 一次性交付范围（不分期）

同一变更集必须同时完成，不以 R0/R1 拆 PR：

1. 修正 `HybridRecall.pool`：空 ids → `[]`；全库仅 `narrow` 显式分支。  
2. 新增 `RecallContext` + `RecallEmptyReason`；`DishRecommendService.matchIds(..., RecallContext)`。  
3. `DialogueOrchestrator` / `DialogueExecutor`：从 `LongTermMemory` 组装 Context；去掉「长期菜系/标签只拼 searchHints」主路径。  
4. `MatchReport` 带 `emptyReason` / `filterExpr` / `recalledCount`；进程内召回指标。  
5. 单测 T1–T8 全部通过。  
6. `emptyReason` **不**写入小程序展示字段（仅日志与 MatchReport；API 可不序列化到 C 端）。

真库冒烟 §11.2：有环境则同批执行并记录；无密钥/断网时不阻塞合并，但不得用「以后分期」代替单测门禁。

不上 MQ；不改写入侧集合 schema。

### 12.1 已锁定产品决策

1. 多条菜系记忆：只取**第一条**注入 `cuisine`。  
2. LIKE 食材：只 softRank，不进 Milvus filter。  
3. 不分期，一次交齐 §12。  
4. `emptyReason` 不对小程序展示。

---

## 13. 现状差距摘要（相对本文）

| 项 | 现状 | 本文要求 |
| --- | --- | --- |
| 记忆菜系/标签 | 拼入 textHints | RecallContext 字段 |
| pool(空) | 退全库 | 返回 [] |
| 空因 | 无 | 枚举 + 指标 |
| 有 filter 失败 | 大体空列表 | 强制 + 可观测 |
| 客户端偏好 | 已降 MODERATE（记忆侧） | 检索侧不提升为硬过滤 |

---

## 14. 文档关系

- 本文为**推荐检索读路径**的企业级基线。  
- 与 `2026-11-16-菜谱向量化与检索-design.md` 冲突时：**读路径降级/pool/记忆贯通以本文为准**；写入/集合字段仍以 11-16 为准。  
- 记忆权威以 V2 / V2.1 为准；本文不重新定义 PreferenceStore。
