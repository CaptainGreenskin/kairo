状态: DONE
模块: kairo-core
标题: ToolCallAccumulator 补充测试

目标:
先读取 ToolCallAccumulator 和现有测试，为未覆盖的场景追加测试。

测试场景（按实际 API 确定）:
- 多个 tool call 并发时的线程安全性
- 相同 callId 的更新（增量 token append）
- 清空后再添加

文件:
- kairo-core/src/test/java/.../ToolCallAccumulatorTest.java（追加或新建）

约束:
- 不修改 kairo-api/
- 先 find 确认现有测试文件
