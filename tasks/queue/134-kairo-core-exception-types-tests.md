状态: DONE
模块: kairo-core
标题: CircuitBreakerOpenException + HashChainViolationException 单元测试

目标:
先读取两个异常类源码，补充基本测试。

测试场景（按实际 API 确定）:
- 继承体系（extends RuntimeException / KairoException 等）
- 构造函数 message 保留
- 可 throw/catch
- cause 行为

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/CircuitBreakerOpenExceptionTest.java
- kairo-core/src/test/java/io/kairo/core/execution/HashChainViolationExceptionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
