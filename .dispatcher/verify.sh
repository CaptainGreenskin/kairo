#!/usr/bin/env bash
# Kairo verify command. Run inside the worktree root after the executor
# finishes. Exit 0 = pass.
#
# Args (optional): list of module dirs to constrain the build, e.g.
#     verify.sh kairo-tools kairo-mcp
# When omitted, runs full project verify.
#
# Portable timeout: prefer GNU `timeout`, fall back to `gtimeout` (Homebrew
# coreutils on macOS), fall back to no timeout. Avoids the silent "exec:
# timeout: not found" failure on a fresh macOS box.

set -euo pipefail

TIMEOUT_BIN="$(command -v timeout || command -v gtimeout || true)"

if [[ "$#" -gt 0 ]]; then
  MODULES="$(IFS=, ; echo "$*")"
  if [[ -n "$TIMEOUT_BIN" ]]; then
    exec "$TIMEOUT_BIN" 30m mvn -pl "$MODULES" -am -q verify
  fi
  exec mvn -pl "$MODULES" -am -q verify
fi

if [[ -n "$TIMEOUT_BIN" ]]; then
  exec "$TIMEOUT_BIN" 45m mvn -q verify
fi
exec mvn -q verify
