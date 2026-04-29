状态: DONE
模块: kairo-core
标题: CustomContextSource 单元测试

目标:
为 kairo-core/.../context/source/CustomContextSource.java 补充测试。

测试场景:
- of(name, priority, supplier) 工厂方法创建 always-active source
- isActive() 默认返回 true
- getName() 返回正确名称
- priority() 返回正确优先级
- collect() 返回 supplier 的值
- of(name, priority, contentSupplier, activeSupplier) 工厂方法
- activeSupplier 返回 false 时 isActive() 为 false
- collect() 每次都调用 supplier（不缓存）
- toString() 包含名称

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/CustomContextSourceTest.java

约束:
- 不修改 kairo-api/
