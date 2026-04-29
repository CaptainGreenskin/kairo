状态: DONE
模块: kairo-core
标题: RecoveryResult + HashChainViolationException 测试

目标:
先读取两个类，再补充单元测试：
- RecoveryResult (kairo-core/.../execution/RecoveryResult.java)
- HashChainViolationException (kairo-core/.../execution/HashChainViolationException.java)

测试场景（按实际 API 确定）:
RecoveryResult:
- 字段访问
- equals/hashCode（如果是 record）

HashChainViolationException:
- 是 RuntimeException 子类
- getMessage() 包含描述

新增文件:
- kairo-core/src/test/java/io/kairo/core/execution/RecoveryResultTest.java
- kairo-core/src/test/java/io/kairo/core/execution/HashChainViolationExceptionTest.java

约束:
- 不修改 kairo-api/
- 先读取源码再写测试
