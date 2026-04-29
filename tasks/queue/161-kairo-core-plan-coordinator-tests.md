状态: DONE
模块: kairo-core
标题: PlanFileManager + CoordinatorConfig 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- PlanFileManager: 创建/读取/列出 plan 文件（使用 @TempDir）
- PlanFileManager: 不存在目录时行为
- CoordinatorConfig: 默认值、builder 构建、字段存储

新增文件:
- kairo-core/src/test/java/io/kairo/core/plan/PlanFileManagerTest.java
- kairo-core/src/test/java/io/kairo/core/agent/CoordinatorConfigTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- PlanFileManager 使用 @TempDir 避免真实文件系统依赖
