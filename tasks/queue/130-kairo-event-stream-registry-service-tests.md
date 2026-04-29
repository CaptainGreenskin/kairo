状态: DONE
模块: kairo-event-stream
标题: EventStreamRegistry + DefaultEventStreamService 单元测试

目标:
先读取 EventStreamRegistry 和 DefaultEventStreamService 源码，补充测试。

测试场景（按实际 API 确定）:
- EventStreamRegistry: register/unregister/get/contains 基本操作
- DefaultEventStreamService: subscribe 创建 subscription，cancel 注销

新增文件:
- kairo-event-stream/src/test/java/io/kairo/eventstream/EventStreamRegistryTest.java
- 如 DefaultEventStreamService 可独立测试，追加 DefaultEventStreamServiceTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
