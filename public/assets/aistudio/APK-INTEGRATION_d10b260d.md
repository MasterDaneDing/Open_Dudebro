# APK ↔ OpenClaw integration notes

How your APK talks to the gateway, and the fixes needed in the APK.

> **Instruction bridge via Google Drive:** the APK can be driven by files in
> `My Drive/OpenClaw/APK-Bridge/` (drop a JSON into `instructions/`, results land in
> `processed/`). See `README.md` in that folder. The PC reaches it with `rclone gdrive:`.
Gateway endpoint: `POST http://<gateway-host>:18789/v1/chat/completions` (OpenAI-compatible, enabled).
Auth: `Authorization: Bearer <gateway-token>` (this is the operator token — full access).

## Hard rules (these are why it broke)

1. **`model` field = an agent target, not a backend model.**
   - Send exactly `openclaw` (or `openclaw/default`, or `openclaw/main`).
   - `Default` / `Local` / `3rd Party` are YOUR app's labels — they must map to `openclaw`.
     Sending anything else returns `HTTP 400 "Invalid model. Use openclaw or openclaw/<agentId>"`.
   - To pick the *backend* model, send a header: `x-openclaw-model: venice/deepseek-v4-1-flash-uncensored`.
   - Discover valid targets: `GET /v1/models`.

2. **Outbound images are NOT embedded in the response.** The endpoint handles *inbound* images only.
   The assistant reply is TEXT. When the agent attaches an image, the file path appears in the text
   (e.g. `MEDIA:/abs/path.png` or `PATH=/abs/path.png`). Your APK must:
   a. parse the path out of the message text, then
   b. fetch it from the assistant-media route WITH auth (see below) and render locally.
   - An empty text reply renders as the stub `"No response from OpenClaw."` — that just means the
     turn produced no text, not a crash.

3. **Fetching media requires auth (or a short-lived ticket).** The media route is token-gated.
   - Metadata (needs bearer token):
     `GET /__openclaw__/assistant-media?meta=1&source=<path>&sessionKey=<key>&agentId=main`
     → JSON `{available, mimeType, sizeBytes, mediaTicket, mediaTicketExpiresAt}`.
   - Bytes: use the returned `mediaTicket` **with all three params**:
     `GET /__openclaw__/assistant-media?mediaTicket=<t>&source=<path>&sessionKey=<key>&agentId=main`
     (ticket alone 404s; ticket + source + sessionKey + agentId = 200).
     Or just send the bearer token on the byte request and skip the ticket.
   - A raw `file:///home/...` path is meaningless on the phone — always fetch over HTTP.

## Latency / disconnects

4. **Turn on `stream: true`.** Non-streaming blocks until the whole answer is done. With the local
   4B model and a growing session context this hits 90s+ and your client aborts ("connection abort",
   `ClientDisconnectError`).
5. **Steer the backend model with `x-openclaw-model`.** Measured on the LAN today:
   - `venice/deepseek-v4-1-flash-uncensored` → **~1.9s** for a trivial turn
   - default (`ollama/qwen3.5-fast`, local 4B) → 90s+ on a warm/large session
   Use the cloud model for chat; local is fine for short prompts only.
6. **Send a stable per-conversation `user` value** (e.g. `user: "conv:<threadId>"`). One value per
   thread keeps one server session and stops context from ballooning. Don't use an account-wide id
   unless you *want* all devices/threads to share one session.
7. **Raise your HTTP read/idle timeout** (e.g. 60–120s) as a backstop.

## Working request (example)

```bash
curl -N http://127.0.0.1:18789/v1/chat/completions \
  -H 'Authorization: Bearer <GATEWAY_TOKEN>' \
  -H 'Content-Type: application/json' \
  -H 'x-openclaw-model: venice/deepseek-v4-1-flash-uncensored' \
  -d '{
    "model": "openclaw",
    "user": "conv:my-thread-1",
    "stream": true,
    "messages": [{"role":"user","content":"hi"}]
  }'
```

Inbound image (send image TO the agent) — use a data URL:

```json
{ "role":"user", "content":[
  {"type":"text","text":"what is this?"},
  {"type":"image_url","image_url":{"url":"data:image/png;base64,<BASE64>"}}
]}
```
HTTP image URLs also work now (server `images.allowUrl` was flipped on). Default limits:
8 images/last message, 10MB each.

## Gateway-side changes already made for you

- Device pairing for your phone was **approved** (was stuck pending → WS client failed).
- `gateway.http.endpoints.chatCompletions.images.allowUrl = true` (was off → http image sends dropped).

## Optional hardening for the gateway

- `gateway.controlUi.allowedOrigins` currently lists only localhost. If the APK's WebView ever hits
  origin/CORS issues from the LAN/Tailscale IP, add those origins.
