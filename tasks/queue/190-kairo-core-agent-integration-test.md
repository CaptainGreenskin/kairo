状态: DONE
模块: kairo-core
标题: DefaultReActAgent 端到端集成测试

目标:
补充 DefaultReActAgent 的集成测试，验证从工具调用到结果返回的完整 ReAct 循环，
无需真实模型调用（使用 stub ModelProvider）。

测试场景:
- 单轮无工具调用（直接返回文本响应）
- 单轮工具调用 → 工具结果 → 最终响应
- 多轮循环（工具结果触发下一轮调用）
- LoopDetector 在重复调用时中止
- Hook 链在正确的生命周期点触发
- Session 在循环结束时正确关闭

新增文件:
- kairo-core/src/test/java/io/kairo/core/agent/DefaultReActAgentIT.java

约束:
- 不修改 kairo-api/
- 使用 stub ModelProvider（不调用真实 API）
- 测试类命名 *IT.java（集成测试）
