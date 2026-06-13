# 升级指南：v1.0 → v1.1

本指南覆盖 Kairo v1.1.0 和 v1.1.1 引入的变更。v1.1 的主题是**云 / 多租户 / Code-Agent 的 SPI 地基**——四个新 SPI 包全部以 `@Stable(since = "1.1.0")` 发布，japicmp 门控已对 `1.0.0` 基线验证通过。

> **TL;DR——既有用户无需改代码。** 所有新 SPI 都是纯新增，零既有方法签名变更。五个被动消费点（KairoEventBus、OTel Exporter、SecurityEventSink、ToolContext、BridgeMeta）自动投射租户信息，无需调用方适配。

---

## 破坏式变更

### ConsoleApprovalHandler 重写（v1.1.1）

`kairo-core` 的 `ConsoleApprovalHandler` 整体替换为取消安全的实现。

**谁会受影响？** 自定义子类或依赖旧构造器签名的代码。

**变更内容：**

- 旧构造器 `ConsoleApprovalHandler(Set<String> alwaysAllowed, Duration timeout)` 已移除
- 新构造器接受 `BufferedReader` + `PrintWriter`（可测试），另有无参构造器默认使用 `System.in` / `System.out`
- 新增 `ApprovalDecision` 枚举值：`ALWAYS_ALLOW` / `ALWAYS_DENY`
- 新增 `getApprovalState()` / `restoreApprovals()` 用于会话恢复

**迁移步骤：**

```java
// 旧
new ConsoleApprovalHandler(Set.of("bash"), Duration.ofSeconds(30));

// 新：无参构造器（使用 System.in/out）
new ConsoleApprovalHandler();

// 新：自定义 I/O（测试场景）
new ConsoleApprovalHandler(reader, writer);
```

这是孵化阶段的 hard break——经过验证，reactor 内无调用方依赖旧签名。

---

## 新功能

### ExecutionSandbox SPI（@Stable）

安全执行沙箱，隔离外部命令执行：

- **核心类型**：`ExecutionSandbox` / `SandboxRequest`（Builder 模式，30s 超时 / 1MiB 输出默认） / `SandboxHandle`（AutoCloseable） / `SandboxOutputChunk`（sealed：`Stdout` / `Stderr`） / `SandboxExit`
- **默认实现**：`LocalProcessSandbox`（在 `kairo-tools`），保持 v1.0 `BashTool` 行为不变
- **TCK**：`ExecutionSandboxTCK`——8 个场景验证合规性
- **设计文档**：ADR-025

### WorkspaceProvider SPI（@Stable）

工作空间管理，支持相对路径解析和遍历防护：

- **核心类型**：`Workspace` / `WorkspaceProvider`（acquire / release） / `WorkspaceKind`（`LOCAL` 已实装；`REMOTE_GIT` / `EPHEMERAL` 预留 v1.3） / `WorkspaceRequest`
- **默认实现**：`LocalDirectoryWorkspaceProvider`
- **路径解析**：Read / Write / Edit / Glob / Grep 五个文件工具自动使用 `currentWorkspace().root()` 解析相对路径；`startsWith(root)` + `normalize()` 防 `..` 遍历
- **零行为变更**：`Workspace.cwd()` 静态工厂确保单工作空间用户无感知
- **设计文档**：ADR-026

### TenantContext SPI（@Stable）

多租户上下文传播，不含配额/隔离（推到 v1.2）：

- **核心类型**：`TenantContext`（record，`SINGLE` 哨兵值） / `TenantContextHolder`（`Scope` AutoCloseable + `NOOP` opt-out）
- **默认实现**：`ThreadLocalTenantContextHolder` + `ReactorTenantContextPropagator`（跨异步边界）
- **5 个被动消费点**：`KairoEventBus`、`KairoEventOTelExporter`、`SecurityEventSink`、`ToolContext.tenant()`、`BridgeMeta.tenant`——自动投射 tenant id，无方法签名变更
- **设计文档**：ADR-027

### Bridge Protocol SPI（@Stable）

编辑器 ↔ Agent 的 JSON-RPC 桥接协议：

- **核心类型**：`BridgeRequest` / `BridgeResponse` / `BridgeMeta` / `BridgeRequestHandler` / `BridgeServer`
- **默认传输**：`WebSocketBridgeServer` + `KairoBridgeWebSocketHandler`（挂载 `/ws/bridge`，独立于事件流）
- **5 个操作**：`agent.run` / `agent.cancel` / `agent.status` / `tool.approve` / `workspace.list`
- **payload 类型**：`Map<String, Object>`（非 `JsonNode`——保持 `kairo-api` Jackson-free）
- **设计文档**：ADR-028

---

## 新增依赖

v1.1 的四个 SPI 包全部在 `kairo-api` 中，无需额外依赖。默认实现分布在 `kairo-core` 和 `kairo-tools` 中，已被现有 starter 传递引入。

如果你需要 Bridge 的 WebSocket 传输：

```xml
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-event-stream-ws</artifactId>
</dependency>
```

---

## 已弃用

v1.1 无新增 deprecated API。

---

## ADR

- **ADR-025**：ExecutionSandbox SPI
- **ADR-026**：WorkspaceProvider SPI
- **ADR-027**：TenantContext SPI
- **ADR-028**：Bridge Protocol SPI
