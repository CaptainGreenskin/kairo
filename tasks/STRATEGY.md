# Kairo 自动化开发战略

> 此文件是 Agent 决策的唯一依据。人工修改需重新审核。
> 最后审核：待用户确认

---

## 终极目标

**kairo-code 成为 Kairo 框架的主要开发者。**
人类角色：架构师 + PR 审核者。Agent 角色：全天候工程师。

---

## 里程碑

| 里程碑 | 时间 | 目标 | 完成标志 |
|--------|------|------|----------|
| M2 | 2026-05 | 自动化基础设施 | --task-file + 执行日志 + 7*24 循环稳定 |
| M3 | 2026-06 | kairo-code 工程质量 | 测试 100+，错误恢复，任务超时 |
| M4 | 2026-07 | kairo-code 能力完善 | 多模型支持，并行任务，进度上报 |
| M5 | 2026-08 | kairo-code 自我开发 | kairo-code 能修改自身代码并通过测试 |
| M6 | 2026-09 | kairo-code 开发 Kairo | 第一个由 Agent 提交的 Kairo framework PR |
| M7 | 2026-10 | Self-Evolution 正式 | kairo-code 是 Kairo 主要开发者 |

---

## Agent 的工作范围

### 可以自主执行

- 在 `kairo-core/`、`kairo-capabilities/`、`kairo-transports/`、`kairo-starters/` 实现功能
- 在 `kairo-code/` 所有模块实现功能
- 写测试、修 bug、补文档
- 生成任务（当队列空时从战略推导下一步）
- 直接 commit 到 main（测试通过是唯一门槛）

### 必须标记等待人工

- 修改 `kairo-api/` 的任何 SPI 接口
- 新增外部依赖（非 Kairo 生态）
- 重命名或删除 `@Stable` 方法
- 检测到架构方向冲突

### 绝对禁止

- 测试未通过时提交代码
- 修改 `VERSION-STATUS-SOT.md`
- 修改 `.plans/ROADMAP.md`
- 在同一个 commit 里混合多个不相关的功能

---

## 当前优先级（M2 阶段）

按顺序执行，完成后自动推进到 M3：

1. kairo-code `--task-file` 支持（任务 001）
2. kairo-code 执行日志（任务 002）
3. kairo-evolution 端到端测试（任务 004）
4. kairo-code 早上摘要脚本（任务 005）
5. kairo-core 任务超时（任务 003，需 SPI 审核）
6. 完成 M2 后自动生成 M3 任务列表

---

## 任务生成规则

当 `tasks/queue/` 中没有 TODO 任务时，Agent 按以下规则生成新任务：

1. 读取当前里程碑目标
2. 查看最近 10 个 git commit，了解已完成的工作
3. 分析任务依赖关系，区分串行和并行
4. 生成 5-10 个下一步任务，写入 `tasks/queue/`
5. 任务文件命名：`{下一个序号}-{模块}-{简短描述}.md`
6. **识别可并行任务**：将互相独立的任务写入 `tasks/parallel/`，格式见下方
7. 生成后立即开始执行串行队列中的第一个

## 并行任务计划格式

并行任务写入 `tasks/parallel/{序号}-parallel-plan.md`：

```markdown
# 并行执行计划：{主题}

## 可同时执行的任务（互相独立，无依赖）

### Agent A — {模块A}
目标：...
文件：...
验收：mvn test -pl {moduleA}

### Agent B — {模块B}
目标：...
文件：...
验收：mvn test -pl {moduleB}

### Agent C — {模块C}
目标：...
文件：...
验收：mvn test -pl {moduleC}

## 全部完成后执行
{依赖上述所有任务的后续步骤}
```

---

## PR 提交规范

每个 PR 必须包含：

```
## 做了什么
[1-3 句话]

## 为什么
[对应哪个里程碑目标]

## 测试证明
[测试通过截图或命令输出]

## 人工需要关注
[如有 SPI 改动或设计决策，明确说明]
```

---

## 人工介入点

你只需要做三件事：

1. **合并 PR**：看到 PR 描述，没问题就 merge，有问题就 comment
2. **审核 SPI 改动**：标记了 `needs-human-review` 的 issue，你来决定
3. **调整战略**：每月看一次这个文件，觉得方向不对就修改

**不需要你做的**：定义具体任务、写代码、跑测试、管理分支
