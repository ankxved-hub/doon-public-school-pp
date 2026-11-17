const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const col = (db, uid, name) => db.collection("accounts").doc(uid).collection(name);

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

function buildValidUser(ownerUid, role = "TEACHER", userId = "TCH101") {
  return {
    ownerUid,
    role,
    userId,
    password: "password123",
    fullName: "Mr. Rajesh Sharma",
    phone: "9837011223",
    wing: "Upper School",
    className: "Class X",
    section: "A",
    subject: "Mathematics",
    parentName: "",
    monthlyTuition: 0,
    transportFee: 0,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

test("Unauthenticated user cannot read or list any account subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(col(unauthDb, ALICE_UID, "users").get());
  await assertFails(col(unauthDb, ALICE_UID, "users").doc("usr_1").get());
  await assertFails(col(unauthDb, ALICE_UID, "dptDaily").get());
});

test("Authenticated user can create and list own users subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").doc("usr_tch_1").set(buildValidUser(ALICE_UID))
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").get()
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").where("ownerUid", "==", ALICE_UID).get()
  );
});

test("Authenticated user cannot list or read another user's subcollections", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(col(bobDb, ALICE_UID, "users").get());
  await assertFails(col(bobDb, ALICE_UID, "dptDaily").get());
  await assertFails(col(bobDb, ALICE_UID, "payments").get());
});

test("Profile edit: owner can update fullName, phone, and password in users subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").doc("usr_std_1").set(buildValidUser(ALICE_UID, "ADMIN", "ADMIN01"))
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").doc("usr_std_1").update({
      fullName: "Sudhakar Ojha (Principal)",
      phone: "9998887776",
      password: "newSecret99",
      updatedAt: ts(),
    })
  );
});

test("Shadow update test: adding ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").doc("usr_std_2").set(buildValidUser(ALICE_UID, "STUDENT", "DPS2026-002"))
  );
  await assertFails(
    col(aliceDb, ALICE_UID, "users").doc("usr_std_2").update({
      phone: "9998887776",
      isVerified: true,
      updatedAt: ts(),
    })
  );
});

test("Cross-user isolation: Bob cannot read, create, or update inside Alice's accounts subtree", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "users").doc("usr_alice").set(buildValidUser(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(col(bobDb, ALICE_UID, "users").doc("usr_alice").get());
  await assertFails(
    col(bobDb, ALICE_UID, "users").doc("usr_spoof").set(buildValidUser(BOB_UID))
  );
  await assertFails(
    col(bobDb, ALICE_UID, "users").doc("usr_alice").update({
      phone: "0000000000",
      password: "hacked",
      updatedAt: ts(),
    })
  );
});

test("Terminal state locking: Verified payment cannot be modified", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "payments").doc("pay_1").set({
      ownerUid: ALICE_UID,
      studentDocId: "usr_std_1",
      studentName: "Aarav Verma",
      admissionNo: "DPS2026-001",
      className: "Class X-A",
      feeItemIds: ["fee_1"],
      feeSummary: "Tuition Nov",
      amount: 2800,
      mode: "UPI",
      utrOrRef: "628914509812",
      upiApp: "Google Pay",
      status: "PENDING",
      receiptNo: "",
      rejectReason: "",
      verifiedBy: "",
      verifiedAtStr: "",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "payments").doc("pay_1").update({
      status: "VERIFIED",
      receiptNo: "DPS-RCP-2026-0102",
      verifiedBy: "Mr. Amit Gupta",
      verifiedAtStr: "2026-11-15",
      updatedAt: ts(),
    })
  );
  await assertFails(
    col(aliceDb, ALICE_UID, "payments").doc("pay_1").update({
      status: "REJECTED",
      rejectReason: "Tamper",
      updatedAt: ts(),
    })
  );
});

