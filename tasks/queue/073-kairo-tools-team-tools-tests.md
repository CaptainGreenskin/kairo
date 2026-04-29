状态: DONE
创建时间: 2026-04-27
优先级: P2（M3：测试补全）

## 目标

为 `TeamCreateTool` 和 `TeamDeleteTool` 添加单元测试。

## 背景

这两个工具依赖 `TeamManager` SPI（可以 Mockito mock），逻辑简单但缺乏测试。

## 需要实现

`kairo-tools/src/test/java/io/kairo/tools/agent/TeamToolsTest.java`

测试用例：
- TeamCreateTool：name 有效时，返回 isError=false，文本包含 team 名称
- TeamCreateTool：name 为空时，返回 isError=true
- TeamCreateTool：name 为 null 时，返回 isError=true
- TeamDeleteTool：name 有效时，调用 teamManager.delete(name)，返回 isError=false
- TeamDeleteTool：name 为空时，返回 isError=true
- metadata 中有 teamName（TeamCreateTool）

使用 `Mockito.mock(TeamManager.class)` 和 `Mockito.mock(Team.class)`。

## 验收标准

- [ ] 6+ 测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
