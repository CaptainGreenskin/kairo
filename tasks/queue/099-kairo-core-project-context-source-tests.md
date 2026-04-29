状态: DONE
模块: kairo-core
标题: ProjectContextSource 单元测试

目标:
为 kairo-core/.../context/source/ProjectContextSource.java 补充测试。

测试场景:
- getName() 返回 "project-structure"
- priority() 返回 20
- isActive() 总是返回 true
- collect() 返回包含 "Project structure" 的字符串
- collect() 返回内容被缓存（同一实例，第二次调用相同或更快）
- collect() 不抛异常

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/ProjectContextSourceTest.java

约束:
- 不修改 kairo-api/
