# 简介

**Kairo**（源自希腊语 *Kairos* — 行动的决定性时刻）是一个 Java Agent 操作系统，为 AI Agent 提供完整的运行时环境。Kairo 不是一个简单的 LLM 封装库，而是将 Agent 运行时的每个组件映射到操作系统概念。

Kairo 不是封装层 — 它是基础设施。正如 Netty 之于网络、Jackson 之于序列化，Kairo 之于 AI Agent。

## OS 概念映射

| OS 概念 | Kairo 映射 | 说明 |
|---------|-----------|------|
| 内存管理 | Context | 上下文窗口 = 有限内存，需要智能压缩 |
| 系统调用 | Tool | 21+ 专用工具，Agent 与外部世界的接口 |
| 进程 | Agent | ReAct 循环驱动的独立执行单元 |
| 文件系统 | Memory | 持久化知识存储（文件 / 内存） |
| 信号处理 | Hook | 10 个钩子点，支持 CONTINUE/MODIFY/SKIP/ABORT/INJECT 决策 |
| 可执行文件 | Skill | Markdown 格式的即插即用能力包 |
| 作业调度 | Task + Team | 多 Agent 任务编排与团队协作 |
| IPC | A2A 协议 | Agent-to-Agent 通信，跨 Agent 调用 |
| 中间件 | 中间件管道 | 声明式请求/响应拦截 |
| 检查点 | 快照 | Agent 状态序列化与恢复 |

## 为什么选择 Kairo

基于 Project Reactor 构建，完全响应式、非阻塞执行，开箱即用支持 Claude、GLM、Qwen、GPT 等模型。框架与模型无关 — 切换提供者无需修改 Agent 逻辑。
