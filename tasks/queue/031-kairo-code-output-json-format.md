状态: DONE
创建时间: 2026-04-26
优先级: P2（M6 准备：结构化输出）

## 目标

在 `--task` 单次模式中添加 `--output-format json` 选项，
将 Agent 响应以 JSON 格式输出，包含 response、iterations、tokens 字段。

## 背景

当 kairo-code 被外部脚本或 CI/CD 调用时，结构化输出便于解析。
M6 中自动化流水线需要读取 Agent 的执行结果。

## 需要实现

### 1. 在 KairoCodeMain 添加 --output-format 选项

```java
@Option(names = "--output-format", description = "Output format: text (default), json",
        defaultValue = "text")
private String outputFormat;
```

### 2. JSON 输出格式

```json
{
  "response": "...",
  "iterations": 5,
  "total_tokens": 12345,
  "exit_code": 0
}
```

当 --show-usage 时从 AgentSnapshot 读取 iterations 和 total_tokens。

### 3. 测试：KairoCodeJsonOutputTest.java（3+ 用例）

## 验收标准

- [ ] --output-format json 输出合法 JSON
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES
