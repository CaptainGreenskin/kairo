状态: DONE
模块: kairo-skill
标题: SkillHotReload — 技能文件热更新（WatchService）

目标:
让技能系统能在运行时检测 Markdown 技能文件变更并自动重加载，
无需重启 Agent。

## 需要实现

`io.kairo.skill.SkillHotReloadWatcher`
- 实现 AutoCloseable
- 用 java.nio.file.WatchService 监听技能目录（*.md 文件变更）
- 检测到 MODIFY/CREATE/DELETE 事件时调用 SkillLoader.reload(path)
- start()/stop() 生命周期
- 去抖动：同一文件 500ms 内多次事件只触发一次 reload

`io.kairo.skill.SkillReloadEvent`
- record: skillId(String), type(CREATED/UPDATED/DELETED), timestamp(Instant)

修改 `SkillRegistry`（如已存在）或新增 `DefaultSkillRegistry`：
- 支持 register(SkillDefinition) 和 deregister(skillId)
- 线程安全（ConcurrentHashMap）

### 约束
- 不修改 kairo-api/
- 使用 WatchService（不用轮询）
- Watcher 应为守护线程
- 热更新不影响正在运行的 Agent（新请求用新版本）
