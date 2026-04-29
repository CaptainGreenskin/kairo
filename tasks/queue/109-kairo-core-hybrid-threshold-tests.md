状态: DONE
模块: kairo-core
标题: HybridThreshold 单元测试

目标:
先读取 HybridThreshold 源码，再补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认类型（record/class/enum）
- 字段访问和构造
- 关键计算逻辑（如阈值判断）

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/HybridThresholdTest.java

约束:
- 不修改 kairo-api/
- 先读取完整源码再决定测试场景
