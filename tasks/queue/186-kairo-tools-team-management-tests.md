状态: DONE
模块: kairo-tools
标题: TeamCreateTool / TeamDeleteTool / EnterPlanModeTool 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- TeamCreateTool: 工具元数据、执行创建团队
- TeamDeleteTool: 工具元数据、执行删除团队
- EnterPlanModeTool: 工具元数据、执行进入 plan 模式

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/agent/TeamCreateToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/agent/TeamDeleteToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/agent/EnterPlanModeToolTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
