#!/usr/bin/env bash
# Kairo verify command. Run inside the worktree root after the executor
# finishes. Exit 0 = pass.
#
# Args (optional): list of module dirs to constrain the build, e.g.
#     verify.sh kairo-tools kairo-mcp
# When omitted, runs full project verify.

set -euo pipefail

if [[ "$#" -gt 0 ]]; then
  MODULES="$(IFS=, ; echo "$*")"
  exec timeout 30m mvn -pl "$MODULES" -am -q verify
fi

exec timeout 45m mvn -q verify
