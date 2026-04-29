# Kairo — Java Agent OS Framework

## 项目定位

Kairo 是 Java AI Agent 运行时基础设施框架，目标是成为 Java 生态的 Agent 操作系统。
kairo-code 是 Kairo 的 dogfooding 应用，用于证明框架的真实价值。

**核心原则**：不新增抽象，用现有 SPI 覆盖需求。设计不合理直接大改，不做兼容迁移。

---

## 模块导航

```
kairo-api/          ← SPI 接口层（改动需人工审批）
kairo-core/         ← 核心实现（可自主修改）
kairo-capabilities/
  kairo-tools/      ← 17+ 内置工具
  kairo-mcp/        ← MCP 协议集成
  kairo-multi-agent/← A2A 协议 + 团队协调
  kairo-skill/      ← Markdown 技能系统
  kairo-evolution/  ← 自进化管道
  kairo-expert-team/← plan/generate/evaluate 协调
  kairo-observability/← OpenTelemetry
  kairo-security-pii/ ← PII 脱敏 + 审计
kairo-transports/
  kairo-channel/    ← Channel SPI + TCK
  kairo-channel-dingtalk/ ← 钉钉集成
  kairo-event-stream/     ← 事件总线
kairo-starters/     ← 9 个 Spring Boot 自动配置
kairo-examples/     ← 示例代码
docs/               ← VitePress 文档
.plans/             ← 设计文档和路线图
```

### 包结构

```
io.kairo.api.*      ← SPI 接口（kairo-api 模块）
io.kairo.core.*     ← 核心实现（kairo-core 模块）
io.kairo.tools.*    ← 工具实现
io.kairo.skill.*    ← 技能系统
io.kairo.evolution.*← 进化机制
```

---

## 核心 SPI 速查

| SPI | 路径 | 职责 |
|-----|------|------|
| `Agent` | `io.kairo.api.agent.Agent` | Agent 抽象，核心执行接口 |
| `AgentFactory` | `io.kairo.api.agent.AgentFactory` | 创建 Agent 实例 |
| `ModelProvider` | `io.kairo.api.model.ModelProvider` | 模型推理，支持流式 |
| `ToolExecutor` | `io.kairo.api.tool.ToolExecutor` | 工具执行框架 |
| `ContextManager` | `io.kairo.api.context.ContextManager` | 上下文生命周期 |
| `MemoryStore` | `io.kairo.api.memory.MemoryStore` | 持久知识存储 |
| `Middleware` | `io.kairo.api.middleware.Middleware` | 请求/响应拦截 |
| `Channel` | `io.kairo.api.channel.Channel` | 消息频道集成 |
| `TeamCoordinator` | `io.kairo.api.team.TeamCoordinator` | 多智能体编排 |
| `WorkspaceProvider` | `io.kairo.api.workspace.WorkspaceProvider` | 工作空间管理 |

### Hook 生命周期（10个点）

PreReasoning → PostReasoning → PreActing → PostActing →
PreToolExecute → PostToolExecute → PreModelCall → PostModelCall →
OnLoopDetected → OnError

Hook 决策值：CONTINUE / MODIFY / SKIP / ABORT / INJECT

### 上下文压缩阶段

Snip → Micro → Collapse → Auto → Partial → CircuitBreaker

---

## 关键实现类

| 类 | 路径 | 说明 |
|----|------|------|
| `DefaultReActAgent` | `kairo-core/.../agent/` | ReAct 循环主实现 |
| `ContextCompactionEngine` | `kairo-core/.../context/` | 6 阶段压缩引擎 |
| `DefaultToolExecutor` | `kairo-core/.../tool/` | 工具执行框架 |
| `MiddlewarePipeline` | `kairo-core/.../middleware/` | 中间件链 |
| `CircuitBreaker` | `kairo-core/.../resilience/` | 模型+工具熔断器 |
| `LoopDetector` | `kairo-core/.../resilience/` | 哈希+频率双检循环检测 |
| `AnthropicModelProvider` | `kairo-core/.../model/` | Claude 模型实现 |

---

## 构建和测试命令

```bash
# 编译全部模块
mvn compile

# 运行全部单元测试
mvn test

# 运行单个模块测试
mvn test -pl kairo-core

# 运行能力模块测试
mvn test -pl kairo-capabilities -am

# 运行集成测试
mvn verify -Pintegration-tests

# 格式检查（提交前必须通过）
mvn spotless:check

# 自动修复格式
mvn spotless:apply

# 打包（跳过测试）
mvn clean package -DskipTests
```

**测试命名约定**：
- `*Test.java` — 单元测试（Surefire）
- `*IT.java` — 集成测试（Failsafe）
- `*TCK.java` — SPI 兼容性测试

---

## 代码规范

- **Java 版本**：JDK 17+，使用 `-release 17`
- **格式**：Google Java Format (AOSP 风格)，`mvn spotless:apply` 自动修复
- **响应式**：全面使用 Project Reactor（`Mono<T>` / `Flux<T>`），禁止阻塞调用
- **注解**：
  - `@Stable(since = "x.x.x")` — 公开 API，承诺兼容
  - `@Experimental` — 试验性，可能变化
  - `@Internal` — 内部使用，不保证兼容
- **注释**：只在 WHY 不明显时写，不写解释 WHAT 的注释

---

## 安全边界（Agent 必须遵守）

### 可以自主修改
- `kairo-core/` — 核心实现
- `kairo-capabilities/` — 能力模块实现
- `kairo-transports/` — 传输层实现
- `kairo-starters/` — Spring Boot 自动配置
- `kairo-examples/` — 示例代码
- 测试文件

### 需要人工审批才能修改
- `kairo-api/` — SPI 接口定义（任何接口变更都需要 review）
- `kairo-bom/` — 依赖版本管理
- `pom.xml` 根文件
- `.plans/` 设计文档

### 禁止操作
- 直接 push 到 main/master 分支
- 删除任何 `@Stable` 接口方法
- 引入阻塞调用到响应式链中

---

## 开发工作流

1. 从 `main` 切 feature 分支：`git checkout -b feature/issue-{号}`
2. 实现代码，遵循现有包结构
3. 运行 `mvn spotless:apply` 修复格式
4. 运行 `mvn test -pl <module>` 验证测试通过
5. Commit：`git commit -m "feat(module): 简短描述"`
6. 创建 PR，关联 issue

### Commit 消息格式

```
feat(kairo-core): 添加 LoopDetector 频率检查
fix(kairo-tools): 修复 BashTool 超时处理
test(kairo-evolution): 补充 EvolutionHook 边界测试
docs(kairo-api): 更新 MemoryStore SPI 注释
```

---

## 遇到歧义时

- **设计决策不明确**：在对应 issue 下留评论说明卡点，跳过该任务
- **测试失败无法修复**：不强行让测试通过，在 PR 描述中说明原因
- **需要修改 kairo-api**：创建新 issue 标记 `needs-human-review`，不擅自修改

---

## 依赖版本参考

```
Spring Boot: 3.3.0
Project Reactor: 3.7.3
JUnit 5: 5.11.4
Jackson: 2.18.2
MCP SDK: 1.1.1
OpenTelemetry: 1.44.1
Mockito: 5.11.0
```

所有版本由 `kairo-bom` 统一管理，不在子模块中重复声明版本。
