状态: DONE
模块: kairo-core
标题: ProviderRetry 单元测试

目标:
为 kairo-core 的 ProviderRetry 类补充单元测试。
先读取源码确认实际 API，然后针对重试逻辑写测试。

新增文件:
- kairo-core/src/test/java/io/kairo/core/... (按实际包路径)

测试场景（按实际 API 确定）:
- 成功时不重试
- 可重试错误触发重试
- 最大重试次数后抛异常
- 不可重试错误直接传播

约束:
- 不修改 kairo-api/
- Java 25：不 mock 具体类，使用手写 stub 或真实对象
