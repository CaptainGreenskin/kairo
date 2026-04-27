状态: TODO
优先级: P2
模块: kairo-skill
标题: SkillHotReloadWatcher 单元测试

目标:
为 SkillHotReloadWatcher 补充单元测试（现有的 SkillHotReloadWatcher.java 为
working tree 未提交文件，PR #205）。

## 需要实现

`io.kairo.skill.SkillHotReloadWatcherTest`（6+ 测试用例）

场景：
- 监听目录中创建 .md 文件 → 触发 CREATED 事件
- 修改已存在文件 → 触发 UPDATED 事件
- 删除文件 → 触发 DELETED 事件
- 停止 watcher 后不再触发事件
- 非 .md 文件变化被忽略
- SkillLoader.reloadFile 成功时 listener 收到 SkillReloadEvent

### 约束
- 使用临时目录（@TempDir）
- 不 mock 文件系统
- 测试需 await 异步事件（StepVerifier 或 CountDownLatch）
