# APK → OpenClaw Gateway — Build Spec for Google AI Studio

Paste this whole file into Google AI Studio as the instruction for building/fixing the
Android chat app. It is the complete, current contract. Follow it exactly — the "internal
error" you hit is caused by two specific request-shape mistakes (Section 3).

> Generated 2026-11-22 by Dudebro on CLAW-DUDEBRO. Supersedes earlier notes.

---

## 0. What the app is

A small Android chat client that talks to a personal OpenClaw gateway over an
**OpenAI-compatible** HTTP API. It sends a chat message, streams the reply back, and
renders it. It must also handle assistant-attached images and inbound image sends.

It is NOT using api.openai.com — it talks to our own gateway. So the usual OpenAI
conventions mostly apply, with a few gateway-specific rules.

---

## 1. Connection

| Item | Value |
|---|---|
| Method | `POST` |
| URL | `http://<HOST>:18789/v1/chat/completions` |
| Content-Type | `application/json` |
| Auth | `Authorization: Bearer <GATEWAY_TOKEN>` |
| HOST (LAN) | `192.168.68.53` |
| HOST (Tailscale) | `100.91.98.120` |
| Port | `18789` |
| Gateway token | `86399a88e60f59a336baa901970934c6fdc896daa04309f1` |

Put HOST + TOKEN in the app's Settings screen so they are editable without a rebuild.
Default to the LAN host; fall back to the Tailscale host if the LAN one is unreachable.
Trailing slashes in HOST must be tolerated.

The token is a plain shared secret. Send it on **every** request, including the media
fetch (Section 8). A missing/wrong token returns `401`.

Sanity check that the app should be able to run:
`GET http://<HOST>:18789/v1/models` with the bearer token → returns a small JSON list.
Use this as a "Test connection" button.

---

## 2. The `model` field (CRITICAL)

The gateway treats `model` as an **agent target**, NOT a backend model name.

- Send exactly: `"model": "openclaw/apk"`  ✅
- Do NOT send bare `"openclaw"` or `"openclaw/default"` → HTTP **400**
  ("Multiple agents are configured, but this operation has no explicit owner").
- Do NOT send backend names like `gpt-4o`, `Local`, `3rd Party` → HTTP **400**
  ("Invalid `model`.").

If the app has a model dropdown with app-side labels ("Default"/"Local"/"3rd Party"),
map **all of them** to `openclaw/apk`. The actual backend model is chosen server-side.

**(Optional) pick the backend model** with a header — only if you add a advanced setting:
`x-openclaw-model: venice/deepseek-v4-1-flash-uncensored`
(hardcode this to force the fast cloud model; the `apk` agent already defaults to it).

---

## 3. ⚠️ THE "internal error" FIX (the actual bug)

**Symptom:** mid-chat you get `HTTP 500` with body `{"error":{"message":"internal error","type":"api_error"}}`
(or, in streaming, a `{"error":{...}}` chunk just before `[DONE]`).

**Cause:** the app is sending an `x-openclaw-session-key` header whose value is
**not agent-qualified** (e.g. `conv:abc`, `openai-user:abc`, `user:abc`). On a gateway with
more than one agent, an unqualified key can't be resolved to an owning agent → 500.

**Reproduced on the live gateway (all with `model: openclaw/apk`):**

| Request | Result |
|---|---|
| `x-openclaw-session-key: conv:x` | ❌ 500 `internal error` |
| `x-openclaw-session-key: openai-user:x` | ❌ 500 `internal error` |
| `x-openclaw-session-key: agent:main:conv:x` (wrong agent) | ❌ 500 `Internal Server Error` |
| no session-key header, `user: "conv:x"` | ✅ 200 |
| `x-openclaw-session-key: agent:apk:conv:x` (matches model agent) | ✅ 200 |

### The fix — pick ONE, this is mandatory

