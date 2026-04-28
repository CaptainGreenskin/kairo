状态: IN_PROGRESS
模块: kairo-channel-dingtalk
标题: DingTalkOutboundClient 单元测试

目标:
为 DingTalkOutboundClient 添加单元测试，覆盖所有响应分支。

背景:
DingTalkOutboundClient 实现了完整的响应分类逻辑（OK/RATE_LIMITED/REJECTED/DELIVERY_FAILED/SEND_FAILED），
但目前没有任何测试。需要用 MockWebServer 或自定义 HttpClient stub 验证所有分支。

## 需要实现

### 测试文件
`kairo-channel-dingtalk/src/test/java/io/kairo/channel/dingtalk/DingTalkOutboundClientTest.java`

测试场景（共 10+ 个）：
- HTTP 2xx + errcode=0 → ChannelAck.ok()
- HTTP 2xx + errcode=130101 (rate-limit) → RATE_LIMITED
- HTTP 2xx + errcode=130102 (rate-limit) → RATE_LIMITED
- HTTP 2xx + errcode=130103 (rate-limit) → RATE_LIMITED
- HTTP 2xx + errcode=400001 (非 rate-limit 错误) → REJECTED
- HTTP 429 → RATE_LIMITED
- HTTP 500 → DELIVERY_FAILED
- HTTP 403 → DELIVERY_FAILED
- 网络 IO 异常 → SEND_FAILED
- 超时 → SEND_FAILED 或 DELIVERY_FAILED

实现提示：
- 可以用 MockWebServer（已在依赖中）或自定义 HttpClient stub 实现
- 如果 MockWebServer 不在依赖中，改用 Mockito spy DingTalkOutboundClient 并 stub sendRaw() 方法

约束:
- 不修改 kairo-api/
- 不新增外部依赖
