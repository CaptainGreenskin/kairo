状态: DONE
优先级: P1
模块: kairo-skill
标题: SkillHotReloadWatcherTest 补充测试用例（当前 6 个，目标 10+）

目标:
SkillHotReloadWatcherTest 当前有 6 个测试用例，不满足 M58 门控（每个核心类 ≥10 用例）。
补充 4 个以上缺失场景。

## 当前已覆盖场景（不要重复）

- createMdFile_triggersCreatedEvent
- modifyExistingFile_triggersUpdatedEvent
- deleteFile_triggersDeletedEvent
- stopWatcher_noMoreEvents
- nonMdFileChange_isIgnored
- reloadFileSuccess_skillRegisteredInRegistry

## 需要新增场景（至少 4 个）

建议补充：
- 快速连续修改同一文件时，事件被正确接收（多次触发场景）
- 监听目录下子目录中的 .md 文件变化（如果实现支持递归）或验证不监听子目录（如果不支持）
- watcher 停止后再次 start() 成功恢复监听
- reload 时 MD 解析失败（无效格式）→ 不 crash，记录错误，旧 skill 保留
- 多个不同文件同时变化 → 每个文件各触发一次事件
- 在 watcher 未启动时直接调用 stop() 不抛异常

直接在现有 `io.kairo.skill.SkillHotReloadWatcherTest` 中追加测试方法，不新建文件。

### 约束
- 使用 @TempDir 隔离，不依赖真实 skill 文件
- 注意 WatchService 的事件延迟，适当 await/sleep（≤3s）
- 不修改 kairo-api/
