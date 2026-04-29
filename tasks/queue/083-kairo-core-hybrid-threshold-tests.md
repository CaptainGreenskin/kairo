状态: DONE
模块: kairo-core
标题: HybridThreshold 单元测试

目标:
为 kairo-core 的 HybridThreshold (io.kairo.core.context.compaction) 补充单元测试。
该类为 package-private 静态工具，只有一个方法 shouldTrigger(ContextState, float, int)。

逻辑：
- contextWindow==0 时仅用 pressure 判断（fallback）
- contextWindow>0 时：effectiveThreshold = min(percentage * window, window - absoluteBuffer)
- usedTokens<=0 且 pressure>0 时从 pressure 推导 usedTokens

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/HybridThresholdTest.java

测试场景（≥8个）:
1. contextWindow=0, pressure<threshold → false
2. contextWindow=0, pressure>=threshold → true
3. percentage 触发 < absolute 触发，percentage 生效
4. absolute 触发 < percentage 触发，absolute 生效
5. usedTokens=0, pressure推导后触发
6. usedTokens=0, pressure推导后不触发
7. 边界：effectiveThreshold 恰好等于 usedTokens
8. absoluteBuffer > contextWindow（negative trigger）

约束:
- 不修改 kairo-api/
- 测试在同包（io.kairo.core.context.compaction）以访问 package-private 类
