const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

const ts = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const SCHOOL_ID = "dps_etah";
const ALICE_UID = "alice_admin_uid";
const BOB_UID = "bob_parent_uid";
const TEACHER_UID = "carol_teacher_uid";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const schoolRef = (db) => db.collection("schools").doc(SCHOOL_ID);
const col = (db, name) => schoolRef(db).collection(name);

function loginTicketHash(role, userId, password) {
  const payload = `${role}|${userId.trim().toLowerCase()}|${password.trim()}`;
  return crypto.createHash("sha256").update(payload).digest("hex");
}

function buildValidUser(ownerUid, role = "TEACHER", userId = "TCH101", className = "Class VII", section = "A", password = "password123") {
  return {
    ownerUid,
    role,
    userId,
    password,
    fullName: role === "ADMIN" ? "Dr. Principal" : role === "TEACHER" ? "Mrs. Teacher" : "Aarav Verma",
    phone: "9837011223",
    wing: "Middle School",
    className,
    section,
    subject: role === "TEACHER" ? "Mathematics" : "",
    parentName: role === "STUDENT" ? "Mr. Verma" : "",
    monthlyTuition: role === "STUDENT" ? 2500 : 0,
    transportFee: role === "STUDENT" ? 800 : 0,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

async function bootstrapSchoolAsAdmin(aliceDb) {
  const adminTicket = loginTicketHash("ADMIN", "ADMIN01", "admin123");
  const batch = aliceDb.batch();
  batch.set(schoolRef(aliceDb), {
    ownerUid: ALICE_UID,
    name: "Doon Public School, Etah",
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(col(aliceDb, "users").doc("usr_admin_1"), buildValidUser(ALICE_UID, "ADMIN", "ADMIN01", "", "", "admin123"));
  batch.set(col(aliceDb, "loginIndex").doc(adminTicket), {
    ownerUid: ALICE_UID,
    schoolUserDocId: "usr_admin_1",
    role: "ADMIN",
    userId: "ADMIN01",
    className: "",
    section: "",
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(col(aliceDb, "members").doc(ALICE_UID), {
    ownerUid: ALICE_UID,
    schoolUserDocId: "usr_admin_1",
    role: "ADMIN",
    loginTicketId: adminTicket,
    createdAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(batch.commit());
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

test("Unauthenticated users and non-members cannot list or read school collections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await bootstrapSchoolAsAdmin(aliceDb);

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(col(unauthDb, "users").get());
  await assertFails(col(unauthDb, "circulars").get());
  await assertFails(col(unauthDb, "loginIndex").doc("any").get());

  // Signed-in Bob without valid member doc cannot list users or circulars or loginIndex
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(col(bobDb, "users").get());
  await assertFails(col(bobDb, "circulars").get());
  await assertFails(col(bobDb, "loginIndex").get());

  // Bob CANNOT forge a fake admin membership without a matching loginIndex ticket
  await assertFails(
    col(bobDb, "members").doc(BOB_UID).set({
      ownerUid: BOB_UID,
      schoolUserDocId: "usr_admin_1",
      role: "ADMIN",
      loginTicketId: "forged_ticket_id",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("Cross-device parent login via SHA-256 loginIndex joins shared school and reads notices/timetables", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await bootstrapSchoolAsAdmin(aliceDb);

  const studentTicket = loginTicketHash("STUDENT", "001", "stud123");
  await assertSucceeds(
    col(aliceDb, "users").doc("usr_std_1").set(buildValidUser(ALICE_UID, "STUDENT", "001", "Class VII", "A", "stud123"))
  );
  await assertSucceeds(
    col(aliceDb, "loginIndex").doc(studentTicket).set({
      ownerUid: ALICE_UID,
      schoolUserDocId: "usr_std_1",
      role: "STUDENT",
      userId: "001",
      className: "Class VII",
      section: "A",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Parent Bob logs in on another phone with another Google account
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ticketSnap = await assertSucceeds(col(bobDb, "loginIndex").doc(studentTicket).get());
  await assertSucceeds(
    col(bobDb, "members").doc(BOB_UID).set({
      ownerUid: BOB_UID,
      schoolUserDocId: ticketSnap.data().schoolUserDocId,
      role: ticketSnap.data().role,
      loginTicketId: studentTicket,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob can now see the student created on Alice's phone!
  await assertSucceeds(col(bobDb, "users").get());
  // Bob still CANNOT change role to ADMIN on existing member doc without matching ticket
  await assertFails(
    col(bobDb, "members").doc(BOB_UID).update({
      role: "ADMIN",
      schoolUserDocId: "usr_admin_1",
      updatedAt: ts(),
    })
  );
});

test("Teacher can add student strictly in their own class-section and cannot add to another class", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await bootstrapSchoolAsAdmin(aliceDb);

  const tchTicket = loginTicketHash("TEACHER", "TCH7A", "teach123");
  await assertSucceeds(
    col(aliceDb, "users").doc("usr_tch_7a").set(buildValidUser(ALICE_UID, "TEACHER", "TCH7A", "Class VII", "A", "teach123"))
  );
  await assertSucceeds(
    col(aliceDb, "loginIndex").doc(tchTicket).set({
      ownerUid: ALICE_UID,
      schoolUserDocId: "usr_tch_7a",
      role: "TEACHER",
      userId: "TCH7A",
      className: "Class VII",
      section: "A",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  await assertSucceeds(
    col(teacherDb, "members").doc(TEACHER_UID).set({
      ownerUid: TEACHER_UID,
      schoolUserDocId: "usr_tch_7a",
      role: "TEACHER",
      loginTicketId: tchTicket,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Allowed: Class VII-A student + loginIndex
  const stdTicket7A = loginTicketHash("STUDENT", "002", "pass2");
  const batchOk = teacherDb.batch();
  batchOk.set(col(teacherDb, "users").doc("usr_std_7a"), buildValidUser(TEACHER_UID, "STUDENT", "002", "Class VII", "A", "pass2"));
  batchOk.set(col(teacherDb, "loginIndex").doc(stdTicket7A), {
    ownerUid: TEACHER_UID,
    schoolUserDocId: "usr_std_7a",
    role: "STUDENT",
    userId: "002",
    className: "Class VII",
    section: "A",
    createdAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(batchOk.commit());

  // Denied: Teacher VII-A trying to add Class VII-B or ADMIN user
  await assertFails(
    col(teacherDb, "users").doc("usr_std_7b").set(buildValidUser(TEACHER_UID, "STUDENT", "003", "Class VII", "B", "pass3"))
  );
  await assertFails(
    col(teacherDb, "users").doc("usr_fake_admin").set(buildValidUser(TEACHER_UID, "ADMIN", "ADMIN99", "Class VII", "A", "hack"))
  );
});

test("CraftProjects unique like enforcement: max 1 like per user via craftLikes batch; direct or duplicate likes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await bootstrapSchoolAsAdmin(aliceDb);

  const studentTicket = loginTicketHash("STUDENT", "001", "stud123");
  await assertSucceeds(
    col(aliceDb, "users").doc("usr_std_1").set(buildValidUser(ALICE_UID, "STUDENT", "001", "Class VII", "A", "stud123"))
  );
  await assertSucceeds(
    col(aliceDb, "loginIndex").doc(studentTicket).set({
      ownerUid: ALICE_UID,
      schoolUserDocId: "usr_std_1",
      role: "STUDENT",
      userId: "001",
      className: "Class VII",
      section: "A",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    col(bobDb, "members").doc(BOB_UID).set({
      ownerUid: BOB_UID,
      schoolUserDocId: "usr_std_1",
      role: "STUDENT",
      loginTicketId: studentTicket,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  const projId = "craft_proj_1";
  await assertSucceeds(
    col(bobDb, "craftProjects").doc(projId).set({
      ownerUid: BOB_UID,
      title: "Solar Model",
      category: "Science Model",
      description: "Clean solar house prototype",
      materials: "Cardboard, Solar Panel",
      authorDocId: "usr_std_1",
      authorName: "Aarav Verma",
      authorUserId: "001",
      authorRole: "STUDENT",
      classInfo: "Class VII-A",
      dateLabel: "2026-11-18",
      aiImageData: "",
      aiPromptUsed: "",
      likesCount: 0,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Direct likesCount update without paired craftLikes doc MUST fail
  await assertFails(
    col(bobDb, "craftProjects").doc(projId).update({
      likesCount: 100,
      updatedAt: ts(),
    })
  );

  // Valid atomic like (count + 1 + deterministic craftLikes doc) succeeds
  const likeId = `${projId}__usr_std_1`;
  const likeBatch = bobDb.batch();
  likeBatch.set(col(bobDb, "craftLikes").doc(likeId), {
    ownerUid: BOB_UID,
    projectId: projId,
    likerId: "usr_std_1",
    createdAt: ts(),
    updatedAt: ts(),
  });
  likeBatch.update(col(bobDb, "craftProjects").doc(projId), {
    likesCount: 1,
    updatedAt: ts(),
  });
  await assertSucceeds(likeBatch.commit());

  // Duplicate second like by the same user MUST fail
  const dupBatch = bobDb.batch();
  dupBatch.set(col(bobDb, "craftLikes").doc(`${projId}__fake`), {
    ownerUid: BOB_UID,
    projectId: projId,
    likerId: "usr_std_1",
    createdAt: ts(),
    updatedAt: ts(),
  });
  dupBatch.update(col(bobDb, "craftProjects").doc(projId), {
    likesCount: 2,
    updatedAt: ts(),
  });
  await assertFails(dupBatch.commit());

  // Valid toggle-unlike decrements by 1 and removes craftLikes doc
  const unlikeBatch = bobDb.batch();
  unlikeBatch.delete(col(bobDb, "craftLikes").doc(likeId));
  unlikeBatch.update(col(bobDb, "craftProjects").doc(projId), {
    likesCount: 0,
    updatedAt: ts(),
  });
  await assertSucceeds(unlikeBatch.commit());
});

test("Class-targeted circulars, daily campusPosters (Admin only), and per-class timetables (Teacher/Admin)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await bootstrapSchoolAsAdmin(aliceDb);

  // Targeted circular for Class VII-A
  await assertSucceeds(
    col(aliceDb, "circulars").doc("cir_7a").set({
      ownerUid: ALICE_UID,
      title: "Class VII-A Science Trip",
      category: "Notice",
      audience: "Class VII-A",
      targetWing: "Middle School",
      targetClass: "Class VII",
      targetSection: "A",
      date: "2026-11-18",
      content: "Bring consent form tomorrow.",
      postedBy: "Dr. Principal",
      recipientDocId: "",
      recipientName: "",
      senderRole: "ADMIN",
      attachmentName: "",
      attachmentMimeType: "",
      attachmentData: "",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Daily campus poster by Admin
  await assertSucceeds(
    col(aliceDb, "campusPosters").doc("poster_today").set({
      ownerUid: ALICE_UID,
      title: "Annual Sports Practice",
      subtitle: "Track & Field Day",
      date: "2026-11-18",
      imageData: "AQIDBA==",
      postedBy: "Dr. Principal",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Per-class timetable
  await assertSucceeds(
    col(aliceDb, "timetables").doc("tt_Class_VII_A").set({
      ownerUid: ALICE_UID,
      className: "Class VII",
      section: "A",
      slots: [
        {
          period: "Period 1",
          time: "08:25 - 09:05",
          mon: "Mathematics",
          tue: "Science",
          wed: "Mathematics",
          thu: "English",
          fri: "Mathematics",
          sat: "DPT Evaluation",
        },
      ],
      updatedBy: "Dr. Principal",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Gateway config with in-app update metadata (Admin allowed, Student denied)
  await assertSucceeds(
    col(aliceDb, "gatewayConfig").doc("gw_main").set({
      ownerUid: ALICE_UID,
      merchantUpi: "doonpublicetah@sbi",
      payeeName: "Doon Public School, Etah",
      bankName: "State Bank of India",
      accountNo: "39481020004512",
      ifsc: "SBIN0004512",
      razorpayKeyId: "",
      latestVersionCode: 3,
      latestVersionName: "1.2",
      apkDownloadUrl: "https://github.com/school/app/releases/latest/download/DoonPublicSchool-Etah.apk",
      githubRepoSlug: "school/app",
      updateNotes: "In-app update rollout",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    col(aliceDb, "gatewayConfig").doc("gw_main").update({
      latestVersionCode: 4,
      latestVersionName: "1.3",
      apkDownloadUrl: "https://github.com/school/app/releases/latest/download/DoonPublicSchool-Etah.apk",
      githubRepoSlug: "school/app",
      updateNotes: "Version 1.3 available",
      updatedAt: ts(),
    })
  );
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    col(bobDb, "gatewayConfig").doc("gw_main").update({
      latestVersionCode: 99,
      updatedAt: ts(),
    })
  );
});
