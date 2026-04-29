状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：安全事件日志测试）

## 目标

为 `LoggingSecurityEventSink` 添加单元测试，验证安全事件
被正确路由到日志级别。

## 背景

`LoggingSecurityEventSink` 将 GUARDRAIL_DENY 和 MCP_BLOCK 事件
以 WARN 级别记录，其他事件以 INFO 级别记录。

## 需要实现

先读取 `LoggingSecurityEventSink.java`、`SecurityEvent`、`SecurityEventType`
理解接口，然后编写：

### 测试：LoggingSecurityEventSinkTest.java

使用 logback `ListAppender` 捕获日志输出，验证：
- GUARDRAIL_DENY 事件以 WARN 级别记录，包含 agent/target/policy/reason
- MCP_BLOCK 事件以 WARN 级别记录
- GUARDRAIL_ALLOW 或其他事件以 INFO 级别记录
- record() 调用不抛出任何异常

注意：如无 ListAppender，可以只验证 record() 不抛异常即可（4 个简单测试）。

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
