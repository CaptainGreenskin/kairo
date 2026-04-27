# 任务模板

复制此文件到 `tasks/queue/`，命名 `<编号>-<slug>.md`（编号决定执行顺序）。

---

```markdown
---
状态: TODO
创建时间: 2026-04-27
优先级: P2          # P1 阻塞型 / P2 默认 / P3 锦上添花
模块: kairo-core    # kairo-core / kairo-tools / kairo-code-cli / ...
预计耗时: 30m
depends_on: []      # ["003", "034"]
里程碑: M3
---

# <任务标题>

## 目标

[一句话说清楚要做什么]

## 上下文

- 相关文件：`src/main/java/io/kairo/.../XxxClass.java`
- 背景：[为什么要做这件事]

## 验收标准

- [ ] 编译通过 `mvn compile -pl <模块>`
- [ ] 单元测试通过 `mvn test -pl <模块>`
- [ ] [具体功能验收条件]

## 备注

- agent 可自主重写本任务（如果范围 / 验收不明确）
- agent 可自主修改 kairo-api/ SPI（japicmp 报 BREAKING 时 commit message 加 `BREAKING CHANGE:`）
- agent 自主推 PR + auto-merge

---

<!-- 任务执行过程中 Agent 追加 -->
开始时间: <ISO>
完成时间: <ISO>
耗时: <分钟>
PR: <pr-url>
commit: <hash>

## 完成记录
<涉及文件清单>

<!-- 失败时 Agent 追加（必填） -->
## 卡点
- 错误摘要：
- 已尝试方案：
- 推测原因：

<!-- 任务被重写时 Agent 追加 -->
## 任务被自主重写
- 原版本范围：
- 新版本范围：
- 为什么改：
```
