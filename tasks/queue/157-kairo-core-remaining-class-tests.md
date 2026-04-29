状态: DONE
模块: kairo-core
标题: kairo-core 剩余未测试类补充

目标:
检查 kairo-core 中仍缺少测试的重要类，补充测试。
优先：AnthropicRequestBuilder、PostCompactRecoveryHook、ReActLoopContext、RecoveryResult

测试场景（按实际 API 确定）:
- AnthropicRequestBuilder: 请求构建，message/system prompt
- PostCompactRecoveryHook: 压缩后恢复逻辑
- RecoveryResult: 结果类型和字段
- ReActLoopContext: 循环上下文状态

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/AnthropicRequestBuilderTest.java
- kairo-core/src/test/java/io/kairo/core/resilience/RecoveryResultTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
