状态: DONE
模块: kairo-core
标题: DefaultMiddlewarePipeline 单元测试

目标:
先读取完整源码，补充 DefaultMiddlewarePipeline 的单元测试。

测试场景:
- 空管道直接执行
- 单个中间件前后置处理
- 多个中间件按顺序执行
- 中间件抛异常时管道中止
- SKIP 决策跳过后续中间件
- ABORT 决策中止请求
- MODIFY 决策修改请求内容

新增文件:
- kairo-core/src/test/java/io/kairo/core/middleware/DefaultMiddlewarePipelineTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
