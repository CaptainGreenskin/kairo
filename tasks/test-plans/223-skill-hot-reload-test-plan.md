# Test Plan: 223 — SkillHotReload (WatchService)

## Scope

`SkillHotReloadWatcher`, `SkillReloadEvent`, `SkillLoader.reloadFile(Path)`

## Test Cases

### TC-01: CREATED event fires on new .md file
- Set up a temp directory, start watcher
- Write a new valid skill `.md` file
- Assert: listener receives `SkillReloadEvent(type=CREATED)` within 1s
- Assert: skill appears in registry

### TC-02: UPDATED event fires on modified .md file
- Load a skill, then overwrite file with updated content
- Assert: listener receives `UPDATED` event
- Assert: registry contains updated version

### TC-03: DELETED event fires on removed .md file
- Load a skill, then delete the file
- Assert: listener receives `DELETED` event
- Assert: skill no longer in registry

### TC-04: Debounce — rapid writes produce single reload
- Write the same file 5 times within 200ms
- Assert: listener called exactly once (not 5 times)
- Assert: skill in registry is the final version

### TC-05: Non-.md files are ignored
- Create a `.txt` and `.json` file in the watched directory
- Assert: listener never called

### TC-06: Watcher thread is daemon
- Start watcher, get the watch thread reference
- Assert: `watchThread.isDaemon() == true`

### TC-07: stop() is idempotent
- Call `stop()` then `stop()` again
- Assert: no exception thrown

### TC-08: AutoCloseable works via try-with-resources
- Use `try (var w = new SkillHotReloadWatcher(...)) { w.start(); }`
- Assert: no leak; resources released after block exits

### TC-09: Multiple listeners all notified
- Add 3 listeners, trigger a CREATE event
- Assert: all 3 listeners receive the event

### TC-10: Listener exception does not stop other listeners
- Add a listener that throws; add a second normal listener
- Trigger an event
- Assert: second listener still receives event

### TC-11: Invalid .md file (bad frontmatter) logs warning, no crash
- Write a file with malformed frontmatter
- Assert: no exception escapes; listener not called for that file

### TC-12: Delete of unknown skill is a no-op
- Delete a `.md` file that was never loaded (e.g., temp file)
- Assert: `unregister` called but no exception; registry unchanged

### TC-13: SkillReloadEvent record fields
- Construct `new SkillReloadEvent("my-skill", EventType.UPDATED, Instant.now())`
- Assert: `skillId()`, `type()`, `timestamp()` return expected values

### TC-14: Watcher on non-existent directory throws IOException
- Call `start()` with a directory that doesn't exist
- Assert: `IOException` is thrown during `start()`

### TC-15: Concurrent file changes handled without data race
- Use 5 threads to simultaneously create 5 different `.md` files
- Assert: all 5 CREATED events received within 2s
- Assert: all 5 skills appear in registry