**Option A (recommended, simplest): stop sending the `x-openclaw-session-key` header
entirely. Use the OpenAI `user` field for session continuity.**

```jsonc
{
  "model": "openclaw/apk",
  "user": "conv:<stable-thread-id>",
  "stream": true,
  "messages": [ { "role": "user", "content": "hi" } ]
}
```
The gateway derives a correctly agent-scoped session key from `user`. One `user` value per
conversation thread; a new value per thread so threads don't share history.

**Option B: keep the header, but fully qualify it to the SAME agent as `model`:**
```
x-openclaw-session-key: agent:apk:<your-thread-id>
```
It MUST start with `agent:apk:` (because `model` is `openclaw/apk`). `agent:main:...`
with `model: openclaw/apk` → 500.

**Never** send `cron:`, `subagent:`, `acp:` as a bare session key (clean 400).

---

## 4. Required request body

```jsonc
POST /v1/chat/completions
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "model": "openclaw/apk",
  "user": "conv:<thread-id>",      // Option A: session continuity
  "stream": true,                   // REQUIRED (see Section 5)
  "messages": [
    { "role": "user", "content": "hello" }
  ]
}
```

- Keep the full `messages` history in the array for multi-turn context (the server keeps a
  session too, but sending history is safe and expected).
- **`stream: true` is required.**

### Backend timing note (why stream matters)
Non-streaming blocks until the whole reply is finished. With a large session this can hit
90s+ and the Android HTTP client aborts (`ClientDisconnectError`) → user sees a hang.
With `stream: true` and the fast cloud model (`x-openclaw-model` above) a trivial turn is
~2s.

---

## 5. Streaming (SSE) handling

The response is Server-Sent Events: lines starting with `data: `. Parse each:

```
data: {"choices":[{"delta":{"content":"Hi"}}]}
data: {"choices":[{"delta":{"content":" there"}}]}
data: {"choices":[{"delta":{}}],"finish_reason":"stop"}   // sometimes finish_reason at top of choice
data: [DONE]
```

- Append `choices[0].delta.content` to the visible message as it arrives.
- Stop on the line `data: [DONE]`.
- A final chunk may carry usage only (`"choices":[]`).
- **Error chunk:** `data: {"error":{"message":"...","type":"..."}}` — show it, then `[DONE]`.
- Use an SSE-aware reader (`okhttp-sse` / EventSource style). Do **not** read the whole body
  then parse if you want live typing — but either works functionally.

If you also support non-stream, read `choices[0].message.content` instead.

Empty-content note: a reply can legitimately be empty; the server then sends
`"content":"No response from OpenClaw."`. That is not an error.

---

## 6. Inbound images (app → agent)

Send images as a **base64 data URL** inside the user message content array:

```jsonc
{
  "role": "user",
  "content": [
    { "type": "text", "text": "what is this?" },
    { "type": "image_url",
      "image_url": { "url": "data:image/png;base64,<BASE64>" } }
  ]
}
```

- `http(s)://` image URLs also work (server allowlist is open), but a data URL is the most
  reliable from a phone.
- Limits: up to 8 images in the last message, ≤10 MB each. Downscale big camera shots.

---

## 7. Multi-turn behavior

- One stable `user` value per thread (Section 3, Option A).
- On "New chat", generate a new thread id → new `user` value.
- On app restart, if you persist threads, reuse the same `user` value for that thread.

---

## 8. Outbound media (agent → app) — IMPORTANT

The assistant reply is **TEXT ONLY**. Images the agent attaches are NOT embedded in the
response. Instead the text contains a path line:

```
Here you go.
MEDIA:/home/mcwiggles/.openclaw/workspace/tmp/frog.png
```
(also may appear as `PATH=/abs/path.png`)

The app must:
1. Scan reply text for `MEDIA:<path>` / `PATH=<path>`.
2. Strip that line from the shown text.
3. Fetch the bytes over HTTP **with auth** and render.

