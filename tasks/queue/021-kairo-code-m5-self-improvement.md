状态: DONE
创建时间: 2026-04-26
优先级: P1（M5 前置：kairo-code 自我修改验证）

## 目标

验证 kairo-code 当前工具集是否足以执行"修改 kairo-code 自身代码并通过测试"的任务。
如果不足，补齐缺失能力。

## 背景

M5 里程碑要求 kairo-code 能修改自身代码并通过测试。当前工具集：
- ReadTool, WriteTool, EditTool, GlobTool, GrepTool（文件操作）
- BashTool（运行 mvn test, git 命令）
- TaskTool（子任务）

理论上已经具备，需要验证：
1. 系统提示词是否包含 kairo-code 项目结构信息
2. BashTool 是否能运行 mvn test -pl kairo-code-cli
3. EditTool 能否编辑 kairo-code-cli 中的 Java 文件

## 需要实现

### 1. 读取 CodeAgentFactory 中的系统提示词

检查 `kairo-code-cli/src/main/resources/system-prompt.md`：
- 是否包含 kairo-code 项目结构说明
- 是否包含"如何修改 kairo-code"的指导
- 是否包含 mvn test 命令

### 2. 补充系统提示词（如果不足）

在 system-prompt.md 中添加：
```markdown
## kairo-code 自我修改指南

当被要求修改 kairo-code 自身时：
1. 用 GlobTool 定位要修改的文件
2. 用 ReadTool 读取当前内容
3. 用 EditTool 进行精确修改
4. 用 BashTool 运行 mvn test -pl kairo-code-cli 验证
5. 用 BashTool 运行 git add + git commit 提交
```

### 3. 添加一个 M5 能力测试任务

创建 `kairo-code-cli/src/test/java/.../SelfModificationCapabilityTest.java`
验证所有必要工具都已注册。

## 验收标准

- [ ] system-prompt.md 包含自我修改指南
- [ ] 所有必要工具（Read/Edit/Bash/Glob/Grep）都已注册
- [ ] 能力测试通过

## Agent 可以自主完成

YES
