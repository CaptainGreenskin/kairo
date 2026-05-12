状态: DONE
优先级: P1
模块: kairo-tools
标题: BashTool 安全场景测试

目标:
为 BashTool 补充专注安全防护的测试类。现有 BashToolTest 覆盖基础功能，
但缺少注入防护、沙箱边界、超时强制等安全相关场景。

## 需要实现

`io.kairo.tools.exec.BashToolSecurityTest`（10+ 测试用例）

场景：
- shell 注入尝试（`; rm -rf /`、反引号、`$(cmd)`）不被执行额外命令
- 命令包含换行符 `\n` 时被正确处理或拒绝
- timeoutSeconds=1 时慢命令被强制终止，isError=true
- 超时后进程不留存（进程 handle 已关闭）
- LocalProcessSandbox 与 DockerSandbox 切换时工具行为一致（mock DockerSandbox）
- 空 command 参数返回 isError=true
- 仅空白字符的 command 参数返回 isError=true
- 超长输出（>100KB）被截断并在元数据中注明
- workingDirectory 为不存在路径时返回 isError=true
- 命令退出码正确映射到 metadata.exitCode

### 约束
- 使用 JDK 内置能力，不引入新依赖
- 不 mock BashTool 内部，只 mock ExecutionSandbox SPI
- 不修改 kairo-api/
