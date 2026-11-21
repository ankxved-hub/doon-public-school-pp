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
    if (s.startsWith("INSERT INTO users")) {
      const [id, role, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at] = args;
      tables.users.set(id, { id, role, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at });
      return { results: [], first: null };
    }
    if (s.startsWith("SELECT * FROM users WHERE id = ?")) {
      const first = tables.users.get(args[0]) || null;
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

test("schema.sql contains required indexes for 3,000-user scale", () => {
  const sql = fs.readFileSync(new URL("../schema.sql", import.meta.url), "utf8");
  assert.ok(sql.includes("idx_users_role_class_sec"));
  assert.ok(sql.includes("idx_attendance_student"));
  assert.ok(sql.includes("UNIQUE(project_id, liker_id)"));
});
