状态: DONE
模块: kairo-core
标题: ClaudeModelHarness + ModelHarness 单元测试（补充）

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- ModelHarness: 基本构建、配置存取
- ClaudeModelHarness: provider 选择、call() 转发

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/ModelHarnessTest.java（如不存在）

约束:
- 不修改 kairo-api/
- 先读完整源码
