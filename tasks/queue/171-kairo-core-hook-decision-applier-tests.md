状态: DONE
模块: kairo-core
标题: HookDecisionApplier 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- apply(CONTINUE) 不修改 pipeline
- apply(SKIP) 跳过当前步骤
- apply(ABORT) 终止执行
- apply(MODIFY) 替换 payload

新增文件:
- kairo-core/src/test/java/io/kairo/core/hook/HookDecisionApplierTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
