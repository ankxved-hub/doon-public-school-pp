import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { handleApiRequest } from "../src/index.js";

// Lightweight stateful in-memory SQLite-style mock for Cloudflare D1 to verify parameterized queries, auth & RBAC
function createMockD1() {
  const tables = {
    users: new Map(),
    gateway_config: new Map(),
    circulars: new Map(),
    dpt_daily: new Map(),
    dpt_weekly: new Map(),
    attendance: new Map(),
    leaves: new Map(),
    homework: new Map(),
    term_marks: new Map(),
    fee_items: new Map(),
    fee_payments: new Map(),
    doubts: new Map(),
    craft_projects: new Map(),
    craft_likes: new Map(),
    campus_posters: new Map(),
    timetables: new Map(),
    admission_inquiries: new Map(),
  };

  function runStmt(sql, args = []) {
    const s = sql.trim();

    if (s.startsWith("SELECT COUNT(*) as cnt FROM users WHERE role = 'ADMIN'")) {
      const cnt = [...tables.users.values()].filter((u) => u.role === "ADMIN").length;
      return { results: [{ cnt }], first: { cnt } };
    }
    if (s.startsWith("SELECT COUNT(*) as cnt FROM users WHERE role = 'STUDENT'")) {
      const cnt = [...tables.users.values()].filter((u) => u.role === "STUDENT").length;
      return { results: [{ cnt }], first: { cnt } };
    }
    if (s.startsWith("SELECT COUNT(*) as cnt FROM users WHERE role = 'TEACHER'")) {
      const cnt = [...tables.users.values()].filter((u) => u.role === "TEACHER").length;
      return { results: [{ cnt }], first: { cnt } };
    }
    if (s.startsWith("SELECT COUNT(*) as cnt FROM users WHERE role = 'FEE_MANAGER'")) {
      const cnt = [...tables.users.values()].filter((u) => u.role === "FEE_MANAGER").length;
      return { results: [{ cnt }], first: { cnt } };
    }
    if (s.startsWith("SELECT COUNT(*) as cnt FROM users")) {
      return { results: [{ cnt: tables.users.size }], first: { cnt: tables.users.size } };
    }
    if (s.startsWith("SELECT * FROM gateway_config LIMIT 1")) {
      const first = [...tables.gateway_config.values()][0] || null;
      return { results: first ? [first] : [], first };
    }
    if (s.startsWith("INSERT OR IGNORE INTO gateway_config")) {
      if (!tables.gateway_config.has(args[0])) {
        tables.gateway_config.set(args[0], {
          id: args[0],
          merchant_upi: args[1],
          payee_name: args[2],
          bank_name: args[3],
          account_no: args[4],
          ifsc: args[5],
          razorpay_key_id: args[6],
          latest_version_code: args[7],
          latest_version_name: args[8],
          apk_download_url: args[9],
          github_repo_slug: args[10],
          update_notes: args[11],
          updated_at: args[12],
        });
      }
      return { results: [], first: null };
    }
    if (s.startsWith("UPDATE gateway_config SET")) {
      const [
        merchant_upi,
        payee_name,
        bank_name,
        account_no,
        ifsc,
        razorpay_key_id,
        latest_version_code,
        latest_version_name,
        apk_download_url,
        github_repo_slug,
        update_notes,
        updated_at,
        id,
      ] = args;
      const existing = tables.gateway_config.get(id);
      if (existing) {
        tables.gateway_config.set(id, {
          ...existing,
          merchant_upi,
          payee_name,
          bank_name,
          account_no,
          ifsc,
          razorpay_key_id,
          latest_version_code,
          latest_version_name,
          apk_download_url,
          github_repo_slug,
          update_notes,
          updated_at,
        });
      }
      return { results: [], first: null };
    }
    if (s.startsWith("ALTER TABLE users ADD COLUMN extra_roles")) {
      return { results: [], first: null };
    }
    if (s.startsWith("INSERT INTO users")) {
      if (s.includes("extra_roles")) {
        const [id, role, extra_roles, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at] = args;
        tables.users.set(id, { id, role, extra_roles, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at });
      } else {
        const [id, role, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at] = args;
        tables.users.set(id, { id, role, extra_roles: "", user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at });
      }
      return { results: [], first: null };
    }
    if (s.startsWith("UPDATE users SET role = ?, extra_roles = ?")) {
      const [role, extra_roles, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, updated_at, id] = args;
      const existing = tables.users.get(id);
      if (existing) {
        tables.users.set(id, { ...existing, role, extra_roles, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, updated_at });
      }
      return { results: [], first: null };
    }
    if (s.startsWith("SELECT * FROM users WHERE id = ?")) {
      const first = tables.users.get(args[0]) || null;
      return { results: first ? [first] : [], first };
    }
    if (s.startsWith("SELECT * FROM users WHERE LOWER(user_id) = LOWER(?) LIMIT 1")) {
      const [uid] = args;
      const first = [...tables.users.values()].find((u) => u.user_id.toLowerCase() === String(uid).toLowerCase()) || null;
      return { results: first ? [first] : [], first };
    }
    if (s.startsWith("SELECT * FROM users WHERE role = ? AND LOWER(user_id) = LOWER(?) LIMIT 1")) {
      const [role, uid] = args;
      const first = [...tables.users.values()].find((u) => u.role === role && u.user_id.toLowerCase() === String(uid).toLowerCase()) || null;
      return { results: first ? [first] : [], first };
    }
    if (s.startsWith("SELECT user_id FROM users WHERE role = 'STUDENT'")) {
      const res = [...tables.users.values()].filter((u) => u.role === "STUDENT").map((u) => ({ user_id: u.user_id }));
      return { results: res, first: res[0] || null };
    }
    if (s.startsWith("SELECT id FROM users WHERE LOWER(user_id) = LOWER(?) AND id != ?")) {
      const [uid, skipId] = args;
      const first = [...tables.users.values()].find((u) => u.user_id.toLowerCase() === String(uid).toLowerCase() && u.id !== skipId) || null;
      return { results: first ? [first] : [], first };
    }
    if (s.startsWith("SELECT id FROM users WHERE LOWER(user_id) = LOWER(?)")) {
      const [uid] = args;
      const first = [...tables.users.values()].find((u) => u.user_id.toLowerCase() === String(uid).toLowerCase()) || null;
      return { results: first ? [first] : [], first };
    }
    if (s.startsWith("SELECT * FROM users")) {
      const res = [...tables.users.values()];
      return { results: res, first: res[0] || null };
    }
    if (s.startsWith("INSERT INTO craft_projects")) {
      const [id, title, category, description, materials, author_doc_id, author_name, author_user_id, author_role, class_info, date_label, ai_image_data, ai_prompt_used, created_at] = args;
      tables.craft_projects.set(id, { id, title, category, description, materials, author_doc_id, author_name, author_user_id, author_role, class_info, date_label, ai_image_data, ai_prompt_used, likes_count: 0, created_at });
      return { results: [], first: null };
    }
    if (s.startsWith("SELECT id, likes_count FROM craft_projects WHERE id = ?")) {
      const p = tables.craft_projects.get(args[0]) || null;
      return { results: p ? [p] : [], first: p };
    }
    if (s.startsWith("SELECT like_id FROM craft_likes WHERE like_id = ?")) {
      const l = tables.craft_likes.get(args[0]) || null;
      return { results: l ? [l] : [], first: l };
    }
    if (s.startsWith("INSERT INTO craft_likes")) {
      tables.craft_likes.set(args[0], { like_id: args[0], project_id: args[1], liker_id: args[2], created_at: args[3] });
      return { results: [], first: null };
    }
    if (s.startsWith("DELETE FROM craft_likes WHERE like_id = ?")) {
      tables.craft_likes.delete(args[0]);
      return { results: [], first: null };
    }
    if (s.startsWith("UPDATE craft_projects SET likes_count = likes_count + 1")) {
      const p = tables.craft_projects.get(args[0]);
      if (p) p.likes_count += 1;
      return { results: [], first: null };
    }
    if (s.startsWith("UPDATE craft_projects SET likes_count = MAX(0, likes_count - 1)")) {
      const p = tables.craft_projects.get(args[0]);
      if (p) p.likes_count = Math.max(0, p.likes_count - 1);
      return { results: [], first: null };
    }
    if (s.startsWith("SELECT * FROM craft_projects")) {
      const res = [...tables.craft_projects.values()];
      return { results: res, first: res[0] || null };
    }
    if (s.startsWith("SELECT project_id FROM craft_likes WHERE liker_id = ?")) {
      const res = [...tables.craft_likes.values()].filter((l) => l.liker_id === args[0]).map((l) => ({ project_id: l.project_id }));
      return { results: res, first: res[0] || null };
    }
    if (s.startsWith("SELECT id, full_name, class_name, section, monthly_tuition, transport_fee FROM users WHERE role = 'STUDENT'")) {
      let rows = [...tables.users.values()].filter((u) => u.role === "STUDENT");
      if (s.includes("class_name IN (")) {
        const m = s.match(/class_name IN \(([^)]+)\)/);
        const classCount = m ? m[1].split(",").length : 0;
        const targetClasses = new Set(args.slice(0, classCount));
        rows = rows.filter((u) => targetClasses.has(u.class_name));
        if (s.includes("AND section = ?")) {
          const sec = args[classCount];
          rows = rows.filter((u) => u.section === sec);
        }
      } else if (s.includes("id IN (")) {
        const targetIds = new Set(args);
        rows = rows.filter((u) => targetIds.has(u.id));
      }
      return { results: rows, first: rows[0] || null };
    }
    if (s.startsWith("SELECT * FROM users WHERE role = 'STUDENT'")) {
      const rows = [...tables.users.values()].filter((u) => u.role === "STUDENT");
      return { results: rows, first: rows[0] || null };
    }
    if (s.startsWith("SELECT student_doc_id, title FROM fee_items WHERE month = ?")) {
      const rows = [...tables.fee_items.values()].filter((f) => f.month === args[0]).map((f) => ({ student_doc_id: f.student_doc_id, title: f.title }));
      return { results: rows, first: rows[0] || null };
    }
    if (s.startsWith("INSERT INTO fee_items")) {
      const [id, student_doc_id, title, month, amount, created_at] = args;
      tables.fee_items.set(id, { id, student_doc_id, title, month, amount, status: "UNPAID", payment_id: "", created_at });
      return { results: [], first: null };
    }
    if (s.startsWith("SELECT * FROM fee_items WHERE student_doc_id = ?")) {
      const rows = [...tables.fee_items.values()].filter((f) => f.student_doc_id === args[0]);
      return { results: rows, first: rows[0] || null };
    }
    if (s.startsWith("SELECT * FROM fee_items")) {
      const rows = [...tables.fee_items.values()];
      return { results: rows, first: rows[0] || null };
    }
    if (s.startsWith("CREATE TABLE IF NOT EXISTS admission_inquiries")) {
      return { results: [], first: null };
    }
    if (s.startsWith("INSERT INTO admission_inquiries")) {
      const [id, student_name, parent_name, phone, applying_class, previous_school, notes, created_at] = args;
      tables.admission_inquiries.set(id, { id, student_name, parent_name, phone, applying_class, previous_school, notes, status: "NEW", created_at });
      return { results: [], first: null };
    }
    if (s.startsWith("SELECT * FROM admission_inquiries")) {
      const rows = [...tables.admission_inquiries.values()];
      return { results: rows, first: rows[0] || null };
    }
    if (s.startsWith("UPDATE admission_inquiries SET status = ? WHERE id = ?")) {
      const item = tables.admission_inquiries.get(args[1]);
      if (item) item.status = args[0];
      return { results: [], first: null };
    }
    return { results: [], first: null };
  }

  return {
    prepare(sql) {
      let bound = [];
      const stmt = {
        bind(...args) {
          bound = args;
          return stmt;
        },
        async first() {
          return runStmt(sql, bound).first;
        },
        async all() {
          return { results: runStmt(sql, bound).results };
        },
        async run() {
          runStmt(sql, bound);
          return { success: true };
        },
      };
      return stmt;
    },
    async batch(stmts) {
      for (const st of stmts) {
        await st.run();
      }
      return [];
    },
  };
}

