状态: DONE
优先级: P2
模块: kairo-tools
标题: MvnTool 单元测试

目标:
为 MvnTool 编写单元测试。MvnTool 构建 mvn 命令并执行，支持 goals、
module（-pl）、skipTests、timeout 等参数，解析 BUILD SUCCESS/FAILURE。

## 需要实现

`io.kairo.tools.exec.MvnToolTest`（10+ 测试用例）

场景（使用 mock/stub ProcessBuilder 或 LocalProcessSandbox 注入）：
- goals 参数构建正确命令（e.g. `mvn compile`）
- module 参数添加 `-pl <module>`
- skipTests=true 时添加 `-DskipTests`
- profiles 参数添加 `-P <profile>`
- mvn 命令不存在（PATH 中无 mvn）→ isError=true
- 构建成功（退出码 0 + 含 BUILD SUCCESS）→ isError=false
- 构建失败（退出码 1 + 含 BUILD FAILURE）→ isError=true
- timeout 超时时进程被强制终止 → isError=true
- goals 参数缺失时 → isError=true
- 构建输出中提取 Tests run / Failures / Errors 摘要到 metadata
- workingDirectory 解析到 workspace root

### 约束
- 使用 @TempDir 创建临时 workspace
- 允许使用 Mockito mock ExecutionSandbox（kairo-tools 已有 mockito 依赖）
- 不修改 kairo-api/
