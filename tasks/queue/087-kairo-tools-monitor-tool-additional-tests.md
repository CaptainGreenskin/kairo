状态: DONE
模块: kairo-tools
标题: MonitorTool 边界场景补充测试

目标:
查看现有 MonitorToolTest.java，为未覆盖的边界场景追加测试：
- 资源不存在时的处理
- 多次调用稳定性
- 参数缺失 / 无效参数处理
- 输出格式正确性

约束:
- 不修改 kairo-api/
- 先读取现有测试和源码确认差距再追加
