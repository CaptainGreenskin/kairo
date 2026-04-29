状态: DONE
模块: kairo-event-stream
标题: DefaultSubscription + EventStreamAuthorizationException 单元测试

目标:
先读取两个类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- DefaultSubscription: 创建、取消、isActive 状态，事件推送
- EventStreamAuthorizationException: 消息、构造函数

新增文件:
- kairo-event-stream/src/test/java/io/kairo/event/DefaultSubscriptionTest.java
- kairo-event-stream/src/test/java/io/kairo/event/EventStreamAuthorizationExceptionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
