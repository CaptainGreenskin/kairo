状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：压缩流水线集成测试）

## 目标

为 `CompactionPipeline` 添加集成测试，验证多阶段压缩策略
按优先级顺序执行，以及第一个触发的策略被调用。

## 背景

`CompactionPipeline` 持有多个 `CompactionStrategy`，按优先级
排序，调用第一个 `shouldTrigger()` 返回 true 的策略。
是 context compaction 系统的调度中枢。

## 需要实现

先读取 `CompactionPipeline.java`，然后编写：

### 测试：CompactionPipelineTest.java

验证：
- 无策略触发时返回 empty Mono
- 第一个触发的策略被调用，其他跳过
- 优先级低的策略先触发时，优先级高的不会抢先（按注册顺序而非优先级过滤）
- 多个策略注册时，按 `shouldTrigger()` 第一个匹配的执行

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
