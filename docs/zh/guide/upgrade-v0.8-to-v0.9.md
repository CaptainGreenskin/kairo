# 升级指南：v0.8 → v0.9

本指南覆盖 Kairo v0.9.0 和 v0.9.1 引入的变更，以及从 v0.8.x 升级时需要关注的事项。

v0.9 的主题是**平台能力补齐**——Channel SPI、Event Stream 双传输、OTel 事件导出、自进化管线接入，以及清理 v0.5 时代遗留的 deprecated API。

---

## 破坏式变更

### D5：deprecated API 物理删除

以下 API 已被物理删除，不再可用：

- `io.kairo.api.task.*` 包（整个包移除）
- `TeamScheduler` 接口

**谁会受影响？** 任何直接引用 `io.kairo.api.task` 包或 `TeamScheduler` 的项目。

**迁移步骤：**

```java
// 旧：io.kairo.api.task.TaskExecutor
// 新：使用 io.kairo.api.agent.Agent 的执行能力
agent.call(userMessage).block();

// 旧：TeamScheduler
// 新：使用 TeamCoordinator SPI
@Autowired TeamCoordinator coordinator;
coordinator.coordinate(task).subscribe();
```

如果你在 v0.7/v0.8 已经迁移到新 API，此变更对你没有影响。

---

## 新功能

### Channel SPI（@Experimental）

新增 `io.kairo.api.gateway.Channel` 接口，统一 IM / webhook 适配层。

- **核心接口**：`Channel`——实现后注册为 Spring bean
- **配套模块**：`kairo-channel`（base）+ `kairo-spring-boot-starter-channel`
- **TCK**：`ChannelTCK` 验证实现合规性
- **设计文档**：ADR-021

### Event Stream（SSE + WebSocket 双传输）

新增事件流模块，支持两种传输协议：

| 模块 | 传输 | 说明 |
|---|---|---|
| `kairo-event-stream` | base | 传输无关的事件总线基础 |
| `kairo-event-stream-sse` | SSE | WebFlux SSE transport |
| `kairo-event-stream-ws` | WebSocket | WebFlux WebSocket transport |

- **Authorizer SPI**——事件流连接的鉴权扩展点
- **设计文档**：ADR-018

### KairoEvent OTel Exporter（@Experimental）

`KairoEventOTelExporter` 将 `KairoEvent` 投射为 OpenTelemetry LogRecord，支持按域采样。

- **配套模块**：`kairo-observability` + `kairo-spring-boot-starter-observability`
- **设计文档**：ADR-022

### Self-Evolution Wiring Proof

默认 Spring Agent 行为级别证明了 evolution wiring 的消费路径。事件域隔离（`ExecutionEventType` vs `EvolutionEventType`）有回归守卫。

### DingTalk Channel Adapter（v0.9.1）

基于 Channel SPI 的钉钉适配器，首个 Channel 实现：

- **模块**：`kairo-channel-dingtalk` + `kairo-spring-boot-starter-channel-dingtalk`
- **组件**：verifier + mapper + outbound client + channel + webhook controller
- **验证**：TCK + 3 个钉钉场景 + 4 个 auto-config 测试

---

## 新增依赖

```xml
<!-- Channel SPI -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-channel</artifactId>
</dependency>

<!-- Event Stream（SSE） -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-event-stream-sse</artifactId>
</dependency>

<!-- Event Stream（WebSocket） -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-event-stream-ws</artifactId>
</dependency>

<!-- OTel 事件导出 -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-observability</artifactId>
</dependency>

<!-- 钉钉 Channel（v0.9.1） -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-channel-dingtalk</artifactId>
</dependency>
```

所有版本由 `kairo-bom` 管理，无需声明版本号。

---

## 已弃用

v0.9 无新增 deprecated API。此版本聚焦于清理旧 deprecated 和补齐平台能力。

---

## ADR

- **ADR-018**：Event Stream 设计
- **ADR-021**：Channel SPI 设计
- **ADR-022**：KairoEvent OTel Exporter
