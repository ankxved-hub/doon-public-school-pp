"use strict";

const SCHOOL_INFO = {
  name: "Doon Public School, Etah",
  affiliation: "CBSE Affiliated (Nursery to Class XII)",
  trust: "Managed by Murli Krishna Educational Trust",
  motto: "Where Excellence is a Habit! — Excelling with Legacy, Leading with Purpose",
  address: "4th Milestone, Opposite PAC, Kasganj Road, Aruna Nagar, Etah – 207001 (U.P.)",
  phones: "+91 76176 14471, +91 76176 14470",
  email: "doonpublicschooletah@gmail.com",
  leadDeveloper: "Vedant Pratap Singh",
  coDevelopers: ["Dev Pratap Singh", "Ankur Kumar Gupta", "Naitik Yadav", "Advit Pratap"],
  mentors: ["Sudhakar Ojha Sir", "Uday Raj Sir", "Akash Sir"],
};

const WINGS = [
  { name: "Doon Kidz", subtitle: "Pre-Primary Wing", label: "Nursery, LKG, UKG", classes: ["Nursery", "LKG", "UKG"] },
  { name: "Lower School", subtitle: "Primary Wing", label: "Classes I to V", classes: ["Class I", "Class II", "Class III", "Class IV", "Class V"] },
  { name: "Middle School", subtitle: "Upper Primary Wing", label: "Classes VI to VIII", classes: ["Class VI", "Class VII", "Class VIII"] },
  { name: "Upper School", subtitle: "Senior Secondary Wing", label: "Classes IX to XII", classes: ["Class IX", "Class X", "Class XI", "Class XII"] },
];

const ALL_CLASSES = WINGS.flatMap((w) => w.classes);
const SECTIONS = ["A", "B", "C"];
const DPT_WEEKS = ["Week 1 (Nov 2026)", "Week 2 (Nov 2026)", "Week 3 (Nov 2026)", "Week 4 (Nov 2026)"];
const CRAFT_CATEGORIES = ["Art & Craft", "Science Model", "Eco Recycling", "Robotics & STEM", "Clay & Sculpture", "Skill Showcase"];

function wingForClass(className) {
  const found = WINGS.find((w) => w.classes.includes(className));
  return found ? found.name : "Upper School";
}

function subjectsForClass(className) {
  if (["Nursery", "LKG", "UKG"].includes(className)) {
    return [
      "English", "Hindi", "Mathematics", "Rhymes & Phonics", "General Awareness",
      "EVS & Nature", "Art & Craft", "Storytelling", "Music & Movement", "Physical Activity",
    ];
  }
  if (["Class I", "Class II", "Class III", "Class IV", "Class V"].includes(className)) {
    return [
      "English", "Hindi", "Mathematics", "EVS", "Computer Science",
      "General Knowledge", "Moral Science", "Art & Craft", "Music & Dance",
      "Physical Education", "Reasoning & Aptitude",
    ];
  }
  if (["Class VI", "Class VII", "Class VIII"].includes(className)) {
    return [
      "English", "Hindi", "Sanskrit", "Mathematics", "Science", "Physics", "Chemistry",
      "Biology", "Social Science", "History & Civics", "Geography", "Computer Science & AI",
      "General Knowledge", "Moral Science", "Art & Craft", "Physical Education",
    ];
  }
  if (["Class IX", "Class X"].includes(className)) {
    return [
      "English", "Hindi", "Sanskrit", "Mathematics", "Science", "Physics", "Chemistry",
      "Biology", "Social Science", "History", "Geography", "Political Science", "Economics",
      "Information Technology (IT)", "Artificial Intelligence (AI)", "Computer Applications",
      "Physical Education", "Art & Culture",
    ];
  }
  return [
    "English Core", "Hindi Core", "Mathematics", "Applied Mathematics", "Physics", "Chemistry",
    "Biology", "Computer Science", "Informatics Practices", "Accountancy", "Business Studies",
    "Economics", "History", "Geography", "Political Science", "Sociology", "Psychology",
    "Physical Education", "Fine Arts",
  ];
}

const ALL_SUBJECTS = Array.from(new Set(ALL_CLASSES.flatMap((c) => subjectsForClass(c))));

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function pick(obj, camel, snake, fallback = "") {
  if (!obj) return fallback;
  if (obj[camel] !== undefined && obj[camel] !== null) return obj[camel];
  if (obj[snake] !== undefined && obj[snake] !== null) return obj[snake];
  return fallback;
}

function isSafeHttpUrl(raw) {
  try {
    const parsed = new URL(String(raw || "").trim());
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch (_) {
    return false;
  }
}

function isSafeImageSrc(raw) {
  const s = String(raw || "").trim();
  if (!s) return false;
  if (s.startsWith("/") && !s.startsWith("//")) return true;
  if (/^data:image\/(png|jpeg|jpg|webp|svg\+xml);base64,[a-zA-Z0-9+/=]+$/.test(s)) return true;
  return isSafeHttpUrl(s);
}

function safeParseJson(raw, fallback) {
  if (typeof raw !== "string") return raw ?? fallback;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return fallback;
  }
}

function defaultTimetableSlots(className, section) {
  const wing = wingForClass(className);
  if (wing === "Doon Kidz") {
    return [
      { period: "Assembly", time: "08:30 - 08:50", mon: "Morning Rhymes", tue: "Action Songs", wed: "Yoga & Stretching", thu: "Story Time", fri: "Prayer & Manners", sat: "Fun Assembly" },
      { period: "Period 1", time: "08:50 - 09:30", mon: "English Phonics", tue: "English Writing", wed: "English Phonics", thu: "English Reading", fri: "English Rhymes", sat: "Picture Talk" },
      { period: "Period 2", time: "09:30 - 10:10", mon: "Number Magic", tue: "Counting & Shapes", wed: "Number Magic", thu: "Number Tracing", fri: "Mental Games", sat: "Activity Corner" },
      { period: "Recess", time: "10:10 - 10:35", mon: "Lunch Break", tue: "Lunch Break", wed: "Lunch Break", thu: "Lunch Break", fri: "Lunch Break", sat: "Lunch Break" },
      { period: "Period 3", time: "10:35 - 11:15", mon: "Hindi Swar", tue: "Hindi Rhymes", wed: "Hindi Writing", thu: "Hindi Reading", fri: "General Awareness", sat: "Colouring" },
    ];
  }
  const shift = section === "B" ? 1 : section === "C" ? 2 : 0;
  const subs = ["Mathematics", "Science", "English", "Social Science", "Hindi / Sanskrit", "Computer / IT"];
  const s = (i) => subs[(i + shift) % subs.length];
  return [
    { period: "Assembly", time: "08:00 - 08:25", mon: "Prayer & News", tue: "Thought & Quiz", wed: "PT / Yoga", thu: "Value Talk", fri: "House Activity", sat: "Club Assembly" },
    { period: "Period 1", time: "08:25 - 09:05", mon: s(0), tue: s(1), wed: s(0), thu: s(2), fri: s(0), sat: "DPT Evaluation" },
    { period: "Period 2", time: "09:05 - 09:45", mon: s(2), tue: s(0), wed: s(1), thu: s(0), fri: s(2), sat: "English Grammar" },
    { period: "Period 3", time: "09:45 - 10:25", mon: s(1), tue: s(2), wed: `${s(1)} (Lab)`, thu: s(3), fri: s(1), sat: "Science Quiz" },
    { period: "DPT Slot", time: "10:25 - 10:45", mon: "Daily Practice Test", tue: "Daily Practice Test", wed: "Daily Practice Test", thu: "Daily Practice Test", fri: "Daily Practice Test", sat: "Weekly Recap" },
    { period: "Recess", time: "10:45 - 11:10", mon: "Lunch Break", tue: "Lunch Break", wed: "Lunch Break", thu: "Lunch Break", fri: "Lunch Break", sat: "Lunch Break" },
    { period: "Period 4", time: "11:10 - 11:50", mon: s(3), tue: s(4), wed: s(3), thu: s(1), fri: s(3), sat: "Library" },
  ];
}

// Safe DOM Builder (Zero innerHTML)
function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  if (attrs && typeof attrs === "object") {
    for (const [k, v] of Object.entries(attrs)) {
      if (v === undefined || v === null || v === false) continue;
      if (k === "className") {
        node.className = String(v);
      } else if (k === "textContent") {
        node.textContent = String(v);
      } else if (k === "style" && typeof v === "object") {
        Object.assign(node.style, v);
      } else if (k.startsWith("on") && typeof v === "function") {
        node.addEventListener(k.slice(2).toLowerCase(), v);
      } else if (k === "value") {
        node.value = String(v);
      } else if (k === "checked") {
        node.checked = Boolean(v);
      } else if (k === "selected") {
        node.selected = Boolean(v);
      } else if (k === "disabled") {
        node.disabled = Boolean(v);
      } else if (k === "href") {
        if (isSafeHttpUrl(v)) node.setAttribute("href", String(v));
      } else if (k === "src") {
        if (isSafeImageSrc(v)) node.setAttribute("src", String(v));
      } else {
        node.setAttribute(k, String(v));
      }
    }
  }
  const flat = Array.isArray(children) ? children : [children];
  for (const child of flat) {
    if (child === null || child === undefined || child === false) continue;
    if (typeof child === "string" || typeof child === "number") {
      node.appendChild(document.createTextNode(String(child)));
    } else if (child instanceof Node) {
      node.appendChild(child);
    }
  }
  return node;
}

function clearNode(node) {
  while (node.firstChild) {
    node.removeChild(node.firstChild);
  }
}

// In-Memory State (JWT NEVER stored in localStorage / sessionStorage)
const state = {
  booting: true,
  needsAdminSetup: false,
  selectedRole: "STUDENT",
  darkMode: false,
  token: null,
  dashboard: null,
  activeTab: 0,
  teacherFilterClass: "",
  teacherFilterSection: "",
  adminUserSearch: "",
  adminRoleFilter: "",
  feeSearch: "",
  selectedFeeIds: new Set(),
  modal: null,
  isBusy: false,
};

function showToast(message) {
  const root = document.getElementById("toast-root");
  if (!root || !message) return;
  const item = el("div", { className: "toast", textContent: String(message) });
  root.appendChild(item);
  setTimeout(() => {
    item.remove();
  }, 3600);
}

async function apiRequest(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  const resp = await fetch(path, { ...options, headers });
  const data = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    throw new Error(data.error || `Request failed (${resp.status})`);
  }
  return data;
}

async function refreshDashboard() {
  if (!state.token) return;
  const params = new URLSearchParams();
  const user = state.dashboard?.currentUser;
  if (user?.role === "ADMIN") {
    if (state.adminUserSearch.trim()) params.set("search", state.adminUserSearch.trim());
    if (state.adminRoleFilter) params.set("role", state.adminRoleFilter);
  } else if (user?.role === "TEACHER") {
    if (state.teacherFilterClass) params.set("className", state.teacherFilterClass);
    if (state.teacherFilterSection) params.set("section", state.teacherFilterSection);
  } else if (user?.role === "FEE_MANAGER") {
    if (state.feeSearch.trim()) params.set("search", state.feeSearch.trim());
  }
  const qs = params.toString() ? `?${params.toString()}` : "";
  const data = await apiRequest(`/api/dashboard${qs}`);
  state.dashboard = data;
  if (data.currentUser?.role === "TEACHER" && !state.teacherFilterClass) {
    state.teacherFilterClass = data.currentUser.className || "Class I";
    state.teacherFilterSection = data.currentUser.section || "A";
  }
}

