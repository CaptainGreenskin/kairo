状态: DONE
创建时间: 2026-04-26
优先级: P0（启用夜间自动化的前提）

## 目标

为 kairo-code CLI 添加 `--task-file <path>` 选项，从 Markdown 文件读取任务描述，
而不是通过命令行字符串传入。这是夜间自动化循环的核心基础。

## 上下文

- 相关模块：kairo-code-cli
- 相关文件：
  - `kairo-code-cli/src/main/java/io/kairo/code/cli/KairoCodeMain.java`
  - `kairo-code-cli/src/main/java/io/kairo/code/cli/ReplLoop.java`
- 现状：`--task "string"` 只能接受单行字符串，复杂任务无法表达

## 需要实现

1. 在 `KairoCodeMain` 添加 `--task-file <path>` PicoCLI 选项
2. 读取文件内容作为任务描述（UTF-8，支持多行 Markdown）
3. `--task` 和 `--task-file` 互斥，同时指定时报错退出
4. 文件不存在时给出明确错误信息

## 验收标准

- [ ] `--task-file tasks/queue/001-xxx.md` 能正常执行任务
- [ ] 单元测试：文件读取成功、文件不存在报错、与 --task 互斥检查
- [ ] `mvn test -pl kairo-code-cli` 通过
- [ ] 不破坏现有 `--task` 和 REPL 模式

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES

---
## 完成记录
- 时间：2026-04-26
- 分支：feature/task-001-task-file-input
- 改动：KairoCodeMain.java 新增 --task-file 选项，新增 KairoCodeMainTaskFileTest.java（4个测试）
- 测试：68/68 通过
