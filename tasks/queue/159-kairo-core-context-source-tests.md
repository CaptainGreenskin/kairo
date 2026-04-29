状态: DONE
模块: kairo-core
标题: Context Source 类单元测试

目标:
先读取完整源码，为以下类补充单元测试。

测试场景（按实际 API 确定）:
- SystemInfoContextSource: getName()、priority()、isActive()、collect() 非空含 "System:"
- DateContextSource: getName()、collect() 含当前日期、缓存行为
- ProjectContextSource: getName()、priority()、collect() 含项目结构关键词
- CustomContextSource: 自定义名称/优先级/收集函数

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/SystemInfoContextSourceTest.java
- kairo-core/src/test/java/io/kairo/core/context/source/DateContextSourceTest.java
- kairo-core/src/test/java/io/kairo/core/context/source/CustomContextSourceTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
