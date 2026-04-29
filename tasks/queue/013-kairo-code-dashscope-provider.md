状态: DONE
创建时间: 2026-04-26
优先级: P1（M4：多模型支持 - 阿里云百炼/千问）

## 目标

为 kairo-code 添加阿里云百炼 DashScope/Qianwen 支持，通过 OpenAI 兼容接口。
用户可以用 `--provider qianwen` 或者直接设置
`--base-url https://dashscope.aliyuncs.com/compatible-mode/v1`。

## 背景

DashScope 提供 OpenAI 兼容接口，因此理论上直接设置 --base-url 即可。
需要验证当前 OpenAI 实现是否完全兼容 DashScope，并添加
`--provider qianwen` 快捷选项自动设置正确的 base-url。

## 需要实现

### 1. 在 KairoCodeMain.java 支持 provider=qianwen

- 自动将 base-url 设为 `https://dashscope.aliyuncs.com/compatible-mode/v1`
- API key 从 --api-key 或 KAIRO_CODE_API_KEY 读取（同现有逻辑）
- 默认 model 改为 `qwen-max`（可被 --model 覆盖）

### 2. 环境变量支持

- KAIRO_CODE_PROVIDER=qianwen 可替代 --provider qianwen

### 3. 测试：KairoCodeProviderTest.java（与 task-012 合并）

- provider=qianwen 时 base-url 自动设置
- 默认 model 为 qwen-max
- 显式 --model 覆盖默认值

## 验收标准

- [ ] `--provider qianwen` 自动配置 DashScope base-url
- [ ] 不影响 openai 默认行为
- [ ] 新增测试覆盖 qianwen provider 配置

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
