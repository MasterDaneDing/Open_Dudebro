# Snappiness fixes (`claude/snappy`)

## What was slow (verified in code)

1. **Blocking pre-flight probe on every send.** `executeChatCompletionsWithSseFallback` ran
   `probeTcpReachable(activeBase, 10_000)` before *every* chat request, then could probe the fallback
   for another 10 s. That meant up to **20 s of dead air** before the real request even started on a
   flaky phone→tailnet path. When a fallback was reachable, a successful probe still cost a full TCP
   handshake (RTT) per send.
2. **`pingHost` double-probed** the primary and then the fallback (10 s each) before its own GET (12 s
   connect), so the worst case was about 44 s.
3. **Timeouts that hid stalls.** `connectTimeout = 60 s` and read idle `= 180 s`. A dead route took
   about 62 s to show up as a connect failure, then the 10 s fallback probe, then another 60 s connect.
   A link that connected but never answered sat for 180 s. Both looked like the "about 2 minutes,
   then timeout" problem.
4. **Bad failover retry.** After a connect failure with no fallback, the code evicted the pool and
   **retried the same dead host** (another 60 s).
5. **Wasteful history in General chat.** It replayed 12 turns untruncated, including
   `"Thinking / running tools on host..."` placeholders and FAILED error banners, as if the bot had said
   them. The gateway already keeps the session via the stable `user` key, so this only added tokens
   (and confusion).
6. **Per-token preview cost.** Every SSE delta ran 3 regexes over the *whole* accumulated text and
   pushed a StateFlow update (O(n²) over a reply, plus a recompose per token).

## What changed

All changes are in `app/src/main/java/com/example/data/openclaw/OpenClawClient.kt` unless noted.

- **:2632–2644 constants**
  - `SSE_CONNECT_TIMEOUT_SECONDS` 60 → **10**.
  - `FAST_TCP_PROBE_TIMEOUT_MS` 10 000 → **2 500**. It is now used only by the background primary
    re-check, never on the send path.
  - New: `PING_CONNECT_TIMEOUT_SECONDS=5`, `HEADERS_STALL_TIMEOUT_MS=30_000`,
    `ENDPOINT_MEMO_TTL_MS=10 min`, `PREWARM_THROTTLE_MS=45 s`, `PARTIAL_EMIT_INTERVAL_MS=40`,
    `GENERAL_HISTORY_TURNS=6`, `GENERAL_HISTORY_CHARS=2000`.
  - The mid-stream read idle stays at 180 s on purpose: the gateway sends no keepalives while tools
    run, so a long silent tool call is legitimate.
- **:166 `pingClient`** connect timeout 12 → 5 s. It shares the same connection pool, so a ping or
  prewarm leaves a warm keep-alive socket that the chat request reuses.
- **:184–230 last-good-endpoint cache.** `lastGoodEndpoint` maps primary URL to the endpoint that
  last answered over HTTP (10 min TTL).
  - `preferredBase()` picks it.
  - `forgetEndpoint()` drops it on a real connect failure or stall.
  - `schedulePrimaryRecheck()` re-checks the primary in the background while we ride the fallback, so
    we switch back automatically.
- **:234 `prewarm(host)`** is a non-blocking, throttled background `pingHost`. It resolves LAN vs
  Tailscale and pre-opens the pooled connection.
- **:495–560 `pingHost`**
  - **Removed both blocking TCP probes.** The real `GET /v1/models` (5 s connect) is the probe.
  - It tries the remembered-good endpoint first, fails over only on an actual exception, and
    records the result in the cache.
- **:1214–1250 send path**
  - **Removed the pre-flight probe entirely.**
  - The request goes straight to the remembered-good endpoint (a cache read, so no network cost).
  - New `retarget()` helper; failover now works in both directions.
