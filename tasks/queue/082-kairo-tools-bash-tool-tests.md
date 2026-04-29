状态: DONE
模块: kairo-tools
标题: BashTool 单元测试补充

目标:
为 BashTool 补充更多边界场景测试（如已有测试文件则追加）：
- 超时场景（timeout 参数）
- stderr 输出捕获
- 非零退出码处理
- 工作目录参数
- 空命令处理

约束:
- 不修改 kairo-api/
- 先检查是否已有 BashToolTest.java，有则追加测试，无则新建
- 不依赖外部工具，用 echo/sleep 等系统命令
