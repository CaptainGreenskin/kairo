状态: DONE
模块: kairo-core
标题: ReasoningPhase 工具调用解析和 Guardrail 集成测试补充

目标:
先读取 ReasoningPhase.java 和 ReasoningPhaseGuardrailTest.java 完整内容，
检查现有测试覆盖，补充工具调用解析、流式响应处理等缺失场景。

背景:
ReasoningPhase 是 ReAct 循环的核心推理步骤，处理模型返回（文本/工具调用/流式）。
ReasoningPhaseGuardrailTest.java 可能只覆盖了 guardrail 路径，
纯文本响应、流式工具调用、空响应等场景可能缺失。

需要补充的测试场景（根据读取源码确认）:
- 模型返回纯文本消息时 phase 产生 TEXT 类型输出
- 模型返回工具调用时 phase 产生 TOOL_CALL 列表
- 模型返回空内容时 phase 正确处理（不崩溃）
- 流式响应累积成完整工具调用
- guardrail BLOCK 决策中止推理（已有，确认）

修改文件:
- kairo-core/src/test/java/io/kairo/core/agent/ReasoningPhaseGuardrailTest.java（追加场景）
  或新建 ReasoningPhaseTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
