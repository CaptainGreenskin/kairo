状态: DONE
创建时间: 2026-04-27
优先级: P2（M3：测试补全）

## 目标

为 `TodoWriteTool` 的更新和删除操作补充测试。

## 背景

现有 `TodoToolTest` 只覆盖了创建、读取、清空场景。
TodoWriteTool 支持通过 id 更新单个 todo（status、priority、title 更新），
以及删除指定 id 的 todo——这些场景缺乏测试。

## 需要实现

在 `TodoToolTest.java` 中追加，或新建 `TodoUpdateDeleteTest.java`：

先读取 `TodoWriteTool.java` 确认 update/delete 操作的输入参数格式，然后：
- 写入 todo 后，用 update 操作修改 status 为 "completed"
- 写入多个 todo 后，删除其中一个，读取验证只剩一个
- 更新不存在的 id 时返回 isError=true 或优雅处理
- 同时更新 status + priority
- 读取后 count 字段正确

## 验收标准

- [ ] 5+ 新测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
