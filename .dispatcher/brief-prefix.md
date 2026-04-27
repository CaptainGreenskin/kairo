# Per-project brief prefix

This file is automatically prepended to every dispatch brief. Use it to repeat
project-wide rules the executor must always honour. Keep it short — every line
is sent to the executor before its task description.

## Hard rules

- Work only inside the current worktree directory.
- Do not push, do not modify CI configuration, do not edit `.dispatcher/`.
- If the verify command fails, fix the failure — do not pass `--no-verify`,
  `-DskipTests`, `--force`, or any equivalent.
- Stay within the task scope. Do not refactor unrelated code.

## Style

- Match the surrounding code's style. Look at the nearest sibling file.
- New tests required for new behaviour.
- Commit message must follow the repo's existing convention.
