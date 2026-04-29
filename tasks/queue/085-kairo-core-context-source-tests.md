状态: DONE
模块: kairo-core
标题: DateContextSource + SystemInfoContextSource 测试

目标:
为未测试的 ContextSource 实现补充单元测试：
- DateContextSource：提供当前日期/时间信息到系统提示
- SystemInfoContextSource：提供 JVM/OS 系统信息

先 ls 确认实际类结构，再针对性写测试。

新增文件（按实际类名确认）:
- kairo-core/src/test/java/io/kairo/core/.../DateContextSourceTest.java
- kairo-core/src/test/java/io/kairo/core/.../SystemInfoContextSourceTest.java

约束:
- 不修改 kairo-api/
- 先 find 确认类路径
