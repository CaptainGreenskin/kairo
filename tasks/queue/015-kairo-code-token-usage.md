状态: DONE
创建时间: 2026-04-26
优先级: P2（M4：能力完善 - Token 使用统计）

## 目标

为 kairo-code 单次任务模式添加 `--show-usage` 选项，在任务完成后打印 token 消耗统计到 stderr。

## 背景

自动化场景需要监控 token 消耗和估算成本。当前 Agent 响应中没有 usage 信息可见。

## 需要实现

### 1. KairoCodeMain.java 添加 --show-usage 选项

```java
@Option(names = "--show-usage", description = "Print token usage stats to stderr after completion")
private boolean showUsage;
```

### 2. 查看 Msg 或 Agent 是否暴露 usage 信息

先检查 `io.kairo.api.message.Msg` 和 `io.kairo.api.agent.Agent` 是否有 usage() 或 metadata() 方法。
如果没有，标记 NEEDS_HUMAN_REVIEW（需要 SPI 扩展）。

### 3. 如果有 usage 信息

在 runOneShot 中输出：
```
[USAGE] input_tokens=1234 output_tokens=567 total=1801
```

### 4. 测试：至少 3 个用例

## 验收标准

- [ ] `--show-usage` 打印 token 统计到 stderr
- [ ] 不指定时无额外输出
- [ ] 如果 SPI 不支持，正确标记 NEEDS_HUMAN_REVIEW

## Agent 可以自主完成

CONDITIONAL（取决于 SPI 是否已暴露 usage）

## 不需要修改 kairo-api SPI

CONDITIONAL
