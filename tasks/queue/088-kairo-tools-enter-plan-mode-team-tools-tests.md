状态: DONE
模块: kairo-tools
标题: EnterPlanModeTool + TeamCreateTool + TeamDeleteTool 测试

目标:
为三个尚未测试的 agent 工具补充单元测试。

EnterPlanModeTool 测试场景:
- 无 name 参数时使用 "Untitled Plan"
- 有 name 参数时使用该名称
- 无 planFileManager 时 metadata 只含 mode=plan
- 无 toolExecutor 时不抛异常
- 结果 isError() 为 false

TeamCreateTool 测试场景:
- name 为 null 时返回错误
- name 为空白时返回错误
- 正常 name 时调用 teamManager.create() 并返回成功
- 成功结果 metadata 含 teamName

TeamDeleteTool 测试场景:
- name 为 null 时返回错误
- name 为空白时返回错误
- 正常 name 时调用 teamManager.delete()
- 成功结果 isError() 为 false

约束:
- 不修改 kairo-api/
- TeamManager 是接口，可直接写 stub 实现