async function compressImageFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read selected file."));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("Selected file is not a valid image."));
      img.onload = () => {
        const maxDim = 960;
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        const out = canvas.toDataURL("image/jpeg", 0.78);
        resolve(out);
      };
      img.src = String(reader.result || "");
    };
    reader.readAsDataURL(file);
  });
}

function toggleDarkMode() {
  state.darkMode = !state.darkMode;
  document.body.classList.toggle("dark", state.darkMode);
  render();
}

function buildCreditsCard() {
  return el("section", { className: "card" }, [
    el("div", { className: "eyebrow", textContent: "App Architecture & School Leadership" }),
    el("h3", { style: { margin: "6px 0 8px" }, textContent: SCHOOL_INFO.name }),
    el("p", { style: { margin: "0 0 6px", fontSize: "0.9rem" }, textContent: `${SCHOOL_INFO.affiliation} • ${SCHOOL_INFO.trust}` }),
    el("p", { style: { margin: "0 0 6px", fontSize: "0.9rem" } }, [
      el("strong", { textContent: "Lead Developer & Architect: " }),
      SCHOOL_INFO.leadDeveloper,
    ]),
    el("p", { style: { margin: "0 0 6px", fontSize: "0.9rem" } }, [
      el("strong", { textContent: "Co-Developers: " }),
      SCHOOL_INFO.coDevelopers.join(", "),
    ]),
    el("p", { style: { margin: "0 0 6px", fontSize: "0.9rem" } }, [
      el("strong", { textContent: "Mentors & Faculty Guidance: " }),
      SCHOOL_INFO.mentors.join(", "),
    ]),
    el("p", { style: { margin: "0", fontSize: "0.82rem", color: "var(--text-muted)" }, textContent: `${SCHOOL_INFO.address} • ${SCHOOL_INFO.phones}` }),
  ]);
}

