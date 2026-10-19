# Specification

## Product

- Android-only app, Kotlin and Jetpack Compose, no cross-platform frameworks. Never published on Google Play; distributed through GitHub releases and Obtainium.
- Chat-style UI: the user messages themselves. Bubbles for entries, a text box that grows with long input.
- Append-only diary. No editing or deleting entries. Plain UTF-8 text, no markdown.
- Each entry is its own timestamped file and its own commit, committed immediately, online or not.
- Sizes: over 100 KiB show the size, over 1 MiB show the entry in red, 10 MiB hard limit.
- One git remote per installation, any forge or host. Make no assumptions about visibility or provider. Credentials are entered by the user.
- Sync: fetch and integrate before push, rebase semantics, the remote wins, never lose a local entry. Entries live under `entries/` so conflicts should not happen; when they do, keep the remote file and store the local entry under a new name.
- When the remote is unreachable, show a small notice and retry with backoff, triggered by network availability. Be gentle on the battery.
- While the app is visible, poll the remote branch tip every 30 seconds with `ls-remote` and fetch only when it changed, backing off to five minutes on failure. No polling when not visible. No push services or servers.
- Read-only mode: follow a remote without committing or pushing. Uses a separate local mirror reset to the remote; the writable diary repository is untouched and resumes syncing when read-only is off.
- App-scoped storage only. No storage permission ever.
- SHA-1 repositories for now. Switching git libraries later for SHA-256 is acceptable.
- Self-contained: users install nothing else. Free dependencies only, as few as practical, always the latest versions.

## Engineering

- SOLID, current Android best practices, latest stable dependencies and GitHub Actions.
- No deprecated APIs, DSLs or opt-out flags when a supported replacement exists: AGP built-in Kotlin and new DSL, lifecycle-aware state collection, `java.time` durations, current Compose test APIs. Builds should be free of deprecation warnings.
- CI uses only open source tooling, including the MIT-licensed basic Gradle cache provider.
- Every push to `main` produces a full signed GitHub release with an APK and a strictly increasing version code.
- Automated tests run on free GitHub Actions runners of a public repository.
- Signing secrets (`release.keystore`, `release.keystore.base64`, `keystore.properties`, `password.sh`, `password.log`, `local.properties`) are never committed.

## Working rules for LLM contributions

- Read `docs/llm/dump.txt` first. Keep code concise so the dump fits in Claude project knowledge.
- No code comments. Low verbosity.
- Under about five changed files: give full paths and full file contents. More: one tar.gz with unambiguous paths, and list files to delete.
- No sed or patch scripts. Full files only.
- Keep `README.md` and this file current with the code.
