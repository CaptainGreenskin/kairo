# Introduction

**Kairo** (from Greek *Kairos* — the decisive moment for action) is a Java Agent operating system that provides a complete runtime environment for AI agents. Rather than being yet another LLM wrapper, Kairo models the agent runtime as an operating system, mapping every component to a familiar OS concept.

Kairo is not a wrapper — it's infrastructure. Think Netty for networking, Jackson for serialization, Kairo for AI Agents.

## OS Concept Mapping

| OS Concept | Kairo Mapping | Description | Status |
|------------|---------------|-------------|--------|
| Memory | Context | Context window as bounded memory with intelligent compaction | Implemented |
| System Call | Tool | 21+ specialized tools — the agent's interface to the outside world | Implemented |
| Process | Agent | Independent execution unit driven by a ReAct loop | Implemented |
| File System | Memory | Persistent knowledge storage (file / in-memory / JDBC) | Implemented |
| Signal | Hook | 10 hook points with CONTINUE/MODIFY/SKIP/ABORT/INJECT decisions | Implemented |
| Executable | Skill | Plug-and-play capability packs in Markdown format | Implemented |
| Job Scheduling | Task + Team | Multi-agent task orchestration and team collaboration | Implemented |
| IPC | A2A Protocol | Agent-to-Agent communication for cross-agent invocation | Implemented |
| Middleware | Middleware Pipeline | Declarative request/response interception | Implemented |
| Checkpoint | Snapshot | Agent state serialization and restoration | Implemented |

## Why Kairo

Kairo is built on Project Reactor for fully reactive, non-blocking execution and supports Claude, GLM, Qwen, GPT, and other models out of the box. The framework is model-agnostic — swap providers without changing agent logic.
