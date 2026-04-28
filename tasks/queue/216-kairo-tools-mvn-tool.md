状态: DONE
模块: kairo-tools
标题: MvnTool — Maven 构建工具（kairo-code 核心工具）

目标:
实现 MvnTool，让 Agent 可以运行 Maven 命令并获取结构化结果。
这是 kairo-code 修改代码后验证的关键工具。

## 需要实现

`io.kairo.tools.exec.MvnTool`
- 参数：
  - goals（required）: List<String>，如 ["compile"], ["test", "-pl", "kairo-core"]
  - workingDir（optional）：相对 workspace 根的目录，默认 workspace 根
  - profiles（optional）：List<String>，如 ["-Pintegration-tests"]
  - skipTests（optional，默认 false）
  - timeout（optional，默认 300 秒）
- 行为：
  - 通过 ProcessBuilder 运行 mvn，工作目录为 workingDir
  - 流式捕获 stdout+stderr，合并输出
  - 超时强制结束
  - 返回：exitCode, output(最后 50KB), buildSuccess(boolean), failedTests(List<String> 从 BUILD FAILURE 解析)
- metadata: exitCode, buildSuccess, failedTestCount, durationMs

### 约束
- 不修改 kairo-api/
- sideEffect = SYSTEM_CHANGE（构建会修改文件系统）
- workspace 模式下 workingDir 相对 workspace 根
- 最大输出 100KB，超出截断（只保留尾部）
