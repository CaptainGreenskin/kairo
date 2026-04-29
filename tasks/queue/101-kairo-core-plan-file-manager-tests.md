状态: DONE
模块: kairo-core
标题: PlanFileManager 单元测试

目标:
先读取 PlanFileManager（kairo-core/.../plan/PlanFileManager.java），
再补充单元测试。使用 @TempDir 隔离文件系统操作。

测试场景（按实际 API 确定）:
- createPlan(name) 创建计划文件
- 计划有唯一 ID
- 计划状态初始为 DRAFT
- listPlans() 列出所有计划
- updatePlanStatus() 更新状态

新增文件:
- kairo-core/src/test/java/io/kairo/core/plan/PlanFileManagerTest.java

约束:
- 不修改 kairo-api/
- 使用 @TempDir 不污染真实文件系统
- 先读取源码确认实际 API
