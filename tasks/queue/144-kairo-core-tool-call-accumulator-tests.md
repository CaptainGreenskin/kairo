状态: DONE
模块: kairo-core
标题: ToolCallAccumulator 单元测试

目标:
先读取 ToolCallAccumulator 完整源码，补充测试。

测试场景（按实际 API 确定）:
- 初始状态为空
- 追加流式工具调用片段
- build() 返回完整的 DetectedToolCall 列表
- 多工具并发累积
- 边界条件（空输入，重复 id）

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/openai/ToolCallAccumulatorTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认 append()/build() 方法签名
