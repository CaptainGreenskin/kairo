状态: DONE
模块: kairo-core
标题: SystemInfoContextSource 单元测试

目标:
为 kairo-core/.../context/source/SystemInfoContextSource.java 补充测试。

测试场景:
- 实现 ContextSource 接口
- getName() 返回 "system-info"
- priority() 返回 10
- isActive() 返回 true
- collect() 不抛异常
- collect() 返回非 null 非空
- collect() 包含 "System:" 行
- collect() 包含 "Java:" 行
- collect() 包含 "Working Directory:" 行
- 结果被缓存（assertSame 验证）

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/SystemInfoContextSourceTest.java

约束:
- 不修改 kairo-api/
