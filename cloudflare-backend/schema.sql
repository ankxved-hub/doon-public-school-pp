-- Doon Public School, Etah — Cloudflare D1 (SQLite) Schema
-- Optimized with composite indexes for 3,000+ accounts, 3,000 daily logins, and 30+ concurrent users.

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  role TEXT NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'FEE_MANAGER', 'STUDENT')),
  user_id TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  full_name TEXT NOT NULL,
  phone TEXT NOT NULL DEFAULT '',
  wing TEXT NOT NULL DEFAULT '',
  class_name TEXT NOT NULL DEFAULT '',
  section TEXT NOT NULL DEFAULT '',
  subject TEXT NOT NULL DEFAULT '',
  parent_name TEXT NOT NULL DEFAULT '',
  monthly_tuition INTEGER NOT NULL DEFAULT 0,
  transport_fee INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_role_class_sec ON users(role, class_name, section, user_id);

CREATE TABLE IF NOT EXISTS gateway_config (
  id TEXT PRIMARY KEY,
  merchant_upi TEXT NOT NULL,
  payee_name TEXT NOT NULL,
  bank_name TEXT NOT NULL DEFAULT '',
  account_no TEXT NOT NULL DEFAULT '',
  ifsc TEXT NOT NULL DEFAULT '',
  razorpay_key_id TEXT NOT NULL DEFAULT '',
  latest_version_code INTEGER NOT NULL DEFAULT 2,
  latest_version_name TEXT NOT NULL DEFAULT '1.1',
  apk_download_url TEXT NOT NULL DEFAULT '',
  github_repo_slug TEXT NOT NULL DEFAULT '',
  update_notes TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS circulars (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'Notice',
  audience TEXT NOT NULL DEFAULT 'All Wings',
  target_wing TEXT NOT NULL DEFAULT '',
  target_class TEXT NOT NULL DEFAULT '',
  target_section TEXT NOT NULL DEFAULT '',
  date TEXT NOT NULL,
  content TEXT NOT NULL,
  posted_by TEXT NOT NULL,
  recipient_doc_id TEXT NOT NULL DEFAULT '',
  recipient_name TEXT NOT NULL DEFAULT '',
  sender_role TEXT NOT NULL DEFAULT 'ADMIN' CHECK (sender_role IN ('ADMIN', 'TEACHER')),
  attachment_name TEXT NOT NULL DEFAULT '',
  attachment_mime_type TEXT NOT NULL DEFAULT '',
  attachment_data TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_circulars_created ON circulars(created_at DESC);

CREATE TABLE IF NOT EXISTS dpt_daily (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  subject TEXT NOT NULL,
  assessment_type TEXT NOT NULL DEFAULT 'DPT' CHECK (assessment_type IN ('DPT', 'EXAM')),
  held INTEGER NOT NULL DEFAULT 1,
  topic TEXT NOT NULL DEFAULT '',
  max_marks INTEGER NOT NULL DEFAULT 0,
  notes TEXT NOT NULL DEFAULT '',
  reason TEXT NOT NULL DEFAULT '',
  attachment_name TEXT NOT NULL DEFAULT '',
  attachment_mime_type TEXT NOT NULL DEFAULT '',
  attachment_data TEXT NOT NULL DEFAULT '',
  teacher_id TEXT NOT NULL DEFAULT '',
  teacher_name TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dpt_daily_class_date ON dpt_daily(class_name, section, date DESC);

CREATE TABLE IF NOT EXISTS dpt_weekly (
  id TEXT PRIMARY KEY,
  week TEXT NOT NULL,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  subject TEXT NOT NULL,
  student_doc_id TEXT NOT NULL,
  student_name TEXT NOT NULL,
  admission_no TEXT NOT NULL,
  scored INTEGER NOT NULL DEFAULT 0,
  max_marks INTEGER NOT NULL DEFAULT 20,
  teacher_id TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dpt_weekly_class_week ON dpt_weekly(class_name, section, week);
CREATE INDEX IF NOT EXISTS idx_dpt_weekly_student ON dpt_weekly(student_doc_id);

CREATE TABLE IF NOT EXISTS attendance (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  student_doc_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('Present', 'Absent', 'Leave')),
  marked_by TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_doc_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_class_date ON attendance(class_name, section, date DESC);

CREATE TABLE IF NOT EXISTS leaves (
  id TEXT PRIMARY KEY,
  student_doc_id TEXT NOT NULL,
  student_name TEXT NOT NULL,
  admission_no TEXT NOT NULL,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  from_date TEXT NOT NULL,
  to_date TEXT NOT NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending', 'Approved', 'Rejected')),
  teacher_remark TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_leaves_student ON leaves(student_doc_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_leaves_class ON leaves(class_name, section, created_at DESC);

CREATE TABLE IF NOT EXISTS homework (
  id TEXT PRIMARY KEY,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  subject TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  due_date TEXT NOT NULL,
  teacher_name TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_homework_class ON homework(class_name, section, created_at DESC);

CREATE TABLE IF NOT EXISTS term_marks (
  id TEXT PRIMARY KEY,
  term TEXT NOT NULL,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  student_doc_id TEXT NOT NULL,
  student_name TEXT NOT NULL,
  admission_no TEXT NOT NULL,
  marks_json TEXT NOT NULL,
  remarks TEXT NOT NULL DEFAULT '',
  published_by TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_term_marks_student ON term_marks(student_doc_id);

CREATE TABLE IF NOT EXISTS fee_items (
  id TEXT PRIMARY KEY,
  student_doc_id TEXT NOT NULL,
  title TEXT NOT NULL,
  month TEXT NOT NULL,
  amount INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID', 'PENDING_VERIFICATION', 'PAID')),
  payment_id TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fee_items_student ON fee_items(student_doc_id, status);

CREATE TABLE IF NOT EXISTS fee_payments (
  id TEXT PRIMARY KEY,
  student_doc_id TEXT NOT NULL,
  student_name TEXT NOT NULL,
  admission_no TEXT NOT NULL,
  class_name TEXT NOT NULL,
  fee_item_ids_json TEXT NOT NULL,
  fee_summary TEXT NOT NULL,
  amount INTEGER NOT NULL DEFAULT 0,
  mode TEXT NOT NULL DEFAULT 'UPI' CHECK (mode IN ('UPI', 'RAZORPAY')),
  utr_or_ref TEXT NOT NULL,
  upi_app TEXT NOT NULL DEFAULT 'GPay',
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
  receipt_no TEXT NOT NULL DEFAULT '',
  reject_reason TEXT NOT NULL DEFAULT '',
  verified_by TEXT NOT NULL DEFAULT '',
  verified_at_str TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fee_payments_student ON fee_payments(student_doc_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fee_payments_status ON fee_payments(status, created_at DESC);

CREATE TABLE IF NOT EXISTS doubts (
  id TEXT PRIMARY KEY,
  student_doc_id TEXT NOT NULL,
  student_name TEXT NOT NULL,
  class_name TEXT NOT NULL,
  subject TEXT NOT NULL,
  message TEXT NOT NULL,
  reply TEXT NOT NULL DEFAULT '',
  replied_by TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'REPLIED')),
  date_label TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_doubts_student ON doubts(student_doc_id, created_at DESC);

CREATE TABLE IF NOT EXISTS craft_projects (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'Art & Craft',
  description TEXT NOT NULL,
  materials TEXT NOT NULL DEFAULT '',
  author_doc_id TEXT NOT NULL,
  author_name TEXT NOT NULL,
  author_user_id TEXT NOT NULL,
  author_role TEXT NOT NULL CHECK (author_role IN ('STUDENT', 'TEACHER', 'ADMIN')),
  class_info TEXT NOT NULL DEFAULT '',
  date_label TEXT NOT NULL,
  ai_image_data TEXT NOT NULL DEFAULT '',
  ai_prompt_used TEXT NOT NULL DEFAULT '',
  likes_count INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_craft_created ON craft_projects(created_at DESC);

CREATE TABLE IF NOT EXISTS craft_likes (
  like_id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL,
  liker_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  UNIQUE(project_id, liker_id)
);

CREATE TABLE IF NOT EXISTS campus_posters (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  subtitle TEXT NOT NULL DEFAULT '',
  date TEXT NOT NULL,
  image_data TEXT NOT NULL,
  posted_by TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_campus_posters_date ON campus_posters(date DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS timetables (
  id TEXT PRIMARY KEY,
  class_name TEXT NOT NULL,
  section TEXT NOT NULL,
  slots_json TEXT NOT NULL,
  updated_by TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
