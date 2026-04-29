状态: DONE
模块: kairo-core
标题: RecoveryHandler 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- 正常恢复流程返回 RecoveryResult
- 恢复失败时的错误处理
- 无法恢复时返回空结果

新增文件:
- kairo-core/src/test/java/io/kairo/core/execution/RecoveryHandlerTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