Fetch has two steps (both need the bearer token):

```
# 1) metadata
GET /__openclaw__/assistant-media?meta=1&source=<path>&sessionKey=<key>&agentId=main
   -> {"available":true,"mimeType":"image/png","sizeBytes":12345,
       "mediaTicket":"<t>","mediaTicketExpiresAt":"..."}

# 2) bytes  (ticket + all three params)
GET /__openclaw__/assistant-media?mediaTicket=<t>&source=<path>&sessionKey=<key>&agentId=main
```
- `ticket` alone → 404. `ticket` + `source` + `sessionKey` + `agentId=main` → 200.
- Simpler alternative: skip the ticket and send the bearer token on the byte request to the
  `?source=<path>` URL. Either works; always send the token.
- `file:///home/...` paths are meaningless on the phone — always fetch over HTTP.
- Use `agentId=main` in the media route (matches the existing integration).

---

## 9. Errors to handle gracefully

| HTTP | Meaning | App behavior |
|---|---|---|
| 400 | bad request (wrong `model`, reserved session key, bad image) | show `error.message`, fixable |
| 401 | missing/wrong token | prompt to check Settings token |
| 403 | scope/authority | show message |
| 409 | session archived | show "session archived, restore in Control UI" |
| 500 | server error | show `error.message`; retry once |

Always surface `error.message` from the JSON body so failures are diagnosable. Do not show
a generic "Connection failed" for a structured error.

---

## 10. Timeouts & robustness

- Http client read/idle timeout: 60–120s (stream-friendly).
- On `ClientDisconnectError`/socket abort: retry once, then surface.
- Do not hammer: one in-flight request per thread.

---

## 11. Settings screen fields

- `host` (default `192.168.68.53`; secondary `100.91.98.120`)
- `port` (default `18789`)
- `token` (the gateway token above)
- "Test connection" → `GET /v1/models`
- (advanced) `x-openclaw-model` override, default `venice/deepseek-v4-1-flash-uncensored`

---

## 12. Do-not-do list

- ❌ `model: "openclaw"` / `"openclaw/default"` / a backend name → use `openclaw/apk`.
- ❌ unqualified `x-openclaw-session-key` → use `user` field, or `agent:apk:` prefix.
- ❌ `agent:main:` prefixed key with `model: openclaw/apk`.
- ❌ assuming images come inline in the JSON — parse `MEDIA:`/`PATH=`.
- ❌ fetching media without the bearer token (→ 401, invisible image).
- ❌ non-streaming with a tight timeout.

---

## 13. Acceptance test (all must pass)

1. Test connection → 200.
2. "hi" → streamed reply appears within a few seconds, no 500.
3. Send 3 turns in one thread → context retained, stable session (same `user`).
4. New chat → new `user`, fresh context.
5. Trigger an image reply → image fetched + rendered, `MEDIA:` line hidden.
6. Send a photo → agent describes it.
7. Kill network mid-stream → graceful error, no crash.
8. Wrong token → clear 401 message.

---

## 14. Appendix — exact wire examples

Minimal working call:
```bash
curl -N http://192.168.68.53:18789/v1/chat/completions \
  -H 'Authorization: Bearer 86399a88e60f59a336baa901970934c6fdc896daa04309f1' \
  -H 'Content-Type: application/json' \
  -H 'x-openclaw-model: venice/deepseek-v4-1-flash-uncensored' \
  -d '{"model":"openclaw/apk","user":"conv:test-1","stream":true,
       "messages":[{"role":"user","content":"hi"}]}'
```

Known-good error strings (so the app can match/log them):
- 400: `Multiple agents are configured, but this operation has no explicit owner...`
- 400: ``Invalid `model`. Use `openclaw` or `openclaw/<agentId>`.``
- 400: ``x-openclaw-session-key` cannot use reserved internal session namespace...`
- 500: `internal error`  ← the one this spec fixes
