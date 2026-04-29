状态: DONE
模块: kairo-core
标题: CacheCheckResult + ToolPhase 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- CacheCheckResult: 字段存储、命中/未命中判断
- ToolPhase: 枚举值完整性

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/CacheCheckResultTest.java
- kairo-core/src/test/java/io/kairo/core/agent/ToolPhaseTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
