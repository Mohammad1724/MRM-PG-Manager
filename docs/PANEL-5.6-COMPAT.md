# PasarGuard 5.6 compatibility

The Android client supports the following 5.6 panel changes when the panel's
responses expose the corresponding fields. Older panels keep their existing
behavior; absent or malformed capability data is not treated as proof of support.

- API keys: show request usage, exhausted and unknown states; create and edit
  quotas when support is established. Editing does not reset the request count.
  Newly entered limits are restricted to the portable signed 32-bit database range.
  If the first key's quota support cannot be established, limited creation is not
  offered. Successful writes are checked against the returned fields.
- Settings: edit runtime usage, node health, shutdown and cleanup/rollup fields.
  Potentially destructive cleanup and chart-reset changes require confirmation.
  Each edit reads current settings, rebases intended changes, checks for conflicts,
  writes once and verifies the response. Unknown fields are retained. A masked
  notification token that cannot be safely preserved blocks the affected write.
- Node outbound usage: retrieve and graph usage by outbound tag for the selected
  time range and node. Permission failures, unsupported servers and malformed data
  are distinct from an empty result.
- Host transport: preserve XDrive XMux and accept panel camelCase aliases; expose
  its fields along with MASQUE XMux settings when available, with validation.

Settings and API-key caches are scoped to the exact session without storing the
credential in their keys. Permission checks fail closed, and responses from stale
or cancelled requests do not authorize a different session. Settings, quota,
key-list and outbound requests do not follow redirects that could forward an
API-key header.

## Boundaries

A fresh GET followed by a PUT is not atomic: the panel does not expose a
compare-and-swap precondition. Another writer may still change a section between
those requests. The app also cannot prove that an asynchronous node operation has
finished merely because the panel accepted it.

The client does not yet implement all web-dashboard features, including admin and
role CRUD, Passkeys, bulk proxy editing, and a complete structured FinalMask editor.
Live-panel behavior, all Android/Compose interactions and all device configurations
require validation on an actual panel and Android build.
