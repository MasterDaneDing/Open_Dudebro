# 📋 Put this file into Google AI Studio (APK fix, v2 — 2026-11-22)

**What to do:** open Google AI Studio for the Android chat app, and paste the whole of
`AI-STUDIO-BUILD-SPEC.md` (this folder) in as the build/instruction prompt.

**What changed in v2 — the two things actually breaking the app (do these first):**

1. **`Software caused connection abort`** — the app's HTTP read/idle timeout is too short.
   When the agent runs tools, the SSE stream goes silent for ~20s+ and the client kills the
   socket. **Fix:** OkHttp `readTimeout` + `connectTimeout` = 60–120s. Treat a mid-stream
   gap as normal. (Spec section 0 + section 10.)
2. **Attached image never renders** — the media fetch must send the bearer token and must
   NOT append a `sessionKey` param (unqualified `sessionKey` → 404). Correct form:
   `GET /__openclaw__/assistant-media?source=<abs-path>` + `Authorization: Bearer <token>`.
   (Spec section 0 + section 8, full pass/fail matrix.)

Both are client-side. The server was verified clean today: the exact PNG serves HTTP 200,
byte-identical, over LAN and localhost; SSE + chat + the `model: openclaw/apk` contract all
work.

**Also in v2:** the `internal error` (HTTP 500) fix from v1 is unchanged (section 3 —
use the `user` field, or qualify an `x-openclaw-session-key` as `agent:apk:...`), plus a new
CORS/WebView caveat on the media route (section 8).

Full live test matrix + root-cause notes: `APK-INTEGRATION.md` and, on the PC,
`~/.local/state/apk-bridge/finding-internal-error.md`.
