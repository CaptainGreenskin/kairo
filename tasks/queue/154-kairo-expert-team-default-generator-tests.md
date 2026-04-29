状态: DONE
模块: kairo-expert-team
标题: DefaultGenerator 单元测试

目标:
先读取 DefaultGenerator 完整源码，补充测试。

测试场景（按实际 API 确定）:
- DefaultGenerator: generate() 调用 ModelProvider，返回生成文本
- DefaultGenerator: 错误处理，ModelProvider 失败时行为
- DefaultGenerator: 空/null 输入处理

新增文件:
- kairo-expert-team/src/test/java/io/kairo/expertteam/internal/DefaultGeneratorTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- DefaultGenerator 在 internal 包中，测试必须在同包
