状态: DONE
模块: kairo-core
标题: CostAwareRoutingPolicy 完整边界测试

目标:
扩展 CostAwareRoutingPolicy 测试，覆盖预算超出、tier 降级链、兜底逻辑等场景。

背景:
CostAwareRoutingPolicy 是多模型路由核心，负责根据 token 预算选模型。
需要验证所有路由决策分支确保生产可靠性。

## 需要实现

检查现有 CostAwareRoutingPolicyTest，补充或创建：
`kairo-core/src/test/java/io/kairo/core/routing/CostAwareRoutingPolicyExtTest.java`

测试场景（共 10+ 个）：
- 预算充足时选首选模型
- 预算不足时降级到更便宜 tier
- 所有 tier 超预算时使用最后兜底
- tokens=0 时不触发降级
- ModelTierRegistry 为空时 fallback
- 单 tier 系统正常路由
- PREMIUM → STANDARD → ECONOMY 降级链完整验证
- 自定义预算阈值
- 并发路由请求（无 race condition）
- 相同 token 量不同 tier 选择正确

约束:
- 不修改 kairo-api/
- 不新增外部依赖