- **:1254–1280 stall watchdog.** The gateway writes SSE headers plus a role chunk *before* it runs the
  agent (checked in the gateway's `openai-http` handler). So if there are no response headers within
  30 s (plus an upload allowance for big attachments), the call is cancelled with a clear "link looks
  stalled" message instead of hanging for 180 s.
  - It does **not** auto-resend, because the request may already have reached the agent and a resend
    would run the turn twice.
- **:1340–1370 streaming**
  - The first token goes to `onPartialText` **immediately**.
  - Later tokens are coalesced to at most one update per 40 ms, with a final flush.
  - `previewText()` (:2567) skips the media-stripping regexes unless the text contains
    `data:`/`MEDIA:`/`PATH=`.
- **:1485–1500 failover**
  - Triggered only by a real `ConnectException`/`NoRouteToHost`/`UnknownHost`/connect-timeout.
  - The dead endpoint is forgotten and we switch to the other one.
  - With no alternate, the error is returned **immediately** instead of retrying the same dead host.
- **:920–940 General chat context**
  - Drops PARSING/DISPATCHED/FAILED messages, so placeholders and error banners are no longer replayed.
  - Keeps the last 6 turns, each capped at 2 000 chars (was 12 turns, uncapped).
  - The system message is still only the short media hint, so the agent's own identity is kept.
- **`ClawPalViewModel.kt:395`** calls `openClawClient.prewarm(primaryHost)` whenever the host list
  loads or changes. By the time the user types "hello", the endpoint is resolved and a socket is
  already open.
- **`OpenClawIntegrationTest.kt`**
  - Updated the two assertions that pinned the old 60 s / 10 s constants.
  - The slow-host test now also asserts **zero probes on the send path**.

## Expected improvement (client side)

These figures were worked out by hand, not timed on a phone.

| Scenario | Before | After |
|---|---|---|
| Healthy host, every send | 1 TCP handshake probe + new connect (≈ 2×RTT, about 100–600 ms on tailnet/5G) | 0 probes; reuses the warm pooled socket → **0 ms added** |
| Primary flaky, probe times out, fallback OK | up to **20 s** of probes before the request | Remembered fallback used straight away (**0 s**); first-ever miss costs ≤ 10 s connect timeout, then cached |
| Primary dead, no fallback | about 62 s connect + 0.35 s + about 62 s retry ≈ **2 min** | **≤ 10 s**, clear error |
| Connected but link stalls before a reply | up to **180 s** | **≈ 30 s**, clear "stalled" error |
| `pingHost` worst case | 10 + 10 + 12 + 12 ≈ 44 s | 5 + 5 = **10 s** |
| Token rendering | regex over whole text per token | first token instant; ≤ 25 UI updates/s |

**Server side (not fixable in the app):** a "hello" still triggers about 12.5 k prompt tokens of
OpenClaw agent bootstrap, which is about 5–6 s of model time. The app's own addition for General chat
is now only the one-line media hint plus at most 6 short turns.

Bot chats still send `buildBotSystemPrompt`, which is small and needed for the bot persona and tool
allowlist. Captain Context still sends its document deck, which is required for grounding.

To cut the ~12.5 k, adjust the `apk` agent on the gateway: a lighter bootstrap / workspace file set,
fewer injected skills, or a faster model via `x-openclaw-model`.

## Verification

- `./gradlew assembleDebug` (JDK 17): **BUILD SUCCESSFUL**, produced `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest --tests OpenClawIntegrationTest`: 13/14 pass, including LAN→Tailscale failover,
  HTTP 500 retry, slow-probe host and the Captain Context payload test.
  - The 1 failure (`tailscaleMode_neverFallsBackToLan...`, at `fallbackBaseUrl` assertion line 1284)
    **also fails on untouched `main`**. It comes from `OpenClawHost.fallbackBaseUrl` in
    `BotModels.kt`, which this branch did not change.

## Not verified / caveats

- **Not tested on a real phone or tailnet.** The latency figures above come from the code paths and
  timeouts, not from on-device timing.
- **Build needed `app/google-services.json`.** It was deleted from the repo in commit `d37b4a0`;
  without it, `AuthScreen.kt` fails with `Unresolved reference 'default_web_client_id'`. I restored
  it locally from `d37b4a0^` to build, but **did not commit it**, since it may have been removed on
  purpose. Re-add it (or define `default_web_client_id`) for CI builds.
- **The 10 s connect timeout is per endpoint.** A genuinely very slow cold 5G-to-tailnet handshake
  above 10 s would now fail over or error instead of waiting. Raise `SSE_CONNECT_TIMEOUT_SECONDS` if
  that turns up in practice.

## Per-mode audit

This pass covers each chat mode's own payload, context and loops. The network layer from the
previous commit (no probes, cached endpoint, fast fail, stall watchdog) was **not touched**.

Sizes are worked out by hand from the code (chars; about 4 chars per token), not measured on a phone.

| Mode | Per-request payload (before → after) | Issue | Fix | Win |
|---|---|---|---|---|
| **General chat** (`sendGeneralChat`) | ≤ 6 × 2 000 + media hint (unchanged) | An old reply holding an inline `data:image/...;base64` screenshot was replayed as up to 2 000 chars of base64 junk | `compactHistoryText()` swaps the data-URL for `[inline image omitted]` | Up to 2 000 chars (~500 tok) per image-bearing turn; otherwise re-verified, unchanged |
| **Bot dispatch** (`dispatchInstruction`) | system ~0.6–1.2 k + history ≤ 8 × 2 500 = **20 k** → ≤ 8 × 2 000 = **16 k** | `takeLast(8)` ran *before* filtering, so PARSING/DISPATCHED placeholders took slots; FAILED error banners were replayed as bot speech; base64 blobs replayed | Filter (also FAILED) → `takeLast(BOT_HISTORY_TURNS=8)` → `compactHistoryText(…, 2 000)` | ≤ 4 k chars per turn, plus no junk turns; better context quality |
| **Bot spawn** (`spawnBackgroundTask`) | system + one user turn, no history | — | Not changed | — |
| **Captain Context** (`sendCaptainContextChat` + deck) | deck: `max(1 800, 38 000/N)` per doc, so **N × 1 800, unbounded** for N > 21 docs. History 12 × 3 000 = **36 k**. → deck **≤ 38 k text** (≤ 12 docs inlined, rest listed by name) + history 8 × 1 500 = **12 k** | (1) the 1 800 floor broke the 38 k "total"; (2) a short doc's unused share was wasted while long docs got truncated; (3) duplicate docs (same ref, or same name + text) were sent twice, and so were their **images**; (4) blank-line padding from PDF extraction; (5) history re-sent 36 k although the deck already rides on every turn; (6) FAILED banners replayed | Water-fill `fairShareBudgets()` (sum ≤ total; short docs keep all their text); `CONTEXT_DECK_MAX_TEXT_DOCS = 12`; `dedupeContextDocs()` for both the deck and image payloads; `compactDocText()`; history filter → 8 × 1 500 | Typical 3-doc case: deck the same, history **−24 k chars (~6 k tok) per turn** once the thread is long. 30-doc vault: 54 k → 38 k deck. Duplicate image: one fewer 100–300 KB image_url |
| **Captain Context – Gemini direct** (ViewModel) | **N × 12 000** doc text, unbounded | Separate hand-built vault with no total cap | Uses the same `buildCaptainContextPromptDeck()` | 5 docs: 60 k → ≤ 38 k; 10 docs: 120 k → ≤ 38 k |
| **Cafe** (`dispatchCafeBotTurn` + `selectTranscriptWindow`) | system ~1.5–2.5 k + window ≤ 14 entries / 14 k chars (already bounded) | Base64 in transcript entries; `[[DONE]]` regexes compiled on every turn | `compactHistoryText(…, 3 000)` (same cap as before); hoisted `DONE_TOKEN_*` regexes to companion | Small CPU win; blob-heavy turns shrink. Window logic unchanged (CafeOrchestratorTest 14/14) |
| **Chat Rooms** (`dispatchChatRoomTurn`) | ≤ 8 msgs, **uncapped per msg** | No length cap. **It has no callers**: rooms run through Cafe, and the offline path uses `refinerService.runChatRoomTurn` | Per-message cap (3 000 + base64 strip) as a guard | No effect on today's runtime (dead code) |
| **Host control** (`sendControlAction`) | ~200–600 chars | — | Not changed | — |
| **pingHost** | `GET /v1/models`, no body | Already tuned last pass | Not changed | — |
| **syncBotProfile** | ~1–1.5 k chars, runs only on save, mission edit or manual sync | Full chat round-trip just to "register", but that is the host contract | Not changed | — |

### ClawPalViewModel loops and jobs

- **Elapsed timers (before)**
  - Each busy lane (bot, general, context, background sub-agent) ran its own
    `while(true){ delay(1000) }` coroutine.
  - They did **not** run forever: each was cancelled in the lifecycle `finally`/abort.
  - But N parallel lanes meant N coroutines and N StateFlow map rebuilds and emissions per second.
- **Elapsed timers (after)**
  - **One shared ticker** (`startElapsed`/`stopElapsed`/`tickElapsed`).
  - It exits by itself once no lane is running.
  - It derives seconds from `SystemClock.elapsedRealtime()`, so there is no drift.
  - It only updates the flows whose lanes are live.
  - Result: with 3 bots plus 1 sub-agent busy, 4 coroutines and 4 emissions/s become 1 coroutine and ≤ 2 emissions/s. Idle cost is 0.
- **`onCleared()` added**
  - It aborts in-flight OkHttp calls and force-releases wake locks when the ViewModel dies.
  - Before, `viewModelScope` cancelled the coroutines, but a blocking SSE read and its wake lock
    could outlive them until the socket noticed.

### Not changed, and why

- **Requests keep running when you switch tabs.** This is intended: jobs are ViewModel-scoped, so a
  reply still lands in Firestore if you leave the screen. The screens already use
  `collectAsStateWithLifecycle` and `WhileSubscribed(5000)`, so UI collection stops on exit.
- **Per-keystroke work.**
  - The composer state is local to each screen.
  - The bot draft persistence is already debounced (400 ms, `ChatWorkspaceScreen:211`).
  - Message sanitising is `remember(msg.rawText)`.
  - Nothing to fix.
- **Per-token work.**
  - The OpenClaw SSE path is already coalesced (first token instant, then ≤ every 40 ms).
  - The Gemini `streamGeminiChat` stream emits per chunk, but Gemini chunks are coarse
    (sentence-sized), so it was left alone.
- **The elapsed counter recomposes the live bubble once a second.** Only the single live bubble reads
  it, so this is negligible.
- **The 38 k deck total and the 45 k per-file extraction cap were kept.** Lowering them would trade
  grounding accuracy for tokens, which is a product decision.
- **The server-side ~12.5 k-token agent bootstrap is still the dominant cost** for short turns (see above).

### Verification (this pass)

- `sh gradlew --offline assembleDebug`: **BUILD SUCCESSFUL**.
- `testDebugUnitTest --tests OpenClawIntegrationTest`: 13/14 pass. The single failure is the same
  pre-existing one, `tailscaleMode_neverFallsBackToLan_failsFast_andResolvesGeminiFlashWheel`
  (`fallbackBaseUrl` assertion, line 1284).
  - This includes the Captain Context multi-turn payload test.
- New `PerModePayloadTest`: 5/5 pass. It checks the deck bound with 40 × 50 k docs, water-fill
  budgets, dedupe, and base64 stripping.
- `CafeOrchestratorTest`: 14/14 pass.
