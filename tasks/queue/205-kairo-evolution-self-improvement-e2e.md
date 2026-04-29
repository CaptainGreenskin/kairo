状态: DONE
模块: kairo-evolution
标题: Self-Evolution 自改进循环端到端测试（M5 基础）

目标:
为 kairo-evolution 模块添加完整的端到端测试，验证自改进循环：
检测 → 提议 → 生成 → 评估 → 应用 的完整流程。

背景:
M5 目标是「kairo-code 能修改自身代码并通过测试」。
kairo-evolution 的 EvolutionPipelineOrchestrator 已实现，
但缺乏验证完整循环的端到端测试。现有测试只覆盖单个组件。

## 需要实现

### 测试文件
`kairo-evolution/src/test/java/io/kairo/evolution/EvolutionCycleE2ETest.java`

使用 StubAgent（已有）+ Mockito 验证完整循环：

**场景 1: 正常自改进循环**
- EvolutionTrigger 检测到需要改进（stub 返回 true）
- Planner 生成改进计划
- Generator 生成代码变更
- Evaluator 评估通过
- Orchestrator 记录 EvolutionSignal
- hookChain 触发 EvolutionHook

**场景 2: 评估失败时循环中止**
- Evaluator 返回 REJECT
- Orchestrator 不记录成功 signal
- EvolutionState 保持 IDLE

**场景 3: Generator 失败时有 fallback**
- Generator 抛出异常
- Orchestrator 捕获异常，记录 FAILED signal
- 不影响后续循环

**场景 4: 累计改进次数跟踪**
- 多次循环后 InMemoryEvolutionRuntimeStateStore 的计数正确累积

共 12+ 测试

约束:
- 不修改 kairo-api/
- 只用已有的 StubAgent、RecordingEventBus 等测试工具类