function apiReq(urlPath, method = "GET", body = null, token = null) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return new Request(`https://school.example.workers.dev${urlPath}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
}

test("Cloudflare Worker handles Admin setup claim, PBKDF2 login, sequential roll numbers (001), RBAC, and unique 1-like-per-user", async () => {
  const env = {
    DB: createMockD1(),
    JWT_SECRET: "test_secret_key_for_unit_test_only",
    SCHOOL_NAME: "Doon Public School, Etah",
  };

  // 1. Initial bootstrap reports needsSetup = true
  let res = await handleApiRequest(apiReq("/api/bootstrap/status"), env);
  assert.equal(res.status, 200);
  let data = await res.json();
  assert.equal(data.needsSetup, true);

  // 2. Claim initial Principal/Admin password
  res = await handleApiRequest(
    apiReq("/api/auth/claim-admin", "POST", {
      fullName: "Dr. Principal",
      userId: "ADMIN01",
      phone: "9837000001",
      password: "StrongPassword123",
    }),
    env
  );
  assert.equal(res.status, 201);
  const adminSession = await res.json();
  assert.ok(adminSession.token);
  assert.equal(adminSession.user.role, "ADMIN");

  // Second claim attempt MUST fail
  res = await handleApiRequest(
    apiReq("/api/auth/claim-admin", "POST", { password: "HackPassword" }),
    env
  );
  assert.equal(res.status, 409);

  // 3. Create first student WITHOUT userId -> auto-generates roll "001"
  res = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      {
        role: "STUDENT",
        fullName: "Aarav Sharma",
        password: "studentPass1",
        className: "Class VII",
        section: "A",
        wing: "Middle School",
      },
      adminSession.token
    ),
    env
  );
  assert.equal(res.status, 201);
  const st1 = (await res.json()).user;
  assert.equal(st1.userId, "001");

  // Create second student -> auto-generates roll "002"
  res = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      {
        role: "STUDENT",
        fullName: "Ananya Verma",
        password: "studentPass2",
        className: "Class VII",
        section: "A",
        wing: "Middle School",
      },
      adminSession.token
    ),
    env
  );
  assert.equal(res.status, 201);
  const st2 = (await res.json()).user;
  assert.equal(st2.userId, "002");

  // 4. Student logs in via PBKDF2 verification and receives scoped dashboard
  res = await handleApiRequest(
    apiReq("/api/auth/login", "POST", {
      role: "STUDENT",
      userId: "001",
      password: "studentPass1",
    }),
    env
  );
  assert.equal(res.status, 200);
  const stLogin = await res.json();
  assert.equal(stLogin.user.fullName, "Aarav Sharma");

  // Student CANNOT create accounts (403 Forbidden)
  res = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      { role: "ADMIN", userId: "HACK", fullName: "Hacker", password: "123456" },
      stLogin.token
    ),
    env
  );
  assert.equal(res.status, 403);

  // 5. Craft project + unique 1-like-per-user toggle
  res = await handleApiRequest(
    apiReq(
      "/api/craft",
      "POST",
      { title: "Solar Toy Car", category: "Science Model", description: "Solar powered eco car" },
      stLogin.token
    ),
    env
  );
  assert.equal(res.status, 201);
  const { id: craftId } = await res.json();

  // First like -> liked: true
  res = await handleApiRequest(apiReq(`/api/craft/${craftId}/like`, "POST", {}, stLogin.token), env);
  assert.equal((await res.json()).liked, true);

  // Second tap toggles off -> liked: false (never inflates likes!)
  res = await handleApiRequest(apiReq(`/api/craft/${craftId}/like`, "POST", {}, stLogin.token), env);
  assert.equal((await res.json()).liked, false);
});

test("POST /api/fee-bills bills authoritative per-student account fees and prevents duplicates", async () => {
  const env = {
    DB: createMockD1(),
    JWT_SECRET: "test_secret_key_for_unit_test_only",
    SCHOOL_NAME: "Doon Public School, Etah",
  };

  const adminRes = await handleApiRequest(
    apiReq("/api/auth/claim-admin", "POST", {
      fullName: "Dr. Principal",
      userId: "ADMIN01",
      password: "StrongPassword123",
    }),
    env
  );
  const { token: adminToken } = await adminRes.json();

  // Create Fee Manager + 3 students (2 in Class II with distinct fees, 1 in Class V)
  await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      { role: "FEE_MANAGER", userId: "FEE201", fullName: "Accountant Ji", password: "feePassword1" },
      adminToken
    ),
    env
  );
  const s1Res = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      {
        role: "STUDENT",
        fullName: "Rohan Class2",
        password: "pass1234",
        className: "Class II",
        section: "A",
        monthlyTuition: 2100,
        transportFee: 650,
      },
      adminToken
    ),
    env
  );
  const s1 = (await s1Res.json()).user;

  const s2Res = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      {
        role: "STUDENT",
        fullName: "Meera Class2",
        password: "pass1234",
        className: "Class II",
        section: "B",
        monthlyTuition: 2600,
        transportFee: 0,
      },
      adminToken
    ),
    env
  );
  const s2 = (await s2Res.json()).user;

  const s3Res = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      {
        role: "STUDENT",
        fullName: "Kabir Class5",
        password: "pass1234",
        className: "Class V",
        section: "A",
        monthlyTuition: 3200,
        transportFee: 900,
      },
      adminToken
    ),
    env
  );
  const s3 = (await s3Res.json()).user;

  const fmLogin = await handleApiRequest(
    apiReq("/api/auth/login", "POST", { role: "FEE_MANAGER", userId: "FEE201", password: "feePassword1" }),
    env
  );
  const fmToken = (await fmLogin.json()).token;

  const stLogin = await handleApiRequest(
    apiReq("/api/auth/login", "POST", { role: "STUDENT", userId: s1.userId, password: "pass1234" }),
    env
  );
  const s1Token = (await stLogin.json()).token;

  // Student cannot trigger bills
  const forbiddenRes = await handleApiRequest(
    apiReq("/api/fee-bills", "POST", { scope: "ALL", billType: "BOTH", month: "December 2026" }, s1Token),
    env
  );
  assert.equal(forbiddenRes.status, 403);

  // Fee Manager triggers Class II (even attempting a bogus miscAmount is ignored for BOTH)
  const billRes = await handleApiRequest(
    apiReq(
      "/api/fee-bills",
      "POST",
      { scope: "CLASSES", classes: ["Class II"], billType: "BOTH", month: "December 2026", miscAmount: 9999 },
      fmToken
    ),
    env
  );
  assert.equal(billRes.status, 201);
  const summary = await billRes.json();
  assert.equal(summary.totalStudentsMatched, 2);
  assert.equal(summary.studentsBilled, 2);
  assert.equal(summary.itemsCreated, 3);
  assert.equal(summary.skippedDuplicates, 0);

  // Verify Rohan's dashboard has exact 2100 tuition + 650 transport
  const s1Dash = await (await handleApiRequest(apiReq("/api/dashboard", "GET", null, s1Token), env)).json();
  assert.equal(s1Dash.feeItems.length, 2);
  assert.equal(s1Dash.feeItems.find((f) => f.title.includes("Tuition")).amount, 2100);
  assert.equal(s1Dash.feeItems.find((f) => f.title.includes("Transport")).amount, 650);

  // Verify Meera has 2600 tuition only (0 transport skipped)
  const s2Token = (await (await handleApiRequest(apiReq("/api/auth/login", "POST", { role: "STUDENT", userId: s2.userId, password: "pass1234" }), env)).json()).token;
  const s2Dash = await (await handleApiRequest(apiReq("/api/dashboard", "GET", null, s2Token), env)).json();
  assert.equal(s2Dash.feeItems.length, 1);
  assert.equal(s2Dash.feeItems[0].amount, 2600);

  // Verify Kabir in Class V was not billed
  const s3Token = (await (await handleApiRequest(apiReq("/api/auth/login", "POST", { role: "STUDENT", userId: s3.userId, password: "pass1234" }), env)).json()).token;
  const s3Dash = await (await handleApiRequest(apiReq("/api/dashboard", "GET", null, s3Token), env)).json();
  assert.equal(s3Dash.feeItems.length, 0);

  // Re-triggering same month skips all 3 duplicates
  const dupRes = await handleApiRequest(
    apiReq("/api/fee-bills", "POST", { scope: "CLASSES", classes: ["Class II"], billType: "BOTH", month: "December 2026" }, fmToken),
    env
  );
  const dupSummary = await dupRes.json();
  assert.equal(dupSummary.itemsCreated, 0);
  assert.equal(dupSummary.skippedDuplicates, 3);
});

test("Public admission inquiries persist to D1 and only Admin can review/update status", async () => {
  const env = {
    DB: createMockD1(),
    JWT_SECRET: "test_secret_key_for_unit_test_only",
    SCHOOL_NAME: "Doon Public School, Etah",
  };

  const adminRes = await handleApiRequest(
    apiReq("/api/auth/claim-admin", "POST", {
      fullName: "Dr. Principal",
      userId: "ADMIN01",
      password: "StrongPassword123",
    }),
    env
  );
  const { token: adminToken } = await adminRes.json();

  const pubRes = await handleApiRequest(
    apiReq("/api/admission-inquiries", "POST", {
      studentName: "Aryan Tiwari",
      parentName: "Rajesh Tiwari",
      phone: "+91 9837012345",
      applyingClass: "Class II",
      previousSchool: "Little Flower",
      notes: "Need school transport from Kasganj Road",
    }),
    env
  );
  assert.equal(pubRes.status, 201);
  const created = await pubRes.json();
  assert.ok(created.id);

  const dash = await (await handleApiRequest(apiReq("/api/dashboard", "GET", null, adminToken), env)).json();
  assert.equal(dash.admissionInquiries.length, 1);
  assert.equal(dash.admissionInquiries[0].student_name, "Aryan Tiwari");
  assert.equal(dash.admissionInquiries[0].status, "NEW");

  const updRes = await handleApiRequest(
    apiReq(`/api/admission-inquiries/${created.id}`, "PUT", { status: "CONTACTED" }, adminToken),
    env
  );
  assert.equal(updRes.status, 200);
  const dashAfter = await (await handleApiRequest(apiReq("/api/dashboard", "GET", null, adminToken), env)).json();
  assert.equal(dashAfter.admissionInquiries[0].status, "CONTACTED");
});

test("Multi-role Teacher account supports multiple login roles, role switching, and RBAC enforcement", async () => {
  const env = {
    DB: createMockD1(),
    JWT_SECRET: "test_secret_key_for_unit_test_only",
    SCHOOL_NAME: "Doon Public School, Etah",
  };

  const adminRes = await handleApiRequest(
    apiReq("/api/auth/claim-admin", "POST", {
      fullName: "Dr. Principal",
      userId: "ADMIN01",
      password: "StrongPassword123",
    }),
    env
  );
  const { token: adminToken } = await adminRes.json();

  const createRes = await handleApiRequest(
    apiReq(
      "/api/users",
      "POST",
      {
        role: "TEACHER",
        extraRoles: ["FEE_MANAGER", "ADMIN", "STUDENT"],
        userId: "TCH501",
        fullName: "Mrs. Kavita Singh",
        password: "teacherPass1",
        wing: "Middle School",
        className: "Class VIII",
        section: "A",
        subject: "Mathematics, Science",
      },
      adminToken
    ),
    env
  );
  assert.equal(createRes.status, 201);
  const createdUser = (await createRes.json()).user;
  assert.deepEqual(createdUser.roles, ["TEACHER", "FEE_MANAGER", "ADMIN"]);
  assert.deepEqual(createdUser.extraRoles, ["FEE_MANAGER", "ADMIN"]);

  // Can log in as TEACHER
  const loginTeacher = await handleApiRequest(
    apiReq("/api/auth/login", "POST", { role: "TEACHER", userId: "TCH501", password: "teacherPass1" }),
    env
  );
  assert.equal(loginTeacher.status, 200);
  const teacherSession = await loginTeacher.json();
  assert.equal(teacherSession.user.role, "TEACHER");

  // Can also log in directly as FEE_MANAGER using same ID & password
  const loginFeeMgr = await handleApiRequest(
    apiReq("/api/auth/login", "POST", { role: "FEE_MANAGER", userId: "TCH501", password: "teacherPass1" }),
    env
  );
  assert.equal(loginFeeMgr.status, 200);
  assert.equal((await loginFeeMgr.json()).user.role, "FEE_MANAGER");

  // Cannot log in as STUDENT (never granted to staff)
  const loginStudent = await handleApiRequest(
    apiReq("/api/auth/login", "POST", { role: "STUDENT", userId: "TCH501", password: "teacherPass1" }),
    env
  );
  assert.equal(loginStudent.status, 401);

  // Authenticated switch-role to ADMIN succeeds; unauthorized STUDENT fails with 403
  const switchAdmin = await handleApiRequest(
    apiReq("/api/auth/switch-role", "POST", { role: "ADMIN" }, teacherSession.token),
    env
  );
  assert.equal(switchAdmin.status, 200);
  const adminSwitched = await switchAdmin.json();
  assert.equal(adminSwitched.user.role, "ADMIN");

  const switchForbidden = await handleApiRequest(
    apiReq("/api/auth/switch-role", "POST", { role: "STUDENT" }, teacherSession.token),
    env
  );
  assert.equal(switchForbidden.status, 403);
});

test("Public /api/bootstrap/status and /api/app-update expose sanitized appUpdate metadata without leaking bank details", async () => {
  const env = {
    DB: createMockD1(),
    JWT_SECRET: "test_secret_key_for_unit_test_only",
    SCHOOL_NAME: "Doon Public School, Etah",
  };

  const adminRes = await handleApiRequest(
    apiReq("/api/auth/claim-admin", "POST", {
      fullName: "Dr. Principal",
      userId: "ADMIN01",
      password: "StrongPassword123",
    }),
    env
  );
  const { token: adminToken } = await adminRes.json();

  const updateRes = await handleApiRequest(
    apiReq(
      "/api/gateway",
      "PUT",
      {
        latestVersionCode: 4,
        latestVersionName: "1.3",
        apkDownloadUrl: "https://doonpublicschooletah.in/DoonPublicSchool-Etah.apk",
        updateNotes: "Multi-role teacher accounts & instant fee updates.",
      },
      adminToken
    ),
    env
  );
  assert.equal(updateRes.status, 200);

  const bootRes = await handleApiRequest(apiReq("/api/bootstrap/status"), env);
  assert.equal(bootRes.status, 200);
  const boot = await bootRes.json();
  assert.equal(boot.appUpdate.latestVersionCode, 4);
  assert.equal(boot.appUpdate.latestVersionName, "1.3");
  assert.equal(boot.appUpdate.apkDownloadUrl, "https://doonpublicschooletah.in/DoonPublicSchool-Etah.apk");
  assert.equal(boot.appUpdate.merchantUpi, undefined);
  assert.equal(boot.appUpdate.accountNo, undefined);

  const pubRes = await handleApiRequest(apiReq("/api/app-update"), env);
  assert.equal(pubRes.status, 200);
  const pub = await pubRes.json();
  assert.equal(pub.appUpdate.latestVersionCode, 4);
  assert.equal(pub.appUpdate.ifsc, undefined);
});

test("schema.sql contains required indexes for 3,000-user scale", () => {
  const sql = fs.readFileSync(new URL("../schema.sql", import.meta.url), "utf8");
  assert.ok(sql.includes("idx_users_role_class_sec"));
  assert.ok(sql.includes("idx_attendance_student"));
  assert.ok(sql.includes("UNIQUE(project_id, liker_id)"));
  assert.ok(sql.includes("extra_roles"));
});
