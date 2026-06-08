# 快速开始

## 环境要求

- **JDK 17+**
- **Maven 3.8+**
- **Git**
- 至少一个受支持的模型提供者的 API Key

## 安装

Kairo Code 尚未发布到 Maven Central，需要从源码安装：

```bash
# 克隆仓库
git clone https://github.com/CaptainGreenskin/kairo-code.git
cd kairo-code

# 构建所有模块
mvn clean install -DskipTests
```

构建完成后，CLI 可执行 JAR 位于 `kairo-code-cli/target/kairo-code-cli.jar`。

## 配置

### API Key

将首选模型提供者的 API Key 设置为环境变量：

```bash
# Anthropic Claude（推荐）
export ANTHROPIC_API_KEY=sk-ant-...

# GLM
export GLM_API_KEY=your-glm-key

# Qwen
export QWEN_API_KEY=your-qwen-key

# OpenAI
export OPENAI_API_KEY=sk-...
```

只需要配置一个 API Key。Kairo Code 会使用已配置的提供者。

## 运行 Kairo Code

### 启动 REPL

```bash
java -jar kairo-code-cli/target/kairo-code-cli.jar
```

或通过 Maven 直接运行：

```bash
mvn exec:java -pl kairo-code-cli
```

启动后会看到 Kairo Code 提示符：

```
Kairo Code v0.1.0
Model: claude-sonnet-4-20250514
Working directory: /home/user/project

>
```

### 第一次交互

在提示符处输入自然语言请求：

```
> 创建一个 Calculator Java 类，包含加减乘除方法，并编写 JUnit 5 测试。
```

Kairo Code 会：
1. 将 Agent 的推理过程流式输出到终端
2. 在执行文件写入和 Bash 工具前提示你确认
3. 创建源文件和测试文件
4. 编译并运行测试

### 工具审批

默认情况下，Kairo Code 在运行会修改系统的工具（文件写入、Bash 命令、Git 操作）前会请求确认。你会看到类似提示：

```
[Tool: Write] Create file src/main/java/Calculator.java (45 lines)
Allow? [y/n/always]:
```

- `y` -- 批准本次调用
- `n` -- 拒绝，让 Agent 尝试其他方案
- `always` -- 在本次会话中始终批准此工具

## 会话持久化

Kairo Code 使用 `FileSessionStorageProvider` 将对话历史持久化到磁盘。在同一工作目录重启 CLI 时，之前的会话会自动恢复。

会话文件存储在工作目录下的 `.kairo/sessions/` 中。

## 常用命令

| 输入 | 描述 |
|------|------|
| 自然语言 | 向 Agent 发送消息 |
| `/skills` | 列出可用技能 |
| `/plan` | 进入计划模式，进行多步推理 |
| `/session` | 显示会话信息 |
| `/clear` | 清除对话上下文 |
| `/exit` | 退出 REPL |

## 桌面应用

更喜欢图形界面？Kairo Code 桌面应用通过可视化的引导流程带你完成同样的配置。

### 欢迎界面

![欢迎界面](/images/kairo-code/01-welcome-screen.png)

### 配置 API Key

![API 配置](/images/kairo-code/02-api-config.png)

### 打开项目

![打开项目](/images/kairo-code/05-open-project.png)

### 开始对话

![主聊天界面](/images/kairo-code/06-main-chat-interface.png)

### 账户与设置

![设置账户](/images/kairo-code/07-settings-account.png)

## 下一步

- [架构](./architecture) -- 了解 Kairo Code 的内部结构
- [Kairo 框架指南](/zh/guide/introduction) -- 学习底层框架
