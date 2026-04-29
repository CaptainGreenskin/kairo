状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：计划文件管理测试）

## 目标

为 `PlanFileManager` 添加单元测试，验证计划文件的 CRUD 操作。

## 背景

`PlanFileManager` 在磁盘 `.kairo/plans/` 目录下管理 Markdown 格式的
计划文件，每个文件包含 YAML front-matter（id, name, status, createdAt）
和计划内容。

## 需要实现

先读取 `PlanFileManager.java` 理解接口，然后编写：

### 测试：PlanFileManagerTest.java

使用 `@TempDir` 隔离文件系统，验证：
- `create()` 创建计划文件并返回 PlanFile
- `list()` 返回所有计划
- `get(id)` 通过 ID 查找计划
- `get(id)` 对不存在的 ID 返回 Optional.empty()
- `update(id, status)` 更新计划状态
- `delete(id)` 删除计划文件

## 验收标准

- [ ] 6+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
