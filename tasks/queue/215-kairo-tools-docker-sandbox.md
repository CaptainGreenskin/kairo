状态: DONE
模块: kairo-tools
标题: D4 DockerSandbox — 容器隔离执行沙盒

目标:
实现 DockerSandbox（ExecutionSandbox 接口），通过 ProcessBuilder 调用 docker CLI 运行命令，
不引入 Docker Java SDK 外部依赖。与 ExecutionSandboxTCK 兼容。

## 需要实现

`io.kairo.tools.exec.DockerSandbox`
- 实现 ExecutionSandbox
- 构造：image(String), cpuLimit(String默认"0.5"), memoryLimit(String默认"256m"), networkMode(String默认"none")
- start(SandboxRequest):
  1. 挂载 workspaceRoot 为 /workspace（readOnly→:ro）
  2. 构建 docker run 命令：--rm --cpus cpuLimit -m memoryLimit --network networkMode
  3. 用 ProcessBuilder 执行，输出通过 OutputStream 流式返回
  4. 超时通过 SandboxRequest.timeout() 控制（process.waitFor(timeout)）
  5. 返回 LocalSandboxHandle 包装结果（复用 LocalProcessSandbox 的 Handle 模式）

`DockerSandboxConfig`
- record: image, cpuLimit, memoryLimit, networkMode

### 约束
- 不修改 kairo-api/
- 不引入 Docker Java SDK 外部依赖，纯 ProcessBuilder
- 与 ExecutionSandboxTCK 行为契约一致（timeout/maxOutput/readOnly）
- 如果 docker 命令不可用（未安装），start() 抛 UnsupportedOperationException
