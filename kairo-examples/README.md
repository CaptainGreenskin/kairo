# Kairo Examples

Example applications showcasing Kairo framework features and usage patterns.

## Desktop Agent Demo

**File:** `src/main/java/io/kairo/examples/desktop/DesktopAgentDemo.java`

An interactive CLI agent demonstrating Kairo v0.5 features:

- **Cross-session memory persistence** — memories stored in H2 file-mode database survive JVM restarts
- **Checkpoint/rollback** — save agent state and roll back to a previous point
- **Tool context injection** — runtime dependencies (memory store, conversation memory) injected via `toolDependencies`

### How to Run

**Mock mode (no API key needed):**

```bash
mvn exec:java -pl kairo-examples \
    -Dexec.mainClass="io.kairo.examples.desktop.DesktopAgentDemo" \
    -Dexec.args="--mock"
```

**With Anthropic Claude:**

```bash
export ANTHROPIC_API_KEY=sk-ant-xxx
mvn exec:java -pl kairo-examples \
    -Dexec.mainClass="io.kairo.examples.desktop.DesktopAgentDemo"
```

**With Qwen:**

```bash
export QWEN_API_KEY=your-key
mvn exec:java -pl kairo-examples \
    -Dexec.mainClass="io.kairo.examples.desktop.DesktopAgentDemo" \
    -Dexec.args="--qwen"
```

### Interactive Commands

| Command | Description |
|---------|-------------|
| `/remember <key>=<value>` | Store a persistent memory |
| `/recall <key>` | Retrieve a memory by key |
| `/memories` | List recent memories |
| `/save <name>` | Save a checkpoint |
| `/rollback <name>` | Rollback to a checkpoint |
| `/checkpoints` | List all checkpoints |
| `/quit` | Exit |

### Expected Behavior

1. Run the demo and use `/remember name=Alice`
2. Exit with `/quit`
3. Run the demo again and use `/recall name` — it returns "Alice"
4. Memory persists because the H2 database is stored in `./kairo-desktop-data/`

## Other Examples

- **AgentExample** — ReAct reasoning loop with file I/O and bash tools
- **MultiAgentExample** — Multi-agent collaboration patterns
- **SessionExample** — Session persistence with file-based storage
- **SkillExample** — Loading and executing agent skills
- **Spring Boot Demo** — Full Spring Boot integration (see `spring-boot-demo/`)
