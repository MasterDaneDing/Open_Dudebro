const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_claw_123";
const BOB_UID = "bob_claw_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function sampleBotPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Bimbo",
    roleTitle: "Daily Claw Companion",
    archetype: "GLASSES_FELLOW",
    hatStyle: "NONE",
    accessoryStyle: "FOREHEAD_GLASSES",
    primaryColorHex: "#EEA05E",
    accentColorHex: "#DC4D21",
    systemPersona: "Warm, clear, cheerful assistant for OpenClaw tasks.",
    openClawAgentId: "main",
    pinnedDefault: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function sampleMessagePayload(uid, botId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    botId,
    sender: "USER",
    rawText: "hey buddy tidy up my downloads folder please",
    parsedInstruction:
      "Inspect ~/Downloads, group PDFs into ~/Downloads/Invoices, and summarize moved files.",
    instructionGoal: "Organize PDF files in Downloads",
    instructionSteps: [
      "List files in ~/Downloads modified in the past 7 days",
      "Move PDF invoices into ~/Downloads/Invoices",
    ],
    expression: "THINKING",
    status: "PARSING",
    attachedFiles: ["receipt_nov.pdf"],
    linkedPaths: ["~/Downloads"],
    takeoverSuggested: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user: denied read and write on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/bots`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/bots`)
      .doc("bot_1")
      .set(sampleBotPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/hosts`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/linkedFiles`).get());
});

