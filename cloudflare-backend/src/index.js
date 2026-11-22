import { hashPassword, verifyPassword, signJwt, verifyJwt } from "./auth.js";

const VALID_ROLES = new Set(["ADMIN", "TEACHER", "FEE_MANAGER", "STUDENT"]);
const STAFF_ROLES = new Set(["ADMIN", "TEACHER", "FEE_MANAGER"]);

function cleanStr(v, maxLen = 200) {
  return String(v ?? "").trim().slice(0, maxLen);
}

function cleanInt(v, min = 0, max = 1_000_000_000) {
  const n = Number.parseInt(String(v ?? "0"), 10);
  if (!Number.isFinite(n)) return min;
  return Math.max(min, Math.min(max, n));
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function resolveCorsHeaders(request, env) {
  const origin = request.headers.get("Origin");
  if (!origin) return {};

  const reqUrl = new URL(request.url);
  const sameOrigin = origin === reqUrl.origin;
  const allowList = String(env.ALLOWED_ORIGINS || "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  if (sameOrigin || allowList.includes(origin)) {
    return {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Vary": "Origin",
    };
  }
  return {};
}

function jsonResponse(data, status = 200, corsHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...corsHeaders,
    },
  });
}

function parseExtraRoles(raw, primaryRole) {
  const list = Array.isArray(raw)
    ? raw
    : String(raw ?? "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
  if (primaryRole === "STUDENT") return [];
  const out = [];
  for (const r of list) {
    const clean = String(r || "").trim().toUpperCase();
    if (STAFF_ROLES.has(clean) && clean !== primaryRole && !out.includes(clean)) {
      out.push(clean);
    }
  }
  return out;
}

function getAllowedRoles(primaryRole, extraRolesRaw) {
  if (!VALID_ROLES.has(primaryRole)) return [];
  if (primaryRole === "STUDENT") return ["STUDENT"];
  return [primaryRole, ...parseExtraRoles(extraRolesRaw, primaryRole)];
}

function mapUserRow(row, activeRoleOverride = null) {
  if (!row) return null;
  const primaryRole = row.role;
  const extraRoles = parseExtraRoles(row.extra_roles, primaryRole);
  const roles = getAllowedRoles(primaryRole, extraRoles);
  const effectiveRole =
    activeRoleOverride && roles.includes(activeRoleOverride) ? activeRoleOverride : primaryRole;
  return {
    id: row.id,
    role: effectiveRole,
    primaryRole,
    roles,
    extraRoles,
    userId: row.user_id,
    fullName: row.full_name,
    phone: row.phone,
    wing: row.wing,
    className: row.class_name,
    section: row.section,
    subject: row.subject,
    parentName: row.parent_name,
    monthlyTuition: Number(row.monthly_tuition || 0),
    transportFee: Number(row.transport_fee || 0),
    createdAt: Number(row.created_at || 0),
    updatedAt: Number(row.updated_at || 0),
  };
}

function mapGatewayRow(row) {
  if (!row) {
    return {
      id: "dps_etah_gw_main",
      merchantUpi: "doonpublicetah@sbi",
      payeeName: "Doon Public School, Etah",
      bankName: "State Bank of India (Aruna Nagar, Etah)",
      accountNo: "39481020004512",
      ifsc: "SBIN0004512",
      razorpayKeyId: "",
      latestVersionCode: 2,
      latestVersionName: "1.1",
      apkDownloadUrl: "",
      githubRepoSlug: "",
      updateNotes: "",
    };
  }
  return {
    id: row.id,
    merchantUpi: row.merchant_upi,
    payeeName: row.payee_name,
    bankName: row.bank_name,
    accountNo: row.account_no,
    ifsc: row.ifsc,
    razorpayKeyId: row.razorpay_key_id,
    latestVersionCode: Number(row.latest_version_code || 2),
    latestVersionName: row.latest_version_name || "1.1",
    apkDownloadUrl: row.apk_download_url || "",
    githubRepoSlug: row.github_repo_slug || "",
    updateNotes: row.update_notes || "",
  };
}

async function ensureDefaultGateway(db) {
  const existing = await db.prepare("SELECT * FROM gateway_config LIMIT 1").first();
  if (existing) return existing;
  const now = Date.now();
  await db
    .prepare(
      `INSERT OR IGNORE INTO gateway_config (
        id, merchant_upi, payee_name, bank_name, account_no, ifsc, razorpay_key_id,
        latest_version_code, latest_version_name, apk_download_url, github_repo_slug, update_notes, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    )
    .bind(
      "dps_etah_gw_main",
      "doonpublicetah@sbi",
      "Doon Public School, Etah",
      "State Bank of India (Aruna Nagar, Etah)",
      "39481020004512",
      "SBIN0004512",
      "",
      2,
      "1.1",
      "",
      "",
      "",
      now
    )
    .run();
  return db.prepare("SELECT * FROM gateway_config LIMIT 1").first();
}

async function ensureUsersExtraRolesColumn(db) {
  await db
    .prepare("ALTER TABLE users ADD COLUMN extra_roles TEXT NOT NULL DEFAULT ''")
    .run()
    .catch(() => {});
}

async function ensureInquiriesTable(db) {
  await db
    .prepare(
      `CREATE TABLE IF NOT EXISTS admission_inquiries (
        id TEXT PRIMARY KEY,
        student_name TEXT NOT NULL,
        parent_name TEXT NOT NULL,
        phone TEXT NOT NULL,
        applying_class TEXT NOT NULL,
        previous_school TEXT NOT NULL DEFAULT '',
        notes TEXT NOT NULL DEFAULT '',
        status TEXT NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONTACTED', 'ENROLLED')),
        created_at INTEGER NOT NULL
      )`
    )
    .run()
    .catch(() => {});
}

async function nextStudentRollNumber(db) {
  const rows = await db
    .prepare("SELECT user_id FROM users WHERE role = 'STUDENT'")
    .all();
  const existing = new Set();
  let maxNum = 0;
  for (const r of rows.results || []) {
    const uid = String(r.user_id || "").trim().toUpperCase();
    existing.add(uid);
    if (/^\d+$/.test(uid)) {
      const val = Number.parseInt(uid, 10);
      if (val > maxNum) maxNum = val;
    }
  }
  let next = maxNum + 1;
  while (existing.has(String(next).padStart(3, "0"))) {
    next += 1;
  }
  return String(next).padStart(3, "0");
}

async function getAdminBootstrapState(db, env) {
  const countRow = await db
    .prepare("SELECT COUNT(*) as cnt FROM users WHERE role = 'ADMIN'")
    .first();
  const adminCount = Number(countRow?.cnt || 0);
  const envPassConfigured = Boolean(env.ADMIN_BOOT_PASSWORD && String(env.ADMIN_BOOT_PASSWORD).trim().length >= 6);
  return {
    hasAdmin: adminCount > 0,
    envAdminConfigured: envPassConfigured,
    needsSetup: adminCount === 0 && !envPassConfigured,
  };
}

async function requireAuth(request, env, db) {
  const header = request.headers.get("Authorization") || "";
  if (!header.startsWith("Bearer ")) return null;
  const token = header.slice(7).trim();
  const payload = await verifyJwt(token, env.JWT_SECRET);
  if (!payload || !payload.sub) return null;
  const userRow = await db.prepare("SELECT * FROM users WHERE id = ?").bind(payload.sub).first();
  if (!userRow) return null;
  const allowedRoles = getAllowedRoles(userRow.role, userRow.extra_roles);
  const effectiveRole = payload.role && allowedRoles.includes(payload.role) ? payload.role : userRow.role;
  return {
    ...userRow,
    primaryRole: userRow.role,
    role: effectiveRole,
    roles: allowedRoles,
    rawRow: userRow,
  };
}

export async function handleApiRequest(request, env) {
  const cors = resolveCorsHeaders(request, env);
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: cors });
  }

  const db = env.DB;
  if (!db) {
    return jsonResponse({ error: "Cloudflare D1 binding (DB) is not configured." }, 500, cors);
  }

  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method.toUpperCase();

  try {
    if (path === "/api/health" && method === "GET") {
      await ensureDefaultGateway(db);
      const userCount = await db.prepare("SELECT COUNT(*) as cnt FROM users").first();
      return jsonResponse(
        {
          ok: true,
          schoolName: env.SCHOOL_NAME || "Doon Public School, Etah",
          engine: "Cloudflare Workers + D1",
          totalAccounts: Number(userCount?.cnt || 0),
        },
        200,
        cors
      );
    }

    if (path === "/api/bootstrap/status" && method === "GET") {
      await ensureDefaultGateway(db);
      const state = await getAdminBootstrapState(db, env);
      return jsonResponse(state, 200, cors);
    }

    if (path === "/api/admission-inquiries" && method === "POST") {
      await ensureInquiriesTable(db);
      const body = await request.json().catch(() => ({}));
      const studentName = cleanStr(body.studentName, 100);
      const parentName = cleanStr(body.parentName, 100);
      const phone = cleanStr(body.phone, 25);
      const applyingClass = cleanStr(body.applyingClass, 30);
      const previousSchool = cleanStr(body.previousSchool, 120);
      const notes = cleanStr(body.notes, 500);

      if (!studentName || !parentName || phone.replace(/[^0-9]/g, "").length < 8 || !applyingClass) {
        return jsonResponse({ error: "Student name, parent name, valid phone number, and class are required." }, 400, cors);
      }
      const id = `inq_${crypto.randomUUID().slice(0, 12)}`;
      await db
        .prepare(
          `INSERT INTO admission_inquiries (id, student_name, parent_name, phone, applying_class, previous_school, notes, status, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, 'NEW', ?)`
        )
        .bind(id, studentName, parentName, phone, applyingClass, previousSchool, notes, Date.now())
        .run();
      return jsonResponse(
        { id, message: "Admission inquiry received! Our Doon Public School, Etah admission desk will contact you shortly." },
        201,
        cors
      );
    }

    if (path === "/api/auth/claim-admin" && method === "POST") {
      const boot = await getAdminBootstrapState(db, env);
      if (!boot.needsSetup) {
        return jsonResponse({ error: "Initial Principal/Admin setup is already completed." }, 409, cors);
      }
      const body = await request.json().catch(() => ({}));
      const fullName = cleanStr(body.fullName || "Dr. Principal (Admin)", 100);
      const userId = cleanStr(body.userId || "ADMIN01", 40);
      const phone = cleanStr(body.phone || "", 30);
      const password = String(body.password || "").trim();
      if (password.length < 6) {
        return jsonResponse({ error: "Choose an Admin password of at least 6 characters." }, 400, cors);
      }
      const id = `usr_${crypto.randomUUID().slice(0, 12)}`;
      const now = Date.now();
      const pwHash = await hashPassword(password);
      await db
        .prepare(
          `INSERT INTO users (id, role, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        )
        .bind(id, "ADMIN", userId, pwHash, fullName, phone, "All Wings", "", "", "Administration", "", 0, 0, now, now)
        .run();

      const created = await db.prepare("SELECT * FROM users WHERE id = ?").bind(id).first();
      const token = await signJwt({ sub: id, role: "ADMIN" }, env.JWT_SECRET);
      return jsonResponse({ token, user: mapUserRow(created) }, 201, cors);
    }

    if (path === "/api/auth/login" && method === "POST") {
      const body = await request.json().catch(() => ({}));
      const role = cleanStr(body.role, 30);
      const userId = cleanStr(body.userId, 40);
      const password = String(body.password || "").trim();
      if (!VALID_ROLES.has(role) || !userId || !password) {
        return jsonResponse({ error: "Role, User ID, and password are required." }, 400, cors);
      }

      // Support configured server-side ADMIN_BOOT_PASSWORD if initial admin hasn't been created in DB yet
      if (role === "ADMIN" && env.ADMIN_BOOT_PASSWORD && userId.toUpperCase() === "ADMIN01") {
        const existingAdmin = await db
          .prepare("SELECT * FROM users WHERE role = 'ADMIN' AND LOWER(user_id) = LOWER(?) LIMIT 1")
          .bind(userId)
          .first();
        if (!existingAdmin && password === String(env.ADMIN_BOOT_PASSWORD).trim()) {
          const id = `usr_admin_${crypto.randomUUID().slice(0, 8)}`;
          const now = Date.now();
          const pwHash = await hashPassword(password);
          await db
            .prepare(
              `INSERT INTO users (id, role, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
            )
            .bind(id, "ADMIN", userId, pwHash, "Principal / Admin", "", "All Wings", "", "", "Administration", "", 0, 0, now, now)
            .run();
          const created = await db.prepare("SELECT * FROM users WHERE id = ?").bind(id).first();
          const token = await signJwt({ sub: id, role: "ADMIN" }, env.JWT_SECRET);
          return jsonResponse({ token, user: mapUserRow(created) }, 200, cors);
        }
      }

      const userRow = await db
        .prepare("SELECT * FROM users WHERE LOWER(user_id) = LOWER(?) LIMIT 1")
        .bind(userId)
        .first();
      if (!userRow) {
        return jsonResponse({ error: "Invalid User ID or password." }, 401, cors);
      }
      const allowedRoles = getAllowedRoles(userRow.role, userRow.extra_roles);
      if (!allowedRoles.includes(role)) {
        return jsonResponse({ error: "Invalid User ID or password." }, 401, cors);
      }
      const ok = await verifyPassword(password, userRow.password_hash);
      if (!ok) {
        return jsonResponse({ error: "Invalid User ID or password." }, 401, cors);
      }
      const token = await signJwt({ sub: userRow.id, role }, env.JWT_SECRET);
      return jsonResponse({ token, user: mapUserRow(userRow, role) }, 200, cors);
    }

    // All endpoints below require valid Bearer JWT
    const actor = await requireAuth(request, env, db);
    if (!actor) {
      return jsonResponse({ error: "Authentication required. Please sign in." }, 401, cors);
    }

    if (path === "/api/auth/switch-role" && method === "POST") {
      const body = await request.json().catch(() => ({}));
      const targetRole = cleanStr(body.role, 30);
      if (!actor.roles.includes(targetRole)) {
        return jsonResponse({ error: "Your account does not have permission for that role." }, 403, cors);
      }
      const token = await signJwt({ sub: actor.id, role: targetRole }, env.JWT_SECRET);
      return jsonResponse({ token, user: mapUserRow(actor.rawRow, targetRole) }, 200, cors);
    }

    if (path === "/api/dashboard" && method === "GET") {
      await ensureDefaultGateway(db);
      const gw = await db.prepare("SELECT * FROM gateway_config LIMIT 1").first();
      const circulars = await db
        .prepare("SELECT * FROM circulars ORDER BY created_at DESC LIMIT 40")
        .all();
      const posters = await db
        .prepare("SELECT * FROM campus_posters ORDER BY date DESC, created_at DESC LIMIT 15")
        .all();
      const craftProjects = await db
        .prepare("SELECT * FROM craft_projects ORDER BY created_at DESC LIMIT 35")
        .all();
      const myLikes = await db
        .prepare("SELECT project_id FROM craft_likes WHERE liker_id = ?")
        .bind(actor.id)
        .all();

      const classFilter = cleanStr(url.searchParams.get("className") || actor.class_name, 30);
      const sectionFilter = cleanStr(url.searchParams.get("section") || actor.section, 10);

      let users = [];
      let dptDaily = [];
      let dptWeekly = [];
      let attendance = [];
      let leaves = [];
      let homework = [];
      let termMarks = [];
      let feeItems = [];
      let payments = [];
      let doubts = [];
      let timetables = [];

      if (actor.role === "ADMIN") {
        const search = cleanStr(url.searchParams.get("search") || "", 60).toLowerCase();
        const roleFilter = cleanStr(url.searchParams.get("role") || "", 20);
        let uSql = "SELECT * FROM users";
        const binds = [];
        const clauses = [];
        if (roleFilter && VALID_ROLES.has(roleFilter)) {
          clauses.push("role = ?");
          binds.push(roleFilter);
        }
        if (search) {
          clauses.push("(LOWER(full_name) LIKE ? OR LOWER(user_id) LIKE ? OR LOWER(class_name) LIKE ?)");
          const pat = `%${search}%`;
          binds.push(pat, pat, pat);
        }
        if (clauses.length > 0) {
          uSql += " WHERE " + clauses.join(" AND ");
        }
        uSql += " ORDER BY created_at DESC LIMIT 200";
        const uRes = await db.prepare(uSql).bind(...binds).all();
        users = (uRes.results || []).map(mapUserRow);

        const [attRes, dailyRes, weeklyRes, feeRes, payRes, ttRes, countStudents, countTeachers, countFeeMgr, inqRes] = await Promise.all([
          db.prepare("SELECT * FROM attendance ORDER BY updated_at DESC LIMIT 250").all(),
          db.prepare("SELECT * FROM dpt_daily ORDER BY date DESC LIMIT 120").all(),
          db.prepare("SELECT * FROM dpt_weekly ORDER BY updated_at DESC LIMIT 200").all(),
          db.prepare("SELECT * FROM fee_items ORDER BY created_at DESC LIMIT 250").all(),
          db.prepare("SELECT * FROM fee_payments ORDER BY created_at DESC LIMIT 200").all(),
          db.prepare("SELECT * FROM timetables").all(),
          db.prepare("SELECT COUNT(*) as cnt FROM users WHERE role = 'STUDENT'").first(),
          db.prepare("SELECT COUNT(*) as cnt FROM users WHERE role = 'TEACHER'").first(),
          db.prepare("SELECT COUNT(*) as cnt FROM users WHERE role = 'FEE_MANAGER'").first(),
          db.prepare("SELECT * FROM admission_inquiries ORDER BY created_at DESC LIMIT 60").all().catch(() => ({ results: [] })),
        ]);
        attendance = attRes.results || [];
        dptDaily = dailyRes.results || [];
        dptWeekly = weeklyRes.results || [];
        feeItems = feeRes.results || [];
        payments = payRes.results || [];
        timetables = ttRes.results || [];

        return jsonResponse(
          {
            currentUser: mapUserRow(actor.rawRow, actor.role),
            stats: {
              totalStudents: Number(countStudents?.cnt || 0),
              totalTeachers: Number(countTeachers?.cnt || 0),
              totalFeeManagers: Number(countFeeMgr?.cnt || 0),
            },
            gateway: mapGatewayRow(gw),
            users,
            circulars: circulars.results || [],
            campusPosters: posters.results || [],
            craftProjects: craftProjects.results || [],
            likedProjectIds: (myLikes.results || []).map((r) => r.project_id),
            attendance,
            dptDaily,
            dptWeekly,
            feeItems,
            payments,
            timetables,
            admissionInquiries: inqRes.results || [],
          },
          200,
          cors
        );
      } else if (actor.role === "TEACHER") {
        const [uRes, dailyRes, weeklyRes, attRes, leaveRes, hwRes, markRes, doubtRes, ttRes] = await Promise.all([
          db.prepare("SELECT * FROM users WHERE role = 'STUDENT' AND class_name = ? AND section = ? ORDER BY user_id ASC LIMIT 120")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM dpt_daily WHERE class_name = ? AND section = ? ORDER BY date DESC LIMIT 60")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM dpt_weekly WHERE class_name = ? AND section = ? ORDER BY updated_at DESC LIMIT 180")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM attendance WHERE class_name = ? AND section = ? ORDER BY date DESC LIMIT 200")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM leaves WHERE class_name = ? AND section = ? ORDER BY created_at DESC LIMIT 80")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM homework WHERE class_name = ? AND section = ? ORDER BY created_at DESC LIMIT 60")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM term_marks WHERE class_name = ? AND section = ? ORDER BY updated_at DESC LIMIT 120")
            .bind(classFilter, sectionFilter)
            .all(),
          db.prepare("SELECT * FROM doubts ORDER BY created_at DESC LIMIT 80").all(),
          db.prepare("SELECT * FROM timetables WHERE class_name = ? AND section = ?")
            .bind(classFilter, sectionFilter)
            .all(),
        ]);
        users = (uRes.results || []).map(mapUserRow);
        dptDaily = dailyRes.results || [];
        dptWeekly = weeklyRes.results || [];
        attendance = attRes.results || [];
        leaves = leaveRes.results || [];
        homework = hwRes.results || [];
        termMarks = markRes.results || [];
        doubts = doubtRes.results || [];
        timetables = ttRes.results || [];
      } else if (actor.role === "FEE_MANAGER") {
        const search = cleanStr(url.searchParams.get("search") || "", 60).toLowerCase();
        const fmClass = cleanStr(url.searchParams.get("className") || "", 30);
        const clauses = ["role = 'STUDENT'"];
        const binds = [];
        if (fmClass) {
          clauses.push("class_name = ?");
          binds.push(fmClass);
        }
        if (search) {
          clauses.push("(LOWER(full_name) LIKE ? OR LOWER(user_id) LIKE ? OR LOWER(class_name) LIKE ?)");
          const pat = `%${search}%`;
          binds.push(pat, pat, pat);
        }
        const stuSql = `SELECT * FROM users WHERE ${clauses.join(" AND ")} ORDER BY user_id ASC LIMIT 200`;
        const [uRes, feeRes, payRes] = await Promise.all([
          db.prepare(stuSql).bind(...binds).all(),
          db.prepare("SELECT * FROM fee_items ORDER BY created_at DESC LIMIT 250").all(),
          db.prepare("SELECT * FROM fee_payments ORDER BY created_at DESC LIMIT 250").all(),
        ]);
        users = (uRes.results || []).map(mapUserRow);
        feeItems = feeRes.results || [];
        payments = payRes.results || [];
      } else {
        // STUDENT — strictly scoped to self + own class-section (~80-150 row reads total!)
        const [attRes, leaveRes, hwRes, dailyRes, weeklyRes, markRes, feeRes, payRes, doubtRes, ttRes] = await Promise.all([
          db.prepare("SELECT * FROM attendance WHERE student_doc_id = ? ORDER BY date DESC LIMIT 60").bind(actor.id).all(),
          db.prepare("SELECT * FROM leaves WHERE student_doc_id = ? ORDER BY created_at DESC LIMIT 30").bind(actor.id).all(),
          db.prepare("SELECT * FROM homework WHERE class_name = ? AND section = ? ORDER BY created_at DESC LIMIT 30")
            .bind(actor.class_name, actor.section)
            .all(),
          db.prepare("SELECT * FROM dpt_daily WHERE class_name = ? AND section = ? ORDER BY date DESC LIMIT 30")
            .bind(actor.class_name, actor.section)
            .all(),
          db.prepare("SELECT * FROM dpt_weekly WHERE student_doc_id = ? ORDER BY updated_at DESC LIMIT 60").bind(actor.id).all(),
          db.prepare("SELECT * FROM term_marks WHERE student_doc_id = ? ORDER BY updated_at DESC LIMIT 12").bind(actor.id).all(),
          db.prepare("SELECT * FROM fee_items WHERE student_doc_id = ? ORDER BY created_at DESC LIMIT 40").bind(actor.id).all(),
          db.prepare("SELECT * FROM fee_payments WHERE student_doc_id = ? ORDER BY created_at DESC LIMIT 40").bind(actor.id).all(),
          db.prepare("SELECT * FROM doubts WHERE student_doc_id = ? ORDER BY created_at DESC LIMIT 30").bind(actor.id).all(),
          db.prepare("SELECT * FROM timetables WHERE class_name = ? AND section = ? LIMIT 1")
            .bind(actor.class_name, actor.section)
            .all(),
        ]);
        users = [mapUserRow(actor.rawRow, actor.role)];
        attendance = attRes.results || [];
        leaves = leaveRes.results || [];
        homework = hwRes.results || [];
        dptDaily = dailyRes.results || [];
        dptWeekly = weeklyRes.results || [];
        termMarks = markRes.results || [];
        feeItems = feeRes.results || [];
        payments = payRes.results || [];
        doubts = doubtRes.results || [];
        timetables = ttRes.results || [];
      }

      return jsonResponse(
        {
          currentUser: mapUserRow(actor.rawRow, actor.role),
          gateway: mapGatewayRow(gw),
          users,
          circulars: circulars.results || [],
          campusPosters: posters.results || [],
          craftProjects: craftProjects.results || [],
          likedProjectIds: (myLikes.results || []).map((r) => r.project_id),
          dptDaily,
          dptWeekly,
          attendance,
          leaves,
          homework,
          termMarks,
          feeItems,
          payments,
          doubts,
          timetables,
        },
        200,
        cors
      );
    }

    // Profile self-update (All roles)
    if (path === "/api/profile" && method === "PUT") {
      const body = await request.json().catch(() => ({}));
      const fullName = cleanStr(body.fullName, 100);
      const phone = cleanStr(body.phone, 30);
      const newPassword = String(body.password || "").trim();
      if (!fullName || !phone) {
        return jsonResponse({ error: "Full name and phone are required." }, 400, cors);
      }
      const pwHash = newPassword ? await hashPassword(newPassword) : actor.password_hash;
      const now = Date.now();
      await db
        .prepare("UPDATE users SET full_name = ?, phone = ?, password_hash = ?, updated_at = ? WHERE id = ?")
        .bind(fullName, phone, pwHash, now, actor.id)
        .run();
      const updated = await db.prepare("SELECT * FROM users WHERE id = ?").bind(actor.id).first();
      return jsonResponse({ user: mapUserRow(updated, actor.role) }, 200, cors);
    }

    // Create / Update User (Admin full control, Teacher strictly own class-section Students)
    if (path === "/api/users" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") {
        return jsonResponse({ error: "Only Admin or Teacher can manage accounts." }, 403, cors);
      }
      await ensureUsersExtraRolesColumn(db);
      const body = await request.json().catch(() => ({}));
      const isEdit = Boolean(body.id);
      const targetRole = actor.role === "TEACHER" ? "STUDENT" : cleanStr(body.role, 30);
      if (!VALID_ROLES.has(targetRole)) {
        return jsonResponse({ error: "Invalid user role." }, 400, cors);
      }
      const extraRolesList =
        actor.role === "ADMIN" && targetRole === "TEACHER"
          ? parseExtraRoles(body.extraRoles ?? body.roles, targetRole)
          : [];
      const extraRolesStr = extraRolesList.join(",");
      const className = actor.role === "TEACHER" ? actor.class_name : cleanStr(body.className, 30);
      const section = actor.role === "TEACHER" ? actor.section : cleanStr(body.section, 10);
      const wing = cleanStr(body.wing, 50) || actor.wing || "";
      const fullName = cleanStr(body.fullName, 100);
      const phone = cleanStr(body.phone, 30);
      const subject = targetRole === "TEACHER" ? cleanStr(body.subject, 120) : "";
      const parentName = targetRole === "STUDENT" ? cleanStr(body.parentName, 100) : "";
      const monthlyTuition = targetRole === "STUDENT" ? cleanInt(body.monthlyTuition, 0, 500_000) : 0;
      const transportFee = targetRole === "STUDENT" ? cleanInt(body.transportFee, 0, 200_000) : 0;
      const password = String(body.password || "").trim();

      if (!fullName) {
        return jsonResponse({ error: "Full name is required." }, 400, cors);
      }
      const now = Date.now();

      if (isEdit) {
        const existing = await db.prepare("SELECT * FROM users WHERE id = ?").bind(cleanStr(body.id, 128)).first();
        if (!existing) {
          return jsonResponse({ error: "Account not found." }, 404, cors);
        }
        if (
          actor.role === "TEACHER" &&
          (existing.role !== "STUDENT" || existing.class_name !== actor.class_name || existing.section !== actor.section)
        ) {
          return jsonResponse({ error: "Teachers can only edit students in their own class-section." }, 403, cors);
        }
        const finalUserId = cleanStr(body.userId || existing.user_id, 40);
        const dup = await db
          .prepare("SELECT id FROM users WHERE LOWER(user_id) = LOWER(?) AND id != ?")
          .bind(finalUserId, existing.id)
          .first();
        if (dup) {
          return jsonResponse({ error: `User ID '${finalUserId}' already exists.` }, 409, cors);
        }
        const pwHash = password ? await hashPassword(password) : existing.password_hash;
        await db
          .prepare(
            `UPDATE users SET role = ?, extra_roles = ?, user_id = ?, password_hash = ?, full_name = ?, phone = ?, wing = ?, class_name = ?, section = ?, subject = ?, parent_name = ?, monthly_tuition = ?, transport_fee = ?, updated_at = ? WHERE id = ?`
          )
          .bind(
            targetRole,
            extraRolesStr,
            finalUserId,
            pwHash,
            fullName,
            phone,
            wing,
            className,
            section,
            subject,
            parentName,
            monthlyTuition,
            transportFee,
            now,
            existing.id
          )
          .run();
        const saved = await db.prepare("SELECT * FROM users WHERE id = ?").bind(existing.id).first();
        return jsonResponse({ user: mapUserRow(saved) }, 200, cors);
      } else {
        if (password.length < 4) {
          return jsonResponse({ error: "Password must be at least 4 characters." }, 400, cors);
        }
        let finalUserId = cleanStr(body.userId, 40);
        if (targetRole === "STUDENT" && !finalUserId) {
          finalUserId = await nextStudentRollNumber(db);
        }
        if (!finalUserId) {
          return jsonResponse({ error: "User / Employee ID is required." }, 400, cors);
        }
        const dup = await db.prepare("SELECT id FROM users WHERE LOWER(user_id) = LOWER(?)").bind(finalUserId).first();
        if (dup) {
          return jsonResponse({ error: `User ID '${finalUserId}' already exists.` }, 409, cors);
        }
        const newId = `usr_${crypto.randomUUID().slice(0, 12)}`;
        const pwHash = await hashPassword(password);
        await db
          .prepare(
            `INSERT INTO users (id, role, extra_roles, user_id, password_hash, full_name, phone, wing, class_name, section, subject, parent_name, monthly_tuition, transport_fee, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
          )
          .bind(
            newId,
            targetRole,
            extraRolesStr,
            finalUserId,
            pwHash,
            fullName,
            phone,
            wing,
            className,
            section,
            subject,
            parentName,
            monthlyTuition,
            transportFee,
            now,
            now
          )
          .run();
        const saved = await db.prepare("SELECT * FROM users WHERE id = ?").bind(newId).first();
        return jsonResponse({ user: mapUserRow(saved) }, 201, cors);
      }
    }

    if (path.startsWith("/api/users/") && method === "DELETE") {
      const targetId = cleanStr(path.slice("/api/users/".length), 128);
      if (targetId === actor.id) {
        return jsonResponse({ error: "You cannot delete your own active account." }, 400, cors);
      }
      const existing = await db.prepare("SELECT * FROM users WHERE id = ?").bind(targetId).first();
      if (!existing) return jsonResponse({ error: "Not found" }, 404, cors);
      if (actor.role === "ADMIN" || (actor.role === "TEACHER" && existing.role === "STUDENT" && existing.class_name === actor.class_name && existing.section === actor.section)) {
        await db.prepare("DELETE FROM users WHERE id = ?").bind(targetId).run();
        return jsonResponse({ ok: true }, 200, cors);
      }
      return jsonResponse({ error: "Forbidden" }, 403, cors);
    }

    // Gateway & App Update Config (Admin or Fee Manager)
    if (path === "/api/gateway" && method === "PUT") {
      if (actor.role !== "ADMIN" && actor.role !== "FEE_MANAGER") {
        return jsonResponse({ error: "Only Admin or Fee Manager can update gateway configuration." }, 403, cors);
      }
      const existing = await ensureDefaultGateway(db);
      const body = await request.json().catch(() => ({}));
      const merchantUpi = cleanStr(body.merchantUpi || existing.merchant_upi, 100);
      const payeeName = cleanStr(body.payeeName || existing.payee_name, 100);
      const bankName = cleanStr(body.bankName ?? existing.bank_name, 120);
      const accountNo = cleanStr(body.accountNo ?? existing.account_no, 40);
      const ifsc = cleanStr(body.ifsc ?? existing.ifsc, 20);
      const razorpayKeyId = cleanStr(body.razorpayKeyId ?? existing.razorpay_key_id, 80);
      const latestVersionCode = cleanInt(body.latestVersionCode ?? existing.latest_version_code, 1, 999999);
      const latestVersionName = cleanStr(body.latestVersionName ?? existing.latest_version_name, 30);
      const apkDownloadUrl = cleanStr(body.apkDownloadUrl ?? existing.apk_download_url, 500);
      const githubRepoSlug = cleanStr(body.githubRepoSlug ?? existing.github_repo_slug, 160);
      const updateNotes = cleanStr(body.updateNotes ?? existing.update_notes, 400);
      const now = Date.now();

      await db
        .prepare(
          `UPDATE gateway_config SET merchant_upi = ?, payee_name = ?, bank_name = ?, account_no = ?, ifsc = ?, razorpay_key_id = ?, latest_version_code = ?, latest_version_name = ?, apk_download_url = ?, github_repo_slug = ?, update_notes = ?, updated_at = ? WHERE id = ?`
        )
        .bind(
          merchantUpi,
          payeeName,
          bankName,
          accountNo,
          ifsc,
          razorpayKeyId,
          latestVersionCode,
          latestVersionName,
          apkDownloadUrl,
          githubRepoSlug,
          updateNotes,
          now,
          existing.id
        )
        .run();
      const updated = await db.prepare("SELECT * FROM gateway_config LIMIT 1").first();
      return jsonResponse({ gateway: mapGatewayRow(updated) }, 200, cors);
    }

    // Circulars & Targeted Notices (Admin or Teacher)
    if (path === "/api/circulars" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") {
        return jsonResponse({ error: "Only Admin or Teacher can publish notices." }, 403, cors);
      }
      const body = await request.json().catch(() => ({}));
      const title = cleanStr(body.title, 150);
      const content = cleanStr(body.content, 2000);
      if (!title || !content) {
        return jsonResponse({ error: "Title and message are required." }, 400, cors);
      }
      const id = `cir_${crypto.randomUUID().slice(0, 12)}`;
      const now = Date.now();
      await db
        .prepare(
          `INSERT INTO circulars (id, title, category, audience, target_wing, target_class, target_section, date, content, posted_by, recipient_doc_id, recipient_name, sender_role, attachment_name, attachment_mime_type, attachment_data, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        )
        .bind(
          id,
          title,
          cleanStr(body.category || "Notice", 40),
          cleanStr(body.audience || "All Wings", 120),
          cleanStr(body.targetWing, 60),
          cleanStr(body.targetClass, 30),
          cleanStr(body.targetSection, 10),
          cleanStr(body.date || todayIso(), 20),
          content,
          actor.full_name,
          cleanStr(body.recipientDocId, 128),
          cleanStr(body.recipientName, 120),
          actor.role === "TEACHER" ? "TEACHER" : "ADMIN",
          cleanStr(body.attachmentName, 160),
          cleanStr(body.attachmentMimeType, 120),
          String(body.attachmentData || "").slice(0, 450_000),
          now
        )
        .run();
      return jsonResponse({ id }, 201, cors);
    }

    if (path.startsWith("/api/circulars/") && method === "DELETE") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const id = cleanStr(path.slice("/api/circulars/".length), 128);
      await db.prepare("DELETE FROM circulars WHERE id = ?").bind(id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    // Daily Campus Posters (Admin only)
    if (path === "/api/posters" && method === "POST") {
      if (actor.role !== "ADMIN") return jsonResponse({ error: "Admin only" }, 403, cors);
      const body = await request.json().catch(() => ({}));
      const title = cleanStr(body.title, 140);
      const imageData = String(body.imageData || "").slice(0, 460_000);
      if (!title || !imageData) return jsonResponse({ error: "Title and image required." }, 400, cors);
      const id = `pst_${crypto.randomUUID().slice(0, 12)}`;
      await db
        .prepare("INSERT INTO campus_posters (id, title, subtitle, date, image_data, posted_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .bind(id, title, cleanStr(body.subtitle, 180), cleanStr(body.date || todayIso(), 30), imageData, actor.full_name, Date.now())
        .run();
      return jsonResponse({ id }, 201, cors);
    }

    if (path.startsWith("/api/posters/") && method === "DELETE") {
      if (actor.role !== "ADMIN") return jsonResponse({ error: "Admin only" }, 403, cors);
      await db.prepare("DELETE FROM campus_posters WHERE id = ?").bind(cleanStr(path.slice("/api/posters/".length), 128)).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    // Timetables (Admin or Teacher)
    if (path === "/api/timetables" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const body = await request.json().catch(() => ({}));
      const className = cleanStr(body.className, 30);
      const section = cleanStr(body.section, 10);
      const slots = Array.isArray(body.slots) ? body.slots.slice(0, 16) : [];
      if (!className || !section || slots.length === 0) {
        return jsonResponse({ error: "Class, section, and timetable slots required." }, 400, cors);
      }
      const id = `tt_${className}_${section}`.replace(/[^a-zA-Z0-9_-]/g, "_");
      await db
        .prepare(
          `INSERT INTO timetables (id, class_name, section, slots_json, updated_by, updated_at)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET slots_json = excluded.slots_json, updated_by = excluded.updated_by, updated_at = excluded.updated_at`
        )
        .bind(id, className, section, JSON.stringify(slots), actor.full_name, Date.now())
        .run();
      return jsonResponse({ id }, 200, cors);
    }

    // Daily DPT / Attendance / Weekly DPT / Homework / Term Marks (Admin or Teacher)
    if (path === "/api/dpt-daily" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const b = await request.json().catch(() => ({}));
      const date = cleanStr(b.date || todayIso(), 20);
      const className = cleanStr(b.className, 30);
      const section = cleanStr(b.section, 10);
      const subject = cleanStr(b.subject, 60);
      const assessmentType = String(b.assessmentType || "DPT").toUpperCase() === "EXAM" ? "EXAM" : "DPT";
      const id = `dpt_${date}_${className}_${section}_${subject}_${assessmentType}`.replace(/[^a-zA-Z0-9_-]/g, "_");
      await db
        .prepare(
          `INSERT INTO dpt_daily (id, date, class_name, section, subject, assessment_type, held, topic, max_marks, notes, reason, attachment_name, attachment_mime_type, attachment_data, teacher_id, teacher_name, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET held=excluded.held, topic=excluded.topic, max_marks=excluded.max_marks, notes=excluded.notes, reason=excluded.reason, updated_at=excluded.updated_at`
        )
        .bind(
          id,
          date,
          className,
          section,
          subject,
          assessmentType,
          b.held === false ? 0 : 1,
          cleanStr(b.topic, 200),
          cleanInt(b.maxMarks, 0, 100),
          cleanStr(b.notes, 1000),
          cleanStr(b.reason, 200),
          cleanStr(b.attachmentName, 160),
          cleanStr(b.attachmentMimeType, 120),
          String(b.attachmentData || "").slice(0, 450_000),
          actor.user_id,
          actor.full_name,
          Date.now()
        )
        .run();
      return jsonResponse({ id }, 200, cors);
    }

    if (path === "/api/dpt-weekly" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const b = await request.json().catch(() => ({}));
      const entries = Array.isArray(b.entries) ? b.entries.slice(0, 150) : [];
      const now = Date.now();
      const stmts = entries.map((e) => {
        const week = cleanStr(e.week, 50);
        const cls = cleanStr(e.className, 30);
        const sec = cleanStr(e.section, 10);
        const sub = cleanStr(e.subject, 60);
        const stId = cleanStr(e.studentDocId, 128);
        const id = `wk_${week}_${cls}_${sec}_${sub}_${stId}`.replace(/[^a-zA-Z0-9_-]/g, "_");
        const maxM = cleanInt(e.maxMarks, 1, 100);
        const scored = cleanInt(e.scored, 0, maxM);
        return db
          .prepare(
            `INSERT INTO dpt_weekly (id, week, class_name, section, subject, student_doc_id, student_name, admission_no, scored, max_marks, teacher_id, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT(id) DO UPDATE SET scored=excluded.scored, max_marks=excluded.max_marks, updated_at=excluded.updated_at`
          )
          .bind(id, week, cls, sec, sub, stId, cleanStr(e.studentName, 100), cleanStr(e.admissionNo, 40), scored, maxM, actor.user_id, now);
      });
      if (stmts.length > 0) await db.batch(stmts);
      return jsonResponse({ count: stmts.length }, 200, cors);
    }

    if (path === "/api/attendance" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const b = await request.json().catch(() => ({}));
      const records = Array.isArray(b.records) ? b.records : [b];
      const now = Date.now();
      const stmts = [];
      for (const r of records.slice(0, 150)) {
        const date = cleanStr(r.date || todayIso(), 20);
        const cls = cleanStr(r.className, 30);
        const sec = cleanStr(r.section, 10);
        const stId = cleanStr(r.studentDocId, 128);
        const status = ["Present", "Absent", "Leave"].includes(r.status) ? r.status : "Present";
        const id = `att_${date}_${stId}`.replace(/[^a-zA-Z0-9_-]/g, "_");
        stmts.push(
          db
            .prepare(
              `INSERT INTO attendance (id, date, class_name, section, student_doc_id, status, marked_by, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(id) DO UPDATE SET status=excluded.status, marked_by=excluded.marked_by, updated_at=excluded.updated_at`
            )
            .bind(id, date, cls, sec, stId, status, actor.full_name, now)
        );
      }
      if (stmts.length > 0) await db.batch(stmts);
      return jsonResponse({ count: stmts.length }, 200, cors);
    }

    if (path === "/api/homework" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const b = await request.json().catch(() => ({}));
      const title = cleanStr(b.title, 150);
      if (!title) return jsonResponse({ error: "Title required" }, 400, cors);
      const id = `hw_${crypto.randomUUID().slice(0, 12)}`;
      await db
        .prepare("INSERT INTO homework (id, class_name, section, subject, title, description, due_date, teacher_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
        .bind(id, cleanStr(b.className, 30), cleanStr(b.section, 10), cleanStr(b.subject, 60), title, cleanStr(b.description, 1500), cleanStr(b.dueDate || todayIso(), 20), actor.full_name, Date.now())
        .run();
      return jsonResponse({ id }, 201, cors);
    }

    if (path === "/api/term-marks" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const b = await request.json().catch(() => ({}));
      const term = cleanStr(b.term, 60);
      const stId = cleanStr(b.studentDocId, 128);
      if (!term || !stId || typeof b.marks !== "object") return jsonResponse({ error: "Invalid term report" }, 400, cors);
      const id = `tm_${term}_${stId}`.replace(/[^a-zA-Z0-9_-]/g, "_");
      await db
        .prepare(
          `INSERT INTO term_marks (id, term, class_name, section, student_doc_id, student_name, admission_no, marks_json, remarks, published_by, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET marks_json=excluded.marks_json, remarks=excluded.remarks, updated_at=excluded.updated_at`
        )
        .bind(id, term, cleanStr(b.className, 30), cleanStr(b.section, 10), stId, cleanStr(b.studentName, 100), cleanStr(b.admissionNo, 40), JSON.stringify(b.marks), cleanStr(b.remarks, 400), actor.full_name, Date.now())
        .run();
      return jsonResponse({ id }, 200, cors);
    }

    // Leaves
    if (path === "/api/leaves" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const reason = cleanStr(b.reason, 500);
      if (!reason) return jsonResponse({ error: "Leave reason required" }, 400, cors);
      const id = `lv_${crypto.randomUUID().slice(0, 12)}`;
      await db
        .prepare("INSERT INTO leaves (id, student_doc_id, student_name, admission_no, class_name, section, from_date, to_date, reason, status, teacher_remark, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Pending', '', ?)")
        .bind(id, actor.id, actor.full_name, actor.user_id, actor.class_name, actor.section, cleanStr(b.fromDate || todayIso(), 20), cleanStr(b.toDate || todayIso(), 20), reason, Date.now())
        .run();
      return jsonResponse({ id }, 201, cors);
    }

    if (path.startsWith("/api/leaves/") && method === "PUT") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const id = cleanStr(path.slice("/api/leaves/".length), 128);
      const b = await request.json().catch(() => ({}));
      const status = ["Approved", "Rejected"].includes(b.status) ? b.status : "Pending";
      await db.prepare("UPDATE leaves SET status = ?, teacher_remark = ? WHERE id = ?").bind(status, cleanStr(b.remark, 300), id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    // Doubts
    if (path === "/api/doubts" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const message = cleanStr(b.message, 1000);
      if (!message) return jsonResponse({ error: "Message required" }, 400, cors);
      const id = `dbt_${crypto.randomUUID().slice(0, 12)}`;
      await db
        .prepare("INSERT INTO doubts (id, student_doc_id, student_name, class_name, subject, message, reply, replied_by, status, date_label, created_at) VALUES (?, ?, ?, ?, ?, ?, '', '', 'OPEN', ?, ?)")
        .bind(id, actor.id, actor.full_name, `${actor.class_name}-${actor.section}`, cleanStr(b.subject || "General", 60), message, todayIso(), Date.now())
        .run();
      return jsonResponse({ id }, 201, cors);
    }

    if (path.startsWith("/api/doubts/") && method === "PUT") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const id = cleanStr(path.slice("/api/doubts/".length), 128);
      const b = await request.json().catch(() => ({}));
      await db.prepare("UPDATE doubts SET reply = ?, replied_by = ?, status = 'REPLIED' WHERE id = ?").bind(cleanStr(b.reply, 1000), actor.full_name, id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    // Fees & Payments
    if (path === "/api/fee-bills" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "FEE_MANAGER") {
        return jsonResponse({ error: "Forbidden" }, 403, cors);
      }
      const b = await request.json().catch(() => ({}));
      const scope = String(b.scope || "CLASSES").trim().toUpperCase();
      const billType = String(b.billType || "BOTH").trim().toUpperCase();
      const month = cleanStr(b.month, 40);
      const section = cleanStr(b.section, 10);
      const rawClasses = Array.isArray(b.classes)
        ? [...new Set(b.classes.map((c) => cleanStr(c, 30)).filter(Boolean))].slice(0, 20)
        : [];
      const rawStudentIds = Array.isArray(b.studentDocIds)
        ? [...new Set(b.studentDocIds.map((id) => cleanStr(id, 128)).filter(Boolean))].slice(0, 500)
        : [];

      if (!month) {
        return jsonResponse({ error: "Billing month / term is required." }, 400, cors);
      }
      if (!["CLASSES", "STUDENTS", "ALL"].includes(scope)) {
        return jsonResponse({ error: "Invalid target scope." }, 400, cors);
      }
      if (!["BOTH", "TUITION", "TRANSPORT", "MISC"].includes(billType)) {
        return jsonResponse({ error: "Invalid fee bill type." }, 400, cors);
      }

      let miscTitle = "";
      let miscAmount = 0;
      if (billType === "MISC") {
        miscTitle = cleanStr(b.miscTitle || "Extra Fee", 80);
        miscAmount = cleanInt(b.miscAmount, 0, 1_000_000);
        if (!miscTitle || miscAmount <= 0) {
          return jsonResponse({ error: "Extra fee title and positive amount are required." }, 400, cors);
        }
      }

      let matchedStudents = [];
      if (scope === "CLASSES") {
        if (rawClasses.length === 0) {
          return jsonResponse({ error: "Select at least one class to bill." }, 400, cors);
        }
        const ph = rawClasses.map(() => "?").join(",");
        const binds = [...rawClasses];
        let sql = `SELECT id, full_name, class_name, section, monthly_tuition, transport_fee FROM users WHERE role = 'STUDENT' AND class_name IN (${ph})`;
        if (section) {
          sql += " AND section = ?";
          binds.push(section);
        }
        const res = await db.prepare(sql).bind(...binds).all();
        matchedStudents = res.results || [];
      } else if (scope === "STUDENTS") {
        if (rawStudentIds.length === 0) {
          return jsonResponse({ error: "Select at least one student to bill." }, 400, cors);
        }
        for (let i = 0; i < rawStudentIds.length; i += 80) {
          const chunk = rawStudentIds.slice(i, i + 80);
          const ph = chunk.map(() => "?").join(",");
          const res = await db
            .prepare(`SELECT id, full_name, class_name, section, monthly_tuition, transport_fee FROM users WHERE role = 'STUDENT' AND id IN (${ph})`)
            .bind(...chunk)
            .all();
          matchedStudents.push(...(res.results || []));
        }
      } else {
        const res = await db
          .prepare("SELECT id, full_name, class_name, section, monthly_tuition, transport_fee FROM users WHERE role = 'STUDENT'")
          .all();
        matchedStudents = res.results || [];
      }

      const existingRows = await db
        .prepare("SELECT student_doc_id, title FROM fee_items WHERE month = ?")
        .bind(month)
        .all();
      const existingKeys = new Set(
        (existingRows.results || []).map((r) => `${r.student_doc_id}:::${String(r.title || "").trim().toLowerCase()}`)
      );

      const now = Date.now();
      const stmts = [];
      const billedStudentIds = new Set();
      let skippedDuplicates = 0;

      const queueFee = (studentId, title, amount) => {
        const cleanAmt = cleanInt(amount, 0, 1_000_000);
        if (cleanAmt <= 0) return;
        const key = `${studentId}:::${title.trim().toLowerCase()}`;
        if (existingKeys.has(key)) {
          skippedDuplicates += 1;
          return;
        }
        existingKeys.add(key);
        billedStudentIds.add(studentId);
        const id = `fee_${crypto.randomUUID().slice(0, 12)}`;
        stmts.push(
          db
            .prepare("INSERT INTO fee_items (id, student_doc_id, title, month, amount, status, payment_id, created_at) VALUES (?, ?, ?, ?, ?, 'UNPAID', '', ?)")
            .bind(id, studentId, cleanStr(title, 120), month, cleanAmt, now)
        );
      };

      for (const st of matchedStudents) {
        if (billType === "BOTH" || billType === "TUITION") {
          queueFee(st.id, `Monthly Tuition Fee (${month})`, st.monthly_tuition);
        }
        if (billType === "BOTH" || billType === "TRANSPORT") {
          queueFee(st.id, `Transport / Bus Fee (${month})`, st.transport_fee);
        }
        if (billType === "MISC") {
          queueFee(st.id, `${miscTitle} (${month})`, miscAmount);
        }
      }

      for (let i = 0; i < stmts.length; i += 80) {
        await db.batch(stmts.slice(i, i + 80));
      }

      return jsonResponse(
        {
          itemsCreated: stmts.length,
          studentsBilled: billedStudentIds.size,
          skippedDuplicates,
          totalStudentsMatched: matchedStudents.length,
        },
        201,
        cors
      );
    }

    if (path === "/api/fee-items" && method === "POST") {
      if (actor.role !== "ADMIN" && actor.role !== "FEE_MANAGER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const b = await request.json().catch(() => ({}));
      const items = Array.isArray(b.items) ? b.items : [b];
      const now = Date.now();
      const stmts = [];
      for (const item of items.slice(0, 200)) {
        const amt = cleanInt(item.amount, 1, 1_000_000);
        const stId = cleanStr(item.studentDocId, 128);
        if (!stId) continue;
        const id = `fee_${crypto.randomUUID().slice(0, 12)}`;
        stmts.push(
          db.prepare("INSERT INTO fee_items (id, student_doc_id, title, month, amount, status, payment_id, created_at) VALUES (?, ?, ?, ?, ?, 'UNPAID', '', ?)")
            .bind(id, stId, cleanStr(item.title, 120), cleanStr(item.month, 40), amt, now)
        );
      }
      if (stmts.length > 0) await db.batch(stmts);
      return jsonResponse({ count: stmts.length }, 201, cors);
    }

    if (path === "/api/payments" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const feeIds = Array.isArray(b.feeItemIds) ? b.feeItemIds.map((x) => cleanStr(x, 128)).filter(Boolean) : [];
      const utr = cleanStr(b.utrOrRef, 80);
      if (feeIds.length === 0 || utr.length < 8) return jsonResponse({ error: "Select pending fees and enter a valid UTR." }, 400, cors);
      const placeholders = feeIds.map(() => "?").join(",");
      const feeRows = await db.prepare(`SELECT * FROM fee_items WHERE student_doc_id = ? AND status = 'UNPAID' AND id IN (${placeholders})`).bind(actor.id, ...feeIds).all();
      const matched = feeRows.results || [];
      if (matched.length === 0) return jsonResponse({ error: "No matching unpaid fee items found." }, 400, cors);
      const total = matched.reduce((sum, f) => sum + Number(f.amount || 0), 0);
      const summary = matched.map((f) => f.title).join(", ");
      const payId = `pay_${crypto.randomUUID().slice(0, 12)}`;
      const now = Date.now();
      const stmts = [
        db.prepare("INSERT INTO fee_payments (id, student_doc_id, student_name, admission_no, class_name, fee_item_ids_json, fee_summary, amount, mode, utr_or_ref, upi_app, status, receipt_no, reject_reason, verified_by, verified_at_str, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', '', '', '', '', ?)")
          .bind(payId, actor.id, actor.full_name, actor.user_id, `${actor.class_name}-${actor.section}`, JSON.stringify(matched.map((f) => f.id)), summary, total, cleanStr(b.mode || "UPI", 20) === "RAZORPAY" ? "RAZORPAY" : "UPI", utr, cleanStr(b.upiApp || "GPay", 40), now),
      ];
      for (const f of matched) {
        stmts.push(db.prepare("UPDATE fee_items SET status = 'PENDING_VERIFICATION', payment_id = ? WHERE id = ?").bind(payId, f.id));
      }
      await db.batch(stmts);
      return jsonResponse({ id: payId }, 201, cors);
    }

    if (path.startsWith("/api/payments/") && method === "PUT") {
      if (actor.role !== "ADMIN" && actor.role !== "FEE_MANAGER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const payId = cleanStr(path.slice("/api/payments/".length), 128);
      const payment = await db.prepare("SELECT * FROM fee_payments WHERE id = ?").bind(payId).first();
      if (!payment) return jsonResponse({ error: "Payment not found" }, 404, cors);
      const b = await request.json().catch(() => ({}));
      const action = String(b.action || "").toUpperCase();
      const feeIds = JSON.parse(payment.fee_item_ids_json || "[]");
      const nowStr = todayIso();
      if (action === "VERIFY") {
        const cntRow = await db.prepare("SELECT COUNT(*) as cnt FROM fee_payments WHERE status = 'VERIFIED'").first();
        const receiptNo = `DPS-RCP-${String(Number(cntRow?.cnt || 0) + 102).padStart(4, "0")}`;
        const stmts = [
          db.prepare("UPDATE fee_payments SET status = 'VERIFIED', receipt_no = ?, verified_by = ?, verified_at_str = ? WHERE id = ?")
            .bind(receiptNo, actor.full_name, nowStr, payId),
          ...feeIds.map((fid) => db.prepare("UPDATE fee_items SET status = 'PAID', payment_id = ? WHERE id = ?").bind(payId, fid)),
        ];
        await db.batch(stmts);
        return jsonResponse({ receiptNo }, 200, cors);
      } else {
        const stmts = [
          db.prepare("UPDATE fee_payments SET status = 'REJECTED', reject_reason = ?, verified_by = ?, verified_at_str = ? WHERE id = ?")
            .bind(cleanStr(b.reason || "UTR did not match bank ledger", 200), actor.full_name, nowStr, payId),
          ...feeIds.map((fid) => db.prepare("UPDATE fee_items SET status = 'UNPAID', payment_id = '' WHERE id = ?").bind(fid)),
        ];
        await db.batch(stmts);
        return jsonResponse({ ok: true }, 200, cors);
      }
    }

    // Craft Showcase & Atomic 1-Like-Per-User Enforcement
    if (path === "/api/craft" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const title = cleanStr(b.title, 150);
      const description = cleanStr(b.description, 1500);
      if (!title || !description) return jsonResponse({ error: "Title and description required" }, 400, cors);
      const id = `crf_${crypto.randomUUID().slice(0, 12)}`;
      const authorRole = ["ADMIN", "TEACHER"].includes(actor.role) ? actor.role : "STUDENT";
      await db
        .prepare("INSERT INTO craft_projects (id, title, category, description, materials, author_doc_id, author_name, author_user_id, author_role, class_info, date_label, ai_image_data, ai_prompt_used, likes_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)")
        .bind(id, title, cleanStr(b.category || "Art & Craft", 50), description, cleanStr(b.materials, 500), actor.id, actor.full_name, actor.user_id, authorRole, actor.class_name ? `${actor.class_name}-${actor.section}` : actor.wing, todayIso(), String(b.aiImageData || "").slice(0, 455_000), "", Date.now())
        .run();
      return jsonResponse({ id }, 201, cors);
    }

    if (path.startsWith("/api/craft/") && path.endsWith("/like") && method === "POST") {
      const projectId = cleanStr(path.slice("/api/craft/".length, -"/like".length), 128);
      const proj = await db.prepare("SELECT id, likes_count FROM craft_projects WHERE id = ?").bind(projectId).first();
      if (!proj) return jsonResponse({ error: "Project not found" }, 404, cors);
      const likeId = `${projectId}__${actor.id}`;
      const existingLike = await db.prepare("SELECT like_id FROM craft_likes WHERE like_id = ?").bind(likeId).first();
      if (existingLike) {
        await db.batch([
          db.prepare("DELETE FROM craft_likes WHERE like_id = ?").bind(likeId),
          db.prepare("UPDATE craft_projects SET likes_count = MAX(0, likes_count - 1) WHERE id = ?").bind(projectId),
        ]);
        return jsonResponse({ liked: false }, 200, cors);
      } else {
        await db.batch([
          db.prepare("INSERT INTO craft_likes (like_id, project_id, liker_id, created_at) VALUES (?, ?, ?, ?)").bind(likeId, projectId, actor.id, Date.now()),
          db.prepare("UPDATE craft_projects SET likes_count = likes_count + 1 WHERE id = ?").bind(projectId),
        ]);
        return jsonResponse({ liked: true }, 200, cors);
      }
    }

    if (path.startsWith("/api/craft/") && method === "DELETE") {
      const id = cleanStr(path.slice("/api/craft/".length), 128);
      const proj = await db.prepare("SELECT * FROM craft_projects WHERE id = ?").bind(id).first();
      if (!proj) return jsonResponse({ error: "Not found" }, 404, cors);
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER" && proj.author_doc_id !== actor.id) {
        return jsonResponse({ error: "Forbidden" }, 403, cors);
      }
      await db.batch([
        db.prepare("DELETE FROM craft_likes WHERE project_id = ?").bind(id),
        db.prepare("DELETE FROM craft_projects WHERE id = ?").bind(id),
      ]);
      return jsonResponse({ ok: true }, 200, cors);
    }

    if (path.startsWith("/api/homework/") && method === "DELETE") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const id = cleanStr(path.slice("/api/homework/".length), 128);
      await db.prepare("DELETE FROM homework WHERE id = ?").bind(id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    if (path.startsWith("/api/dpt-daily/") && method === "DELETE") {
      if (actor.role !== "ADMIN" && actor.role !== "TEACHER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const id = cleanStr(path.slice("/api/dpt-daily/".length), 128);
      await db.prepare("DELETE FROM dpt_daily WHERE id = ?").bind(id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    if (path.startsWith("/api/fee-items/") && method === "DELETE") {
      if (actor.role !== "ADMIN" && actor.role !== "FEE_MANAGER") return jsonResponse({ error: "Forbidden" }, 403, cors);
      const id = cleanStr(path.slice("/api/fee-items/".length), 128);
      await db.prepare("DELETE FROM fee_items WHERE id = ?").bind(id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    if (path.startsWith("/api/admission-inquiries/") && method === "PUT") {
      if (actor.role !== "ADMIN") return jsonResponse({ error: "Admin only" }, 403, cors);
      const id = cleanStr(path.slice("/api/admission-inquiries/".length), 128);
      const b = await request.json().catch(() => ({}));
      const status = ["NEW", "CONTACTED", "ENROLLED"].includes(String(b.status || "").toUpperCase())
        ? String(b.status).toUpperCase()
        : "CONTACTED";
      await db.prepare("UPDATE admission_inquiries SET status = ? WHERE id = ?").bind(status, id).run();
      return jsonResponse({ ok: true, status }, 200, cors);
    }

    if (path.startsWith("/api/admission-inquiries/") && method === "DELETE") {
      if (actor.role !== "ADMIN") return jsonResponse({ error: "Admin only" }, 403, cors);
      const id = cleanStr(path.slice("/api/admission-inquiries/".length), 128);
      await db.prepare("DELETE FROM admission_inquiries WHERE id = ?").bind(id).run();
      return jsonResponse({ ok: true }, 200, cors);
    }

    return jsonResponse({ error: "Not found" }, 404, cors);
  } catch (err) {
    return jsonResponse({ error: err?.message || "Internal server error" }, 500, cors);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname.startsWith("/api/")) {
      return handleApiRequest(request, env);
    }
    if (env.ASSETS) {
      return env.ASSETS.fetch(request);
    }
    return new Response("Doon Public School, Etah API Worker is running.", { status: 200 });
  },
};
