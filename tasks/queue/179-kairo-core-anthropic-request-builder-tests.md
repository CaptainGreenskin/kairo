状态: DONE
模块: kairo-core
标题: AnthropicRequestBuilder 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- build() 产出正确 JSON 结构
- system prompt 注入
- tool definitions 序列化
- messages 转换

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/AnthropicRequestBuilderTest.java（如不存在）

约束:
- 不修改 kairo-api/
- 先读完整源码
