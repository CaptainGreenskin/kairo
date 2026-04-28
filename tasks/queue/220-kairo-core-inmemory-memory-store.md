状态: DONE
模块: kairo-core
标题: InMemoryMemoryStore — 完整 MemoryStore 实现 + 相关性评分

目标:
实现完整的 MemoryStore，让 Agent 能持久化知识并按相关性检索。

## 需要实现

`io.kairo.core.memory.InMemoryMemoryStore`
- 实现 MemoryStore SPI
- 底层：CopyOnWriteArrayList<MemoryEntry>（读多写少）
- store(MemoryEntry): 保存
- recall(query, limit): 按相关性评分返回 top-N 条目
- delete(id): 删除单条
- clear(agentId): 清空某 Agent 的所有记忆

`io.kairo.core.memory.MemoryRelevanceScorer`
- 简单词频匹配评分（TF 近似）
- 将 query 拆词，计算与 MemoryEntry.content 的词重叠度
- 额外：recency 加权（越新越高，半衰期 7 天）

### 约束
- 不修改 kairo-api/
- 不引入向量库依赖（留 hook 给后续实现）
- 相关性公式: score = 0.7 * termOverlap + 0.3 * recencyFactor
- 接受 MemoryEntry 的全部字段（查看 SPI 接口确认字段）