// ==========================================
// LOGIN & FIRST-RUN SETUP VIEW
// ==========================================
function renderLoginScreen(root) {
  let idVal = "";
  let pwVal = "";
  let setupName = "Principal / Admin";
  let setupUserId = "ADMIN01";
  let setupPhone = "";
  let setupPassword = "";
  const errorBox = el("div", { className: "alert alert-error", style: { display: "none" } });

  const idInput = el("input", {
    className: "input",
    id: "login-userid",
    placeholder:
      state.selectedRole === "STUDENT"
        ? "Enter Roll / Admission No (e.g. 001)"
        : state.selectedRole === "TEACHER"
          ? "Enter Teacher ID (e.g. TCH101)"
          : state.selectedRole === "FEE_MANAGER"
            ? "Enter Staff ID (e.g. FEE201)"
            : "Enter Admin ID (e.g. ADMIN01)",
    onInput: (e) => { idVal = e.target.value; },
  });
  const pwInput = el("input", {
    className: "input",
    id: "login-password",
    type: "password",
    placeholder: "Enter Password",
    onInput: (e) => { pwVal = e.target.value; },
  });

  const handleLogin = async (e) => {
    e.preventDefault();
    errorBox.style.display = "none";
    if (!idVal.trim() || !pwVal.trim()) {
      errorBox.textContent = "Please enter both your ID and Password.";
      errorBox.style.display = "block";
      return;
    }
    try {
      const res = await apiRequest("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ role: state.selectedRole, userId: idVal.trim(), password: pwVal.trim() }),
      });
      state.token = res.token;
      state.activeTab = 0;
      await refreshDashboard();
      showToast(`Welcome, ${res.user.fullName}`);
      render();
    } catch (err) {
      errorBox.textContent = err.message;
      errorBox.style.display = "block";
    }
  };

  const handleClaimAdmin = async (e) => {
    e.preventDefault();
    errorBox.style.display = "none";
    if (setupPassword.trim().length < 6) {
      errorBox.textContent = "Principal password must be at least 6 characters.";
      errorBox.style.display = "block";
      return;
    }
    try {
      const res = await apiRequest("/api/auth/claim-admin", {
        method: "POST",
        body: JSON.stringify({
          fullName: setupName.trim(),
          userId: setupUserId.trim() || "ADMIN01",
          phone: setupPhone.trim(),
          password: setupPassword.trim(),
        }),
      });
      state.token = res.token;
      state.needsAdminSetup = false;
      state.activeTab = 0;
      await refreshDashboard();
      showToast("Principal/Admin account activated on Cloudflare D1!");
      render();
    } catch (err) {
      errorBox.textContent = err.message;
      errorBox.style.display = "block";
    }
  };

  const roles = [
    ["STUDENT", "Student"],
    ["TEACHER", "Teacher"],
    ["FEE_MANAGER", "Accounts"],
    ["ADMIN", "Principal"],
  ];

  const mainCard = state.needsAdminSetup
    ? el("section", { className: "card" }, [
        el("div", { className: "section-title" }, [
          el("span", { textContent: "First-Run School Cloud Setup (Principal / Admin)" }),
          el("span", { className: "pill pill-gold", textContent: "1-Time Setup" }),
        ]),
        el("p", { style: { margin: "0 0 12px", color: "var(--text-muted)", fontSize: "0.9rem" }, textContent: "Create your Principal Admin account once. After setup, all teachers, students (roll 001, 002...), and fee managers can log in from both this website and the Android app." }),
        el("form", { className: "form-grid", onSubmit: handleClaimAdmin }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Principal / Admin Name" }),
            el("input", { className: "input", value: setupName, onInput: (e) => { setupName = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Admin ID" }),
            el("input", { className: "input", value: setupUserId, onInput: (e) => { setupUserId = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Phone Number" }),
            el("input", { className: "input", placeholder: "+91...", onInput: (e) => { setupPhone = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Admin Password (min 6 chars)" }),
            el("input", { className: "input", type: "password", placeholder: "Set strong password", onInput: (e) => { setupPassword = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [errorBox]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Initialize School Cloud & Enter Admin Portal" }),
          ]),
        ]),
      ])
    : el("section", { className: "card" }, [
        el("div", { className: "section-title" }, [
          el("div", {}, [
            el("div", { textContent: "School ERP Web Portal Login" }),
            el("div", { style: { fontSize: "0.78rem", color: "var(--emerald)", fontWeight: 600 }, textContent: "Synced with Cloudflare Workers + D1 Edge Database" }),
          ]),
          el("button", { type: "button", className: "btn btn-outline btn-sm", onClick: toggleDarkMode, textContent: state.darkMode ? "Light Mode" : "Dark Mode" }),
        ]),
        el("div", { className: "eyebrow", style: { marginBottom: "8px" }, textContent: "Step 1: Select Role" }),
        el("div", { className: "role-grid", style: { marginBottom: "14px" } },
          roles.map(([key, label]) =>
            el("button", {
              type: "button",
              className: `role-chip ${state.selectedRole === key ? "active" : ""}`,
              onClick: () => {
                state.selectedRole = key;
                render();
              },
              textContent: label,
            })
          )
        ),
        el("form", { className: "form-grid", onSubmit: handleLogin }, [
          el("div", { className: "field" }, [
            el("label", { for: "login-userid", textContent: state.selectedRole === "STUDENT" ? "Roll / Admission No" : "User / Staff ID" }),
            idInput,
          ]),
          el("div", { className: "field" }, [
            el("label", { for: "login-password", textContent: "Password" }),
            pwInput,
          ]),
          el("div", { className: "field full" }, [errorBox]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Sign In to Doon Public School Portal" }),
          ]),
        ]),
      ]);

  const wrapper = el("div", { className: "login-backdrop" }, [
    el("div", { className: "login-container" }, [
      el("header", { className: "hero-banner" }, [
        el("div", { className: "hero-top" }, [
          el("div", { className: "crest-badge" }, [
            el("img", { src: "/crest.svg", alt: "Doon Public School, Etah Crest" }),
          ]),
          el("div", {}, [
            el("h1", { className: "hero-title", textContent: SCHOOL_INFO.name }),
            el("p", { className: "hero-motto", textContent: `“${SCHOOL_INFO.motto}”` }),
            el("p", { className: "hero-meta", textContent: `${SCHOOL_INFO.affiliation} • ${SCHOOL_INFO.trust}` }),
          ]),
        ]),
      ]),
      mainCard,
      el("section", { className: "card card-navy" }, [
        el("div", { className: "eyebrow", style: { color: "var(--gold-amber)" }, textContent: "Official Academic Wings" }),
        el("div", { className: "item-list", style: { marginTop: "8px", gap: "6px" } },
          WINGS.map((w) =>
            el("div", { style: { fontSize: "0.86rem" }, textContent: `• ${w.name} (${w.subtitle}): ${w.label}` })
          )
        ),
      ]),
      buildCreditsCard(),
    ]),
  ]);

  root.appendChild(wrapper);
}

// ==========================================
// PROFILE & PASSWORD MODAL
// ==========================================
function openProfileModal() {
  const modalRoot = document.getElementById("modal-root");
  clearNode(modalRoot);
  const user = state.dashboard.currentUser;
  let fullName = user.fullName || "";
  let phone = user.phone || "";
  let password = "";

  const backdrop = el("div", { className: "modal-backdrop", onClick: (e) => { if (e.target === backdrop) clearNode(modalRoot); } }, [
    el("div", { className: "modal-card" }, [
      el("div", { className: "section-title" }, [
        el("span", { textContent: "My Profile & Password" }),
        el("button", { type: "button", className: "btn btn-outline btn-sm", onClick: () => clearNode(modalRoot), textContent: "Close" }),
      ]),
      el("form", {
        className: "form-grid",
        onSubmit: async (e) => {
          e.preventDefault();
          try {
            await apiRequest("/api/profile", {
              method: "PUT",
              body: JSON.stringify({ fullName, phone, password }),
            });
            await refreshDashboard();
            clearNode(modalRoot);
            showToast("Profile updated successfully!");
            render();
          } catch (err) {
            showToast(err.message);
          }
        },
      }, [
        el("div", { className: "field full" }, [
          el("label", { textContent: "Full Name" }),
          el("input", { className: "input", value: fullName, onInput: (e) => { fullName = e.target.value; } }),
        ]),
        el("div", { className: "field full" }, [
          el("label", { textContent: "Phone Number" }),
          el("input", { className: "input", value: phone, onInput: (e) => { phone = e.target.value; } }),
        ]),
        el("div", { className: "field full" }, [
          el("label", { textContent: "New Password (leave blank to keep current)" }),
          el("input", { className: "input", type: "password", placeholder: "Optional new password", onInput: (e) => { password = e.target.value; } }),
        ]),
        el("div", { className: "field full" }, [
          el("button", { type: "submit", className: "btn btn-primary", textContent: "Save Changes" }),
        ]),
      ]),
    ]),
  ]);
  modalRoot.appendChild(backdrop);
}

// ==========================================
// SHARED SECTIONS: CRAFT STUDIO, TIMETABLE
// ==========================================
function buildCraftStudioSection(user) {
  const projects = state.dashboard.craftProjects || [];
  const likedIds = new Set(state.dashboard.likedProjectIds || []);
  let title = "";
  let category = CRAFT_CATEGORIES[0];
  let materials = "";
  let description = "";
  let aiImageData = "";

  const container = el("div", { className: "item-list", style: { gap: "16px" } });

  container.appendChild(
    el("section", { className: "card" }, [
      el("h3", { className: "section-title", textContent: "Publish Craft & Project Showcase" }),
      el("form", {
        className: "form-grid",
        onSubmit: async (e) => {
          e.preventDefault();
          try {
            await apiRequest("/api/craft", {
              method: "POST",
              body: JSON.stringify({ title, category, materials, description, aiImageData }),
            });
            await refreshDashboard();
            showToast("Project published to School Showcase!");
            render();
          } catch (err) {
            showToast(err.message);
          }
        },
      }, [
        el("div", { className: "field" }, [
          el("label", { textContent: "Project Title" }),
          el("input", { className: "input", placeholder: "e.g. Solar Irrigation Model", onInput: (e) => { title = e.target.value; } }),
        ]),
        el("div", { className: "field" }, [
          el("label", { textContent: "Category" }),
          el("select", { className: "select", onChange: (e) => { category = e.target.value; } },
            CRAFT_CATEGORIES.map((c) => el("option", { value: c, textContent: c }))
          ),
        ]),
        el("div", { className: "field full" }, [
          el("label", { textContent: "Materials Used" }),
          el("input", { className: "input", placeholder: "Cardboard, mini solar panel, pump, wires", onInput: (e) => { materials = e.target.value; } }),
        ]),
        el("div", { className: "field full" }, [
          el("label", { textContent: "Description & How It Works" }),
          el("textarea", { className: "textarea", placeholder: "Describe your project build and science concept...", onInput: (e) => { description = e.target.value; } }),
        ]),
        el("div", { className: "field full" }, [
          el("label", { textContent: "Project Photo (optional)" }),
          el("input", {
            type: "file",
            accept: "image/*",
            className: "input",
            onChange: async (e) => {
              const file = e.target.files?.[0];
              if (file) {
                aiImageData = await compressImageFile(file);
                showToast("Photo attached & compressed for cloud.");
              }
            },
          }),
        ]),
        el("div", { className: "field full" }, [
          el("button", { type: "submit", className: "btn btn-primary", textContent: "Publish Craft Project" }),
        ]),
      ]),
    ])
  );

  const listCard = el("section", { className: "card" }, [
    el("h3", { className: "section-title", textContent: `School Exhibition Feed (${projects.length})` }),
  ]);
  const listWrap = el("div", { className: "item-list" });
  if (projects.length === 0) {
    listWrap.appendChild(el("p", { style: { color: "var(--text-muted)" }, textContent: "No craft projects posted yet. Be the first!" }));
  } else {
    for (const p of projects) {
      const pId = p.id;
      const isLiked = likedIds.has(pId);
      const likesCount = Number(pick(p, "likesCount", "likes_count", 0));
      const authorDocId = pick(p, "authorDocId", "author_doc_id", "");
      const img = pick(p, "aiImageData", "ai_image_data", "");
      const canDelete = user.role === "ADMIN" || user.role === "TEACHER" || authorDocId === user.id;

      listWrap.appendChild(
        el("div", { className: "card card-compact", style: { background: "var(--surface-alt)" } }, [
          el("div", { className: "section-title" }, [
            el("div", {}, [
              el("div", { textContent: p.title }),
              el("div", { style: { fontSize: "0.78rem", color: "var(--text-muted)", fontWeight: 500 }, textContent: `${p.category} • By ${pick(p, "authorName", "author_name")} (${pick(p, "classInfo", "class_info")})` }),
            ]),
            el("div", { className: "btn-row" }, [
              el("button", {
                type: "button",
                className: `btn btn-sm ${isLiked ? "btn-gold" : "btn-outline"}`,
                onClick: async () => {
                  try {
                    const res = await apiRequest(`/api/craft/${encodeURIComponent(pId)}/like`, { method: "POST" });
                    await refreshDashboard();
                    showToast(res.liked ? "Liked! (1 like per account)" : "Like removed.");
                    render();
                  } catch (err) {
                    showToast(err.message);
                  }
                },
                textContent: `${isLiked ? "Liked" : "Like"} (${likesCount})`,
              }),
              canDelete
                ? el("button", {
                    type: "button",
                    className: "btn btn-sm btn-rose",
                    onClick: async () => {
                      await apiRequest(`/api/craft/${encodeURIComponent(pId)}`, { method: "DELETE" });
                      await refreshDashboard();
                      showToast("Project deleted.");
                      render();
                    },
                    textContent: "Delete",
                  })
                : null,
            ]),
          ]),
          el("p", { style: { margin: "6px 0", fontSize: "0.92rem" }, textContent: p.description }),
          p.materials ? el("div", { className: "pill pill-navy", textContent: `Materials: ${p.materials}` }) : null,
          isSafeImageSrc(img) ? el("img", { src: img, alt: p.title, className: "craft-img" }) : null,
        ])
      );
    }
  }
  listCard.appendChild(listWrap);
  container.appendChild(listCard);
  return container;
}

function buildTimetableTable(className, section) {
  const timetables = state.dashboard.timetables || [];
  const matched = timetables.find((t) => pick(t, "className", "class_name") === className && t.section === section);
  const slots = matched ? safeParseJson(pick(matched, "slotsJson", "slots_json", matched.slots), []) : defaultTimetableSlots(className, section);

  return el("div", { className: "table-wrap" }, [
    el("table", {}, [
      el("thead", {}, [
        el("tr", {}, ["Period", "Time", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((h) => el("th", { textContent: h }))),
      ]),
      el("tbody", {},
        slots.map((s) =>
          el("tr", {}, [
            el("td", { style: { fontWeight: 700 }, textContent: s.period }),
            el("td", { style: { whiteSpace: "nowrap" }, textContent: s.time }),
            el("td", { textContent: s.mon }),
            el("td", { textContent: s.tue }),
            el("td", { textContent: s.wed }),
            el("td", { textContent: s.thu }),
            el("td", { textContent: s.fri }),
            el("td", { textContent: s.sat }),
          ])
        )
      ),
    ]),
  ]);
}

// ==========================================
// STUDENT PORTAL
// ==========================================
function renderStudentPortal(tabIdx) {
  const d = state.dashboard;
  const student = d.currentUser;
  const gw = d.gateway || {};
  const circulars = (d.circulars || []).filter((c) => {
    const recId = pick(c, "recipientDocId", "recipient_doc_id", "");
    if (recId) return recId === student.id;
    const tc = pick(c, "targetClass", "target_class", "");
    const ts = pick(c, "targetSection", "target_section", "");
    const tw = pick(c, "targetWing", "target_wing", "");
    if (tc && tc !== student.className) return false;
    if (ts && ts !== student.section) return false;
    if (tw && tw !== "All Wings" && tw !== student.wing) return false;
    return true;
  });

  if (tabIdx === 0) {
    // Home: Poster + Attendance + Unpaid Fees + Circulars
    const att = d.attendance || [];
    const presentCount = att.filter((a) => a.status === "Present").length;
    const pct = att.length > 0 ? Math.round((presentCount * 100) / att.length) : 100;
    const unpaidTotal = (d.feeItems || []).filter((f) => f.status === "UNPAID").reduce((s, f) => s + Number(f.amount || 0), 0);
    const posters = d.campusPosters || [];

    const out = el("div", { className: "item-list", style: { gap: "16px" } });
    out.appendChild(
      el("div", { className: "stats-grid" }, [
        el("div", { className: "stat-card" }, [
          el("div", { className: "stat-lbl", textContent: "Class & Roll No" }),
          el("div", { className: "stat-val", textContent: `${student.className}-${student.section}` }),
          el("div", { className: "stat-lbl", textContent: `Roll No: ${student.userId}` }),
        ]),
        el("div", { className: "stat-card" }, [
          el("div", { className: "stat-lbl", textContent: "Attendance" }),
          el("div", { className: "stat-val", textContent: `${pct}%` }),
          el("div", { className: "stat-lbl", textContent: `${presentCount}/${att.length} Days Marked` }),
        ]),
        el("div", { className: "stat-card" }, [
          el("div", { className: "stat-lbl", textContent: "Pending Fee Dues" }),
          el("div", { className: "stat-val", textContent: `₹${unpaidTotal}` }),
          el("div", { className: "stat-lbl", textContent: unpaidTotal === 0 ? "All Paid" : "Tap Fees & Pay tab" }),
        ]),
      ])
    );

    if (posters.length > 0) {
      const latest = posters[0];
      const imgData = pick(latest, "imageData", "image_data", "");
      out.appendChild(
        el("section", { className: "card" }, [
          el("div", { className: "section-title" }, [
            el("span", { textContent: latest.title }),
            el("span", { className: "pill pill-gold", textContent: latest.date }),
          ]),
          latest.subtitle ? el("p", { style: { margin: "0 0 8px", color: "var(--text-muted)" }, textContent: latest.subtitle }) : null,
          isSafeImageSrc(imgData) ? el("img", { src: imgData, alt: latest.title, className: "poster-img" }) : null,
        ])
      );
    }

    out.appendChild(
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Notice & Holiday Board (${circulars.length})` }),
        el("div", { className: "item-list" },
          circulars.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No circulars yet." })]
            : circulars.map((c) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("div", { className: "btn-row", style: { marginBottom: "4px" } }, [
                      el("strong", { textContent: c.title }),
                      el("span", { className: `pill ${c.category === "Holiday" ? "pill-gold" : "pill-navy"}`, textContent: c.category }),
                    ]),
                    el("p", { style: { margin: "4px 0", fontSize: "0.9rem" }, textContent: c.content }),
                    el("div", { style: { fontSize: "0.78rem", color: "var(--text-muted)" }, textContent: `${c.date} • Audience: ${c.audience} • By ${pick(c, "postedBy", "posted_by")}` }),
                  ]),
                ])
              )
        ),
      ])
    );
    return out;
  }

  if (tabIdx === 1) {
    // DPT Results & Term Report Cards
    const daily = d.dptDaily || [];
    const weekly = d.dptWeekly || [];
    const termMarks = d.termMarks || [];
    const out = el("div", { className: "item-list", style: { gap: "16px" } });

    out.appendChild(
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Daily DPT & Class Exam Log (${daily.length})` }),
        el("div", { className: "item-list" },
          daily.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No daily DPT updates yet." })]
            : daily.map((r) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${r.subject} — ${r.date}` }),
                    el("div", { style: { fontSize: "0.86rem", marginTop: "3px" }, textContent: Number(r.held) ? `Held: ${r.topic} (Max ${pick(r, "maxMarks", "max_marks", 0)} marks)` : `Not Held: ${r.reason}` }),
                    r.notes ? el("div", { style: { fontSize: "0.82rem", color: "var(--text-muted)" }, textContent: r.notes }) : null,
                  ]),
                  el("span", { className: `pill ${Number(r.held) ? "pill-emerald" : "pill-gold"}`, textContent: Number(r.held) ? pick(r, "assessmentType", "assessment_type", "DPT") : "Skipped" }),
                ])
              )
        ),
      ])
    );

    out.appendChild(
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Weekly DPT Scores (${weekly.length})` }),
        el("div", { className: "item-list" },
          weekly.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No weekly DPT marks recorded yet." })]
            : weekly.map((w) => {
                const maxM = Math.max(1, Number(pick(w, "maxMarks", "max_marks", 20)));
                const pct = Math.round((Number(w.scored || 0) * 100) / maxM);
                return el("div", { className: "list-row" }, [
                  el("div", { style: { flex: 1, minWidth: "220px" } }, [
                    el("strong", { textContent: `${w.subject} • ${w.week}` }),
                    el("div", { style: { fontSize: "0.84rem", margin: "4px 0" }, textContent: `Score: ${w.scored} / ${maxM} (${pct}%)` }),
                    el("div", { className: "progress-track" }, [el("div", { className: "progress-fill", style: { width: `${pct}%` } })]),
                  ]),
                ]);
              })
        ),
      ])
    );

    out.appendChild(
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Term Exam Report Cards (${termMarks.length})` }),
        el("div", { className: "item-list" },
          termMarks.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No term report cards published yet." })]
            : termMarks.map((tm) => {
                const marksObj = safeParseJson(pick(tm, "marksJson", "marks_json", tm.marks), {});
                return el("div", { className: "card card-compact", style: { background: "var(--surface-alt)" } }, [
                  el("strong", { textContent: `${tm.term} Report Card` }),
                  el("div", { className: "table-wrap", style: { marginTop: "8px" } }, [
                    el("table", {}, [
                      el("thead", {}, [el("tr", {}, ["Subject", "Obtained", "Max"].map((h) => el("th", { textContent: h })))]),
                      el("tbody", {},
                        Object.entries(marksObj).map(([sub, vals]) =>
                          el("tr", {}, [
                            el("td", { textContent: sub }),
                            el("td", { textContent: String(vals?.scored ?? 0) }),
                            el("td", { textContent: String(vals?.max ?? 100) }),
                          ])
                        )
                      ),
                    ]),
                  ]),
                  tm.remarks ? el("p", { style: { margin: "8px 0 0", fontSize: "0.86rem" }, textContent: `Teacher Remarks: ${tm.remarks}` }) : null,
                ]);
              })
        ),
      ])
    );
    return out;
  }

  if (tabIdx === 2) {
    // Fees & UPI Payment Proof
    const feeItems = d.feeItems || [];
    const payments = d.payments || [];
    const unpaid = feeItems.filter((f) => f.status === "UNPAID");
    let utrInput = "";
    let upiApp = "GPay";

    const selectedSum = unpaid
      .filter((f) => state.selectedFeeIds.has(f.id))
      .reduce((s, f) => s + Number(f.amount || 0), 0);

    const out = el("div", { className: "item-list", style: { gap: "16px" } });
    out.appendChild(
      el("section", { className: "card card-navy" }, [
        el("div", { className: "eyebrow", style: { color: "var(--gold-amber)" }, textContent: "Official School Fee Desk" }),
        el("h3", { style: { margin: "4px 0" }, textContent: `Merchant UPI ID: ${gw.merchantUpi || "doonpublicetah@sbi"}` }),
        el("p", { style: { margin: 0, fontSize: "0.86rem" }, textContent: `Payee: ${gw.payeeName || SCHOOL_INFO.name} • ${gw.bankName || ""} • A/C ${gw.accountNo || ""} (IFSC: ${gw.ifsc || ""})` }),
      ])
    );

    out.appendChild(
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Pending Fee Heads (${unpaid.length})` }),
        el("div", { className: "item-list" },
          unpaid.length === 0
            ? [el("p", { style: { color: "var(--emerald)", fontWeight: 700 }, textContent: "No unpaid fee demands pending!" })]
            : unpaid.map((f) =>
                el("label", { className: "list-row", style: { cursor: "pointer" } }, [
                  el("div", { className: "btn-row" }, [
                    el("input", {
                      type: "checkbox",
                      checked: state.selectedFeeIds.has(f.id),
                      onChange: (e) => {
                        if (e.target.checked) state.selectedFeeIds.add(f.id);
                        else state.selectedFeeIds.delete(f.id);
                        render();
                      },
                    }),
                    el("div", {}, [
                      el("strong", { textContent: f.title }),
                      el("div", { style: { fontSize: "0.8rem", color: "var(--text-muted)" }, textContent: f.month }),
                    ]),
                  ]),
                  el("strong", { textContent: `₹${f.amount}` }),
                ])
              )
        ),
        unpaid.length > 0
          ? el("form", {
              className: "form-grid",
              style: { marginTop: "14px" },
              onSubmit: async (e) => {
                e.preventDefault();
                try {
                  await apiRequest("/api/payments", {
                    method: "POST",
                    body: JSON.stringify({
                      feeItemIds: Array.from(state.selectedFeeIds),
                      mode: "UPI",
                      utrOrRef: utrInput.trim(),
                      upiApp,
                    }),
                  });
                  state.selectedFeeIds.clear();
                  await refreshDashboard();
                  showToast("UTR submitted! Fee Manager will verify and issue your receipt.");
                  render();
                } catch (err) {
                  showToast(err.message);
                }
              },
            }, [
              el("div", { className: "field" }, [
                el("label", { textContent: "UPI App Used" }),
                el("select", { className: "select", onChange: (e) => { upiApp = e.target.value; } },
                  ["GPay", "PhonePe", "Paytm", "BHIM UPI", "NetBanking"].map((app) => el("option", { value: app, textContent: app }))
                ),
              ]),
              el("div", { className: "field" }, [
                el("label", { textContent: `12-Digit UTR / Transaction Ref (Selected: ₹${selectedSum})` }),
                el("input", { className: "input", placeholder: "Enter UTR / Ref No (min 8 chars)", onInput: (e) => { utrInput = e.target.value; } }),
              ]),
              el("div", { className: "field full" }, [
                el("button", { type: "submit", className: "btn btn-primary", textContent: `Submit Payment Proof (₹${selectedSum})` }),
              ]),
            ])
          : null,
      ])
    );

    out.appendChild(
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Payment History & Receipts (${payments.length})` }),
        el("div", { className: "item-list" },
          payments.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No payment submissions yet." })]
            : payments.map((p) => {
                const pillCls = p.status === "VERIFIED" ? "pill-emerald" : p.status === "REJECTED" ? "pill-rose" : "pill-gold";
                const rcp = pick(p, "receiptNo", "receipt_no", "");
                return el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${pick(p, "feeSummary", "fee_summary")} — ₹${p.amount}` }),
                    el("div", { style: { fontSize: "0.82rem", color: "var(--text-muted)" }, textContent: `${p.mode} (${pick(p, "upiApp", "upi_app")}) • UTR: ${pick(p, "utrOrRef", "utr_or_ref")}` }),
                    rcp ? el("div", { style: { fontSize: "0.84rem", color: "var(--emerald)", fontWeight: 700 }, textContent: `Official Receipt: ${rcp}` }) : null,
                  ]),
                  el("span", { className: `pill ${pillCls}`, textContent: p.status }),
                ]);
              })
        ),
      ])
    );
    return out;
  }

  if (tabIdx === 3) {
    // Academics: Timetable + Homework + Leave + Doubts
    const hw = d.homework || [];
    const leaves = d.leaves || [];
    const doubts = d.doubts || [];
    let fromDate = todayIso();
    let toDate = todayIso();
    let leaveReason = "";
    const classSubs = subjectsForClass(student.className || "Class X");
    let doubtSubject = classSubs[0];
    let doubtMsg = "";

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Class Timetable (${student.className}-${student.section})` }),
        buildTimetableTable(student.className, student.section),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Homework Diary (${hw.length})` }),
        el("div", { className: "item-list" },
          hw.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No homework assigned yet." })]
            : hw.map((item) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${item.subject}: ${item.title}` }),
                    el("p", { style: { margin: "4px 0", fontSize: "0.9rem" }, textContent: item.description }),
                    el("div", { style: { fontSize: "0.78rem", color: "var(--text-muted)" }, textContent: `Due: ${pick(item, "dueDate", "due_date")} • By ${pick(item, "teacherName", "teacher_name")}` }),
                  ]),
                ])
              )
        ),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Apply for Leave" }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            try {
              await apiRequest("/api/leaves", {
                method: "POST",
                body: JSON.stringify({ fromDate, toDate, reason: leaveReason }),
              });
              await refreshDashboard();
              showToast("Leave application submitted!");
              render();
            } catch (err) {
              showToast(err.message);
            }
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "From Date" }),
            el("input", { className: "input", type: "date", value: fromDate, onInput: (e) => { fromDate = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "To Date" }),
            el("input", { className: "input", type: "date", value: toDate, onInput: (e) => { toDate = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Reason" }),
            el("input", { className: "input", placeholder: "Reason for leave", onInput: (e) => { leaveReason = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Submit Leave Request" }),
          ]),
        ]),
        leaves.length > 0
          ? el("div", { className: "item-list", style: { marginTop: "12px" } },
              leaves.map((l) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${pick(l, "fromDate", "from_date")} to ${pick(l, "toDate", "to_date")}` }),
                    el("div", { style: { fontSize: "0.86rem" }, textContent: l.reason }),
                  ]),
                  el("span", { className: `pill ${l.status === "Approved" ? "pill-emerald" : l.status === "Rejected" ? "pill-rose" : "pill-gold"}`, textContent: l.status }),
                ])
              )
            )
          : null,
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Teacher Doubt Helpdesk" }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            try {
              await apiRequest("/api/doubts", {
                method: "POST",
                body: JSON.stringify({ subject: doubtSubject, message: doubtMsg }),
              });
              await refreshDashboard();
              showToast("Doubt sent to teacher helpdesk!");
              render();
            } catch (err) {
              showToast(err.message);
            }
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Subject" }),
            el("select", { className: "select", onChange: (e) => { doubtSubject = e.target.value; } },
              classSubs.map((s) => el("option", { value: s, textContent: s }))
            ),
          ]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Your Question / Doubt" }),
            el("textarea", { className: "textarea", placeholder: "Ask your teacher a question...", onInput: (e) => { doubtMsg = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Send Doubt" }),
          ]),
        ]),
        doubts.length > 0
          ? el("div", { className: "item-list", style: { marginTop: "12px" } },
              doubts.map((db) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${db.subject}: ${db.message}` }),
                    db.reply ? el("div", { style: { marginTop: "4px", color: "var(--emerald)", fontWeight: 600 }, textContent: `Reply (${pick(db, "repliedBy", "replied_by")}): ${db.reply}` }) : null,
                  ]),
                  el("span", { className: `pill ${db.status === "REPLIED" ? "pill-emerald" : "pill-gold"}`, textContent: db.status }),
                ])
              )
            )
          : null,
      ]),
    ]);
  }

  return buildCraftStudioSection(student);
}

// ==========================================
// TEACHER PORTAL
// ==========================================
function renderTeacherPortal(tabIdx) {
  const d = state.dashboard;
  const teacher = d.currentUser;
  const cls = state.teacherFilterClass || teacher.className || "Class I";
  const sec = state.teacherFilterSection || teacher.section || "A";
  const classSubs = subjectsForClass(cls);
  const students = d.users || [];

  const filterBar = el("section", { className: "card card-compact" }, [
    el("div", { className: "btn-row" }, [
      el("span", { className: "eyebrow", textContent: "Active Class & Section:" }),
      el("select", {
        className: "select",
        style: { width: "auto" },
        onChange: async (e) => {
          state.teacherFilterClass = e.target.value;
          await refreshDashboard();
          render();
        },
      }, ALL_CLASSES.map((c) => el("option", { value: c, selected: c === cls, textContent: c }))),
      el("select", {
        className: "select",
        style: { width: "auto" },
        onChange: async (e) => {
          state.teacherFilterSection = e.target.value;
          await refreshDashboard();
          render();
        },
      }, SECTIONS.map((s) => el("option", { value: s, selected: s === sec, textContent: `Section ${s}` }))),
    ]),
  ]);

  if (tabIdx === 0) {
    // Daily DPT
    let date = todayIso();
    let subject = classSubs.includes(teacher.subject) ? teacher.subject : classSubs[0];
    let assessmentType = "DPT";
    let held = "true";
    let topic = "";
    let maxMarks = "20";
    let reason = "Topic in Progress";
    let notes = "";

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      filterBar,
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Publish Daily DPT / Exam Status (${cls}-${sec})` }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            try {
              await apiRequest("/api/dpt-daily", {
                method: "POST",
                body: JSON.stringify({
                  date,
                  className: cls,
                  section: sec,
                  subject,
                  assessmentType,
                  held: held === "true",
                  topic,
                  maxMarks: Number(maxMarks || 20),
                  reason,
                  notes,
                }),
              });
              await refreshDashboard();
              showToast("Daily DPT status published live!");
              render();
            } catch (err) {
              showToast(err.message);
            }
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Date" }),
            el("input", { className: "input", type: "date", value: date, onInput: (e) => { date = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Subject" }),
            el("select", { className: "select", onChange: (e) => { subject = e.target.value; } },
              classSubs.map((s) => el("option", { value: s, selected: s === subject, textContent: s }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Type" }),
            el("select", { className: "select", onChange: (e) => { assessmentType = e.target.value; } },
              [["DPT", "Daily Practice Test (DPT)"], ["EXAM", "Class Test / Exam"]].map(([k, lbl]) => el("option", { value: k, textContent: lbl }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Status" }),
            el("select", { className: "select", onChange: (e) => { held = e.target.value; } }, [
              el("option", { value: "true", textContent: "Held Today" }),
              el("option", { value: "false", textContent: "Not Held Today" }),
            ]),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Topic Covered" }),
            el("input", { className: "input", placeholder: "Chapter / Topic", onInput: (e) => { topic = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Max Marks" }),
            el("input", { className: "input", type: "number", value: maxMarks, onInput: (e) => { maxMarks = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Notes / Reason if skipped" }),
            el("input", { className: "input", placeholder: "Optional notes or skip reason", onInput: (e) => { notes = e.target.value; reason = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Save Daily DPT Update" }),
          ]),
        ]),
      ]),
    ]);
  }

  if (tabIdx === 1) {
    // Weekly DPT Marks Entry
    let week = DPT_WEEKS[0];
    let subject = classSubs.includes(teacher.subject) ? teacher.subject : classSubs[0];
    let maxMarks = 20;
    const scoresMap = {};

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      filterBar,
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Weekly DPT Score Sheet (${cls}-${sec})` }),
        el("div", { className: "form-grid", style: { marginBottom: "12px" } }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Week" }),
            el("select", { className: "select", onChange: (e) => { week = e.target.value; } },
              DPT_WEEKS.map((w) => el("option", { value: w, textContent: w }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Subject" }),
            el("select", { className: "select", onChange: (e) => { subject = e.target.value; } },
              classSubs.map((s) => el("option", { value: s, selected: s === subject, textContent: s }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Max Marks" }),
            el("input", { className: "input", type: "number", value: String(maxMarks), onInput: (e) => { maxMarks = Number(e.target.value || 20); } }),
          ]),
        ]),
        students.length === 0
          ? el("p", { style: { color: "var(--text-muted)" }, textContent: `No students enrolled in ${cls}-${sec} yet.` })
          : el("div", { className: "item-list" }, [
              ...students.map((st) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${st.fullName}` }),
                    el("div", { style: { fontSize: "0.78rem", color: "var(--text-muted)" }, textContent: `Roll: ${st.userId}` }),
                  ]),
                  el("input", {
                    className: "input",
                    style: { width: "110px" },
                    type: "number",
                    placeholder: "Marks",
                    onInput: (e) => { scoresMap[st.id] = e.target.value; },
                  }),
                ])
              ),
              el("button", {
                type: "button",
                className: "btn btn-primary",
                onClick: async () => {
                  const entries = students
                    .filter((st) => String(scoresMap[st.id] ?? "").trim() !== "")
                    .map((st) => ({
                      week,
                      className: cls,
                      section: sec,
                      subject,
                      studentDocId: st.id,
                      studentName: st.fullName,
                      admissionNo: st.userId,
                      scored: Number(scoresMap[st.id] || 0),
                      maxMarks,
                    }));
                  if (entries.length === 0) {
                    showToast("Enter marks for at least one student.");
                    return;
                  }
                  await apiRequest("/api/dpt-weekly", { method: "POST", body: JSON.stringify({ entries }) });
                  await refreshDashboard();
                  showToast("Weekly DPT marks saved!");
                  render();
                },
                textContent: "Save Weekly DPT Marks",
              }),
            ]),
      ]),
    ]);
  }

  if (tabIdx === 2) {
    // Attendance & Leaves
    let date = todayIso();
    const leaves = d.leaves || [];
    const statusMap = {};
    for (const st of students) statusMap[st.id] = "Present";

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      filterBar,
      el("section", { className: "card" }, [
        el("div", { className: "section-title" }, [
          el("span", { textContent: `Attendance Register (${cls}-${sec})` }),
          el("button", {
            type: "button",
            className: "btn btn-emerald btn-sm",
            onClick: async () => {
              if (students.length === 0) return;
              const records = students.map((st) => ({ date, className: cls, section: sec, studentDocId: st.id, status: "Present" }));
              await apiRequest("/api/attendance", { method: "POST", body: JSON.stringify({ records }) });
              await refreshDashboard();
              showToast(`Marked all ${students.length} students Present for ${date}`);
              render();
            },
            textContent: "Mark All Present",
          }),
        ]),
        el("div", { className: "field", style: { maxWidth: "220px", marginBottom: "12px" } }, [
          el("label", { textContent: "Attendance Date" }),
          el("input", { className: "input", type: "date", value: date, onInput: (e) => { date = e.target.value; } }),
        ]),
        students.length === 0
          ? el("p", { style: { color: "var(--text-muted)" }, textContent: "No students in this class-section." })
          : el("div", { className: "item-list" }, [
              ...students.map((st) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: st.fullName }),
                    el("div", { style: { fontSize: "0.78rem", color: "var(--text-muted)" }, textContent: `Roll: ${st.userId}` }),
                  ]),
                  el("select", { className: "select", style: { width: "140px" }, onChange: (e) => { statusMap[st.id] = e.target.value; } },
                    ["Present", "Absent", "Leave"].map((s) => el("option", { value: s, textContent: s }))
                  ),
                ])
              ),
              el("button", {
                type: "button",
                className: "btn btn-primary",
                onClick: async () => {
                  const records = students.map((st) => ({
                    date,
                    className: cls,
                    section: sec,
                    studentDocId: st.id,
                    status: statusMap[st.id] || "Present",
                  }));
                  await apiRequest("/api/attendance", { method: "POST", body: JSON.stringify({ records }) });
                  await refreshDashboard();
                  showToast("Attendance saved!");
                  render();
                },
                textContent: "Save Class Attendance",
              }),
            ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Leave Requests (${leaves.length})` }),
        el("div", { className: "item-list" },
          leaves.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No leave requests for this class." })]
            : leaves.map((l) =>
                el("div", { className: "list-row" }, [
                  el("div", {}, [
                    el("strong", { textContent: `${pick(l, "studentName", "student_name")} (${pick(l, "fromDate", "from_date")} → ${pick(l, "toDate", "to_date")})` }),
                    el("div", { style: { fontSize: "0.86rem" }, textContent: l.reason }),
                  ]),
                  el("div", { className: "btn-row" }, [
                    el("span", { className: `pill ${l.status === "Approved" ? "pill-emerald" : l.status === "Rejected" ? "pill-rose" : "pill-gold"}`, textContent: l.status }),
                    l.status === "Pending"
                      ? el("button", {
                          type: "button",
                          className: "btn btn-emerald btn-sm",
                          onClick: async () => {
                            await apiRequest(`/api/leaves/${encodeURIComponent(l.id)}`, { method: "PUT", body: JSON.stringify({ status: "Approved", remark: "Approved by Teacher" }) });
                            await refreshDashboard();
                            showToast("Leave approved.");
                            render();
                          },
                          textContent: "Approve",
                        })
                      : null,
                    l.status === "Pending"
                      ? el("button", {
                          type: "button",
                          className: "btn btn-rose btn-sm",
                          onClick: async () => {
                            await apiRequest(`/api/leaves/${encodeURIComponent(l.id)}`, { method: "PUT", body: JSON.stringify({ status: "Rejected", remark: "Rejected by Teacher" }) });
                            await refreshDashboard();
                            showToast("Leave rejected.");
                            render();
                          },
                          textContent: "Reject",
                        })
                      : null,
                  ]),
                ])
              )
        ),
      ]),
    ]);
  }

  if (tabIdx === 3) {
    // Teacher Notices + Enroll Class Student (Auto Roll 001, 002...)
    let noticeTitle = "";
    let noticeContent = "";
    let recipientDocId = "";
    let stFullName = "";
    let stParent = "";
    let stPhone = "";
    let stPassword = "";

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      filterBar,
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Send Class Notice (${cls}-${sec})` }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            const recObj = students.find((s) => s.id === recipientDocId);
            await apiRequest("/api/circulars", {
              method: "POST",
              body: JSON.stringify({
                title: noticeTitle,
                category: "Notice",
                audience: recObj ? `${recObj.fullName} (${cls}-${sec})` : `${cls}-${sec}`,
                targetWing: wingForClass(cls),
                targetClass: cls,
                targetSection: sec,
                recipientDocId: recObj ? recObj.id : "",
                recipientName: recObj ? recObj.fullName : "",
                content: noticeContent,
              }),
            });
            await refreshDashboard();
            showToast("Notice published!");
            render();
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Title" }),
            el("input", { className: "input", placeholder: "Notice headline", onInput: (e) => { noticeTitle = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Recipient" }),
            el("select", { className: "select", onChange: (e) => { recipientDocId = e.target.value; } }, [
              el("option", { value: "", textContent: `Entire Class ${cls}-${sec}` }),
              ...students.map((st) => el("option", { value: st.id, textContent: `${st.fullName} (Roll ${st.userId})` })),
            ]),
          ]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Message" }),
            el("textarea", { className: "textarea", placeholder: "Write message...", onInput: (e) => { noticeContent = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Send Notice" }),
          ]),
        ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Add Student to My Class (${teacher.className}-${teacher.section})` }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            try {
              const res = await apiRequest("/api/users", {
                method: "POST",
                body: JSON.stringify({
                  role: "STUDENT",
                  fullName: stFullName,
                  parentName: stParent,
                  phone: stPhone,
                  password: stPassword,
                }),
              });
              await refreshDashboard();
              showToast(`Student ${res.user.fullName} enrolled with Roll ${res.user.userId}!`);
              render();
            } catch (err) {
              showToast(err.message);
            }
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Student Full Name" }),
            el("input", { className: "input", placeholder: "Student name", onInput: (e) => { stFullName = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Parent Name" }),
            el("input", { className: "input", placeholder: "Father / Mother name", onInput: (e) => { stParent = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Phone" }),
            el("input", { className: "input", placeholder: "+91...", onInput: (e) => { stPhone = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Student Password (Roll auto-assigned 001...)" }),
            el("input", { className: "input", type: "password", placeholder: "Min 4 chars", onInput: (e) => { stPassword = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Enroll Student (Auto Roll No)" }),
          ]),
        ]),
      ]),
    ]);
  }

  if (tabIdx === 4) {
    // Diary, Homework, Term Marks & Doubt Replies
    let hwSub = classSubs[0];
    let hwTitle = "";
    let hwDesc = "";
    let hwDue = todayIso();

    let tmTerm = "Term 1 (Half-Yearly)";
    let tmStudentId = students[0]?.id || "";
    const tmScores = {};
    let tmRemarks = "";
    const doubts = d.doubts || [];
    const replyMap = {};

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      filterBar,
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Homework Diary (${cls}-${sec})` }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            await apiRequest("/api/homework", {
              method: "POST",
              body: JSON.stringify({ className: cls, section: sec, subject: hwSub, title: hwTitle, description: hwDesc, dueDate: hwDue }),
            });
            await refreshDashboard();
            showToast("Homework published!");
            render();
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Subject" }),
            el("select", { className: "select", onChange: (e) => { hwSub = e.target.value; } },
              classSubs.map((s) => el("option", { value: s, textContent: s }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Due Date" }),
            el("input", { className: "input", type: "date", value: hwDue, onInput: (e) => { hwDue = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Homework Title" }),
            el("input", { className: "input", placeholder: "Chapter exercise / worksheet", onInput: (e) => { hwTitle = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Details" }),
            el("textarea", { className: "textarea", placeholder: "Page numbers and instructions", onInput: (e) => { hwDesc = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Publish Homework" }),
          ]),
        ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Publish Term Exam Report Card (Leave blank for non-opted electives)" }),
        students.length === 0
          ? el("p", { style: { color: "var(--text-muted)" }, textContent: "No students in this class." })
          : el("form", {
              className: "item-list",
              onSubmit: async (e) => {
                e.preventDefault();
                const st = students.find((s) => s.id === tmStudentId) || students[0];
                const marksPayload = {};
                for (const sub of classSubs) {
                  const valStr = String(tmScores[sub] ?? "").trim();
                  if (valStr !== "") {
                    marksPayload[sub] = { scored: Number(valStr), max: 100 };
                  }
                }
                if (Object.keys(marksPayload).length === 0) {
                  showToast("Enter marks for at least one subject.");
                  return;
                }
                await apiRequest("/api/term-marks", {
                  method: "POST",
                  body: JSON.stringify({
                    term: tmTerm,
                    className: cls,
                    section: sec,
                    studentDocId: st.id,
                    studentName: st.fullName,
                    admissionNo: st.userId,
                    marks: marksPayload,
                    remarks: tmRemarks,
                  }),
                });
                await refreshDashboard();
                showToast(`Term report card published for ${st.fullName}!`);
                render();
              },
            }, [
              el("div", { className: "form-grid" }, [
                el("div", { className: "field" }, [
                  el("label", { textContent: "Term / Exam" }),
                  el("select", { className: "select", onChange: (e) => { tmTerm = e.target.value; } },
                    ["Unit Test 1", "Term 1 (Half-Yearly)", "Unit Test 2", "Annual / Final Exam"].map((t) => el("option", { value: t, selected: t === tmTerm, textContent: t }))
                  ),
                ]),
                el("div", { className: "field" }, [
                  el("label", { textContent: "Student" }),
                  el("select", { className: "select", onChange: (e) => { tmStudentId = e.target.value; } },
                    students.map((st) => el("option", { value: st.id, textContent: `${st.fullName} (Roll ${st.userId})` }))
                  ),
                ]),
              ]),
              el("div", { className: "form-grid" },
                classSubs.map((sub) =>
                  el("div", { className: "field" }, [
                    el("label", { textContent: `${sub} (out of 100, optional)` }),
                    el("input", { className: "input", type: "number", placeholder: "Leave blank if N/A", onInput: (e) => { tmScores[sub] = e.target.value; } }),
                  ])
                )
              ),
              el("div", { className: "field" }, [
                el("label", { textContent: "Class Teacher Remarks" }),
                el("input", { className: "input", placeholder: "Excellent progress...", onInput: (e) => { tmRemarks = e.target.value; } }),
              ]),
              el("button", { type: "submit", className: "btn btn-primary", textContent: "Publish Term Report Card" }),
            ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Student Doubts (${doubts.length})` }),
        el("div", { className: "item-list" },
          doubts.length === 0
            ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No student doubts posted yet." })]
            : doubts.map((db) =>
                el("div", { className: "list-row" }, [
                  el("div", { style: { flex: 1 } }, [
                    el("strong", { textContent: `${pick(db, "studentName", "student_name")} (${pick(db, "className", "class_name")}) • ${db.subject}` }),
                    el("p", { style: { margin: "4px 0" }, textContent: db.message }),
                    db.reply
                      ? el("div", { style: { color: "var(--emerald)", fontWeight: 600 }, textContent: `Replied: ${db.reply}` })
                      : el("div", { className: "btn-row", style: { marginTop: "6px" } }, [
                          el("input", { className: "input", style: { flex: 1 }, placeholder: "Write reply...", onInput: (e) => { replyMap[db.id] = e.target.value; } }),
                          el("button", {
                            type: "button",
                            className: "btn btn-primary btn-sm",
                            onClick: async () => {
                              await apiRequest(`/api/doubts/${encodeURIComponent(db.id)}`, { method: "PUT", body: JSON.stringify({ reply: replyMap[db.id] || "" }) });
                              await refreshDashboard();
                              showToast("Reply sent!");
                              render();
                            },
                            textContent: "Reply",
                          }),
                        ]),
                  ]),
                ])
              )
        ),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Current Timetable (${cls}-${sec})` }),
        buildTimetableTable(cls, sec),
      ]),
    ]);
  }

  return buildCraftStudioSection(teacher);
}

// ==========================================
// FEE MANAGER PORTAL
// ==========================================
function renderFeeManagerPortal(tabIdx) {
  const d = state.dashboard;
  const students = d.users || [];
  const feeItems = d.feeItems || [];
  const payments = d.payments || [];
  const gw = d.gateway || {};

  if (tabIdx === 0) {
    const pending = payments.filter((p) => p.status === "PENDING");
    const reasonMap = {};
    return el("section", { className: "card" }, [
      el("h3", { className: "section-title", textContent: `Pending UTR Verifications (${pending.length})` }),
      el("div", { className: "item-list" },
        pending.length === 0
          ? [el("p", { style: { color: "var(--emerald)", fontWeight: 700 }, textContent: "All submitted fee proofs have been processed!" })]
          : pending.map((p) =>
              el("div", { className: "list-row" }, [
                el("div", { style: { flex: 1, minWidth: "240px" } }, [
                  el("strong", { textContent: `${pick(p, "studentName", "student_name")} (${pick(p, "className", "class_name")}) — ₹${p.amount}` }),
                  el("div", { style: { fontSize: "0.84rem", color: "var(--text-muted)" }, textContent: `Roll: ${pick(p, "admissionNo", "admission_no")} • ${pick(p, "feeSummary", "fee_summary")}` }),
                  el("div", { style: { fontSize: "0.86rem", fontWeight: 700, marginTop: "4px" }, textContent: `UTR: ${pick(p, "utrOrRef", "utr_or_ref")} (${pick(p, "upiApp", "upi_app")})` }),
                  el("input", { className: "input", style: { marginTop: "8px" }, placeholder: "Optional reject reason", onInput: (e) => { reasonMap[p.id] = e.target.value; } }),
                ]),
                el("div", { className: "btn-row" }, [
                  el("button", {
                    type: "button",
                    className: "btn btn-emerald btn-sm",
                    onClick: async () => {
                      const res = await apiRequest(`/api/payments/${encodeURIComponent(p.id)}`, { method: "PUT", body: JSON.stringify({ action: "VERIFY" }) });
                      await refreshDashboard();
                      showToast(`Verified! Official Receipt ${res.receiptNo} issued.`);
                      render();
                    },
                    textContent: "Verify & Issue Receipt",
                  }),
                  el("button", {
                    type: "button",
                    className: "btn btn-rose btn-sm",
                    onClick: async () => {
                      await apiRequest(`/api/payments/${encodeURIComponent(p.id)}`, { method: "PUT", body: JSON.stringify({ action: "REJECT", reason: reasonMap[p.id] || "UTR mismatch" }) });
                      await refreshDashboard();
                      showToast("Payment rejected & fee restored to UNPAID.");
                      render();
                    },
                    textContent: "Reject",
                  }),
                ]),
              ])
            )
      ),
    ]);
  }

  if (tabIdx === 1) {
    let targetStudentId = "ALL";
    let feeHead = "Monthly Tuition Fee";
    let month = "November 2026";
    let customAmt = "2500";
    let searchText = state.feeSearch;

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Assign Fee Demand (Bulk or Student)" }),
        el("div", { className: "btn-row", style: { marginBottom: "12px" } }, [
          el("input", { className: "input", style: { flex: 1 }, value: searchText, placeholder: "Search student by name or roll no...", onInput: (e) => { searchText = e.target.value; } }),
          el("button", {
            type: "button",
            className: "btn btn-outline",
            onClick: async () => {
              state.feeSearch = searchText;
              await refreshDashboard();
              render();
            },
            textContent: "Search",
          }),
        ]),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            const targets = targetStudentId === "ALL" ? students : students.filter((s) => s.id === targetStudentId);
            const items = targets
              .map((st) => {
                let amt = Number(customAmt || 0);
                if (targetStudentId === "ALL") {
                  if (feeHead === "Monthly Tuition Fee" && st.monthlyTuition > 0) amt = st.monthlyTuition;
                  if (feeHead === "Transport / Bus Fee") amt = st.transportFee;
                }
                return { studentDocId: st.id, title: `${feeHead} (${month})`, month, amount: amt };
              })
              .filter((x) => x.amount > 0);
            if (items.length === 0) {
              showToast("No valid positive fee amounts to assign.");
              return;
            }
            const res = await apiRequest("/api/fee-items", { method: "POST", body: JSON.stringify({ items }) });
            await refreshDashboard();
            showToast(`Assigned fee demand to ${res.count} student(s).`);
            render();
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Target Student(s)" }),
            el("select", { className: "select", onChange: (e) => { targetStudentId = e.target.value; } }, [
              el("option", { value: "ALL", textContent: `All Loaded Students (${students.length})` }),
              ...students.map((st) => el("option", { value: st.id, textContent: `${st.fullName} (Roll ${st.userId} • ${st.className}-${st.section})` })),
            ]),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Fee Head" }),
            el("select", { className: "select", onChange: (e) => { feeHead = e.target.value; } },
              ["Monthly Tuition Fee", "Transport / Bus Fee", "Exam Fee", "Annual / Lab Fee"].map((h) => el("option", { value: h, textContent: h }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Billing Month / Term" }),
            el("input", { className: "input", value: month, onInput: (e) => { month = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Default / Custom Amount (₹)" }),
            el("input", { className: "input", type: "number", value: customAmt, onInput: (e) => { customAmt = e.target.value; } }),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Assign Fee Demand" }),
          ]),
        ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Recent Fee Ledger (${feeItems.length})` }),
        el("div", { className: "item-list" },
          feeItems.slice(0, 40).map((f) =>
            el("div", { className: "list-row" }, [
              el("div", {}, [
                el("strong", { textContent: f.title }),
                el("div", { style: { fontSize: "0.8rem", color: "var(--text-muted)" }, textContent: `${f.month} • ₹${f.amount}` }),
              ]),
              el("div", { className: "btn-row" }, [
                el("span", { className: `pill ${f.status === "PAID" ? "pill-emerald" : f.status === "UNPAID" ? "pill-rose" : "pill-gold"}`, textContent: f.status }),
                f.status === "UNPAID"
                  ? el("button", {
                      type: "button",
                      className: "btn btn-rose btn-sm",
                      onClick: async () => {
                        await apiRequest(`/api/fee-items/${encodeURIComponent(f.id)}`, { method: "DELETE" });
                        await refreshDashboard();
                        showToast("Fee item removed.");
                        render();
                      },
                      textContent: "Delete",
                    })
                  : null,
              ]),
            ])
          )
        ),
      ]),
    ]);
  }

  if (tabIdx === 2) {
    let merchantUpi = gw.merchantUpi || "";
    let payeeName = gw.payeeName || SCHOOL_INFO.name;
    let bankName = gw.bankName || "";
    let accountNo = gw.accountNo || "";
    let ifsc = gw.ifsc || "";
    let razorpayKeyId = gw.razorpayKeyId || "";

    return el("section", { className: "card" }, [
      el("h3", { className: "section-title", textContent: "School UPI & Bank Gateway Configuration" }),
      el("form", {
        className: "form-grid",
        onSubmit: async (e) => {
          e.preventDefault();
          await apiRequest("/api/gateway", {
            method: "PUT",
            body: JSON.stringify({ merchantUpi, payeeName, bankName, accountNo, ifsc, razorpayKeyId }),
          });
          await refreshDashboard();
          showToast("Payment gateway updated live across Web & Android!");
          render();
        },
      }, [
        el("div", { className: "field" }, [el("label", { textContent: "Merchant UPI ID" }), el("input", { className: "input", value: merchantUpi, onInput: (e) => { merchantUpi = e.target.value; } })]),
        el("div", { className: "field" }, [el("label", { textContent: "Payee Name" }), el("input", { className: "input", value: payeeName, onInput: (e) => { payeeName = e.target.value; } })]),
        el("div", { className: "field" }, [el("label", { textContent: "Bank Name" }), el("input", { className: "input", value: bankName, onInput: (e) => { bankName = e.target.value; } })]),
        el("div", { className: "field" }, [el("label", { textContent: "Account Number" }), el("input", { className: "input", value: accountNo, onInput: (e) => { accountNo = e.target.value; } })]),
        el("div", { className: "field" }, [el("label", { textContent: "IFSC Code" }), el("input", { className: "input", value: ifsc, onInput: (e) => { ifsc = e.target.value; } })]),
        el("div", { className: "field" }, [el("label", { textContent: "Razorpay Public Key ID (Optional)" }), el("input", { className: "input", value: razorpayKeyId, onInput: (e) => { razorpayKeyId = e.target.value; } })]),
        el("div", { className: "field full" }, [el("button", { type: "submit", className: "btn btn-primary", textContent: "Save Gateway Config" })]),
      ]),
    ]);
  }

  return el("section", { className: "card" }, [
    el("h3", { className: "section-title", textContent: `All Payment Transactions (${payments.length})` }),
    el("div", { className: "item-list" },
      payments.map((p) =>
        el("div", { className: "list-row" }, [
          el("div", {}, [
            el("strong", { textContent: `${pick(p, "studentName", "student_name")} — ₹${p.amount}` }),
            el("div", { style: { fontSize: "0.82rem", color: "var(--text-muted)" }, textContent: `UTR: ${pick(p, "utrOrRef", "utr_or_ref")} • Receipt: ${pick(p, "receiptNo", "receipt_no") || "Pending"}` }),
          ]),
          el("span", { className: `pill ${p.status === "VERIFIED" ? "pill-emerald" : p.status === "REJECTED" ? "pill-rose" : "pill-gold"}`, textContent: p.status }),
        ])
      )
    ),
  ]);
}

// ==========================================
// ADMIN PORTAL
// ==========================================
function renderAdminPortal(tabIdx) {
  const d = state.dashboard;
  const admin = d.currentUser;
  const stats = d.stats || {};
  const users = d.users || [];
  const gw = d.gateway || {};
  const circulars = d.circulars || [];
  const posters = d.campusPosters || [];
  const payments = d.payments || [];

  if (tabIdx === 0) {
    // Overview + In-App APK Update Publisher + Daily Campus Poster
    let latestVersionCode = String(gw.latestVersionCode || 2);
    let latestVersionName = gw.latestVersionName || "1.1";
    let githubRepoSlug = gw.githubRepoSlug || "";
    let apkDownloadUrl = gw.apkDownloadUrl || "";
    let updateNotes = gw.updateNotes || "";

    let posterTitle = "";
    let posterSub = "";
    let posterImg = "";

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      el("div", { className: "stats-grid" }, [
        el("div", { className: "stat-card" }, [el("div", { className: "stat-lbl", textContent: "Enrolled Students" }), el("div", { className: "stat-val", textContent: String(stats.totalStudents ?? 0) })]),
        el("div", { className: "stat-card" }, [el("div", { className: "stat-lbl", textContent: "Active Teachers" }), el("div", { className: "stat-val", textContent: String(stats.totalTeachers ?? 0) })]),
        el("div", { className: "stat-card" }, [el("div", { className: "stat-lbl", textContent: "Fee Managers" }), el("div", { className: "stat-val", textContent: String(stats.totalFeeManagers ?? 0) })]),
        el("div", { className: "stat-card" }, [el("div", { className: "stat-lbl", textContent: "Circulars Published" }), el("div", { className: "stat-val", textContent: String(circulars.length) })]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Publish In-App Android APK Update (Installs Inside App)" }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            await apiRequest("/api/gateway", {
              method: "PUT",
              body: JSON.stringify({
                latestVersionCode: Number(latestVersionCode || 2),
                latestVersionName,
                githubRepoSlug,
                apkDownloadUrl,
                updateNotes,
              }),
            });
            await refreshDashboard();
            showToast("In-app update published live to all Android devices!");
            render();
          },
        }, [
          el("div", { className: "field" }, [el("label", { textContent: "Latest Version Code" }), el("input", { className: "input", type: "number", value: latestVersionCode, onInput: (e) => { latestVersionCode = e.target.value; } })]),
          el("div", { className: "field" }, [el("label", { textContent: "Latest Version Name" }), el("input", { className: "input", value: latestVersionName, onInput: (e) => { latestVersionName = e.target.value; } })]),
          el("div", { className: "field" }, [el("label", { textContent: "GitHub Repo Slug (owner/repo)" }), el("input", { className: "input", value: githubRepoSlug, placeholder: "e.g. user/doon-school-app", onInput: (e) => { githubRepoSlug = e.target.value; } })]),
          el("div", { className: "field" }, [el("label", { textContent: "Direct HTTPS APK Download URL" }), el("input", { className: "input", value: apkDownloadUrl, placeholder: "https://github.com/.../DoonPublicSchool-Etah.apk", onInput: (e) => { apkDownloadUrl = e.target.value; } })]),
          el("div", { className: "field full" }, [el("label", { textContent: "Release Notes" }), el("input", { className: "input", value: updateNotes, placeholder: "What's new in this update", onInput: (e) => { updateNotes = e.target.value; } })]),
          el("div", { className: "field full" }, [el("button", { type: "submit", className: "btn btn-primary", textContent: "Publish App Update Live" })]),
        ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Daily Campus Poster Feed (${posters.length})` }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            if (!posterImg) {
              showToast("Please select a poster image first.");
              return;
            }
            await apiRequest("/api/posters", {
              method: "POST",
              body: JSON.stringify({ title: posterTitle, subtitle: posterSub, date: todayIso(), imageData: posterImg }),
            });
            await refreshDashboard();
            showToast("Daily Campus Poster published!");
            render();
          },
        }, [
          el("div", { className: "field" }, [el("label", { textContent: "Poster Headline" }), el("input", { className: "input", placeholder: "e.g. Annual Sports & Assembly", onInput: (e) => { posterTitle = e.target.value; } })]),
          el("div", { className: "field" }, [el("label", { textContent: "Subtitle" }), el("input", { className: "input", placeholder: "Date / details", onInput: (e) => { posterSub = e.target.value; } })]),
          el("div", { className: "field full" }, [
            el("label", { textContent: "Poster Photo (auto-compressed)" }),
            el("input", {
              type: "file",
              accept: "image/*",
              className: "input",
              onChange: async (e) => {
                const f = e.target.files?.[0];
                if (f) {
                  posterImg = await compressImageFile(f);
                  showToast("Poster image ready.");
                }
              },
            }),
          ]),
          el("div", { className: "field full" }, [el("button", { type: "submit", className: "btn btn-primary", textContent: "Publish Campus Poster" })]),
        ]),
        posters.length > 0
          ? el("div", { className: "item-list", style: { marginTop: "12px" } },
              posters.map((p) =>
                el("div", { className: "list-row" }, [
                  el("strong", { textContent: `${p.title} (${p.date})` }),
                  el("button", {
                    type: "button",
                    className: "btn btn-rose btn-sm",
                    onClick: async () => {
                      await apiRequest(`/api/posters/${encodeURIComponent(p.id)}`, { method: "DELETE" });
                      await refreshDashboard();
                      showToast("Poster removed.");
                      render();
                    },
                    textContent: "Delete",
                  }),
                ])
              )
            )
          : null,
      ]),
    ]);
  }

  if (tabIdx === 1) {
    // Accounts Manager (Students auto roll 001..., Teachers, Fee Managers, Admins)
    let newRole = "STUDENT";
    let newFullName = "";
    let newUserId = "";
    let newPhone = "";
    let newPassword = "";
    let newClass = "Class I";
    let newSection = "A";
    let newSubject = ALL_SUBJECTS[0];
    let newParent = "";
    let newTuition = "2500";
    let newTransport = "900";
    let searchVal = state.adminUserSearch;
    let roleVal = state.adminRoleFilter;

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Create School Account" }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            try {
              const res = await apiRequest("/api/users", {
                method: "POST",
                body: JSON.stringify({
                  role: newRole,
                  fullName: newFullName,
                  userId: newUserId.trim(),
                  phone: newPhone,
                  password: newPassword,
                  wing: newRole === "STUDENT" || newRole === "TEACHER" ? wingForClass(newClass) : "All Wings",
                  className: newRole === "STUDENT" || newRole === "TEACHER" ? newClass : "",
                  section: newRole === "STUDENT" || newRole === "TEACHER" ? newSection : "",
                  subject: newRole === "TEACHER" ? newSubject : "",
                  parentName: newRole === "STUDENT" ? newParent : "",
                  monthlyTuition: Number(newTuition || 0),
                  transportFee: Number(newTransport || 0),
                }),
              });
              await refreshDashboard();
              showToast(`Account created: ${res.user.fullName} (ID: ${res.user.userId})`);
              render();
            } catch (err) {
              showToast(err.message);
            }
          },
        }, [
          el("div", { className: "field" }, [
            el("label", { textContent: "Role" }),
            el("select", { className: "select", onChange: (e) => { newRole = e.target.value; } },
              ["STUDENT", "TEACHER", "FEE_MANAGER", "ADMIN"].map((r) => el("option", { value: r, textContent: r.replace("_", " ") }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Full Name" }),
            el("input", { className: "input", placeholder: "Full Name", onInput: (e) => { newFullName = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "User / Roll ID (blank for Student = auto 001...)" }),
            el("input", { className: "input", placeholder: "e.g. TCH101 / FEE201 or leave blank", onInput: (e) => { newUserId = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Password" }),
            el("input", { className: "input", type: "password", placeholder: "Min 4 characters", onInput: (e) => { newPassword = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Class (Students / Teachers)" }),
            el("select", { className: "select", onChange: (e) => { newClass = e.target.value; } },
              ALL_CLASSES.map((c) => el("option", { value: c, textContent: c }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Section" }),
            el("select", { className: "select", onChange: (e) => { newSection = e.target.value; } },
              SECTIONS.map((s) => el("option", { value: s, textContent: s }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Teacher Subject (if Teacher)" }),
            el("select", { className: "select", onChange: (e) => { newSubject = e.target.value; } },
              ALL_SUBJECTS.map((s) => el("option", { value: s, textContent: s }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Phone Number" }),
            el("input", { className: "input", placeholder: "+91...", onInput: (e) => { newPhone = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Parent Name (if Student)" }),
            el("input", { className: "input", placeholder: "Guardian Name", onInput: (e) => { newParent = e.target.value; } }),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Monthly Tuition / Transport (₹)" }),
            el("div", { className: "btn-row" }, [
              el("input", { className: "input", style: { flex: 1 }, type: "number", value: newTuition, onInput: (e) => { newTuition = e.target.value; } }),
              el("input", { className: "input", style: { flex: 1 }, type: "number", value: newTransport, onInput: (e) => { newTransport = e.target.value; } }),
            ]),
          ]),
          el("div", { className: "field full" }, [
            el("button", { type: "submit", className: "btn btn-primary", textContent: "Create Account in Cloud" }),
          ]),
        ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Directory (${users.length} loaded — scales to 3,000+)` }),
        el("div", { className: "btn-row", style: { marginBottom: "12px" } }, [
          el("input", { className: "input", style: { flex: 1 }, value: searchVal, placeholder: "Search name, roll no, class...", onInput: (e) => { searchVal = e.target.value; } }),
          el("select", { className: "select", style: { width: "auto" }, onChange: (e) => { roleVal = e.target.value; } }, [
            el("option", { value: "", selected: roleVal === "", textContent: "All Roles" }),
            ...["STUDENT", "TEACHER", "FEE_MANAGER", "ADMIN"].map((r) => el("option", { value: r, selected: roleVal === r, textContent: r })),
          ]),
          el("button", {
            type: "button",
            className: "btn btn-primary",
            onClick: async () => {
              state.adminUserSearch = searchVal;
              state.adminRoleFilter = roleVal;
              await refreshDashboard();
              render();
            },
            textContent: "Search",
          }),
        ]),
        el("div", { className: "item-list" },
          users.map((u) =>
            el("div", { className: "list-row" }, [
              el("div", {}, [
                el("strong", { textContent: `${u.fullName} (${u.userId})` }),
                el("div", { style: { fontSize: "0.82rem", color: "var(--text-muted)" }, textContent: `${u.role} • ${u.className ? `${u.className}-${u.section}` : u.wing} ${u.subject ? `• ${u.subject}` : ""}` }),
              ]),
              u.id !== admin.id
                ? el("button", {
                    type: "button",
                    className: "btn btn-rose btn-sm",
                    onClick: async () => {
                      await apiRequest(`/api/users/${encodeURIComponent(u.id)}`, { method: "DELETE" });
                      await refreshDashboard();
                      showToast("Account removed.");
                      render();
                    },
                    textContent: "Delete",
                  })
                : el("span", { className: "pill pill-gold", textContent: "Active You" }),
            ])
          )
        ),
      ]),
    ]);
  }

  if (tabIdx === 2) {
    // Circulars & Holidays Publisher (with Class & Section Dropdowns)
    let title = "";
    let category = "Notice";
    let targetWing = "All Wings";
    let targetClass = "";
    let targetSection = "";
    let content = "";

    return el("div", { className: "item-list", style: { gap: "16px" } }, [
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: "Publish Official Circular / Holiday Notice" }),
        el("form", {
          className: "form-grid",
          onSubmit: async (e) => {
            e.preventDefault();
            const audience = targetClass ? `${targetClass}${targetSection ? `-${targetSection}` : ""}` : targetWing;
            await apiRequest("/api/circulars", {
              method: "POST",
              body: JSON.stringify({ title, category, audience, targetWing, targetClass, targetSection, date: todayIso(), content }),
            });
            await refreshDashboard();
            showToast(`Circular published to ${audience}!`);
            render();
          },
        }, [
          el("div", { className: "field" }, [el("label", { textContent: "Title" }), el("input", { className: "input", placeholder: "Notice or Holiday title", onInput: (e) => { title = e.target.value; } })]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Category" }),
            el("select", { className: "select", onChange: (e) => { category = e.target.value; } },
              ["Notice", "Holiday", "Exam", "Event"].map((c) => el("option", { value: c, textContent: c }))
            ),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Target Wing" }),
            el("select", { className: "select", onChange: (e) => { targetWing = e.target.value; } }, [
              el("option", { value: "All Wings", textContent: "All Wings" }),
              ...WINGS.map((w) => el("option", { value: w.name, textContent: w.name })),
            ]),
          ]),
          el("div", { className: "field" }, [
            el("label", { textContent: "Target Class / Section (optional)" }),
            el("div", { className: "btn-row" }, [
              el("select", { className: "select", style: { flex: 1 }, onChange: (e) => { targetClass = e.target.value; } }, [
                el("option", { value: "", textContent: "All Classes" }),
                ...ALL_CLASSES.map((c) => el("option", { value: c, textContent: c })),
              ]),
              el("select", { className: "select", style: { width: "100px" }, onChange: (e) => { targetSection = e.target.value; } }, [
                el("option", { value: "", textContent: "All Sec" }),
                ...SECTIONS.map((s) => el("option", { value: s, textContent: `Sec ${s}` })),
              ]),
            ]),
          ]),
          el("div", { className: "field full" }, [el("label", { textContent: "Message" }), el("textarea", { className: "textarea", placeholder: "Write full circular content...", onInput: (e) => { content = e.target.value; } })]),
          el("div", { className: "field full" }, [el("button", { type: "submit", className: "btn btn-primary", textContent: "Publish Circular" })]),
        ]),
      ]),
      el("section", { className: "card" }, [
        el("h3", { className: "section-title", textContent: `Published Circulars (${circulars.length})` }),
        el("div", { className: "item-list" },
          circulars.map((c) =>
            el("div", { className: "list-row" }, [
              el("div", {}, [
                el("strong", { textContent: `${c.title} (${c.category})` }),
                el("div", { style: { fontSize: "0.86rem" }, textContent: c.content }),
                el("div", { style: { fontSize: "0.78rem", color: "var(--text-muted)" }, textContent: `${c.date} • ${c.audience}` }),
              ]),
              el("button", {
                type: "button",
                className: "btn btn-rose btn-sm",
                onClick: async () => {
                  await apiRequest(`/api/circulars/${encodeURIComponent(c.id)}`, { method: "DELETE" });
                  await refreshDashboard();
                  showToast("Circular deleted.");
                  render();
                },
                textContent: "Delete",
              }),
            ])
          )
        ),
      ]),
    ]);
  }

  if (tabIdx === 3) {
    return el("section", { className: "card" }, [
      el("h3", { className: "section-title", textContent: `School Fee Payments Audit (${payments.length})` }),
      el("div", { className: "item-list" },
        payments.length === 0
          ? [el("p", { style: { color: "var(--text-muted)" }, textContent: "No payment records yet." })]
          : payments.map((p) =>
              el("div", { className: "list-row" }, [
                el("div", {}, [
                  el("strong", { textContent: `${pick(p, "studentName", "student_name")} (${pick(p, "className", "class_name")}) — ₹${p.amount}` }),
                  el("div", { style: { fontSize: "0.82rem", color: "var(--text-muted)" }, textContent: `UTR: ${pick(p, "utrOrRef", "utr_or_ref")} • Receipt: ${pick(p, "receiptNo", "receipt_no") || "Pending"}` }),
                ]),
                el("span", { className: `pill ${p.status === "VERIFIED" ? "pill-emerald" : p.status === "REJECTED" ? "pill-rose" : "pill-gold"}`, textContent: p.status }),
              ])
            )
      ),
    ]);
  }

  return buildCraftStudioSection(admin);
}

// ==========================================
// AUTHENTICATED SHELL & NAVIGATION
// ==========================================
function renderAuthenticatedShell(root) {
  const user = state.dashboard.currentUser;
  const tabDefs =
    user.role === "ADMIN"
      ? ["Dashboard", "Accounts", "Circulars", "Payments", "Craft Studio"]
      : user.role === "TEACHER"
        ? ["Daily DPT", "Weekly DPT", "Attendance & Leaves", "Notices & Class", "Diary & Marks", "Craft Studio"]
        : user.role === "FEE_MANAGER"
          ? ["Verify Payments", "Fee Ledger", "Gateway Config", "All Receipts"]
          : ["Home & Notices", "DPT & Report Cards", "Fees & UPI Pay", "Academics & Leave", "Craft Studio"];

  if (state.activeTab >= tabDefs.length) state.activeTab = 0;

  const topbar = el("header", { className: "topbar" }, [
    el("div", { className: "topbar-brand" }, [
      el("img", { src: "/crest.svg", alt: "Doon Public School Logo", className: "topbar-logo" }),
      el("div", {}, [
        el("h1", { className: "topbar-title", textContent: SCHOOL_INFO.name }),
        el("p", { className: "topbar-sub", textContent: `${user.fullName} • ${user.role.replace("_", " ")} (${user.userId})` }),
      ]),
    ]),
    el("div", { className: "topbar-actions" }, [
      el("button", { type: "button", className: "icon-btn", onClick: toggleDarkMode, textContent: state.darkMode ? "Light" : "Dark" }),
      el("button", { type: "button", className: "icon-btn", onClick: openProfileModal, textContent: "Profile" }),
      el("button", {
        type: "button",
        className: "icon-btn",
        onClick: async () => {
          await refreshDashboard();
          showToast("Synced with Cloudflare D1!");
          render();
        },
        textContent: "Sync",
      }),
      el("button", {
        type: "button",
        className: "icon-btn",
        onClick: () => {
          state.token = null;
          state.dashboard = null;
          state.activeTab = 0;
          showToast("Signed out.");
          render();
        },
        textContent: "Sign Out",
      }),
    ]),
  ]);

  const mobileTabs = el("nav", { className: "mobile-tabs", "aria-label": "Portal Sections" },
    tabDefs.map((lbl, idx) =>
      el("button", {
        type: "button",
        className: `mobile-tab-btn ${state.activeTab === idx ? "active" : ""}`,
        onClick: () => {
          state.activeTab = idx;
          render();
        },
        textContent: lbl,
      })
    )
  );

  const sidebar = el("aside", { className: "sidebar", "aria-label": "Sidebar Navigation" },
    tabDefs.map((lbl, idx) =>
      el("button", {
        type: "button",
        className: `nav-item ${state.activeTab === idx ? "active" : ""}`,
        onClick: () => {
          state.activeTab = idx;
          render();
        },
        textContent: lbl,
      })
    )
  );

  const pageBody =
    user.role === "ADMIN"
      ? renderAdminPortal(state.activeTab)
      : user.role === "TEACHER"
        ? renderTeacherPortal(state.activeTab)
        : user.role === "FEE_MANAGER"
          ? renderFeeManagerPortal(state.activeTab)
          : renderStudentPortal(state.activeTab);

  const mainContent = el("main", { className: "main-content" }, [pageBody, buildCreditsCard()]);

  root.appendChild(topbar);
  root.appendChild(mobileTabs);
  root.appendChild(el("div", { className: "portal-layout" }, [sidebar, mainContent]));
}

function render() {
  const root = document.getElementById("app-root");
  if (!root) return;
  clearNode(root);

  if (state.booting) {
    root.appendChild(
      el("main", { className: "boot-loader" }, [
        el("div", { className: "boot-card" }, [
          el("div", { className: "spinner", "aria-hidden": "true" }),
          el("h1", { textContent: SCHOOL_INFO.name }),
          el("p", { textContent: "Connecting to Cloudflare Edge Cloud ERP..." }),
        ]),
      ])
    );
    return;
  }

  if (!state.token || !state.dashboard) {
    renderLoginScreen(root);
    return;
  }

  renderAuthenticatedShell(root);
}

async function bootstrap() {
  try {
    const boot = await apiRequest("/api/bootstrap/status");
    state.needsAdminSetup = Boolean(boot.needsSetup);
  } catch (err) {
    showToast(`Cloud connection notice: ${err.message}`);
  } finally {
    state.booting = false;
    render();
  }
}

document.addEventListener("DOMContentLoaded", bootstrap);
