# Focused reliability and security audit — 2026-10-27

## Scope and baseline

This pass inspected high-risk request, account, cache, monitoring, notification, widget and backup paths, as well as static checks across the Kotlin and resource files. It is not a proof that every screen, interaction, panel version, permission change or device configuration has been exercised. The worktree was checked against `e0b74c620c239c8c96b6fd17912c1b288d548098` before this pass. The green Android CI runs for that commit do **not** validate the uncommitted changes described here.

## Changes

- Authenticated requests do not follow cross-origin redirects; mutations are not transparently replayed, and an uncertain write result is reported distinctly. Read retries and same-origin read redirects remain available.
- Memory and offline caches are tied to the full account and credential. Late responses cannot overwrite the active account's disk cache, and session expiry compares the issuing session atomically with the active one.
- Group-name lookups are isolated by credential, invalidated on group changes and permission refresh, and protected against an in-flight lookup reinstalling invalidated names. Unauthorized group lookups are propagated rather than silently retried against the fallback route.
- Monitoring baselines, alert flags and counters use an owner-bound active-session scope; unowned legacy state is retained but not attributed to a new account. A switch or credential rotation establishes a fresh baseline. New backups include an optional owner fingerprint so matching baselines can be restored; legacy backups without a reliable owner are not attributed automatically.
- A late worker response or 401 does not operate on or expire a different active account. Cancelling a blocked system-stat request closes its HTTP call. A missing or invalid CPU reading remains unknown instead of breaking the cache or fabricating zero.
- Widget lifecycle broadcasts remain available to Android, while the manual network refresh uses a separate non-exported receiver. Restoring a different account also synchronizes the running UI and authorization state.

## Verification performed

- A temporary JVM harness compiled the production API/model/cache code against JUnit, OkHttp, MockWebServer and Android-compatible JSON, with simplified Android/Compose/Robolectric stubs: **362 tests passed**.
- Separate JVM harnesses using fake Android preferences and/or WorkManager ran **11 storage tests**, **6 monitoring-worker tests**, and **12 backup/preferences tests**. The backup harness used the JVM crypto implementation, not Android Keystore.
- The widget production source compiled against Android 35 API classes with fake application dependencies. This is not an Android Gradle build or a device test.
- Static checks passed for **247 Kotlin files**, **1,829 strings per language**, Compose references, imports, resources, widget target sizes, palette contrast and seven Python tooling tests. The lambda checker also reported eight type-unknown warnings; it did not identify an incompatible positional argument. The parser ran on all files; later changes were parsed again or compiled in a focused harness.
- Regression tests initially reproduced unsafe redirects/replays and cross-account group-name reuse before the corresponding fixes. Temporary harness setup errors were corrected and the final harness runs above passed.

## Remaining limits and risks

- There is no complete Android SDK, emulator, physical device or live panel in this environment. The new Android/Robolectric/Compose tests, the merged manifest, debug/release builds, real encrypted preferences, notification delivery and widget behavior still need an official Android build and device verification.
- The current encrypted backup format uses AES-CBC without an authentication tag; an altered ciphertext is not reliably detected. Backup inspection and restore also read the entire file into memory without a size bound. The encryption format was not changed in this pass because backwards compatibility and migration require dedicated review and real-device testing.
- Permission refresh invalidates the separate group-name lookup cache, but already-rendered user data or previously saved user caches can still contain group labels until those views reload. Changes to permission scopes on a live panel require additional end-to-end verification.
- An account check before an automatic mutation cannot make the remote operation atomic with a subsequent account switch. A request already sent may still complete on the server.
- Visual behavior across all screen sizes, themes and languages, and full PasarGuard web-feature parity, remain unverified. Several web features are not implemented; see the existing compatibility documents.
