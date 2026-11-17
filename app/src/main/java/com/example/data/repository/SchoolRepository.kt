package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SchoolRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun safeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "doc" }

    private fun col(ownerUid: String, collectionName: String): CollectionReference =
        db.collection("accounts").document(ownerUid).collection(collectionName)

    private fun <T> observeCollection(
        collectionName: String,
        ownerUid: String,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        emitAll(
            col(ownerUid, collectionName)
                .snapshots()
                .map { snap ->
                    snap.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == ownerUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "accounts/$ownerUid/$collectionName")
                    }
                    throw error
                }
        )
    }

    fun observeUsers(ownerUid: String): Flow<List<SchoolUser>> =
        observeCollection("users", ownerUid, SchoolUser::class.java)

    fun observeGatewayConfig(ownerUid: String): Flow<List<GatewayConfig>> =
        observeCollection("gatewayConfig", ownerUid, GatewayConfig::class.java)

    fun observeCirculars(ownerUid: String): Flow<List<Circular>> =
        observeCollection("circulars", ownerUid, Circular::class.java)

    fun observeDptDaily(ownerUid: String): Flow<List<DptDaily>> =
        observeCollection("dptDaily", ownerUid, DptDaily::class.java)

    fun observeDptWeekly(ownerUid: String): Flow<List<DptWeekly>> =
        observeCollection("dptWeekly", ownerUid, DptWeekly::class.java)

    fun observeAttendance(ownerUid: String): Flow<List<AttendanceRecord>> =
        observeCollection("attendance", ownerUid, AttendanceRecord::class.java)

    fun observeLeaves(ownerUid: String): Flow<List<LeaveRequest>> =
        observeCollection("leaves", ownerUid, LeaveRequest::class.java)

    fun observeHomework(ownerUid: String): Flow<List<HomeworkItem>> =
        observeCollection("homework", ownerUid, HomeworkItem::class.java)

    fun observeTermMarks(ownerUid: String): Flow<List<TermMarkCard>> =
        observeCollection("termMarks", ownerUid, TermMarkCard::class.java)

    fun observeFeeItems(ownerUid: String): Flow<List<FeeItem>> =
        observeCollection("feeItems", ownerUid, FeeItem::class.java)

    fun observePayments(ownerUid: String): Flow<List<FeePayment>> =
        observeCollection("payments", ownerUid, FeePayment::class.java)

    fun observeDoubts(ownerUid: String): Flow<List<DoubtMessage>> =
        observeCollection("doubts", ownerUid, DoubtMessage::class.java)

    fun observeCraftProjects(ownerUid: String): Flow<List<CraftProject>> =
        observeCollection("craftProjects", ownerUid, CraftProject::class.java)

    suspend fun getUsersOnce(ownerUid: String): Result<List<SchoolUser>> = runCatching {
        col(ownerUid, "users")
            .get()
            .await()
            .toObjects(SchoolUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "accounts/$ownerUid/users")
    }

    suspend fun seedDemoDataIfEmpty(ownerUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid == ownerUid) { "Owner UID mismatch" }
        val safeUid = safeDocId(uid)
        val usersCol = col(uid, "users")
        val existingSnap = usersCol.get().await()

        val adminId = safeDocId("${safeUid}_usr_admin_1")
        if (!existingSnap.isEmpty) {
            val hasAdmin = existingSnap.documents.any { it.getString("role") == "ADMIN" }
            if (!hasAdmin) {
                usersCol.document(adminId).set(
                    SchoolUser(
                        id = "",
                        ownerUid = uid,
                        role = "ADMIN",
                        userId = "ADMIN01",
                        password = "admin123",
                        fullName = "Dr. R. K. Tiwari (Principal)",
                        phone = "+91 76176 14471",
                        wing = "All Wings",
                        subject = "Administration",
                    )
                ).await()
            }
            return@runCatching
        }

        val today = SchoolCatalog.todayIso()
        val batch = db.batch()

        fun <T : Any> put(colName: String, rawDocId: String, model: T) {
            batch.set(col(uid, colName).document(safeDocId(rawDocId)), model)
        }

        val tch1Id = safeDocId("${safeUid}_usr_tch_1")
        val tch2Id = safeDocId("${safeUid}_usr_tch_2")
        val tch3Id = safeDocId("${safeUid}_usr_tch_3")
        val fee1Id = safeDocId("${safeUid}_usr_fee_1")
        val std1Id = safeDocId("${safeUid}_usr_std_1")
        val std2Id = safeDocId("${safeUid}_usr_std_2")
        val std3Id = safeDocId("${safeUid}_usr_std_3")
        val std4Id = safeDocId("${safeUid}_usr_std_4")
        val std5Id = safeDocId("${safeUid}_usr_std_5")

        // 1. Seed Users (Admin, Teachers, Fee Manager, Students)
        val users = listOf(
            adminId to SchoolUser(
                ownerUid = uid, role = "ADMIN", userId = "ADMIN01", password = "admin123",
                fullName = "Dr. R. K. Tiwari (Principal)", phone = "+91 76176 14471",
                wing = "All Wings", subject = "Administration",
            ),
            tch1Id to SchoolUser(
                ownerUid = uid, role = "TEACHER", userId = "TCH101", password = "teach123",
                fullName = "Mr. Rajesh Sharma", phone = "9837011223",
                wing = "Upper School", className = "Class X", section = "A", subject = "Mathematics",
            ),
            tch2Id to SchoolUser(
                ownerUid = uid, role = "TEACHER", userId = "TCH102", password = "teach123",
                fullName = "Mrs. Priya Verma", phone = "9837044556",
                wing = "Upper School", className = "Class X", section = "A", subject = "Science",
            ),
            tch3Id to SchoolUser(
                ownerUid = uid, role = "TEACHER", userId = "TCH103", password = "teach123",
                fullName = "Ms. Neha Saxena", phone = "9837077889",
                wing = "Lower School", className = "Class V", section = "A", subject = "English",
            ),
            fee1Id to SchoolUser(
                ownerUid = uid, role = "FEE_MANAGER", userId = "FEE201", password = "fee123",
                fullName = "Mr. Amit Gupta (Accountant)", phone = "9837099881",
                wing = "Accounts Office", subject = "Fee Management",
            ),
            std1Id to SchoolUser(
                ownerUid = uid, role = "STUDENT", userId = "DPS2026-001", password = "stud123",
                fullName = "Aarav Verma", phone = "9412510001",
                wing = "Upper School", className = "Class X", section = "A",
                parentName = "Mr. Suresh Verma", monthlyTuition = 2800L, transportFee = 900L,
            ),
            std2Id to SchoolUser(
                ownerUid = uid, role = "STUDENT", userId = "DPS2026-002", password = "stud123",
                fullName = "Ananya Yadav", phone = "9412510002",
                wing = "Upper School", className = "Class X", section = "A",
                parentName = "Mr. Manoj Yadav", monthlyTuition = 2800L, transportFee = 0L,
            ),
            std3Id to SchoolUser(
                ownerUid = uid, role = "STUDENT", userId = "DPS2026-003", password = "stud123",
                fullName = "Vivaan Mishra", phone = "9412510003",
                wing = "Upper School", className = "Class X", section = "A",
                parentName = "Mrs. Kavita Mishra", monthlyTuition = 2800L, transportFee = 900L,
            ),
            std4Id to SchoolUser(
                ownerUid = uid, role = "STUDENT", userId = "DPS2026-004", password = "stud123",
                fullName = "Diya Chauhan", phone = "9412510004",
                wing = "Lower School", className = "Class V", section = "A",
                parentName = "Mr. Devendra Chauhan", monthlyTuition = 2100L, transportFee = 800L,
            ),
            std5Id to SchoolUser(
                ownerUid = uid, role = "STUDENT", userId = "DPS2026-005", password = "stud123",
                fullName = "Kabir Solanki", phone = "9412510005",
                wing = "Doon Kidz", className = "UKG", section = "A",
                parentName = "Mr. Vivek Solanki", monthlyTuition = 1700L, transportFee = 700L,
            ),
        )
        users.forEach { (docId, user) -> put("users", docId, user) }

        // 2. Gateway config
        put(
            "gatewayConfig",
            "${safeUid}_gw_main",
            GatewayConfig(
                ownerUid = uid,
                merchantUpi = "doonpublicetah@sbi",
                payeeName = "Doon Public School Etah",
                bankName = "State Bank of India (Aruna Nagar, Etah)",
                accountNo = "39481020004512",
                ifsc = "SBIN0004512",
                razorpayKeyId = "",
            )
        )

        // 3. Circulars
        put(
            "circulars",
            "${safeUid}_cir_1",
            Circular(
                ownerUid = uid,
                title = "Winter Uniform & Morning Assembly Schedule",
                category = "Notice", audience = "All Wings", date = today,
                content = "All students from Nursery to Class XII must wear full winter uniform neatly from Monday onwards. Morning assembly begins sharply at 8:00 AM.",
                postedBy = "Dr. R. K. Tiwari (Principal)",
            )
        )
        put(
            "circulars",
            "${safeUid}_cir_2",
            Circular(
                ownerUid = uid,
                title = "Pre-Board & Weekly DPT Assessment Guidelines",
                category = "Exam Datesheet", audience = "Upper School (IX-XII)", date = today,
                content = "Daily Practice Tests (DPT) carry 20% internal weightage for Classes IX-XII. Parents are requested to review Weekly DPT Report Cards every Saturday.",
                postedBy = "Dr. R. K. Tiwari (Principal)",
            )
        )

        // 4. Daily DPT Logs
        put(
            "dptDaily",
            "${safeUid}_dpt_math",
            DptDaily(
                ownerUid = uid, date = today,
                className = "Class X", section = "A", subject = "Mathematics",
                held = true, topic = "Quadratic Equations — Discriminant & Roots", maxMarks = 20L,
                notes = "Q1: Find nature of roots for 2x² - 4x + 3 = 0. Q2: Solve by quadratic formula.",
                reason = "", teacherId = "TCH101", teacherName = "Mr. Rajesh Sharma",
            )
        )
        put(
            "dptDaily",
            "${safeUid}_dpt_sci",
            DptDaily(
                ownerUid = uid, date = today,
                className = "Class X", section = "A", subject = "Science",
                held = false, topic = "", maxMarks = 0L, notes = "",
                reason = "Practical Lab Session", teacherId = "TCH102", teacherName = "Mrs. Priya Verma",
            )
        )

        // 5. Weekly DPT Marks
        val weeklySeeds = listOf(
            DptWeekly("${safeUid}_w1_m1", uid, "Week 1 (Nov 2026)", "Class X", "A", "Mathematics", std1Id, "Aarav Verma", "DPS2026-001", 15L, 20L, "TCH101"),
            DptWeekly("${safeUid}_w1_s1", uid, "Week 1 (Nov 2026)", "Class X", "A", "Science", std1Id, "Aarav Verma", "DPS2026-001", 16L, 20L, "TCH102"),
            DptWeekly("${safeUid}_w1_e1", uid, "Week 1 (Nov 2026)", "Class X", "A", "English", std1Id, "Aarav Verma", "DPS2026-001", 8L, 10L, "TCH101"),
            DptWeekly("${safeUid}_w2_m1", uid, "Week 2 (Nov 2026)", "Class X", "A", "Mathematics", std1Id, "Aarav Verma", "DPS2026-001", 18L, 20L, "TCH101"),
            DptWeekly("${safeUid}_w2_s1", uid, "Week 2 (Nov 2026)", "Class X", "A", "Science", std1Id, "Aarav Verma", "DPS2026-001", 17L, 20L, "TCH102"),
            DptWeekly("${safeUid}_w2_e1", uid, "Week 2 (Nov 2026)", "Class X", "A", "English", std1Id, "Aarav Verma", "DPS2026-001", 9L, 10L, "TCH101"),
            DptWeekly("${safeUid}_w3_m1", uid, "Week 3 (Nov 2026)", "Class X", "A", "Mathematics", std1Id, "Aarav Verma", "DPS2026-001", 19L, 20L, "TCH101"),
            DptWeekly("${safeUid}_w3_s1", uid, "Week 3 (Nov 2026)", "Class X", "A", "Science", std1Id, "Aarav Verma", "DPS2026-001", 18L, 20L, "TCH102"),
            DptWeekly("${safeUid}_w2_m2", uid, "Week 2 (Nov 2026)", "Class X", "A", "Mathematics", std2Id, "Ananya Yadav", "DPS2026-002", 19L, 20L, "TCH101"),
            DptWeekly("${safeUid}_w2_s2", uid, "Week 2 (Nov 2026)", "Class X", "A", "Science", std2Id, "Ananya Yadav", "DPS2026-002", 19L, 20L, "TCH102"),
            DptWeekly("${safeUid}_w2_m3", uid, "Week 2 (Nov 2026)", "Class X", "A", "Mathematics", std3Id, "Vivaan Mishra", "DPS2026-003", 11L, 20L, "TCH101"),
            DptWeekly("${safeUid}_w2_d4_e", uid, "Week 2 (Nov 2026)", "Class V", "A", "English", std4Id, "Diya Chauhan", "DPS2026-004", 18L, 20L, "TCH103"),
            DptWeekly("${safeUid}_w2_d4_m", uid, "Week 2 (Nov 2026)", "Class V", "A", "Mathematics", std4Id, "Diya Chauhan", "DPS2026-004", 16L, 20L, "TCH103"),
            DptWeekly("${safeUid}_w2_k5_e", uid, "Week 2 (Nov 2026)", "UKG", "A", "English", std5Id, "Kabir Solanki", "DPS2026-005", 10L, 10L, "TCH103"),
            DptWeekly("${safeUid}_w2_k5_m", uid, "Week 2 (Nov 2026)", "UKG", "A", "Mathematics", std5Id, "Kabir Solanki", "DPS2026-005", 9L, 10L, "TCH103"),
        )
        weeklySeeds.forEach { put("dptWeekly", it.id, it.copy(id = "")) }

        // 6. Attendance
        put("attendance", "${safeUid}_att_1", AttendanceRecord("", uid, today, "Class X", "A", std1Id, "Present", "TCH101"))
        put("attendance", "${safeUid}_att_2", AttendanceRecord("", uid, today, "Class X", "A", std2Id, "Present", "TCH101"))
        put("attendance", "${safeUid}_att_3", AttendanceRecord("", uid, today, "Class X", "A", std3Id, "Absent", "TCH101"))

        // 7. Leaves
        put(
            "leaves",
            "${safeUid}_lv_1",
            LeaveRequest(
                ownerUid = uid, studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001", className = "Class X", section = "A",
                fromDate = today, toDate = today, reason = "Attending family ceremony in Agra",
                status = "Approved", teacherRemark = "Approved. Complete DPT notes upon return.",
            )
        )

        // 8. Homework
        put(
            "homework",
            "${safeUid}_hw_1",
            HomeworkItem(
                ownerUid = uid, className = "Class X", section = "A",
                subject = "Mathematics", title = "NCERT Ex 4.3 (Quadratic Equations)",
                description = "Solve Questions 1 to 6 in your fair practice notebook and revise the quadratic formula derivation.",
                dueDate = today, teacherName = "Mr. Rajesh Sharma",
            )
        )

        // 9. Term Marks (Major Exams)
        put(
            "termMarks",
            "${safeUid}_tm_0",
            TermMarkCard(
                ownerUid = uid, term = "Unit Test 1 (Oct 2026)",
                className = "Class X", section = "A", studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001",
                marks = mapOf(
                    "English" to mapOf("scored" to 80L, "max" to 100L),
                    "Mathematics" to mapOf("scored" to 84L, "max" to 100L),
                    "Science" to mapOf("scored" to 82L, "max" to 100L),
                    "Social Science" to mapOf("scored" to 79L, "max" to 100L),
                    "Hindi / Computer" to mapOf("scored" to 85L, "max" to 100L),
                ),
                remarks = "Strong start to Term 1.",
                publishedBy = "Mr. Rajesh Sharma",
            )
        )
        put(
            "termMarks",
            "${safeUid}_tm_1",
            TermMarkCard(
                ownerUid = uid, term = "Half-Yearly Exam 2026",
                className = "Class X", section = "A", studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001",
                marks = mapOf(
                    "English" to mapOf("scored" to 86L, "max" to 100L),
                    "Mathematics" to mapOf("scored" to 92L, "max" to 100L),
                    "Science" to mapOf("scored" to 88L, "max" to 100L),
                    "Social Science" to mapOf("scored" to 84L, "max" to 100L),
                    "Hindi / Computer" to mapOf("scored" to 91L, "max" to 100L),
                ),
                remarks = "Consistent performer. Keep aiming for 95%+ in Boards.",
                publishedBy = "Mr. Rajesh Sharma",
            )
        )

        // 10. Fee Items & Sample Verified/Pending Payments
        val fee0Id = safeDocId("${safeUid}_fee_0")
        val fee1IdDoc = safeDocId("${safeUid}_fee_1")
        val fee2Id = safeDocId("${safeUid}_fee_2")
        val fee3Id = safeDocId("${safeUid}_fee_3")
        val fee4Id = safeDocId("${safeUid}_fee_4")
        val fee5Id = safeDocId("${safeUid}_fee_5")
        val pay0Id = safeDocId("${safeUid}_pay_seed_0")
        val pay1Id = safeDocId("${safeUid}_pay_seed_1")
        val pay2Id = safeDocId("${safeUid}_pay_seed_2")

        put("feeItems", fee0Id, FeeItem("", uid, std1Id, "Monthly Tuition Fee (Sep 2026)", "September 2026", 2800L, "PAID", pay0Id))
        put("feeItems", fee1IdDoc, FeeItem("", uid, std1Id, "Monthly Tuition Fee (Nov 2026)", "November 2026", 2800L, "UNPAID", ""))
        put("feeItems", fee2Id, FeeItem("", uid, std1Id, "Transport / Bus Fee (Nov 2026)", "November 2026", 900L, "UNPAID", ""))
        put("feeItems", fee3Id, FeeItem("", uid, std1Id, "Exam & DPT Fee (Q3)", "Quarter 3", 500L, "UNPAID", ""))
        put("feeItems", fee4Id, FeeItem("", uid, std1Id, "Monthly Tuition Fee (Oct 2026)", "October 2026", 2800L, "PAID", pay1Id))
        put("feeItems", fee5Id, FeeItem("", uid, std1Id, "Science Lab & Craft Activity Fee", "November 2026", 450L, "PENDING_VERIFICATION", pay2Id))

        put(
            "payments",
            pay0Id,
            FeePayment(
                ownerUid = uid, studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001", className = "Class X-A",
                feeItemIds = listOf(fee0Id), feeSummary = "Monthly Tuition Fee (Sep 2026)",
                amount = 2800L, mode = "UPI", utrOrRef = "625410982231", upiApp = "PhonePe",
                status = "VERIFIED", receiptNo = "DPS-RCP-2026-0088", rejectReason = "",
                verifiedBy = "Mr. Amit Gupta (Accountant)", verifiedAtStr = "2026-09-07 10:15",
            )
        )
        put(
            "payments",
            pay1Id,
            FeePayment(
                ownerUid = uid, studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001", className = "Class X-A",
                feeItemIds = listOf(fee4Id), feeSummary = "Monthly Tuition Fee (Oct 2026)",
                amount = 2800L, mode = "UPI", utrOrRef = "628914509812", upiApp = "Google Pay",
                status = "VERIFIED", receiptNo = "DPS-RCP-2026-0101", rejectReason = "",
                verifiedBy = "Mr. Amit Gupta (Accountant)", verifiedAtStr = "2026-10-06 11:20",
            )
        )
        put(
            "payments",
            pay2Id,
            FeePayment(
                ownerUid = uid, studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001", className = "Class X-A",
                feeItemIds = listOf(fee5Id), feeSummary = "Science Lab & Craft Activity Fee (Nov 2026)",
                amount = 450L, mode = "UPI", utrOrRef = "632018449107", upiApp = "BHIM UPI",
                status = "PENDING", receiptNo = "", rejectReason = "",
                verifiedBy = "", verifiedAtStr = today,
            )
        )

        // 11. Doubts
        put(
            "doubts",
            "${safeUid}_dbt_1",
            DoubtMessage(
                ownerUid = uid, studentDocId = std1Id,
                studentName = "Aarav Verma", className = "Class X-A", subject = "Mathematics",
                message = "Sir, when D = 0 do we write two equal real roots in CBSE Board answers?",
                reply = "Always write 'two equal real roots' explicitly to earn full step marking.",
                repliedBy = "Mr. Rajesh Sharma", status = "REPLIED", dateLabel = today,
            )
        )

        // 12. Craft Ideas & Project Skills
        put(
            "craftProjects",
            "${safeUid}_craft_1",
            CraftProject(
                ownerUid = uid,
                title = "Solar-Powered Smart Garden Drip Irrigation",
                category = "Eco Recycling",
                description = "Working science exhibition prototype using recycled plastic bottles, mini solar panel, soil moisture probe, and drip tubes to water campus saplings automatically.",
                materials = "Cardboard base, 2 plastic bottles, 6V solar panel, mini DC pump, copper wire",
                authorDocId = std1Id,
                authorName = "Aarav Verma",
                authorUserId = "DPS2026-001",
                authorRole = "STUDENT",
                classInfo = "Class X-A",
                dateLabel = today,
                aiImageData = "",
                aiPromptUsed = "",
                likesCount = 4L,
            )
        )
        put(
            "craftProjects",
            "${safeUid}_craft_2",
            CraftProject(
                ownerUid = uid,
                title = "Modular Origami & Polyhedron Geometry Dome",
                category = "Art & Craft",
                description = "Hands-on mathematics & craft skill workshop teaching students to fold 30 interlocking pastel origami units into a sturdy geodesic icosahedron model.",
                materials = "A4 pastel craft sheets, origami bone folder, scale, acrylic display stand",
                authorDocId = tch1Id,
                authorName = "Mr. Rajesh Sharma",
                authorUserId = "TCH101",
                authorRole = "TEACHER",
                classInfo = "Mathematics Dept",
                dateLabel = today,
                aiImageData = "",
                aiPromptUsed = "",
                likesCount = 7L,
            )
        )

        batch.commit().await()
        Log.i("SchoolRepository", "Seeded DPS Etah ERP dataset for accounts/$uid")
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "seed")
    }

    // --- User Account CRUD & Profile Editing (All Roles) ---

    suspend fun saveUser(user: SchoolUser): Result<String> = runCatching {
        val uid = requireUserId()
        val sanitized = user.copy(
            userId = user.userId.trim().take(40),
            password = user.password.trim().take(64),
            fullName = user.fullName.trim().take(100),
            phone = user.phone.trim().take(30),
            wing = user.wing.trim().take(50),
            className = user.className.trim().take(30),
            section = user.section.trim().take(10),
            subject = user.subject.trim().take(60),
            parentName = user.parentName.trim().take(100),
            monthlyTuition = user.monthlyTuition.coerceAtLeast(0L),
            transportFee = user.transportFee.coerceAtLeast(0L),
        )
        val usersCol = col(uid, "users")
        if (sanitized.id.isBlank()) {
            val docRef = usersCol.document()
            docRef.set(sanitized.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
            docRef.id
        } else {
            val docRef = usersCol.document(sanitized.id)
            docRef.update(
                mapOf(
                    "role" to sanitized.role,
                    "userId" to sanitized.userId,
                    "password" to sanitized.password,
                    "fullName" to sanitized.fullName,
                    "phone" to sanitized.phone,
                    "wing" to sanitized.wing,
                    "className" to sanitized.className,
                    "section" to sanitized.section,
                    "subject" to sanitized.subject,
                    "parentName" to sanitized.parentName,
                    "monthlyTuition" to sanitized.monthlyTuition,
                    "transportFee" to sanitized.transportFee,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            docRef.id
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users")
    }

    suspend fun updateUserProfile(
        userDocId: String,
        newFullName: String,
        newPhone: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(userDocId.isNotBlank()) { "User document ID required" }
        val docRef = col(uid, "users").document(userDocId)
        docRef.update(
            mapOf(
                "fullName" to newFullName.trim().take(100),
                "phone" to newPhone.trim().take(30),
                "password" to newPassword.trim().take(64),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$userDocId")
    }

    suspend fun deleteDocument(collectionName: String, docId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        col(uid, collectionName).document(docId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "$collectionName/$docId")
    }

    // --- Gateway Config ---

    suspend fun saveGatewayConfig(config: GatewayConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanMerchantUpi = config.merchantUpi.trim().ifBlank { "doonpublicetah@sbi" }.take(100)
        val cleanPayeeName = config.payeeName.trim().ifBlank { SchoolInfo.NAME }.take(100)
        val cleanBankName = config.bankName.trim().take(120)
        val cleanAccountNo = config.accountNo.trim().take(40)
        val cleanIfsc = config.ifsc.trim().take(20)
        val cleanRazorpay = config.razorpayKeyId.trim().take(80)
        val gwCol = col(uid, "gatewayConfig")
        val existingSnap = gwCol.limit(1).get().await()
        val existingDoc = existingSnap.documents.firstOrNull()
        if (existingDoc == null) {
            val docId = safeDocId("${uid}_gw_main")
            gwCol.document(docId).set(
                config.copy(
                    id = "",
                    ownerUid = uid,
                    merchantUpi = cleanMerchantUpi,
                    payeeName = cleanPayeeName,
                    bankName = cleanBankName,
                    accountNo = cleanAccountNo,
                    ifsc = cleanIfsc,
                    razorpayKeyId = cleanRazorpay,
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            existingDoc.reference.update(
                mapOf(
                    "merchantUpi" to cleanMerchantUpi,
                    "payeeName" to cleanPayeeName,
                    "bankName" to cleanBankName,
                    "accountNo" to cleanAccountNo,
                    "ifsc" to cleanIfsc,
                    "razorpayKeyId" to cleanRazorpay,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "gatewayConfig")
    }

    // --- Circulars & Targeted Notices ---

    suspend fun addCircular(circular: Circular): Result<String> = runCatching {
        val uid = requireUserId()
        val sanitized = circular.copy(
            id = "",
            ownerUid = uid,
            title = circular.title.trim().take(150),
            category = circular.category.trim().ifBlank { "Notice" }.take(40),
            audience = circular.audience.trim().ifBlank { "All Wings" }.take(120),
            date = circular.date.trim().ifBlank { SchoolCatalog.todayIso() }.take(20),
            content = circular.content.trim().take(2000),
            postedBy = circular.postedBy.trim().ifBlank { "Staff" }.take(100),
            recipientDocId = circular.recipientDocId.trim().take(128),
            recipientName = circular.recipientName.trim().take(120),
            senderRole = if (circular.senderRole == "TEACHER") "TEACHER" else "ADMIN",
            attachmentName = circular.attachmentName.trim().take(160),
            attachmentMimeType = circular.attachmentMimeType.trim().take(120),
            attachmentData = circular.attachmentData.take(450_000),
            createdAt = null,
            updatedAt = null,
        )
        val docRef = col(uid, "circulars").document()
        docRef.set(sanitized).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "circulars")
    }

    // --- Daily & Weekly DPT / Exam ---

    suspend fun saveDptDaily(dpt: DptDaily): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanDate = dpt.date.trim().ifBlank { SchoolCatalog.todayIso() }.take(20)
        val cleanClass = dpt.className.trim().ifBlank { "Class X" }.take(30)
        val cleanSection = dpt.section.trim().ifBlank { "A" }.take(10)
        val cleanSubject = dpt.subject.trim().ifBlank { "Mathematics" }.take(60)
        val cleanAssessmentType = if (dpt.assessmentType.uppercase() == "EXAM") "EXAM" else "DPT"
        val cleanTopic = if (dpt.held) dpt.topic.trim().take(200) else ""
        val cleanMaxMarks = if (dpt.held) dpt.maxMarks.coerceIn(0L, 100L) else 0L
        val cleanNotes = if (dpt.held) dpt.notes.trim().take(1000) else ""
        val cleanReason = if (!dpt.held) dpt.reason.trim().ifBlank { SchoolCatalog.dptNotHeldReasons.first() }.take(200) else ""
        val cleanAttachmentName = if (dpt.held) dpt.attachmentName.trim().take(160) else ""
        val cleanAttachmentMime = if (dpt.held) dpt.attachmentMimeType.trim().take(120) else ""
        val cleanAttachmentData = if (dpt.held) dpt.attachmentData.take(450_000) else ""
        val cleanTeacherId = dpt.teacherId.trim().ifBlank { "TCH" }.take(40)
        val cleanTeacherName = dpt.teacherName.trim().ifBlank { "Teacher" }.take(100)

        val safeKey = safeDocId("${uid}_${cleanDate}_${cleanClass}_${cleanSection}_${cleanSubject}_${cleanAssessmentType}")
        val targetDocId = safeDocId(dpt.id.ifBlank { safeKey })

        val dptCol = col(uid, "dptDaily")
        val existingSnap = dptCol.get().await()
        val existingDoc = existingSnap.documents.firstOrNull { doc ->
            doc.id == targetDocId ||
                doc.id == safeKey ||
                (doc.getString("date") == cleanDate &&
                    doc.getString("className") == cleanClass &&
                    doc.getString("section") == cleanSection &&
                    doc.getString("subject") == cleanSubject &&
                    (doc.getString("assessmentType") ?: "DPT") == cleanAssessmentType)
        }
        if (existingDoc == null) {
            dptCol.document(targetDocId).set(
                dpt.copy(
                    id = "",
                    ownerUid = uid,
                    date = cleanDate,
                    className = cleanClass,
                    section = cleanSection,
                    subject = cleanSubject,
                    assessmentType = cleanAssessmentType,
                    topic = cleanTopic,
                    maxMarks = cleanMaxMarks,
                    notes = cleanNotes,
                    reason = cleanReason,
                    attachmentName = cleanAttachmentName,
                    attachmentMimeType = cleanAttachmentMime,
                    attachmentData = cleanAttachmentData,
                    teacherId = cleanTeacherId,
                    teacherName = cleanTeacherName,
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            existingDoc.reference.update(
                mapOf(
                    "assessmentType" to cleanAssessmentType,
                    "held" to dpt.held,
                    "topic" to cleanTopic,
                    "maxMarks" to cleanMaxMarks,
                    "notes" to cleanNotes,
                    "reason" to cleanReason,
                    "attachmentName" to cleanAttachmentName,
                    "attachmentMimeType" to cleanAttachmentMime,
                    "attachmentData" to cleanAttachmentData,
                    "teacherId" to cleanTeacherId,
                    "teacherName" to cleanTeacherName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "dptDaily")
    }

    suspend fun saveDptWeekly(entry: DptWeekly): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanWeek = entry.week.trim().ifBlank { SchoolCatalog.dptWeeks.first() }.take(50)
        val cleanClass = entry.className.trim().ifBlank { "Class X" }.take(30)
        val cleanSection = entry.section.trim().ifBlank { "A" }.take(10)
        val cleanSubject = entry.subject.trim().ifBlank { "Mathematics" }.take(60)
        val cleanStudentDocId = entry.studentDocId.trim().take(128)
        val cleanStudentName = entry.studentName.trim().ifBlank { "Student" }.take(100)
        val cleanAdmissionNo = entry.admissionNo.trim().ifBlank { "DPS" }.take(40)
        val cleanMaxMarks = entry.maxMarks.coerceAtLeast(1L)
        val cleanScored = entry.scored.coerceAtLeast(0L)
        val cleanTeacherId = entry.teacherId.trim().ifBlank { "TCH" }.take(40)

        val safeKey = safeDocId("${uid}_${cleanWeek}_${cleanClass}_${cleanSection}_${cleanSubject}_${cleanStudentDocId}")
        val targetDocId = safeDocId(entry.id.ifBlank { safeKey })

        val weeklyCol = col(uid, "dptWeekly")
        val existingSnap = weeklyCol.get().await()
        val existingDoc = existingSnap.documents.firstOrNull { doc ->
            doc.id == targetDocId ||
                doc.id == safeKey ||
                (doc.getString("week") == cleanWeek &&
                    doc.getString("className") == cleanClass &&
                    doc.getString("section") == cleanSection &&
                    doc.getString("subject") == cleanSubject &&
                    doc.getString("studentDocId") == cleanStudentDocId)
        }
        if (existingDoc == null) {
            weeklyCol.document(targetDocId).set(
                entry.copy(
                    id = "",
                    ownerUid = uid,
                    week = cleanWeek,
                    className = cleanClass,
                    section = cleanSection,
                    subject = cleanSubject,
                    studentDocId = cleanStudentDocId,
                    studentName = cleanStudentName,
                    admissionNo = cleanAdmissionNo,
                    scored = cleanScored,
                    maxMarks = cleanMaxMarks,
                    teacherId = cleanTeacherId,
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            existingDoc.reference.update(
                mapOf(
                    "scored" to cleanScored,
                    "maxMarks" to cleanMaxMarks,
                    "teacherId" to cleanTeacherId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "dptWeekly")
    }

    // --- Attendance & Leaves ---

    suspend fun markAttendance(record: AttendanceRecord): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanDate = record.date.trim().ifBlank { SchoolCatalog.todayIso() }.take(20)
        val cleanClass = record.className.trim().ifBlank { "Class X" }.take(30)
        val cleanSection = record.section.trim().ifBlank { "A" }.take(10)
        val cleanStudentDocId = record.studentDocId.trim().take(128)
        val cleanStatus = if (record.status in setOf("Present", "Absent", "Leave")) record.status else "Present"
        val cleanMarkedBy = record.markedBy.trim().ifBlank { "TCH" }.take(40)

        val safeKey = safeDocId("${uid}_att_${cleanDate}_${cleanStudentDocId}")
        val targetDocId = safeDocId(record.id.ifBlank { safeKey })

        val attCol = col(uid, "attendance")
        val existingSnap = attCol.get().await()
        val existingDoc = existingSnap.documents.firstOrNull { doc ->
            doc.id == targetDocId ||
                doc.id == safeKey ||
                (doc.getString("date") == cleanDate &&
                    doc.getString("studentDocId") == cleanStudentDocId)
        }
        if (existingDoc == null) {
            attCol.document(targetDocId).set(
                record.copy(
                    id = "",
                    ownerUid = uid,
                    date = cleanDate,
                    className = cleanClass,
                    section = cleanSection,
                    studentDocId = cleanStudentDocId,
                    status = cleanStatus,
                    markedBy = cleanMarkedBy,
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            existingDoc.reference.update(
                mapOf(
                    "status" to cleanStatus,
                    "markedBy" to cleanMarkedBy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "attendance")
    }

    suspend fun submitLeave(leave: LeaveRequest): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col(uid, "leaves").document()
        docRef.set(leave.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "leaves")
    }

    suspend fun updateLeaveStatus(leaveId: String, status: String, remark: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        col(uid, "leaves").document(leaveId).update(
            mapOf(
                "status" to status,
                "teacherRemark" to remark,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "leaves/$leaveId")
    }

    // --- Homework & Term Marks ---

    suspend fun addHomework(hw: HomeworkItem): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col(uid, "homework").document()
        docRef.set(hw.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "homework")
    }

    suspend fun saveTermMarkCard(card: TermMarkCard): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanTerm = card.term.trim().ifBlank { "Term Exam" }.take(60)
        val cleanClass = card.className.trim().ifBlank { "Class X" }.take(30)
        val cleanSection = card.section.trim().ifBlank { "A" }.take(10)
        val cleanStudentDocId = card.studentDocId.trim().take(128)
        val cleanStudentName = card.studentName.trim().ifBlank { "Student" }.take(100)
        val cleanAdmissionNo = card.admissionNo.trim().ifBlank { "DPS" }.take(40)
        val cleanRemarks = card.remarks.trim().take(500)
        val cleanPublishedBy = card.publishedBy.trim().ifBlank { "Teacher" }.take(100)

        val safeKey = safeDocId("${uid}_${cleanTerm}_${cleanStudentDocId}")
        val targetDocId = safeDocId(card.id.ifBlank { safeKey })

        val marksCol = col(uid, "termMarks")
        val existingSnap = marksCol.get().await()
        val existingDoc = existingSnap.documents.firstOrNull { doc ->
            doc.id == targetDocId ||
                doc.id == safeKey ||
                (doc.getString("term") == cleanTerm &&
                    doc.getString("studentDocId") == cleanStudentDocId)
        }
        if (existingDoc == null) {
            marksCol.document(targetDocId).set(
                card.copy(
                    id = "",
                    ownerUid = uid,
                    term = cleanTerm,
                    className = cleanClass,
                    section = cleanSection,
                    studentDocId = cleanStudentDocId,
                    studentName = cleanStudentName,
                    admissionNo = cleanAdmissionNo,
                    remarks = cleanRemarks,
                    publishedBy = cleanPublishedBy,
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            existingDoc.reference.update(
                mapOf(
                    "marks" to card.marks,
                    "remarks" to cleanRemarks,
                    "publishedBy" to cleanPublishedBy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "termMarks")
    }

    // --- Fees & Payments ---

    suspend fun addFeeItem(item: FeeItem): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col(uid, "feeItems").document()
        docRef.set(item.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "feeItems")
    }

    suspend fun submitPaymentProof(payment: FeePayment, feeItemIds: List<String>): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col(uid, "payments").document()
        val batch = db.batch()
        batch.set(docRef, payment.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null))
        feeItemIds.forEach { fid ->
            batch.update(
                col(uid, "feeItems").document(fid),
                mapOf(
                    "status" to "PENDING_VERIFICATION",
                    "paymentId" to docRef.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.commit().await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "payments")
    }

    suspend fun verifyPayment(
        payment: FeePayment,
        receiptNo: String,
        verifierName: String,
        verifiedAtStr: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val batch = db.batch()
        batch.update(
            col(uid, "payments").document(payment.id),
            mapOf(
                "status" to "VERIFIED",
                "receiptNo" to receiptNo,
                "verifiedBy" to verifierName,
                "verifiedAtStr" to verifiedAtStr,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        payment.feeItemIds.forEach { fid ->
            batch.update(
                col(uid, "feeItems").document(fid),
                mapOf(
                    "status" to "PAID",
                    "paymentId" to payment.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "payments/${payment.id}")
    }

    suspend fun rejectPayment(
        payment: FeePayment,
        reason: String,
        verifierName: String,
        verifiedAtStr: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val batch = db.batch()
        batch.update(
            col(uid, "payments").document(payment.id),
            mapOf(
                "status" to "REJECTED",
                "rejectReason" to reason,
                "verifiedBy" to verifierName,
                "verifiedAtStr" to verifiedAtStr,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        payment.feeItemIds.forEach { fid ->
            batch.update(
                col(uid, "feeItems").document(fid),
                mapOf(
                    "status" to "UNPAID",
                    "paymentId" to "",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "payments/${payment.id}")
    }

    // --- Doubts ---

    suspend fun submitDoubt(doubt: DoubtMessage): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col(uid, "doubts").document()
        docRef.set(doubt.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "doubts")
    }

    suspend fun replyDoubt(doubtId: String, reply: String, repliedBy: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        col(uid, "doubts").document(doubtId).update(
            mapOf(
                "reply" to reply,
                "repliedBy" to repliedBy,
                "status" to "REPLIED",
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "doubts/$doubtId")
    }

    // --- Craft Ideas & Project Skills ---

    suspend fun addCraftProject(project: CraftProject): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanRole = when (project.authorRole.uppercase()) {
            "TEACHER" -> "TEACHER"
            "ADMIN" -> "ADMIN"
            else -> "STUDENT"
        }
        val sanitized = project.copy(
            id = "",
            ownerUid = uid,
            title = project.title.trim().take(150),
            category = project.category.trim().ifBlank { "Art & Craft" }.take(50),
            description = project.description.trim().take(1500),
            materials = project.materials.trim().take(500),
            authorDocId = project.authorDocId.trim().take(128),
            authorName = project.authorName.trim().ifBlank { "DPS Creator" }.take(100),
            authorUserId = project.authorUserId.trim().take(40),
            authorRole = cleanRole,
            classInfo = project.classInfo.trim().take(80),
            dateLabel = project.dateLabel.trim().ifBlank { SchoolCatalog.todayIso() }.take(20),
            aiImageData = project.aiImageData.take(455_000),
            aiPromptUsed = project.aiPromptUsed.trim().take(600),
            likesCount = project.likesCount.coerceAtLeast(0L),
            createdAt = null,
            updatedAt = null,
        )
        val docRef = col(uid, "craftProjects").document()
        docRef.set(sanitized).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "craftProjects")
    }

    suspend fun updateCraftProjectAiImage(
        projectId: String,
        aiImageData: String,
        aiPromptUsed: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        col(uid, "craftProjects").document(projectId).update(
            mapOf(
                "aiImageData" to aiImageData.take(455_000),
                "aiPromptUsed" to aiPromptUsed.trim().take(600),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "craftProjects/$projectId")
    }

    suspend fun likeCraftProject(projectId: String, currentLikes: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        col(uid, "craftProjects").document(projectId).update(
            mapOf(
                "likesCount" to (currentLikes + 1L),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "craftProjects/$projectId")
    }
}