test("Owner CRUD: bots create, partial update, query, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const botsRef = aliceDb.collection(`users/${ALICE_UID}/bots`);

  await assertSucceeds(botsRef.doc("bot_1").set(sampleBotPayload(ALICE_UID)));

  // Partial update of appearance & character rename
  await assertSucceeds(
    botsRef.doc("bot_1").update({
      name: "Captain Bimbo",
      hatStyle: "BERET",
      accessoryStyle: "STUBBLE",
      accentColorHex: "#E85D2A",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Validate functionPreset + OpenClaw LLM fields on bots
  await assertSucceeds(
    botsRef.doc("bot_translator").set(
      sampleBotPayload(ALICE_UID, {
        name: "Polyglot Volt",
        archetype: "RETRO_ROBOT",
        openClawAgentId: "translator",
        functionPreset: "TRANSLATION",
        llmProvider: "VENICE_AI",
        llmModel: "venice/llama-3.3-70b",
        openClawSynced: true,
      })
    )
  );
  await assertFails(
    botsRef.doc("bot_bad_llm").set(
      sampleBotPayload(ALICE_UID, { llmProvider: "UNKNOWN_PROVIDER" })
    )
  );

  // Validate all 5 additional character templates
  for (const arch of ["DINOSAUR", "GOBLIN", "YANKI_BIKER", "CHINESE_MASTER", "PIGTAIL_GIRL"]) {
    await assertSucceeds(
      botsRef.doc(`bot_${arch}`).set(
        sampleBotPayload(ALICE_UID, {
          name: `Custom_${arch}`,
          archetype: arch,
        })
      )
    );
  }

  // Reject unsupported archetype
  await assertFails(
    botsRef.doc("bot_invalid_arch").set(
      sampleBotPayload(ALICE_UID, { archetype: "ALIEN_MONSTER" })
    )
  );

  // List query matching Kotlin repository
  await assertSucceeds(botsRef.orderBy("createdAt").get());

  // Reject ghost field / @DocumentId 'id' leakage / immutable createdAt change
  await assertFails(
    botsRef.doc("bot_2").set(sampleBotPayload(ALICE_UID, { id: "bot_2" }))
  );
  await assertFails(
    botsRef.doc("bot_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    botsRef.doc("bot_1").update({
      primaryColorHex: "NOT_HEX",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertSucceeds(botsRef.doc("bot_1").delete());
});

test("Cross-user isolation: Bob cannot read or mutate Alice's bots, messages, hosts, or files", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/bots`)
      .doc("bot_1")
      .set(sampleBotPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/bots`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/bots`).doc("bot_1").get()
  );
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/bots`)
      .doc("bot_1")
      .update({ name: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/bots`).doc("bot_1").delete()
  );
});

test("Bot messages: requires parent bot existence, supports optional fields omitted, and enforces updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const botsRef = aliceDb.collection(`users/${ALICE_UID}/bots`);

  // Orphaned message without parent bot must fail
  await assertFails(
    botsRef
      .doc("missing_bot")
      .collection("messages")
      .doc("msg_1")
      .set(sampleMessagePayload(ALICE_UID, "missing_bot"))
  );

  await assertSucceeds(botsRef.doc("bot_1").set(sampleBotPayload(ALICE_UID)));

  // Valid message with optional parsedInstruction & instructionGoal omitted (filtered out nulls)
  const minimalMsg = {
    userId: ALICE_UID,
    botId: "bot_1",
    sender: "USER",
    rawText: "hello there!",
    instructionSteps: [],
    expression: "HAPPY",
    status: "IDLE",
    attachedFiles: [],
    linkedPaths: [],
    takeoverSuggested: false,
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  };
  await assertSucceeds(
    botsRef.doc("bot_1").collection("messages").doc("msg_1").set(minimalMsg)
  );

  // Valid partial update transitioning expression THINKING -> UH_HUH
  await assertSucceeds(
    botsRef
      .doc("bot_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        parsedInstruction: "Execute clean check on host.",
        instructionGoal: "Check host status",
        instructionSteps: ["Ping gateway", "Report status"],
        expression: "UH_HUH",
        status: "COMPLETED",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Future timestamp must fail
  await assertFails(
    botsRef
      .doc("bot_1")
      .collection("messages")
      .doc("msg_2")
      .set(
        sampleMessagePayload(ALICE_UID, "bot_1", {
          createdAt: new Date(Date.now() + 60000),
          updatedAt: new Date(Date.now() + 60000),
        })
      )
  );
});

test("Hosts and LinkedFiles: CRUD and validation parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/hosts`).doc("host_1").set({
      userId: ALICE_UID,
      label: "Windows Studio PC",
      baseUrl: "http://192.168.68.53:18789",
      lanUrl: "http://192.168.68.53:18789",
      tailscaleUrl: "http://100.91.98.120:18789",
      connectionMode: "LAN",
      controlUrl: "",
      gatewayToken: "secret-token-123",
      aiMode: "HYBRID_GEMINI_OPENCLAW",
      defaultLlmProvider: "VENICE_AI",
      defaultLlmModel: "venice/llama-3.3-70b",
      localLlmEndpoint: "http://127.0.0.1:11434/v1",
      autoParseCasual: true,
      lastStatus: "UNVERIFIED",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/hosts`).doc("host_1").update({
      lastStatus: "ONLINE",
      connectionMode: "TAILSCALE",
      baseUrl: "http://100.91.98.120:18789",
      defaultLlmProvider: "LOCAL_LLM",
      defaultLlmModel: "ollama/qwen2.5:14b",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/hosts`).doc("host_1").update({
      connectionMode: "PUBLIC_INTERNET",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/linkedFiles`).doc("file_1").set({
      userId: ALICE_UID,
      displayName: "report_q4.pdf",
      uriOrPath: "C:\\Users\\Demo\\Documents\\report_q4.pdf",
      linkType: "HOST_PATH",
      mimeOrExt: "application/pdf",
      sizeBytes: 204800,
      notes: "Quarterly summary for OpenClaw review",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/linkedFiles`).doc("file_docx").set({
      userId: ALICE_UID,
      displayName: "spec_sheet.docx",
      uriOrPath: "content://com.android.providers.downloads/document/42",
      linkType: "ANDROID_ATTACHMENT",
      mimeOrExt: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      sizeBytes: 48120,
      notes: "Word document attachment",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Negative sizeBytes must fail
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/linkedFiles`).doc("file_bad").set({
      userId: ALICE_UID,
      displayName: "bad.txt",
      uriOrPath: "/tmp/bad.txt",
      linkType: "HOST_PATH",
      mimeOrExt: "text/plain",
      sizeBytes: -5,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("ChatRooms & bot missions: mission update, room CRUD, room messages, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Bot created with mission & lastMessagePreview + mission update anytime
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bots`).doc("bot_volt").set(
      sampleBotPayload(ALICE_UID, {
        name: "Volt-50",
        archetype: "RETRO_ROBOT",
        mission: "I help translate documents!",
        lastMessagePreview: "Ready to translate!",
      })
    )
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bots`).doc("bot_volt").update({
      mission: "I translate JP & EN technical specs!",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // ChatRoom CRUD with maxPingPongTurns
  const roomsRef = aliceDb.collection(`users/${ALICE_UID}/chatRooms`);
  await assertSucceeds(
    roomsRef.doc("room_cafe").set({
      userId: ALICE_UID,
      title: "Cafe Translation Squad",
      topic: "Translate & critique documents together",
      botIds: ["bot_volt", "bot_maru"],
      maxPingPongTurns: 4,
      lastMessagePreview: "Volt-50 & Prof. Maru are at the cafe!",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bots`).doc("bot_volt").update({
      sessionKey: "agent:translator_volt:main",
      toolPermissions: ["read", "write", "spawn"],
      workspacePath: "~/workspaces/translator",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    roomsRef.doc("room_bad_turns").set({
      userId: ALICE_UID,
      title: "Runaway Loop Room",
      topic: "Too many turns",
      botIds: ["bot_volt", "bot_maru"],
      maxPingPongTurns: 50,
      lastMessagePreview: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Room message requires existing parent room
  await assertFails(
    roomsRef.doc("missing_room").collection("messages").doc("m1").set({
      userId: ALICE_UID,
      roomId: "missing_room",
      senderType: "USER",
      senderBotName: "You",
      rawText: "Translate this!",
      expression: "HAPPY",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    roomsRef.doc("room_cafe").collection("messages").doc("m1").set({
      userId: ALICE_UID,
      roomId: "room_cafe",
      senderType: "BOT",
      senderBotId: "bot_volt",
      senderBotName: "Volt-50",
      replyToBotName: "Prof. Maru",
      rawText: "Here is the translated draft!",
      expression: "UH_HUH",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user isolation on chatRooms
  await assertFails(bobDb.collection(`users/${ALICE_UID}/chatRooms`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/chatRooms/room_cafe/messages`).get()
  );
});

test("GeneralChats: owner CRUD, pin toggle, wheelModel validation, parent requirement, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/generalChats`).get());

  const chatsRef = aliceDb.collection(`users/${ALICE_UID}/generalChats`);

  // Orphaned general message without parent thread must fail
  await assertFails(
    chatsRef.doc("missing_chat").collection("messages").doc("gm_1").set({
      userId: ALICE_UID,
      chatId: "missing_chat",
      sender: "USER",
      rawText: "Quick question about Linux permissions",
      modelLabel: "OpenClaw Default",
      status: "IDLE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    chatsRef.doc("chat_1").set({
      userId: ALICE_UID,
      title: "Creating Hybrid Cultures in CK3",
      pinned: false,
      lastMessagePreview: "",
      wheelModel: "OPENCLAW_DEFAULT",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Pin toggle + wheelModel partial update
  await assertSucceeds(
    chatsRef.doc("chat_1").update({
      pinned: true,
      wheelModel: "OPENCLAW_LOCAL",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Invalid wheelModel rejected
  await assertFails(
    chatsRef.doc("chat_bad").set({
      userId: ALICE_UID,
      title: "Bad Model",
      pinned: false,
      lastMessagePreview: "",
      wheelModel: "AI_STUDIO_BILLABLE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // General message create & update
  await assertSucceeds(
    chatsRef.doc("chat_1").collection("messages").doc("gm_1").set({
      userId: ALICE_UID,
      chatId: "chat_1",
      sender: "BOT",
      rawText: "Thinking...",
      modelLabel: "OpenClaw Local",
      status: "DISPATCHED",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    chatsRef.doc("chat_1").collection("messages").doc("gm_1").update({
      rawText: "Here is how hybrid cultures work in CK3...",
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Queries matching Kotlin repository
  await assertSucceeds(chatsRef.orderBy("updatedAt", "desc").get());
  await assertSucceeds(chatsRef.doc("chat_1").collection("messages").orderBy("createdAt", "asc").get());

  // Cross-user isolation
  await assertFails(bobDb.collection(`users/${ALICE_UID}/generalChats`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/generalChats/chat_1/messages`).get());
  await assertFails(chatsRef.doc("chat_1").collection("messages").doc("gm_1").update({
    userId: BOB_UID,
    updatedAt: new Date(Date.now() - 200),
  }));

  await assertSucceeds(chatsRef.doc("chat_1").delete());
});

test("GeneratedMedia & UserVeniceSettings: CRUD, mediaType bounds, message media fields, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  // Unauthenticated denied
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/generatedMedia`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/settings`).get());

  // Valid image & video items in generatedMedia
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/generatedMedia`).doc("media_img_1").set({
      userId: ALICE_UID,
      mediaUrl: "https://cdn.example.com/render_cha_chaan_teng.png",
      mediaType: "IMAGE",
      prompt: "Cozy neon cafe interior with cartoon cups",
      modelUsed: "claude-sonnet-4-6",
      durationSec: 0,
      sourceBotName: "Bimbo",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/generatedMedia`).doc("media_vid_1").set({
      userId: ALICE_UID,
      mediaUrl: "http://192.168.1.50:18789/media/clip_01.mp4",
      mediaType: "VIDEO",
      prompt: "Animate waving mascot with warm glow",
      modelUsed: "venice-video-i2v",
      durationSec: 6,
      sourceBotName: "Volt-50",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/generatedMedia`).doc("media_aud_1").set({
      userId: ALICE_UID,
      mediaUrl: "http://192.168.1.50:18789/media/voice_note.mp3",
      mediaType: "AUDIO",
      prompt: "Read translated summary aloud",
      modelUsed: "tts-1",
      durationSec: 18,
      sourceBotName: "Volt-50",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/generatedMedia`).doc("media_doc_1").set({
      userId: ALICE_UID,
      mediaUrl: "http://192.168.1.50:18789/media/edited_report.docx",
      mediaType: "DOCUMENT",
      prompt: "Edited report.docx",
      modelUsed: "claude-sonnet-4-6",
      durationSec: 0,
      sourceBotName: "Bimbo",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject invalid mediaType & negative duration
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/generatedMedia`).doc("media_bad").set({
      userId: ALICE_UID,
      mediaUrl: "https://cdn.example.com/bad.bin",
      mediaType: "AUDIO_ONLY",
      prompt: "Bad",
      modelUsed: "unknown",
      durationSec: 0,
      sourceBotName: "Bimbo",
      createdAt: now,
      updatedAt: now,
    })
  );

  // UserVenicePreferences create & partial update
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/settings`).doc("venice").set({
      userId: ALICE_UID,
      globalVeniceModel: "claude-sonnet-4-6",
      veniceApiKeyOverride: "venice-test-override-key",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/settings`).doc("venice").update({
      globalVeniceModel: "qwen-2.5-coder-32b",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // BotMessage update with inline media fields
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bots`).doc("bot_media").set(sampleBotPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/bots/bot_media/messages`)
      .doc("msg_media")
      .set(sampleMessagePayload(ALICE_UID, "bot_media"))
  );
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/bots/bot_media/messages`)
      .doc("msg_media")
      .update({
        rawText: "Here is your generated image!",
        status: "COMPLETED",
        expression: "UH_HUH",
        mediaUrl: "https://cdn.example.com/out.png",
        mediaType: "IMAGE",
        mediaPrompt: "Cyberpunk teacup",
        mediaModel: "claude-sonnet-4-6",
        updatedAt: new Date(Date.now() - 200),
      })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection(`users/${ALICE_UID}/generatedMedia`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/settings`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/settings`).doc("venice").set({
      userId: ALICE_UID,
      globalVeniceModel: "hijack",
      veniceApiKeyOverride: "",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("ContextChats: owner CRUD, docRefs/docLabels updates, citedDocs, parent check, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/contextChats`).get());

  const ctxRef = aliceDb.collection(`users/${ALICE_UID}/contextChats`);

  // Orphaned context message must fail
  await assertFails(
    ctxRef.doc("missing_ctx").collection("messages").doc("cm_1").set({
      userId: ALICE_UID,
      chatId: "missing_ctx",
      sender: "USER",
      rawText: "What does section 3 say?",
      modelLabel: "OpenClaw Default",
      status: "IDLE",
      citedDocs: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ctxRef.doc("ctx_1").set({
      userId: ALICE_UID,
      title: "Q4 Lease Review",
      pinned: false,
      lastMessagePreview: "",
      wheelModel: "OPENCLAW_DEFAULT",
      docLabels: ["lease.pdf"],
      docRefs: ["content://docs/lease.pdf"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ctxRef.doc("ctx_1").update({
      pinned: true,
      docLabels: ["lease.pdf", "addendum.docx"],
      docRefs: ["content://docs/lease.pdf", "https://drive.google.com/file/d/abc123/view"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    ctxRef.doc("ctx_1").collection("messages").doc("cm_1").set({
      userId: ALICE_UID,
      chatId: "ctx_1",
      sender: "BOT",
      rawText: "Inspecting lease.pdf...",
      modelLabel: "OpenClaw Default",
      status: "DISPATCHED",
      citedDocs: ["lease.pdf"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ctxRef.doc("ctx_1").collection("messages").doc("cm_1").update({
      rawText: "Per [Source: lease.pdf], the renewal notice period is 60 days.",
      status: "COMPLETED",
      citedDocs: ["lease.pdf"],
      updatedAt: new Date(Date.now() - 300),
    })
  );

  await assertSucceeds(ctxRef.orderBy("updatedAt", "desc").get());
  await assertSucceeds(ctxRef.doc("ctx_1").collection("messages").orderBy("createdAt", "asc").get());

  await assertFails(bobDb.collection(`users/${ALICE_UID}/contextChats`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/contextChats/ctx_1/messages`).get());
  await assertSucceeds(ctxRef.doc("ctx_1").delete());
});

