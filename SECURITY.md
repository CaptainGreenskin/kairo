# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.1.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in Kairo, please report it responsibly.

**DO NOT** open a public GitHub issue for security vulnerabilities.

Instead, please use [GitHub Security Advisories](https://github.com/kairo-agent/kairo/security/advisories/new) to report vulnerabilities privately.

We will acknowledge receipt within 48 hours and provide a detailed response within 7 days.

## Security Best Practices

When using Kairo:

- **Never hardcode API keys** — use environment variables (`ANTHROPIC_API_KEY`, `GLM_API_KEY`, etc.)
- **Review Skills from untrusted sources** — Skills can declare `allowedTools` restrictions, but always review third-party Skill content
- **Use the three-level permission model** — Configure tool permissions (ALLOWED/ASK/DENIED) appropriate to your deployment
- **Session data** — Kairo stores session data locally; ensure proper file permissions on session directories
