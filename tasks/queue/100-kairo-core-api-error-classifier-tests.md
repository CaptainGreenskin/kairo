状态: DONE
模块: kairo-core
标题: ApiErrorClassifierImpl 单元测试

目标:
先读取 ApiErrorClassifierImpl（kairo-core/.../model/ApiErrorClassifierImpl.java），
再补充单元测试。

测试场景（按实际 API 确定）:
- 分类 HTTP 4xx 错误
- 分类 HTTP 5xx 错误
- 分类 RateLimitException
- 分类 TimeoutException
- 未知异常处理

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/ApiErrorClassifierImplTest.java

约束:
- 不修改 kairo-api/
- 先读取源码确认实际 API
