状态: DONE
模块: kairo-mcp
标题: ElicitationResponse + ElicitationAction + handler 单元测试

目标:
为 kairo-mcp 模块补充以下类的单元测试：
- ElicitationAction (enum: ACCEPT/DECLINE/CANCEL)
- ElicitationResponse (factory methods: accept/decline/cancel, fields, equals/hashCode)
- AutoDeclineElicitationHandler (返回 Mono.just(ElicitationResponse.decline()))
- DevOnlyAutoApproveHandler (返回 Mono.just(ElicitationResponse.accept(Map.of())))

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/elicitation/ElicitationActionTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/elicitation/ElicitationResponseTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/elicitation/AutoDeclineElicitationHandlerTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/elicitation/DevOnlyAutoApproveHandlerTest.java

约束:
- 不修改 kairo-api/
- 用 StepVerifier 验证 Reactor Mono
- ElicitationAction 测试覆盖所有枚举值和 name()/ordinal()
