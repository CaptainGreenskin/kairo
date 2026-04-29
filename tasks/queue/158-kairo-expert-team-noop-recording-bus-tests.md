状态: DONE
模块: kairo-expert-team
标题: NoopMessageBus + RecordingEventBus TCK 辅助类测试

目标:
先读取两个 TCK 辅助类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- NoopMessageBus: send() 不抛异常，接收为空
- RecordingEventBus: 记录发送的事件，events() 返回发出的列表

新增文件:
- kairo-expert-team/src/test/java/io/kairo/expertteam/tck/NoopMessageBusTest.java
- kairo-expert-team/src/test/java/io/kairo/expertteam/tck/RecordingEventBusTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
