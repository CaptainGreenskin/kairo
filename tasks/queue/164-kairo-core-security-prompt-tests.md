状态: DONE
模块: kairo-core
标题: LoggingSecurityEventSink + SystemPromptResult 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- LoggingSecurityEventSink: onEvent() 不抛异常、处理 null 事件
- SystemPromptResult: 字段存储、segments 列表、toString

新增文件:
- kairo-core/src/test/java/io/kairo/core/guardrail/LoggingSecurityEventSinkTest.java
- kairo-core/src/test/java/io/kairo/core/prompt/SystemPromptResultTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
