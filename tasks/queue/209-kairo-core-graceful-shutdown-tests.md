状态: DONE
模块: kairo-core
标题: GracefulShutdownManager 边界测试

目标:
扩展 GracefulShutdownManager 测试，覆盖超时、并发关闭、钩子失败等边界场景。

背景:
现有 GracefulShutdownManagerTest 只覆盖基础注册和关闭流程。
需要测试以下场景确保生产可靠性。

## 需要实现

### 测试文件
在现有 GracefulShutdownManagerTest 中添加 / 或创建
`kairo-core/src/test/java/io/kairo/core/shutdown/GracefulShutdownManagerExtTest.java`

测试场景（共 8+ 个）：
- 关闭超时时，超时的钩子被中断
- 多次 shutdown() 调用只执行一次（幂等）
- 钩子抛出异常时其他钩子仍执行
- 钩子按注册顺序执行（有序性）
- 注销钩子后不执行
- 空钩子集合 shutdown() 正常返回
- 并发 shutdown() 不产生死锁

约束:
- 不修改 kairo-api/
- 不新增外部依赖
