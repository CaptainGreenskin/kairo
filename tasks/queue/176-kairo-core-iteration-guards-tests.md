状态: DONE
模块: kairo-core
标题: IterationGuards 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- 超出最大迭代次数时抛异常
- 正常迭代不抛
- 自定义最大值

新增文件:
- kairo-core/src/test/java/io/kairo/core/agent/IterationGuardsTest.java（如包访问允许则在同包）

约束:
- 不修改 kairo-api/
- 先读完整源码
