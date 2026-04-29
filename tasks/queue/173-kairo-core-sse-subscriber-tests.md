状态: DONE
模块: kairo-core
标题: AnthropicSseSubscriber + OpenAISseSubscriber 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- AnthropicSseSubscriber: 解析 SSE 事件流、content_block_delta 积累、message_stop 完成
- OpenAISseSubscriber: 解析 delta chunks、[DONE] 信号终止

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/AnthropicSseSubscriberTest.java
- kairo-core/src/test/java/io/kairo/core/model/openai/OpenAISseSubscriberTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
