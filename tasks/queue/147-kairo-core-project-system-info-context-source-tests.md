状态: DONE
模块: kairo-core
标题: ProjectContextSource + SystemInfoContextSource 单元测试

目标:
先读取两个 ContextSource 类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- ProjectContextSource: getName/priority/isActive，collect() 返回项目路径信息
- SystemInfoContextSource: getName/priority/isActive，collect() 包含 OS/JVM 信息

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/ProjectContextSourceTest.java
- kairo-core/src/test/java/io/kairo/core/context/source/SystemInfoContextSourceTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
