# Kairo

Copyright 2025-2026 the Kairo authors.

Licensed under the Apache License, Version 2.0.

## Third-Party Acknowledgments

This project was inspired by and incorporates design ideas from the following
open-source projects:

### AgentScope Java

- Project: [AgentScope Java](https://github.com/agentscope-ai/agentscope-java)
- Copyright: 2024-2026 the original author or authors (Alibaba)
- License: Apache License 2.0
- Usage: Kairo's modular Maven structure and Hook lifecycle concept were
  inspired by AgentScope's approach to agent-oriented programming architecture.
  (No runtime dependency on AgentScope Java.)

### Claude Code (Anthropic)

- Project: [Claude Code](https://docs.anthropic.com/en/docs/claude-code)
- Copyright: Anthropic, PBC
- Usage: Kairo's three-state permission model (allow/ask/deny), context
  compaction strategy (progressive compression with head/tail protection),
  read/write tool partitioning, plan mode isolation, and dangerous command
  pattern detection were inspired by design patterns from Anthropic's
  Claude Code.
