状态: DONE
模块: kairo-core
标题: CompactionMetricsHook / PostCompact 钩子测试补充

目标:
先读取完整源码，验证 CompactionMetricsHookTest 是否有完整覆盖，
若覆盖不足则补充关键场景。

测试场景（按实际 API 确定）:
- 压缩发生后钩子收到正确的 metrics 数据
- 多次压缩后累计 metrics
- 钩子返回 CONTINUE 不影响流程
- 钩子异常时流程继续（不中止 agent）

涉及文件:
- kairo-core/src/test/java/io/kairo/core/agent/CompactionMetricsHookTest.java（已有，检查或追加）

约束:
- 不修改 kairo-api/
- 先读完整源码
