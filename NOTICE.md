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
- Usage: Kairo's modular Maven structure, SPI interface design, Hook lifecycle
  system, Skill system architecture, and Spring Boot starter patterns were
  influenced by AgentScope Java's approach to agent-oriented programming.

### Claude Code (Anthropic)

- Project: [Claude Code](https://docs.anthropic.com/en/docs/claude-code)
- Copyright: Anthropic, PBC
- Usage: Kairo's permission guard model (ALLOWED/ASK/DENIED three-state
  permissions) and context engineering strategies (progressive compaction,
  facts-first philosophy) were inspired by publicly documented design patterns
  from Anthropic's Claude Code.
