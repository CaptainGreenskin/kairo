状态: DONE
模块: kairo-core
标题: ContextSource 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- DateContextSource: 返回包含日期信息的 ContextSection
- ProjectContextSource: 返回包含项目信息的 ContextSection
- SystemInfoContextSource: 返回包含系统信息的 ContextSection
- CustomContextSource: 返回自定义内容的 ContextSection

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/ContextSourcesTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
