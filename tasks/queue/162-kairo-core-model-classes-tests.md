状态: DONE
模块: kairo-core
标题: Model 工具类单元测试（DetectedToolCall、ProviderRetry、BoundaryMarkerManager）

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- DetectedToolCall: 字段存储、工厂方法
- ProviderRetry: 重试逻辑、退避计算、最大重试次数
- BoundaryMarkerManager: 插入/查找边界标记消息

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/DetectedToolCallTest.java
- kairo-core/src/test/java/io/kairo/core/model/ProviderRetryTest.java
- kairo-core/src/test/java/io/kairo/core/context/BoundaryMarkerManagerTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
