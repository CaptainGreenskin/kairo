状态: IN_PROGRESS
模块: kairo-spring-boot-starter-core
标题: SkillHotReloadAutoConfiguration — 技能热更新 Spring 自动配置

目标:
为 SkillHotReloadWatcher 提供 Spring Boot 自动配置，
使技能热更新可通过 application.properties 控制。

## 需要实现

`io.kairo.spring.SkillHotReloadAutoConfiguration`
- @AutoConfiguration(after = SkillAutoConfiguration.class)
- @ConditionalOnProperty(name="kairo.skill.hot-reload.enabled", havingValue="true", matchIfMissing=false)
- @ConditionalOnBean(SkillLoader.class)
- 创建 SkillHotReloadWatcher bean，注入 SkillLoader + SkillRegistry
- 实现 SmartLifecycle：start() → watcher.start()，stop() → watcher.close()
- skillDir 从 SkillAutoConfiguration 的 search paths 第一个本地路径推断，
  或读取 kairo.skill.hot-reload.directory 属性（Path 类型）

注册到：
`kairo-spring-boot-starter-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 约束
- 不修改 kairo-api/
- hot-reload 默认关闭（matchIfMissing=false），避免生产环境意外开启
- 监听目录不存在时打印 WARN 但不 throw（容错）
