状态: DONE
模块: kairo-tools
标题: TeamCreateTool / TeamDeleteTool / EnterPlanModeTool 单元测试

目标:
先读取源码，为三个团队相关工具补充基本测试。

测试场景（按实际 API 确定）:
- 工具名称/描述不为 null
- 输入 schema 不为 null
- 基本构造不抛异常

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/team/TeamToolsTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认类结构
