状态: TODO
模块: kairo-core
标题: CostAwareRoutingPolicy 完整边界测试

目标:
为 CostAwareRoutingPolicy 添加完整的边界测试，覆盖模型降级、预算超出、tier 回退等场景。

背景:
CostAwareRoutingPolicy 是多模型路由的核心组件，负责根据成本约束选择模型。
需要验证所有路由决策分支。

## 需要实现

检查并扩展
`kairo-core/src/test/java/io/kairo/core/routing/CostAwareRoutingPolicyTest.java`

或创建：
`kairo-core/src/test/java/io/kairo/core/routing/CostAwareRoutingPolicyExtTest.java`

测试场景（共 10+ 个）：
- 预算充足时选择首选模型
- 预算不足时降级到更便宜的 tier
- 所有 tier 均超预算时使用最后兜底
- tokens 估算为 0 时不触发降级
- modelTierRegistry 为空时 fallback
- 单 tier 系统正常路由
- PREMIUM → STANDARD → ECONOMY 降级链
- 自定义预算阈值
- 并发路由请求

约束:
- 不修改 kairo-api/
- 不新增外部依赖
