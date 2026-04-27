# Test Plan: 225 — PatchApplyTool

## Scope

`PatchApplyTool` — unified diff parsing, hunk application, dry-run, rollback

## Test Cases

### TC-01: Simple single-hunk patch applied
- Create a file with 3 lines, generate a patch that changes line 2
- Call `execute(Map.of("patchContent", patch))`
- Assert: file updated; result has `appliedHunks=1, skippedHunks=0`

### TC-02: Multi-hunk patch applied in order
- File with 10 lines; patch modifies lines 2 and 8
- Assert: both hunks applied; file content correct

### TC-03: Dry-run returns success without modifying file
- Valid patch, `dryRun=true`
- Assert: file unchanged; result message contains "Dry-run"; `dryRun=true` in metadata

### TC-04: targetPath override maps patch to different file
- Patch header says `a/old.txt` / `b/old.txt`, but `targetPath="actual.txt"`
- Assert: `actual.txt` is modified (not `old.txt`)

### TC-05: Missing patchContent returns error
- `execute(Map.of())`
- Assert: `isError=true`, message contains "patchContent"

### TC-06: Context mismatch fails with clear error
- Modify the file after generating the patch (so context lines no longer match)
- Assert: `isError=true`, message contains hunk line number; file unmodified

### TC-07: 1-line offset tolerance applied
- Patch generated for line 5; insert a blank line at top so original is now at line 6
- Assert: patch still applies successfully (offset tolerance)

### TC-08: Full rollback on partial failure
- Two-file patch; first file valid, second has bad context
- Assert: neither file is modified; `isError=true`

### TC-09: Addition-only patch (new file creation)
- Patch with `--- /dev/null` / `+++ b/newfile.txt`
- File does not exist in workspace
- Assert: new file created with correct content

### TC-10: Deletion-only patch
- File with 3 lines; patch removes all 3
- Assert: file becomes empty (0 bytes or just newline)

### TC-11: b/ prefix stripped from +++ header
- `+++ b/src/main/java/Foo.java` → resolves to `src/main/java/Foo.java` relative to workspace
- Assert: correct file is written

### TC-12: Timestamp in header ignored
- `+++ b/file.txt\t2026-04-27 10:00:00` — tab-delimited timestamp
- Assert: parses correctly; no parse error

### TC-13: Empty patch content returns error
- `execute(Map.of("patchContent", "   "))`
- Assert: `isError=true`

### TC-14: Invalid hunk header throws parse error
- Pass garbage `@@ ... @@` line
- Assert: `isError=true`, message contains "Parse error"

### TC-15: Metadata includes files, appliedHunks, skippedHunks
- Apply a valid patch
- Assert: result metadata has all three keys with correct values
