状态: DONE
模块: kairo-expert-team
标题: ExpertTeamStateMachine 单元测试

目标:
先读取 ExpertTeamStateMachine 完整源码，为状态机补充测试。

测试场景（按实际 API 确定）:
- 合法状态转换（IDLE→PLANNING 等）不抛异常
- 非法转换抛出预期异常
- 终止状态无法转换
- 所有 State 枚举常量可 valueOf

新增文件:
- kairo-expert-team/src/test/java/io/kairo/expertteam/ExpertTeamStateMachineTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
