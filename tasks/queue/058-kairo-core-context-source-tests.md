状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：上下文信息源测试）

## 目标

为 `DateContextSource`、`SystemInfoContextSource`、`CustomContextSource`
添加单元测试，验证各信息源的采集行为。

## 背景

这三个类实现 `ContextSource` SPI，为 agent 的系统提示提供上下文信息。
测试路径：`io.kairo.core.context.source`（与生产类同包）。

## 需要实现

### 测试：ContextSourceTest.java

验证：
- `DateContextSource.collect()` 返回 "Current date: YYYY-MM-DD" 格式
- `DateContextSource.getName()` 返回 "date"，`priority()` 返回 5
- `SystemInfoContextSource.collect()` 包含 "System:" 和 "Java:" 行
- `SystemInfoContextSource` 缓存结果（第二次调用返回同一对象引用）
- `CustomContextSource` 使用传入的 supplier 采集内容
- `CustomContextSource` 使用传入的 name 和 priority

## 验收标准

- [ ] 6+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
