状态: DONE
创建时间: 2026-04-26
优先级: P1（M4：多模型支持 - Claude/Anthropic）

## 目标

为 kairo-code CLI 添加 Anthropic/Claude 模型支持，让用户可以直接使用
`--base-url https://api.anthropic.com` + `--model claude-sonnet-4-6` 调用 Claude 模型。

## 背景

当前 kairo-code 使用 OpenAI 兼容的 HTTP API。Anthropic API 的请求格式与 OpenAI 不同（
messages 结构、工具格式、流式 SSE 都有差异）。kairo-core 已有 AnthropicModelProvider
实现，需要在 kairo-code 中按需选择正确的 Provider。

## 需要实现

### 1. KairoCodeMain.java 添加 --provider 选项

```java
@Option(names = "--provider", description = "API provider: openai (default) or anthropic",
        defaultValue = "openai")
private String provider;
```

### 2. CodeAgentConfig 或 runOneShot 根据 provider 选择 ModelProvider

- provider=openai → 使用现有 OpenAI 实现（默认）
- provider=anthropic → 使用 kairo-core AnthropicModelProvider
- 环境变量：KAIRO_CODE_PROVIDER 覆盖默认值

### 3. AnthropicModelProvider 是否已在 kairo-core 存在

先检查 `kairo-core/.../model/anthropic/AnthropicModelProvider.java`，
如果已存在则直接在 CodeAgentFactory 中按条件注入，如果不存在则标记 NEEDS_HUMAN_REVIEW。

### 4. 测试：KairoCodeProviderTest.java（至少 3 个用例）

- --provider openai 使用 OpenAI 实现
- --provider anthropic 使用 Anthropic 实现
- 无效 provider 返回退出码 1

## 验收标准

- [ ] `--provider anthropic` 切换到 AnthropicModelProvider
- [ ] `--provider openai`（默认）行为不变
- [ ] 无效 provider 打印错误并退出 1
- [ ] 新增 3+ 测试通过

## Agent 可以自主完成

YES（如果 AnthropicModelProvider 已存在于 kairo-core）
NEEDS_HUMAN_REVIEW（如果需要新建 AnthropicModelProvider）

## 不需要修改 kairo-api SPI

YES
