状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：工具审批流程测试）

## 目标

为 `ToolApprovalFlow` 添加单元测试，验证 ALLOWED / DENIED / ASK
三种权限路径的正确行为。

## 背景

`ToolApprovalFlow` 管理工具调用的用户审批流程，
通过 `ToolPermissionResolver` 解析权限，并路由到
`UserApprovalHandler` 进行人工审批。

## 需要实现

先读取 `ToolApprovalFlow.java`，然后编写：

### 测试：ToolApprovalFlowTest.java

验证：
- `ALLOWED` 权限：直接调用 executor 执行
- `DENIED` 权限：返回错误 ToolResult，不调用 executor
- `ASK` 权限且无 approvalHandler：返回错误（handler not configured）
- `ASK` 权限且 handler 批准：调用 executor 执行
- `ASK` 权限且 handler 拒绝：返回用户拒绝错误，不调用 executor

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
