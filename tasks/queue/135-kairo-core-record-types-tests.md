状态: DONE
模块: kairo-core
标题: CacheCheckResult + DetectedToolCall 单元测试

目标:
先读取两个类的源码，补充基本测试。

测试场景（按实际 API 确定）:
- 构造/工厂方法不抛异常
- 属性可读（accessor methods）
- equals/hashCode（如为 record）
- 边界值

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/CacheCheckResultTest.java
- kairo-core/src/test/java/io/kairo/core/model/DetectedToolCallTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
