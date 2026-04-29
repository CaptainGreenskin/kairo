状态: DONE
创建时间: 2026-04-26
优先级: P2（M6 准备：用户自定义技能）

## 目标

让 kairo-code 支持从 `~/.kairo-code/skills/` 目录加载用户自定义技能（Markdown 文件），
在启动时自动发现并注册，补充内置的 4 个技能。

## 背景

当前只有 4 个内置技能（code-review, test-writer, refactor, commit-message）。
用户无法添加自己的技能。

## 需要实现

### 1. 修改 ReplLoop.bootstrapSkillRegistry()

```java
// 加载内置技能后，再扫描用户技能目录
Path userSkillsDir = kairoDir.resolve("skills");
if (Files.isDirectory(userSkillsDir)) {
    try (var stream = Files.list(userSkillsDir)) {
        stream.filter(p -> p.toString().endsWith(".md"))
              .forEach(p -> {
                  try { registry.loadFromFile(p).block(); }
                  catch (Exception e) { log.warn(...) }
              });
    }
}
```

### 2. 在 DefaultSkillRegistry 中确认 loadFromFile(Path) 方法存在

如果 DefaultSkillRegistry 没有 loadFromFile(Path)，则使用已有方法（读取内容后 load）。

### 3. 测试：UserSkillLoaderTest.java（3+ 用例）

验证：
- 目录不存在时不报错
- .md 文件被加载
- 非 .md 文件被忽略

## 验收标准

- [ ] 用户 skills 目录自动加载
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES（只改 kairo-code-cli，不改 kairo-api）
