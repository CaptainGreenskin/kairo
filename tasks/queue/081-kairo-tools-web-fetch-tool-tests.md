状态: BLOCKED
模块: kairo-tools
标题: WebFetchTool 单元测试（in-process HttpServer）

目标:
为 kairo-tools 模块的 WebFetchTool 补充单元测试。
使用 JDK com.sun.net.httpserver.HttpServer 在随机端口起 in-process 测试服务器。

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/web/WebFetchToolTest.java

测试场景:
- 成功 GET 返回 body
- 404 返回错误信息
- 超时处理
- HTML 响应内容
- URL 参数透传

约束:
- 不修改 kairo-api/
- 不依赖外部网络（全部 in-process）
- Java 25 Byte Buddy 限制：不 mock 具体类
