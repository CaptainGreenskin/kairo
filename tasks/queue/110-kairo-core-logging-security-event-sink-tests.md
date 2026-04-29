状态: DONE
模块: kairo-core
标题: LoggingSecurityEventSink 单元测试

目标:
为 kairo-core/.../guardrail/LoggingSecurityEventSink.java 补充测试。

测试场景:
- 实现 SecurityEventSink 接口
- record() 方法对 GUARDRAIL_DENY 事件不抛异常
- record() 方法对 MCP_BLOCK 事件不抛异常
- record() 方法对 GUARDRAIL_ALLOW 事件不抛异常
- record() 方法对 GUARDRAIL_WARN 事件不抛异常
- 各类型事件均可正常处理（不依赖 log 验证，仅验证无异常）

新增文件:
- kairo-core/src/test/java/io/kairo/core/guardrail/LoggingSecurityEventSinkTest.java

约束:
- 不修改 kairo-api/
- 先读取 SecurityEvent record 确认构造参数
