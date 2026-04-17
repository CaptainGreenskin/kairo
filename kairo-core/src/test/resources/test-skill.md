---
name: test-skill
version: 2.0.0
category: CODE
triggers:
  - "run tests"
  - "/test"
path_patterns:
  - "**/*Test.java"
required_tools:
  - "run_tests"
platform: macos
match_score: 5
allowed_tools:
  - "run_tests"
  - "read_file"
---
# Test Skill

This is a test skill loaded from classpath.

## Instructions
When running tests:
1. Identify the test framework in use
2. Run all tests in the project
3. Report results with pass/fail counts
