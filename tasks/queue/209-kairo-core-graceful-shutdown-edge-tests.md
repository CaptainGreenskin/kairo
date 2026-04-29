状态: DONE
模块: kairo-core
标题: GracefulShutdownManager 边界场景测试

目标:
扩展 GracefulShutdownManager 测试，覆盖并发关闭、钩子失败、幂等性等场景。

背景:
现有 GracefulShutdownManagerTest 只覆盖基础注册和关闭流程。
生产环境需要确保钩子失败不导致其他钩子跳过，且关闭幂等。

## 需要实现

在现有测试文件旁创建：
`kairo-core/src/test/java/io/kairo/core/shutdown/GracefulShutdownManagerExtTest.java`

测试场景（共 8+ 个）：
- 钩子按注册顺序执行（有序性验证）
- 多次 shutdown() 调用只执行一次（幂等性）
- 钩子抛出异常时其他钩子仍执行
- 注销钩子后不执行
- 空钩子集合 shutdown() 正常返回
- 并发调用 shutdown() 不产生重复执行
- shutdown() 完成后注册新钩子不执行

约束:
- 不修改 kairo-api/
- 不新增外部依赖
