# Kairo Spring Boot Demo

A comprehensive demo application showcasing [Kairo](https://github.com/CaptainGreenSkin/kairo) framework integration with Spring Boot. Includes examples of simple chat, structured output, SSE streaming, session management, custom tools, lifecycle hooks, multi-agent orchestration, dynamic model switching, and permission-guarded tool execution.

## Prerequisites

- **JDK 17+**
- **Maven 3.9+**
- An LLM API key (e.g. Qwen / DashScope, Anthropic, OpenAI)

## Quick Start

1. **Set your API key:**

   ```bash
   export QWEN_API_KEY=your-api-key
   ```

2. **Run the application:**

   ```bash
   mvn spring-boot:run -f pom.xml
   ```

   The server starts on `http://localhost:8080`.

## Endpoint Reference

### Chat (`ChatController`)

| Method | Path    | Description                          |
|--------|---------|--------------------------------------|
| POST   | `/chat` | Send a message to the default agent  |

### Structured Output (`StructuredOutputController`)

| Method | Path       | Description                                  |
|--------|------------|----------------------------------------------|
| POST   | `/extract` | Extract structured person info from free text |

### Streaming (`StreamingChatController`)

| Method | Path           | Description                              |
|--------|----------------|------------------------------------------|
| GET    | `/stream/chat` | Stream a chat response as Server-Sent Events |

### Session Chat (`SessionChatController`)

| Method | Path                    | Description                        |
|--------|-------------------------|------------------------------------|
| POST   | `/session/chat`         | Send a message within a session    |
| GET    | `/session/{id}/history` | Retrieve conversation history      |
| DELETE | `/session/{id}`         | Delete a session                   |

### Custom Tools (`CustomToolController`)

| Method | Path          | Description                                |
|--------|---------------|--------------------------------------------|
| POST   | `/tools/chat` | Chat with an agent that has custom tools   |
| GET    | `/tools/list` | List all registered tool definitions       |

### Hooks (`HookDemoController`)

| Method | Path             | Description                           |
|--------|------------------|---------------------------------------|
| POST   | `/hooks/chat`    | Chat with a hook-instrumented agent   |
| GET    | `/hooks/metrics` | View timing metrics and audit log     |
| DELETE | `/hooks/reset`   | Reset all metrics and audit log       |

### Multi-Agent (`MultiAgentController`)

| Method | Path                               | Description                          |
|--------|------------------------------------|--------------------------------------|
| POST   | `/multi-agent/plan`                | Create a task plan with dependencies |
| GET    | `/multi-agent/tasks`               | List all tasks with status           |
| PUT    | `/multi-agent/tasks/{id}/status`   | Update a task's status               |
| POST   | `/multi-agent/message`             | Send an inter-agent message          |
| GET    | `/multi-agent/inbox/{agentName}`   | Poll an agent's message inbox        |
| POST   | `/multi-agent/reset`               | Reset task board and message bus     |

### Security (`PermissionGuardController`)

| Method | Path               | Description                               |
|--------|--------------------|-------------------------------------------|
| POST   | `/secure/chat`     | Chat with a permission-guarded agent      |
| GET    | `/secure/policy`   | View current permission policy            |
| PUT    | `/secure/policy`   | Update the permission policy              |
| POST   | `/secure/test-tool`| Test if a tool is permitted under policy  |

### Model Switch (`ModelSwitchController`)

| Method | Path               | Description                              |
|--------|--------------------|------------------------------------------|
| POST   | `/models/chat`     | Chat using a specific model provider     |
| GET    | `/models/available`| List available and known providers       |

## Example curl Commands

```bash
# Simple chat
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Kairo?"}'

# Structured output
curl -X POST http://localhost:8080/extract \
  -H "Content-Type: application/json" \
  -d '{"text": "John Doe is a 30-year-old software engineer from San Francisco."}'

# SSE streaming
curl -N "http://localhost:8080/stream/chat?message=Tell+me+a+joke"

# Session chat — start a new session
curl -X POST http://localhost:8080/session/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "My name is Alice"}'

# Session chat — continue (replace <session-id> with the ID from above)
curl -X POST http://localhost:8080/session/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "<session-id>", "message": "What is my name?"}'

# Custom tools — ask about weather
curl -X POST http://localhost:8080/tools/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather in Beijing?"}'

# List registered tools
curl http://localhost:8080/tools/list

# Hooks — chat and inspect metrics
curl -X POST http://localhost:8080/hooks/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather in Tokyo?"}'
curl http://localhost:8080/hooks/metrics

# Multi-agent — create a plan
curl -X POST http://localhost:8080/multi-agent/plan \
  -H "Content-Type: application/json" \
  -d '{"tasks": [
    {"subject": "Design API", "description": "Design the REST API schema"},
    {"subject": "Implement", "description": "Code the endpoints", "blockedBy": ["1"]},
    {"subject": "Test", "description": "Write tests", "blockedBy": ["2"]}
  ]}'

# Model switch — chat with a specific provider
curl -X POST "http://localhost:8080/models/chat?provider=anthropic" \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello!"}'

# Security — view and update policy
curl http://localhost:8080/secure/policy
curl -X PUT http://localhost:8080/secure/policy \
  -H "Content-Type: application/json" \
  -d '{"READ_ONLY": "ALLOW", "WRITE": "ALLOW", "SYSTEM_CHANGE": "DENY"}'
```

## Environment Variables

| Variable           | Description                        | Required |
|--------------------|------------------------------------|----------|
| `QWEN_API_KEY`     | Qwen / DashScope API key           | Yes (default provider) |
| `ANTHROPIC_API_KEY` | Anthropic API key (for model switch) | No |
| `OPENAI_API_KEY`   | OpenAI API key (for model switch)  | No |
| `DASHSCOPE_API_KEY`| DashScope API key (alternative)    | No |
| `ZHIPU_API_KEY`    | Zhipu AI / GLM API key             | No |
| `DEEPSEEK_API_KEY` | DeepSeek API key                   | No |

## Configuration

The main configuration is in `src/main/resources/application.yml`. Key sections:

- **`kairo.model`** — Model provider, API key, base URL, and model name
- **`kairo.agent`** — Default agent name, system prompt, iteration limits
- **`kairo.tool`** — Enable/disable built-in tool categories
- **`kairo.memory`** — Memory store type (in-memory by default)

To switch to Anthropic as the default provider:

```yaml
kairo:
  model:
    provider: anthropic
    api-key: ${ANTHROPIC_API_KEY:}
    model-name: claude-sonnet-4-20250514
```
