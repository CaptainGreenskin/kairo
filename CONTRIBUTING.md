# Contributing to Kairo

Thank you for your interest in contributing to Kairo! This document provides guidelines and instructions for contributing.

## Prerequisites

- **JDK 17+** — Kairo requires Java 17 or later.
- **Maven 3.9+** — Used for building, testing, and managing dependencies.

## Getting Started

1. Fork the repository and clone your fork.
2. Create a new branch for your feature or bugfix:
   ```bash
   git checkout -b feat/my-new-feature
   ```
3. Make your changes and verify they build:
   ```bash
   mvn clean verify
   ```

## Building

```bash
# Full build with tests and formatting checks
mvn clean verify

# Quick compile (skip tests)
mvn clean compile -DskipTests
```

## Code Formatting

Kairo uses [Spotless](https://github.com/diffplug/spotless) with Google Java Format (AOSP style) for consistent code formatting.

```bash
# Auto-format all code
mvn spotless:apply

# Check formatting (fails if code is not formatted)
mvn spotless:check
```

**Tip:** Run `mvn spotless:apply` before committing to ensure your code is properly formatted.

## Running Tests

```bash
# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl kairo-core

# Run a specific test class
mvn test -pl kairo-core -Dtest=MyTest
```

## Test Coverage

JaCoCo is configured to generate test coverage reports. After running tests, reports are available at `target/site/jacoco/index.html` in each module.

## Commit Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix     | Description                              |
|------------|------------------------------------------|
| `feat:`    | New feature                              |
| `fix:`     | Bug fix                                  |
| `docs:`    | Documentation changes                    |
| `style:`   | Formatting, missing semicolons, etc.     |
| `refactor:`| Code restructuring without behavior change|
| `test:`    | Adding or updating tests                 |
| `chore:`   | Build process, dependencies, tooling     |

**Examples:**
```
feat: add memory compaction strategy
fix: resolve NPE in tool executor
docs: add javadoc for Agent interface
test: add unit tests for ContextManager
```

## Pull Request Checklist

Before submitting a PR, please ensure:

- [ ] Code formatted with `mvn spotless:apply`
- [ ] All tests passing (`mvn test`)
- [ ] Javadoc comments added for new public APIs
- [ ] License header present on all new Java files
- [ ] Documentation updated if needed
- [ ] Commit messages follow Conventional Commits format

## Code Style Guidelines

- Use meaningful variable and method names.
- Prefer immutability — use `record` types and `final` fields where appropriate.
- Write Javadoc for all public interfaces and classes.
- Keep methods focused and small.
- Use Reactor types (`Mono`, `Flux`) for asynchronous operations.

## Reporting Issues

- Use the GitHub issue templates for bug reports and feature requests.
- Provide as much detail as possible, including Java version and OS.

## License

By contributing to Kairo, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
