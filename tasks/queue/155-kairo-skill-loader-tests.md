状态: DONE
模块: kairo-skill
标题: SkillLoader 单元测试

目标:
先读取 SkillLoader 完整源码，补充测试。

测试场景（按实际 API 确定）:
- SkillLoader: 从目录加载 Markdown 技能文件
- SkillLoader: 解析 frontmatter 提取 name/description/triggers
- SkillLoader: 空目录时行为
- SkillLoader: 按需加载全文内容（progressive disclosure）

新增文件:
- kairo-skill/src/test/java/io/kairo/skill/SkillLoaderTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- 使用 @TempDir 避免真实文件系统依赖