test("dptDaily upsert pattern: subcollection list + create + EXAM mode + maxMarks 100 + attachments succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docId = `${ALICE_UID}_2026_11_17_Class_X_A_Mathematics_DPT`;

  await assertSucceeds(col(aliceDb, ALICE_UID, "dptDaily").get());

  await assertSucceeds(
    col(aliceDb, ALICE_UID, "dptDaily").doc(docId).set({
      ownerUid: ALICE_UID,
      date: "2026-11-17",
      className: "Class X",
      section: "A",
      subject: "Mathematics",
      assessmentType: "DPT",
      held: true,
      topic: "Quadratic Equations",
      maxMarks: 100,
      notes: "Exercise 4.2 Q1-Q5",
      reason: "",
      attachmentName: "quadratic_sheet.jpg",
      attachmentMimeType: "image/jpeg",
      attachmentData: "AQIDBA==",
      teacherId: "TCH101",
      teacherName: "Mr. Rajesh Sharma",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    col(aliceDb, ALICE_UID, "dptDaily").doc(docId).update({
      assessmentType: "EXAM",
      held: true,
      topic: "Unit Assessment",
      maxMarks: 80,
      notes: "Full syllabus chapters 1-4",
      reason: "",
      attachmentName: "unit_paper.pdf",
      attachmentMimeType: "application/pdf",
      attachmentData: "JVBERi0xLjQK",
      teacherId: "TCH101",
      teacherName: "Mr. Rajesh Sharma",
      updatedAt: ts(),
    })
  );

  // Negative validations
  await assertFails(
    col(aliceDb, ALICE_UID, "dptDaily").doc(docId).update({
      assessmentType: "DPT",
      held: true,
      topic: "Over Limit",
      maxMarks: 101,
      notes: "",
      reason: "",
      attachmentName: "",
      attachmentMimeType: "",
      attachmentData: "",
      teacherId: "TCH101",
      teacherName: "Mr. Rajesh Sharma",
      updatedAt: ts(),
    })
  );
  await assertFails(
    col(aliceDb, ALICE_UID, "dptDaily").doc(docId).update({
      assessmentType: "QUIZ",
      held: true,
      topic: "Bad Type",
      maxMarks: 20,
      notes: "",
      reason: "",
      attachmentName: "",
      attachmentMimeType: "",
      attachmentData: "",
      teacherId: "TCH101",
      teacherName: "Mr. Rajesh Sharma",
      updatedAt: ts(),
    })
  );
  await assertFails(
    col(aliceDb, ALICE_UID, "dptDaily").doc(docId).update({
      held: true,
      topic: "Invalid",
      maxMarks: -5,
      notes: "",
      reason: "",
      teacherId: "TCH101",
      teacherName: "Mr. Rajesh Sharma",
      updatedAt: ts(),
    })
  );
  await assertFails(
    col(aliceDb, ALICE_UID, "dptDaily").doc(docId).update({
      date: "2026-11-18",
      updatedAt: ts(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(col(bobDb, ALICE_UID, "dptDaily").doc(docId).get());
  await assertFails(
    col(bobDb, ALICE_UID, "dptDaily").doc(`${BOB_UID}_fake`).set({
      ownerUid: BOB_UID,
      date: "2026-11-17",
      className: "Class X",
      section: "A",
      subject: "Mathematics",
      assessmentType: "DPT",
      held: true,
      topic: "Spoof",
      maxMarks: 20,
      notes: "",
      reason: "",
      attachmentName: "",
      attachmentMimeType: "",
      attachmentData: "",
      teacherId: "TCH101",
      teacherName: "Hacker",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("circulars: teacher targeted notice with photo/document attachment succeeds and rejects invalid senderRole", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docId = `${ALICE_UID}_notice_1`;
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "circulars").doc(docId).set({
      ownerUid: ALICE_UID,
      title: "Parent Meeting for Aarav",
      category: "Personal Notice",
      audience: "Aarav Verma (Class X-A)",
      date: "2026-11-17",
      content: "Please bring signed lab manual tomorrow.",
      postedBy: "Mr. Rajesh Sharma",
      recipientDocId: "usr_std_1",
      recipientName: "Aarav Verma",
      senderRole: "TEACHER",
      attachmentName: "ptm_slip.pdf",
      attachmentMimeType: "application/pdf",
      attachmentData: "JVBERi0xLjQK",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertFails(
    col(aliceDb, ALICE_UID, "circulars").doc(`${ALICE_UID}_notice_bad`).set({
      ownerUid: ALICE_UID,
      title: "Spoof Student Notice",
      category: "Notice",
      audience: "All",
      date: "2026-11-17",
      content: "Unauthorized role",
      postedBy: "Student",
      recipientDocId: "",
      recipientName: "",
      senderRole: "STUDENT",
      attachmentName: "",
      attachmentMimeType: "",
      attachmentData: "",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("dptWeekly, attendance, and termMarks subcollection list + create + partial update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // dptWeekly
  const weeklyId = `${ALICE_UID}_Week_1_Class_X_A_Mathematics_std1`;
  await assertSucceeds(col(aliceDb, ALICE_UID, "dptWeekly").get());
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "dptWeekly").doc(weeklyId).set({
      ownerUid: ALICE_UID,
      week: "Week 1 (Nov 2026)",
      className: "Class X",
      section: "A",
      subject: "Mathematics",
      studentDocId: "std1",
      studentName: "Aarav Verma",
      admissionNo: "DPS2026-001",
      scored: 18,
      maxMarks: 20,
      teacherId: "TCH101",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "dptWeekly").doc(weeklyId).update({
      scored: 19,
      maxMarks: 20,
      teacherId: "TCH101",
      updatedAt: ts(),
    })
  );

  // attendance
  const attId = `${ALICE_UID}_att_2026-11-17_std1`;
  await assertSucceeds(col(aliceDb, ALICE_UID, "attendance").get());
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "attendance").doc(attId).set({
      ownerUid: ALICE_UID,
      date: "2026-11-17",
      className: "Class X",
      section: "A",
      studentDocId: "std1",
      status: "Present",
      markedBy: "TCH101",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "attendance").doc(attId).update({
      status: "Absent",
      markedBy: "TCH101",
      updatedAt: ts(),
    })
  );

  // termMarks
  const termId = `${ALICE_UID}_Half-Yearly_Exam_2026_std1`;
  await assertSucceeds(col(aliceDb, ALICE_UID, "termMarks").get());
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "termMarks").doc(termId).set({
      ownerUid: ALICE_UID,
      term: "Half-Yearly Exam 2026",
      className: "Class X",
      section: "A",
      studentDocId: "std1",
      studentName: "Aarav Verma",
      admissionNo: "DPS2026-001",
      marks: { Mathematics: { scored: 92, max: 100 } },
      remarks: "Very Good Progress",
      publishedBy: "Mr. Rajesh Sharma",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "termMarks").doc(termId).update({
      marks: { Mathematics: { scored: 95, max: 100 } },
      remarks: "Excellent!",
      publishedBy: "Mr. Rajesh Sharma",
      updatedAt: ts(),
    })
  );
});

test("craftProjects: student/teacher create, AI image attachment update, like increment & invalid role rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docId = `${ALICE_UID}_craft_1`;
  await assertSucceeds(col(aliceDb, ALICE_UID, "craftProjects").get());
  await assertSucceeds(
    col(aliceDb, ALICE_UID, "craftProjects").doc(docId).set({
      ownerUid: ALICE_UID,
      title: "Solar Smart Drip Irrigation",
      category: "Eco Recycling",
      description: "Recycled bottle drip watering system powered by 6V solar panel.",
      materials: "Plastic bottle, solar cell, tube",
      authorDocId: "usr_std_1",
      authorName: "Aarav Verma",
      authorUserId: "DPS2026-001",
      authorRole: "STUDENT",
      classInfo: "Class X-A",
      dateLabel: "2026-11-17",
      aiImageData: "",
      aiPromptUsed: "",
      likesCount: 0,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    col(aliceDb, ALICE_UID, "craftProjects").doc(docId).update({
      aiImageData: "AQIDBA==",
      aiPromptUsed: "Studio exhibition photo of solar drip irrigation craft",
      likesCount: 1,
      updatedAt: ts(),
    })
  );

  await assertFails(
    col(aliceDb, ALICE_UID, "craftProjects").doc(docId).update({
      likesCount: 0,
      updatedAt: ts(),
    })
  );
  await assertFails(
    col(aliceDb, ALICE_UID, "craftProjects").doc(`${ALICE_UID}_craft_bad`).set({
      ownerUid: ALICE_UID,
      title: "Invalid Role Post",
      category: "Art & Craft",
      description: "Testing role rejection",
      materials: "Paper",
      authorDocId: "usr_1",
      authorName: "Test",
      authorUserId: "FEE201",
      authorRole: "FEE_MANAGER",
      classInfo: "Accounts",
      dateLabel: "2026-11-17",
      aiImageData: "",
      aiPromptUsed: "",
      likesCount: 0,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});
