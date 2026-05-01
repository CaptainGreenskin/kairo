状态: DONE
模块: kairo-core
标题: SessionMemoryCompact 和 ConversationMemory 集成测试

目标:
为 SessionMemoryCompact 和 ConversationMemory 添加更完整的集成测试，
验证内存摘要、容量限制、过期清理等边界场景。

背景:
现有 ConversationMemoryTest 和 SessionMemoryCompactTest 覆盖基础功能，
但缺少以下场景：容量满时最旧记录被驱逐、过期记录过滤、标签过滤组合等。

## 需要实现

### 测试文件
`kairo-core/src/test/java/io/kairo/core/memory/ConversationMemoryIntegrationTest.java`

测试场景（共 10+ 个）：
- 添加超过容量的记录 → 最旧被驱逐
- TTL 过期后 getActive() 不返回过期记录
- 按标签过滤返回正确子集
- 多标签 AND 过滤
- compact() 后旧记录被归档
- compact() 不影响 PINNED 类型记录
- clear() 清空所有记录
- 并发写入不丢记录（多线程场景）
- 空 store 的 compact() 返回 Mono.empty()
- getRecent(n) 返回最新 n 条

约束:
- 不修改 kairo-api/
- 不新增外部依赖
