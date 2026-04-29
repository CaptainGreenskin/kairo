状态: DONE
创建时间: 2026-04-27
优先级: P2（M3：测试补全）

## 目标

为 `EnterPlanModeTool` 添加单元测试，覆盖有无 PlanFileManager 的两种场景。

## 背景

`kairo-tools/agent/` 目录中 `EnterPlanModeTool` 是唯一没有对应测试的工具之一。
它支持可选的 `PlanFileManager` 和 `DefaultToolExecutor` 依赖注入。

## 需要实现

`kairo-tools/src/test/java/io/kairo/tools/agent/EnterPlanModeToolTest.java`

测试用例：
- 无 PlanFileManager 时，结果包含 "Entered Plan Mode"，metadata 有 `mode=plan`
- 传入 name 参数时，结果文本包含该名称（或在 planId 中体现）
- 不传 name 时，使用默认名 "Untitled Plan"
- PlanFileManager 不为空时，metadata 中有 `planId` 字段
- DefaultToolExecutor 不为空时，setPlanMode(true) 被调用（用 Mockito mock）
- isError=false 在所有正常场景中

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
