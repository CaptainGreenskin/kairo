状态: DONE
模块: kairo-event-stream
标题: EventStreamAuthorizationException + DefaultSubscription 单元测试

目标:
为 kairo-event-stream 中两个未测试的类补充基本测试。

测试场景（按实际 API 确定）:
- EventStreamAuthorizationException: 继承 RuntimeException，message 可读
- DefaultSubscription: 构造不抛异常，id()/isActive()/cancel() 行为

新增文件:
- kairo-event-stream/src/test/java/io/kairo/eventstream/EventStreamAuthorizationExceptionTest.java
- kairo-event-stream/src/test/java/io/kairo/eventstream/internal/DefaultSubscriptionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认类结构
