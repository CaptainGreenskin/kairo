状态: IN_PROGRESS
创建时间: 2026-04-26
优先级: P2（M6：测试补全）

## 目标

为 `SkillToolManager` 添加单元测试，覆盖 MCP 初始化跳过逻辑、技能限制清理、
MCP registry 关闭等行为。

## 背景

`SkillToolManager` 是 package-private 类，位于 `io.kairo.core.agent`，
负责懒初始化 MCP servers、工具注册、skill_load 限制管理。
目前无专属测试文件。

## 需要实现

### 测试：SkillToolManagerTest.java

位置：`kairo-core/src/test/java/io/kairo/core/agent/SkillToolManagerTest.java`
（与被测类同包，可访问 package-private 成员）

验证：
- 无 MCP server 配置时 `initMcpIfConfigured()` 返回 `Mono<Void>` 且不抛异常
- 已初始化（mcpInitialized=true）时重复调用是幂等的
- `clearSkillRestrictions()` 调用 `toolExecutor.clearAllowedTools()`
- `closeMcpRegistry()` 在 mcpRegistryPlugin 为 null 时不抛异常
- mcpCapability.serverConfigs() 为空列表时 init 返回 Mono.empty()

使用 Mockito mock `ToolExecutor` 和 `AgentConfig`，用 `StepVerifier` 验证响应式流。

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
