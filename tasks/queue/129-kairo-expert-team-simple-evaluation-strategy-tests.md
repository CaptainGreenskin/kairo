状态: DONE
模块: kairo-expert-team
标题: SimpleEvaluationStrategy 单元测试

目标:
先读取 SimpleEvaluationStrategy 完整源码，补充测试。

测试场景（按实际 API 确定）:
- 非空 artifact → PASS verdict
- 空 artifact → REVISE verdict
- 自定义 rubric 函数被调用
- rubric 抛异常 → REVIEW_EXCEEDED

新增文件:
- kairo-expert-team/src/test/java/io/kairo/expertteam/SimpleEvaluationStrategyTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认类结构
