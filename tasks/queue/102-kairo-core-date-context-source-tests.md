状态: DONE
模块: kairo-core
标题: DateContextSource 单元测试

目标:
为 kairo-core/.../context/source/DateContextSource.java 补充测试。

测试场景:
- getName() 返回 "date"
- priority() 返回 5
- isActive() 返回 true
- collect() 不抛异常
- collect() 返回非 null 非空
- collect() 包含 "Current date:" 前缀
- collect() 包含 yyyy-MM-dd 格式日期（正则验证）
- 实现 ContextSource 接口
- 每次调用返回当前日期（不缓存）

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/DateContextSourceTest.java

约束:
- 不修改 kairo-api/
