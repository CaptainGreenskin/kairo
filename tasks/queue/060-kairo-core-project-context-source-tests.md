状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：ProjectContextSource 测试）

## 目标

为 `ProjectContextSource` 添加单元测试，验证项目上下文采集行为。

## 背景

`ProjectContextSource` 读取当前工作目录下的项目文件
（如 pom.xml、package.json、pyproject.toml 等）来识别项目类型，
并为 agent 提供项目元信息。

## 需要实现

先读取 `ProjectContextSource.java` 理解接口，然后编写：

### 测试：ProjectContextSourceTest.java

验证：
- `getName()` 和 `priority()` 返回正确值
- `collect()` 在不同工作目录下的行为
- 可识别常见项目类型（如 Maven 项目有 pom.xml）
- 无法识别时返回合理的默认值

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
