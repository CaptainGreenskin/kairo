状态: DONE
模块: kairo-core
标题: PostCompactRecoveryHook + PostCompactRecoveryHandler 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- PostCompactRecoveryHook: 注册自身为 hook、压缩后上下文恢复
- PostCompactRecoveryHandler: handle() 构建恢复消息

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/recovery/PostCompactRecoveryHookTest.java
- kairo-core/src/test/java/io/kairo/core/context/recovery/PostCompactRecoveryHandlerTest.java（如不存在）

约束:
- 不修改 kairo-api/
- 先读完整源码
