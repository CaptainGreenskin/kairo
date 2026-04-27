状态: TODO
优先级: P2
模块: kairo-tools
标题: JsonQueryTool 单元测试

目标:
为 JsonQueryTool（PR #210）补充单元测试，覆盖主要 jq-like 查询场景。

## 需要实现

`io.kairo.tools.info.JsonQueryToolTest`（8+ 测试用例）

场景：
- `.field` 访问简单字段
- `.[0]` 数组索引
- `.field.nested` 嵌套访问
- `.[] | .name` 数组映射
- `select(.age > 18)` 条件过滤
- 无效 JSON 输入 → isError=true
- 不存在的字段 → 返回 null
- 复杂嵌套查询

### 约束
- 不引入 jq 二进制依赖
- 纯 Java 实现的测试
- 使用 JUnit 5
