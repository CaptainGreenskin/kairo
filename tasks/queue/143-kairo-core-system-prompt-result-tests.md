状态: DONE
模块: kairo-core
标题: SystemPromptResult + LoopDetectionException 单元测试

目标:
先读取两个类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- SystemPromptResult: 构造/工厂，content/metadata 属性，equals/hashCode
- LoopDetectionException: 继承体系，message 保留，detectedAt 属性

新增文件:
- kairo-core/src/test/java/io/kairo/core/prompt/SystemPromptResultTest.java
- kairo-core/src/test/java/io/kairo/core/resilience/LoopDetectionExceptionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
