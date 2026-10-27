# Contributing

## Language

**Commit messages and pull requests are in English**, with conventional-commit prefixes:
`feat(scope):`, `fix(scope):`, `test(scope):`, `docs(scope):`, `chore(scope):`. Subject lines are
English too — `git log --oneline` and the Actions list should read the same way for everyone.

Everything *about the app's behavior* keeps the language the file already uses, which is mostly
Persian: KDoc comments, `docs/*-AUDIT*` / phase notes, and the strings in `values-fa/`. Don't
translate those while touching a file; that diff noise hides the real change.

Persian stays useful inside an English commit body when it quotes what the user actually sees
(«لاگِ نود», «ادامهٔ دریافت») — that phrase *is* the interface, so name it once in the body.

## Every commit is expected to pass CI

The `Build Android APK` workflow is the gate: resource + Kotlin source checks
(`tools/*.py`), then `app/src/test`, then the APKs. The source checkers need only `python3`, so run
them before pushing:

```sh
for f in tools/*.py; do python3 "$f" >/dev/null 2>&1 || echo "FAIL $f"; done
```

`docs/CI.md` is the part that saves time: what actually runs, how to read a red run without the
log, the test rules CI taught us (why a `Dialog` can't be opened in a Robolectric test, why
`assertIsDisplayed` is unreliable there, why a live SSE stream is tested by hoisting its content),
and the three test seams that exist in production code.

A few rules that are not in code, so they are here:

- The app targets panel **5.5.1+**; no backward-compatibility shims for older panels.
- App settings (this device, works offline) and *My panel* (needs a session) stay separate —
  see `docs/SETTINGS-SPLIT.md`. Don't add a catch-all “Advanced” tab; a new feature goes into a
  tab whose one-line purpose explains it.
- Anything that writes to the panel is gated on the permission the endpoint requires, and a
  half-form that would only produce 422 is worse than a read-only screen.
- Version bumps: `versionName` / `versionCode` in `app/build.gradle.kts` per landed phase, either
  in the feature commit or as its own `chore(release):` commit. **Every landed fix batch bumps as
  well** (patch digit) — several builds of one release often sit on the phone at the same time and
  a visual fix that cannot be identified by version cannot be verified either.
