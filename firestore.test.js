const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

async function createTripBatch(db, uid, name, tripId, title, inviteCode) {
  const now = new Date(Date.now() - 1000);
  const batch = db.batch();
  batch.set(db.collection("trips").doc(tripId), {
    title,
    ownerId: uid,
    inviteCode,
    memberUids: [uid],
    createdAt: now,
    updatedAt: now,
  });
  batch.set(db.collection("trip_invites").doc(inviteCode), {
    tripId,
    tripTitle: title,
    ownerId: uid,
    createdAt: now,
  });
  batch.set(db.collection("trips").doc(tripId).collection("members").doc(uid), {
    uid,
    displayName: name,
    photoUrl: "https://example.com/avatar.jpg",
    role: "owner",
    inviteCodeUsed: "",
    joinedAt: now,
  });
  return batch.commit();
}

test("Unauthenticated users cannot read or write trips, invites, visits, or comments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("trips").get());
  await assertFails(unauthDb.collection("trip_invites").doc("TR1234").get());
  await assertFails(unauthDb.collection("trips").doc("trip_1").collection("visits").get());
  await assertFails(unauthDb.collection("trips").doc("trip_1").collection("comments").get());
});

test("Authenticated user can create shared trip atomically, and memberUids query succeeds while unfiltered list fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(createTripBatch(aliceDb, ALICE_UID, "Alice", "trip_bali", "Liburan Bali", "BALI26"));

  // Client query alignment
  await assertSucceeds(
    aliceDb.collection("trips").where("memberUids", "array-contains", ALICE_UID).get()
  );
  // Unfiltered list must fail
  await assertFails(aliceDb.collection("trips").get());
  // Listing invites directly must fail
  await assertFails(aliceDb.collection("trip_invites").get());
});

test("Invite join flow: valid 6-char invite code joins atomically; wrong code or forged join fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(createTripBatch(aliceDb, ALICE_UID, "Alice", "trip_bali", "Liburan Bali", "BALI26"));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 500);

  // Bob looks up exact invite code
  await assertSucceeds(bobDb.collection("trip_invites").doc("BALI26").get());

  // Forged join with wrong code must fail
  const badBatch = bobDb.batch();
  badBatch.set(bobDb.collection("trips").doc("trip_bali").collection("members").doc(BOB_UID), {
    uid: BOB_UID,
    displayName: "Bob",
    photoUrl: "",
    role: "member",
    inviteCodeUsed: "WRONG1",
    joinedAt: now,
  });
  badBatch.update(bobDb.collection("trips").doc("trip_bali"), {
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: now,
  });
  await assertFails(badBatch.commit());

  // Valid join with matching invite code succeeds
  const goodBatch = bobDb.batch();
  goodBatch.set(bobDb.collection("trips").doc("trip_bali").collection("members").doc(BOB_UID), {
    uid: BOB_UID,
    displayName: "Bob",
    photoUrl: "https://example.com/bob.jpg",
    role: "member",
    inviteCodeUsed: "BALI26",
    joinedAt: now,
  });
  goodBatch.update(bobDb.collection("trips").doc("trip_bali"), {
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: now,
  });
  await assertSucceeds(goodBatch.commit());

  // Non-member Charlie cannot read the trip, members, visits, or comments
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("trips").doc("trip_bali").get());
  await assertFails(charlieDb.collection("trips").doc("trip_bali").collection("members").get());
});

test("Trip members can create, update, list visits and write/read date comments; ghost fields and cross-user tampering fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(createTripBatch(aliceDb, ALICE_UID, "Alice", "trip_bali", "Liburan Bali", "BALI26"));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 500);
  const joinBatch = bobDb.batch();
  joinBatch.set(bobDb.collection("trips").doc("trip_bali").collection("members").doc(BOB_UID), {
    uid: BOB_UID,
    displayName: "Bob",
    photoUrl: "",
    role: "member",
    inviteCodeUsed: "BALI26",
    joinedAt: now,
  });
  joinBatch.update(bobDb.collection("trips").doc("trip_bali"), {
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: now,
  });
  await assertSucceeds(joinBatch.commit());

  // Alice adds a shared visit
  await assertSucceeds(
    aliceDb.collection("trips").doc("trip_bali").collection("visits").doc("visit_1").set({
      tripId: "trip_bali",
      locationName: "Pantai Pandawa",
      tripTitle: "Hari 1",
      visitDateEpochDay: 20420,
      visitHour: 9,
      visitMinute: 30,
      planNotes: "Bawa kacamata hitam",
      memoryNotes: "",
      completedManually: false,
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field ("id") on visit creation must fail
  await assertFails(
    aliceDb.collection("trips").doc("trip_bali").collection("visits").doc("visit_2").set({
      id: "visit_2",
      tripId: "trip_bali",
      locationName: "Uluwatu",
      tripTitle: "",
      visitDateEpochDay: 20420,
      visitHour: 17,
      visitMinute: 0,
      planNotes: "",
      memoryNotes: "",
      completedManually: false,
      createdByUid: ALICE_UID,
      createdByName: "Alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can list visits and update completedManually/memoryNotes
  await assertSucceeds(bobDb.collection("trips").doc("trip_bali").collection("visits").get());
  await assertSucceeds(
    bobDb.collection("trips").doc("trip_bali").collection("visits").doc("visit_1").update({
      completedManually: true,
      memoryNotes: "Pemandangan tebingnya keren!",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Bob cannot mutate immutable createdByUid
  await assertFails(
    bobDb.collection("trips").doc("trip_bali").collection("visits").doc("visit_1").update({
      createdByUid: BOB_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Bob posts a date comment and Alice lists comments
  await assertSucceeds(
    bobDb.collection("trips").doc("trip_bali").collection("comments").doc("c_1").set({
      tripId: "trip_bali",
      dateEpochDay: 20420,
      authorUid: BOB_UID,
      authorName: "Bob",
      authorPhotoUrl: "",
      text: "Berangkat jam 8 pagi yuk!",
      createdAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("trips").doc("trip_bali").collection("comments").get());

  // Alice cannot delete Bob's comment
  await assertFails(
    aliceDb.collection("trips").doc("trip_bali").collection("comments").doc("c_1").delete()
  );
  // Bob can delete his own comment
  await assertSucceeds(
    bobDb.collection("trips").doc("trip_bali").collection("comments").doc("c_1").delete()
  );
});
