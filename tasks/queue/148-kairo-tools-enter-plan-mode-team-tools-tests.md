状态: DONE
模块: kairo-tools
标题: EnterPlanModeTool + TeamCreateTool + TeamDeleteTool 单元测试

目标:
先读取三个 Tool 类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- EnterPlanModeTool: @Tool 注解属性（name/category/sideEffect），execute() 无 name 时返回默认名，带 name 时返回 planId，planFileManager/toolExecutor 可选
- TeamCreateTool: execute() 缺少 name 参数时返回 error，正常创建返回 team 信息
- TeamDeleteTool: execute() 正常删除，team 不存在时行为

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/agent/EnterPlanModeToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/agent/TeamCreateToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/agent/TeamDeleteToolTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
