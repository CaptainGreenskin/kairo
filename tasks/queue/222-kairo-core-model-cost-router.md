状态: DONE
模块: kairo-core
标题: ModelCostRouter — 按任务类型路由到不同模型（成本优化）

目标:
实现智能模型路由，让简单任务用便宜模型，复杂任务用高级模型。
降低 kairo-code 运行成本。

## 需要实现

`io.kairo.core.model.ModelCostRouter`
- 实现 ModelProvider（装饰器模式）
- 持有多个 ModelTier（FREE/STANDARD/PREMIUM）对应的 ModelProvider
- 路由规则由 RoutingPolicy 决定

`io.kairo.core.model.RoutingPolicy`
- 接口：ModelTier select(AgentRequest, List<Msg> context)
- 默认实现 TokenBudgetRoutingPolicy：
  - context token 数 < 2000 → FREE（claude-haiku 级别）
  - context token 数 < 8000 → STANDARD（claude-sonnet 级别）
  - 其他 → PREMIUM（claude-opus 级别）

`io.kairo.core.model.ModelTier`
- enum: FREE, STANDARD, PREMIUM

`io.kairo.core.model.ModelRouterConfig`
- record: Map<ModelTier, ModelProvider>

### 约束
- 不修改 kairo-api/
- 路由决策在每次 call() 前评估
- 路由失败时 fallback 到 PREMIUM
