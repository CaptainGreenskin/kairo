状态: DONE
模块: kairo-core
标题: HybridThreshold 单元测试

目标:
先读取 HybridThreshold 完整源码，补充测试。

测试场景（按实际 API 确定）:
- shouldTrigger: 压力超过阈值 → true
- shouldTrigger: 压力低于阈值 → false
- absoluteBuffer 边界条件
- contextWindow=0 时退化为百分比阈值
- 私有构造器（如适用）

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/HybridThresholdTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
