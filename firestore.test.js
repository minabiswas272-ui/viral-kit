const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_123";
const BOB_UID = "bob_creator_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validKitPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId,
    title: "3 Editing Tricks for 10x Retention",
    mediaLabel: "reel_edit.mp4 (4.2 MB)",
    mediaKind: "video",
    language: "Hinglish",
    platform: "Instagram Reels",
    modelUsed: "gemini-4-hydrogen",
    viralScore: 86,
    diagnosisCritique:
      "First 2 seconds move too slowly before the visual pattern interrupt. Trim the breath pause and open with movement.",
    first30sStrategy:
      "Stack an open curiosity loop at 0:03 and resolve only at 0:24 while showing split-screen before/after cuts.",
    curiosityHook: "99% creators yeh ek cut miss karte hain—and it kills their reach.",
    contrarianHook: "Stop buying expensive mics—your retention problem is actually in frame 1.",
    directValueHook: "3 seconds mein apni Reel retention double karo using this pacing rule.",
    pacingSegments: [
      {
        timestamp: "0:00-0:03",
        bRollSuggestion: "Fast whip-pan onto timeline screen",
        onScreenText: "STOP SCROLLING",
        cutAndSfx: "Hard jump cut + bass whoosh SFX",
      },
    ],
    instagramCaption: "Save this pacing checklist before your next Reel! Comment 'HOOK' for templates.",
    hashtags: ["#ReelsGrowth", "#CreatorTipsIndia", "#VideoRetention", "#HinglishCreators", "#ShortFormStrategy"],
    youtubeTitle: "Fix Your Reels Retention in 3 Cuts",
    youtubeDescription: "Learn the exact 3-second hook and B-roll pacing blueprint for Instagram Reels and YouTube Shorts.",
    thumbnailIdeas: ["FIX THIS CUT", "86% DROP OFF", "3 SECOND RULE"],
    rawJson: "{\"viralScore\":86}",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated users cannot read or write profiles, kits, or chat messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("kits").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("kits")
      .doc("kit_1")
      .set(validKitPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chatMessages").get());
});

test("Creator profile create, read, partial update, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creator",
      email: "alice@example.com",
      preferredLanguage: "Bengali",
      preferredPlatform: "Instagram Reels",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user read & list denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Partial update allowed (including FCM token and subscribed niche topic)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Hinglish",
      fcmToken: "fcm_sample_token_abc123",
      fcmNicheTopic: "niche_indian_tech_ai_creators",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user FCM update denied and oversized token rejected
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "hijacked_token",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "x".repeat(520),
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Tampered createdAt or invalid language rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Spanish",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("ViralGrowthKit create, list, update, delete, and schema/bounds enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Valid create (NO @DocumentId 'id' field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("kits").doc("kit_001").set(validKitPayload(ALICE_UID))
  );

  // List subcollection succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("kits").get());

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("kits").doc("kit_001").update({
      title: "Updated Hook Title",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject 'id' ghost field in payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("kits")
      .doc("kit_002")
      .set(validKitPayload(ALICE_UID, { id: "kit_002" }))
  );

  // Reject out-of-range viralScore or future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("kits")
      .doc("kit_003")
      .set(validKitPayload(ALICE_UID, { viralScore: 150 }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("kits")
      .doc("kit_004")
      .set(validKitPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Cross-user read/write/delete denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("kits").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("kits").doc("kit_001").delete());

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("kits").doc("kit_001").delete());
});

test("ChatMessageRecord create, list, immutability, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Give me 3 Bengali hooks for a street food Reel.",
      modelId: "gemini-4-hydrogen",
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_2").set({
      userId: ALICE_UID,
      role: "model",
      text: "Here are 3 scroll-stopping Bengali hooks...",
      modelId: "gemini-4-carbon",
      thinkingSummary: "Evaluated retention triggers for Kolkata street food niche.",
      groundingSources: [{ title: "YouTube Trends", uri: "https://trends.google.com" }],
      createdAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chatMessages").get());
  // Chat messages are immutable (update forbidden)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").update({ text: "Tampered" })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").delete());
});

test("TaggedContentIdea create, list, immutability, and ViralGrowthKit trendingTopics persistence", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("kits")
      .doc("kit_tagged_1")
      .set(
        validKitPayload(ALICE_UID, {
          trendingTopics: ["AI Video Editing", "Shorts Loop Retention"],
          trendingTagSources: [{ title: "Google Trends", uri: "https://trends.google.com" }],
        })
      )
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("kits")
      .doc("kit_tagged_1")
      .update({
        trendingTopics: ["CapCut Speed Ramp", "Creator Automation"],
        updatedAt: new Date(Date.now() - 200),
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("taggedIdeas").doc("idea_1").set({
      userId: ALICE_UID,
      ideaText: "Show 3 budget lighting setups for solo creators recording Reels at home.",
      sourceType: "voice",
      industryNiche: "Indian Tech & AI Creators",
      language: "Hinglish",
      platform: "Instagram Reels",
      extractedKeywords: ["budget lighting", "solo creators", "Reels setup"],
      trendingTopics: ["Desk Setup 2026", "Zero Budget Studio", "RGB Key Light"],
      groundingRationale: "Budget creator studio builds are trending across tech Reels.",
      groundingSources: [{ title: "Creator Insider", uri: "https://example.com/studio" }],
      createdAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("taggedIdeas").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("taggedIdeas").get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taggedIdeas")
      .doc("idea_1")
      .update({ ideaText: "Tampered" })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("taggedIdeas").doc("idea_1").delete()
  );
});

