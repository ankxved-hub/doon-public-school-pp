package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String,
    val notes: String,
)

private fun resolveFirestoreInstance(context: Context): FirebaseFirestore? {
    val appContext = context.applicationContext
    if (FirebaseApp.getApps(appContext).isEmpty()) return null
    val resId = appContext.resources.getIdentifier("firestore_database_id", "string", appContext.packageName)
    val dbId = if (resId != 0) runCatching { appContext.getString(resId) }.getOrNull().orEmpty().trim() else ""
    return runCatching {
        if (dbId.isNotEmpty()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
    }.getOrNull()
}

class SchoolRepository(private val dbOrNull: FirebaseFirestore?) {

    constructor(context: Context) : this(resolveFirestoreInstance(context))

    private val db: FirebaseFirestore
        get() = dbOrNull
            ?: throw IllegalStateException("Firebase Firestore is not configured in this build. Connect your Cloudflare Worker URL.")

    private val authOrNull: FirebaseAuth?
        get() = runCatching { Firebase.auth }.getOrNull()

    private val schoolId = SchoolInfo.SCHOOL_ID

    private fun requireUserId(): String =
        authOrNull?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun safeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "doc" }

    private fun schoolDoc(): DocumentReference =
        db.collection("schools").document(schoolId)

    private fun col(collectionName: String): CollectionReference =
        schoolDoc().collection(collectionName)

    private fun legacyCol(ownerUid: String, collectionName: String): CollectionReference =
        db.collection("accounts").document(ownerUid).collection(collectionName)

    fun computeLoginTicketId(role: String, userId: String, password: String): String {
        val normalized = "${role.trim()}|${userId.trim().lowercase(Locale.US)}|${password.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loginTicketPayload(
        uid: String,
        schoolUserDocId: String,
        role: String,
        userId: String,
        className: String,
        section: String,
    ): Map<String, Any> = mapOf(
        "ownerUid" to uid,
        "schoolUserDocId" to schoolUserDocId,
        "role" to role.trim(),
        "userId" to userId.trim().take(40),
        "className" to className.trim().take(30),
        "section" to section.trim().take(10),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun <T> observeCollection(
        collectionName: String,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        emitAll(
            col(collectionName)
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
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "schools/$schoolId/$collectionName")
                    }
                    throw error
                }
        )
    }

    fun observeUsers(): Flow<List<SchoolUser>> = observeCollection("users", SchoolUser::class.java)
    fun observeGatewayConfig(): Flow<List<GatewayConfig>> = observeCollection("gatewayConfig", GatewayConfig::class.java)
    fun observeCirculars(): Flow<List<Circular>> = observeCollection("circulars", Circular::class.java)
    fun observeDptDaily(): Flow<List<DptDaily>> = observeCollection("dptDaily", DptDaily::class.java)
    fun observeDptWeekly(): Flow<List<DptWeekly>> = observeCollection("dptWeekly", DptWeekly::class.java)
    fun observeAttendance(): Flow<List<AttendanceRecord>> = observeCollection("attendance", AttendanceRecord::class.java)
    fun observeLeaves(): Flow<List<LeaveRequest>> = observeCollection("leaves", LeaveRequest::class.java)
    fun observeHomework(): Flow<List<HomeworkItem>> = observeCollection("homework", HomeworkItem::class.java)
    fun observeTermMarks(): Flow<List<TermMarkCard>> = observeCollection("termMarks", TermMarkCard::class.java)
    fun observeFeeItems(): Flow<List<FeeItem>> = observeCollection("feeItems", FeeItem::class.java)
    fun observePayments(): Flow<List<FeePayment>> = observeCollection("payments", FeePayment::class.java)
    fun observeDoubts(): Flow<List<DoubtMessage>> = observeCollection("doubts", DoubtMessage::class.java)
    fun observeCraftProjects(): Flow<List<CraftProject>> = observeCollection("craftProjects", CraftProject::class.java)
    fun observeCraftLikes(): Flow<List<CraftLike>> = observeCollection("craftLikes", CraftLike::class.java)
    fun observeCampusPosters(): Flow<List<CampusPoster>> = observeCollection("campusPosters", CampusPoster::class.java)
    fun observeTimetables(): Flow<List<ClassTimetable>> = observeCollection("timetables", ClassTimetable::class.java)
    fun observeLibraryIssues(): Flow<List<LibraryIssue>> = observeCollection("libraryIssues", LibraryIssue::class.java)

    suspend fun isCurrentMemberReady(): Boolean {
        val uid = authOrNull?.currentUser?.uid ?: return false
        return try {
            col("members").document(uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearCurrentMemberSession() {
        val uid = authOrNull?.currentUser?.uid ?: return
        try {
            col("members").document(uid).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("SchoolRepository", "Unable to delete member session doc: ${e.message}")
        }
    }

    suspend fun loginWithCredentials(
        role: String,
        userId: String,
        password: String,
    ): Result<SchoolUser> = runCatching {
        val uid = requireUserId()
        ensureSchoolInitialized().getOrThrow()

        val ticketId = computeLoginTicketId(role, userId, password)
        val ticketSnap = col("loginIndex").document(ticketId).get().await()
        if (!ticketSnap.exists()) {
            throw IllegalArgumentException(
                "Invalid ID or password for ${role.replace('_', ' ')}. Please verify the selected role, ID (e.g. Roll / Admission No), and password."
            )
        }
        val schoolUserDocId = ticketSnap.getString("schoolUserDocId").orEmpty()
        val matchedRole = ticketSnap.getString("role").orEmpty()
        require(schoolUserDocId.isNotBlank() && matchedRole == role) {
            "Credential ticket mismatch. Please try again."
        }

        val memberRef = col("members").document(uid)
        val existingMember = memberRef.get().await()
        if (!existingMember.exists()) {
            memberRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "schoolUserDocId" to schoolUserDocId,
                    "role" to matchedRole,
                    "loginTicketId" to ticketId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } else {
            memberRef.update(
                mapOf(
                    "schoolUserDocId" to schoolUserDocId,
                    "role" to matchedRole,
                    "loginTicketId" to ticketId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }

        val userSnap = col("users").document(schoolUserDocId).get().await()
        val loadedUser = userSnap.toObject(SchoolUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalStateException("School account profile was not found.")
        val baseRole = loadedUser.primaryRole.ifBlank { loadedUser.role }
        loadedUser.copy(
            role = matchedRole,
            primaryRole = baseRole,
            roles = (listOf(baseRole) + loadedUser.roles + loadedUser.extraRoles).distinct(),
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        if (e is FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.GET, "schools/$schoolId/loginIndex")
        }
    }

    fun buildLoginTicketId(role: String, userId: String, password: String): String =
        computeLoginTicketId(role, userId, password)

    suspend fun loginWithSavedTicket(ticketId: String): Result<SchoolUser> = runCatching {
        val uid = requireUserId()
        require(ticketId.isNotBlank()) { "Saved session ticket is missing. Please sign in again." }
        ensureSchoolInitialized().getOrThrow()

        val ticketSnap = col("loginIndex").document(ticketId).get().await()
        if (!ticketSnap.exists()) {
            throw IllegalArgumentException("Saved session expired or password changed. Please sign in again.")
        }
        val schoolUserDocId = ticketSnap.getString("schoolUserDocId").orEmpty()
        val matchedRole = ticketSnap.getString("role").orEmpty()
        require(schoolUserDocId.isNotBlank() && matchedRole.isNotBlank()) {
            "Saved session ticket is no longer valid."
        }

        val memberRef = col("members").document(uid)
        val existingMember = memberRef.get().await()
        if (!existingMember.exists()) {
            memberRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "schoolUserDocId" to schoolUserDocId,
                    "role" to matchedRole,
                    "loginTicketId" to ticketId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } else {
            memberRef.update(
                mapOf(
                    "schoolUserDocId" to schoolUserDocId,
                    "role" to matchedRole,
                    "loginTicketId" to ticketId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }

        val userSnap = col("users").document(schoolUserDocId).get().await()
        val loadedUser = userSnap.toObject(SchoolUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalStateException("School account profile was not found.")
        val baseRole = loadedUser.primaryRole.ifBlank { loadedUser.role }
        loadedUser.copy(
            role = matchedRole,
            primaryRole = baseRole,
            roles = (listOf(baseRole) + loadedUser.roles + loadedUser.extraRoles).distinct(),
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        if (e is FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.GET, "schools/$schoolId/loginIndex")
        }
    }

    /**
     * Ensures /schools/dps_etah exists on the server.
     * If not initialized yet:
     * 1. Checks legacy /accounts/{uid}/... and migrates existing school records into /schools/dps_etah.
     * 2. Otherwise seeds the initial Doon Public School ERP dataset into /schools/dps_etah.
     */
    suspend fun ensureSchoolInitialized(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val rootSnap = schoolDoc().get().await()
        if (rootSnap.exists()) {
            return@runCatching
        }

        val legacyUsersSnap = try {
            legacyCol(uid, "users").get().await()
        } catch (_: Exception) {
            null
        }

        if (legacyUsersSnap != null && !legacyUsersSnap.isEmpty) {
            migrateLegacyAccountToSharedSchool(uid, legacyUsersSnap.toObjects(SchoolUser::class.java))
        } else {
            seedSharedSchoolDataset(uid)
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/init")
    }

    private suspend fun migrateLegacyAccountToSharedSchool(
        uid: String,
        legacyUsers: List<SchoolUser>,
    ) {
        Log.i("SchoolRepository", "Migrating ${legacyUsers.size} legacy users from accounts/$uid to schools/$schoolId")
        val bootstrapBatch = db.batch()
        bootstrapBatch.set(
            schoolDoc(),
            mapOf(
                "ownerUid" to uid,
                "name" to SchoolInfo.NAME,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )

        var adminCandidate: SchoolUser? = legacyUsers.firstOrNull { it.role == "ADMIN" } ?: legacyUsers.firstOrNull()
        legacyUsers.forEach { u ->
            val docId = safeDocId(u.id.ifBlank { "${safeDocId(uid)}_usr_${u.userId}" })
            val cleanUser = u.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)
            bootstrapBatch.set(col("users").document(docId), cleanUser)
            val ticketId = computeLoginTicketId(cleanUser.role, cleanUser.userId, cleanUser.password)
            bootstrapBatch.set(
                col("loginIndex").document(ticketId),
                loginTicketPayload(uid, docId, cleanUser.role, cleanUser.userId, cleanUser.className, cleanUser.section)
            )
            if (u.id == adminCandidate?.id) {
                adminCandidate = cleanUser.copy(id = docId)
            }
        }

        // Temporary admin member during migration so subsequent batches can write all subcollections cleanly
        val anchor = adminCandidate
        var createdTempMember = false
        if (anchor != null) {
            val anchorTicket = computeLoginTicketId(anchor.role, anchor.userId, anchor.password)
            bootstrapBatch.set(
                col("members").document(uid),
                mapOf(
                    "ownerUid" to uid,
                    "schoolUserDocId" to anchor.id,
                    "role" to anchor.role,
                    "loginTicketId" to anchorTicket,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            createdTempMember = true
        }
        bootstrapBatch.commit().await()

        // Migrate remaining collections in safe chunked batches
        val pendingWrites = mutableListOf<Pair<DocumentReference, Any>>()

        suspend fun <T : Any> enqueue(colName: String, clazz: Class<T>, transform: (T) -> T) {
            val snap = try { legacyCol(uid, colName).get().await() } catch (_: Exception) { null } ?: return
            snap.documents.forEach { doc ->
                val obj = doc.toObject(clazz) ?: return@forEach
                val docId = safeDocId(doc.id)
                pendingWrites.add(col(colName).document(docId) to transform(obj))
            }
        }

        enqueue("gatewayConfig", GatewayConfig::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("circulars", Circular::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("dptDaily", DptDaily::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("dptWeekly", DptWeekly::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("attendance", AttendanceRecord::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("leaves", LeaveRequest::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("homework", HomeworkItem::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("termMarks", TermMarkCard::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("feeItems", FeeItem::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("payments", FeePayment::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("doubts", DoubtMessage::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }
        enqueue("craftProjects", CraftProject::class.java) { it.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null) }

        pendingWrites.chunked(350).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, obj) -> batch.set(ref, obj) }
            batch.commit().await()
        }

        // Remove temporary member only if it didn't intend to bypass role login
        if (createdTempMember) {
            try {
                col("members").document(uid).delete().await()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun seedSharedSchoolDataset(uid: String) {
        val today = SchoolCatalog.todayIso()
        val batch = db.batch()

        batch.set(
            schoolDoc(),
            mapOf(
                "ownerUid" to uid,
                "name" to SchoolInfo.NAME,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )

        fun <T : Any> put(colName: String, rawDocId: String, model: T) {
            batch.set(col(colName).document(safeDocId(rawDocId)), model)
        }

        val adminId = "${schoolId}_usr_admin_1"
        val tch1Id = "${schoolId}_usr_tch_1"
        val tch2Id = "${schoolId}_usr_tch_2"
        val tch3Id = "${schoolId}_usr_tch_3"
        val fee1Id = "${schoolId}_usr_fee_1"
        val std1Id = "${schoolId}_usr_std_1"
        val std2Id = "${schoolId}_usr_std_2"
        val std3Id = "${schoolId}_usr_std_3"
        val std4Id = "${schoolId}_usr_std_4"
        val std5Id = "${schoolId}_usr_std_5"

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
                wing = "Middle School", className = "Class VII", section = "A", subject = "Science",
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
                wing = "Middle School", className = "Class VII", section = "A",
                parentName = "Mrs. Kavita Mishra", monthlyTuition = 2500L, transportFee = 900L,
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
        users.forEach { (docId, user) ->
            put("users", docId, user)
            val ticketId = computeLoginTicketId(user.role, user.userId, user.password)
            batch.set(
                col("loginIndex").document(ticketId),
                loginTicketPayload(uid, safeDocId(docId), user.role, user.userId, user.className, user.section)
            )
        }

        put(
            "gatewayConfig",
            "${schoolId}_gw_main",
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

        put(
            "circulars",
            "${schoolId}_cir_1",
            Circular(
                ownerUid = uid,
                title = "Winter Uniform & Morning Assembly Schedule",
                category = "Notice",
                audience = "All Wings",
                date = today,
                content = "All students from Nursery to Class XII must wear full winter uniform neatly from Monday onwards. Morning assembly begins sharply at 8:00 AM.",
                postedBy = "Dr. R. K. Tiwari (Principal)",
            )
        )
        put(
            "circulars",
            "${schoolId}_cir_2",
            Circular(
                ownerUid = uid,
                title = "Pre-Board & Weekly DPT Assessment Guidelines",
                category = "Exam Datesheet",
                audience = "Upper School",
                targetWing = "Upper School",
                date = today,
                content = "Daily Practice Tests (DPT) carry 20% internal weightage for Classes IX-XII. Parents are requested to review Weekly DPT Report Cards every Saturday.",
                postedBy = "Dr. R. K. Tiwari (Principal)",
            )
        )

        put(
            "dptDaily",
            "${schoolId}_dpt_math",
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
            "${schoolId}_dpt_sci",
            DptDaily(
                ownerUid = uid, date = today,
                className = "Class VII", section = "A", subject = "Science",
                held = false, topic = "", maxMarks = 0L, notes = "",
                reason = "Practical Lab Session", teacherId = "TCH102", teacherName = "Mrs. Priya Verma",
            )
        )

        val weeklySeeds = listOf(
            DptWeekly("${schoolId}_w1_m1", uid, "Week 1 (Nov 2026)", "Class X", "A", "Mathematics", std1Id, "Aarav Verma", "DPS2026-001", 15L, 20L, "TCH101"),
            DptWeekly("${schoolId}_w1_s1", uid, "Week 1 (Nov 2026)", "Class X", "A", "Science", std1Id, "Aarav Verma", "DPS2026-001", 16L, 20L, "TCH101"),
            DptWeekly("${schoolId}_w2_m1", uid, "Week 2 (Nov 2026)", "Class X", "A", "Mathematics", std1Id, "Aarav Verma", "DPS2026-001", 18L, 20L, "TCH101"),
            DptWeekly("${schoolId}_w2_s1", uid, "Week 2 (Nov 2026)", "Class X", "A", "Science", std1Id, "Aarav Verma", "DPS2026-001", 17L, 20L, "TCH101"),
            DptWeekly("${schoolId}_w2_m2", uid, "Week 2 (Nov 2026)", "Class X", "A", "Mathematics", std2Id, "Ananya Yadav", "DPS2026-002", 19L, 20L, "TCH101"),
            DptWeekly("${schoolId}_w2_m3", uid, "Week 2 (Nov 2026)", "Class VII", "A", "Science", std3Id, "Vivaan Mishra", "DPS2026-003", 16L, 20L, "TCH102"),
        )
        weeklySeeds.forEach { put("dptWeekly", it.id, it.copy(id = "")) }

        put("attendance", "${schoolId}_att_1", AttendanceRecord("", uid, today, "Class X", "A", std1Id, "Present", "TCH101"))
        put("attendance", "${schoolId}_att_2", AttendanceRecord("", uid, today, "Class X", "A", std2Id, "Present", "TCH101"))

        put(
            "homework",
            "${schoolId}_hw_1",
            HomeworkItem(
                ownerUid = uid, className = "Class X", section = "A",
                subject = "Mathematics", title = "NCERT Ex 4.3 (Quadratic Equations)",
                description = "Solve Questions 1 to 6 in your fair practice notebook and revise the quadratic formula derivation.",
                dueDate = today, teacherName = "Mr. Rajesh Sharma",
            )
        )

        val fee0Id = safeDocId("${schoolId}_fee_0")
        val fee1IdDoc = safeDocId("${schoolId}_fee_1")
        val pay0Id = safeDocId("${schoolId}_pay_seed_0")
        put("feeItems", fee0Id, FeeItem("", uid, std1Id, "Monthly Tuition Fee (Oct 2026)", "October 2026", 2800L, "PAID", pay0Id))
        put("feeItems", fee1IdDoc, FeeItem("", uid, std1Id, "Monthly Tuition Fee (Nov 2026)", "November 2026", 2800L, "UNPAID", ""))
        put(
            "payments",
            pay0Id,
            FeePayment(
                ownerUid = uid, studentDocId = std1Id,
                studentName = "Aarav Verma", admissionNo = "DPS2026-001", className = "Class X-A",
                feeItemIds = listOf(fee0Id), feeSummary = "Monthly Tuition Fee (Oct 2026)",
                amount = 2800L, mode = "UPI", utrOrRef = "628914509812", upiApp = "Google Pay",
                status = "VERIFIED", receiptNo = "DPS-RCP-2026-0101", rejectReason = "",
                verifiedBy = "Mr. Amit Gupta (Accountant)", verifiedAtStr = "2026-10-06 11:20",
            )
        )

        put(
            "craftProjects",
            "${schoolId}_craft_1",
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
                likesCount = 1L,
            )
        )

        batch.commit().await()
        Log.i("SchoolRepository", "Seeded Doon Public School shared ERP dataset for schools/$schoolId")
    }

    // --- User Account CRUD & Profile Editing ---

    suspend fun saveUser(user: SchoolUser, editorRole: String = "ADMIN"): Result<String> = runCatching {
        val uid = requireUserId()
        val staffAllowed = setOf("TEACHER", "FEE_MANAGER", "ADMIN")
        val baseRole = user.primaryRole.ifBlank { user.role }.trim().uppercase(Locale.US)
        val cleanExtraRoles = if (baseRole == "TEACHER") {
            user.extraRoles.map { it.trim().uppercase(Locale.US) }.filter { it in staffAllowed && it != baseRole }.distinct()
        } else {
            emptyList()
        }
        val allRoles = (listOf(baseRole) + cleanExtraRoles).distinct()
        val sanitized = user.copy(
            role = baseRole,
            primaryRole = baseRole,
            roles = allRoles,
            extraRoles = cleanExtraRoles,
            userId = user.userId.trim().take(40),
            password = user.password.trim().take(64),
            fullName = user.fullName.trim().take(100),
            phone = user.phone.trim().take(30),
            wing = user.wing.trim().take(50),
            className = user.className.trim().take(30),
            section = user.section.trim().take(10),
            subject = user.subject.trim().take(120),
            parentName = user.parentName.trim().take(100),
            monthlyTuition = user.monthlyTuition.coerceAtLeast(0L),
            transportFee = user.transportFee.coerceAtLeast(0L),
        )
        val usersCol = col("users")
        if (sanitized.id.isBlank()) {
            val docRef = usersCol.document()
            val batch = db.batch()
            batch.set(docRef, sanitized.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null))
            allRoles.forEach { r ->
                val ticketId = computeLoginTicketId(r, sanitized.userId, sanitized.password)
                batch.set(
                    col("loginIndex").document(ticketId),
                    loginTicketPayload(uid, docRef.id, r, sanitized.userId, sanitized.className, sanitized.section)
                )
            }
            batch.commit().await()
            docRef.id
        } else {
            val docRef = usersCol.document(sanitized.id)
            val oldSnap = docRef.get().await()
            val oldUser = oldSnap.toObject(SchoolUser::class.java)
            val oldTicketIds = oldUser?.let { u ->
                u.effectiveRoles().map { r -> computeLoginTicketId(r, u.userId, u.password) }
            }.orEmpty().toSet()
            val newTicketsByRole = allRoles.associateWith { r ->
                computeLoginTicketId(r, sanitized.userId, sanitized.password)
            }
            val newTicketIds = newTicketsByRole.values.toSet()

            val memberRef = col("members").document(uid)
            val memberSnap = memberRef.get().await()
            val isEditingCurrentLogin = memberSnap.exists() && memberSnap.getString("schoolUserDocId") == sanitized.id
            val activeMemberRole = memberSnap.getString("role")?.takeIf { it in allRoles } ?: sanitized.role

            val batch = db.batch()
            val updateFields = mutableMapOf<String, Any>(
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
            if (editorRole == "ADMIN") {
                updateFields["role"] = sanitized.role
                updateFields["primaryRole"] = sanitized.primaryRole
                updateFields["roles"] = sanitized.roles
                updateFields["extraRoles"] = sanitized.extraRoles
            }
            batch.update(docRef, updateFields)

            newTicketsByRole.forEach { (r, ticketId) ->
                batch.set(
                    col("loginIndex").document(ticketId),
                    loginTicketPayload(uid, sanitized.id, r, sanitized.userId, sanitized.className, sanitized.section)
                )
            }
            (oldTicketIds - newTicketIds).forEach { staleId ->
                if (staleId.isNotBlank()) batch.delete(col("loginIndex").document(staleId))
            }
            if (isEditingCurrentLogin) {
                batch.update(
                    memberRef,
                    mapOf(
                        "schoolUserDocId" to sanitized.id,
                        "role" to activeMemberRole,
                        "loginTicketId" to (newTicketsByRole[activeMemberRole] ?: newTicketIds.first()),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            docRef.id
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/users")
    }

    suspend fun switchActiveMemberRole(user: SchoolUser, targetRole: String): Result<SchoolUser> = runCatching {
        val uid = requireUserId()
        val allowed = user.effectiveRoles()
        require(targetRole in allowed) { "Your account does not have permission for $targetRole." }
        val ticketId = computeLoginTicketId(targetRole, user.userId, user.password)
        col("members").document(uid).update(
            mapOf(
                "role" to targetRole,
                "loginTicketId" to ticketId,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        user.copy(role = targetRole, roles = allowed)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/members")
    }

    suspend fun deleteUserAccount(user: SchoolUser): Result<Unit> = runCatching {
        requireUserId()
        val batch = db.batch()
        batch.delete(col("users").document(user.id))
        user.effectiveRoles().forEach { r ->
            val ticketId = computeLoginTicketId(r, user.userId, user.password)
            batch.delete(col("loginIndex").document(ticketId))
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "schools/$schoolId/users/${user.id}")
    }

    suspend fun updateUserProfile(
        userDocId: String,
        newFullName: String,
        newPhone: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(userDocId.isNotBlank()) { "User document ID required" }
        val docRef = col("users").document(userDocId)
        val existingSnap = docRef.get().await()
        val oldUser = existingSnap.toObject(SchoolUser::class.java)
            ?: throw IllegalStateException("Profile not found")

        val cleanName = newFullName.trim().take(100)
        val cleanPhone = newPhone.trim().take(30)
        val cleanPw = newPassword.trim().take(64)

        val roles = oldUser.effectiveRoles()
        val oldTicketIds = roles.map { r -> computeLoginTicketId(r, oldUser.userId, oldUser.password) }.toSet()
        val newTicketsByRole = roles.associateWith { r -> computeLoginTicketId(r, oldUser.userId, cleanPw) }
        val newTicketIds = newTicketsByRole.values.toSet()

        val memberRef = col("members").document(uid)
        val memberSnap = memberRef.get().await()
        val isOwnMember = memberSnap.exists() && memberSnap.getString("schoolUserDocId") == userDocId
        val activeMemberRole = memberSnap.getString("role")?.takeIf { it in roles } ?: oldUser.role

        val batch = db.batch()
        batch.update(
            docRef,
            mapOf(
                "fullName" to cleanName,
                "phone" to cleanPhone,
                "password" to cleanPw,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        if (oldTicketIds != newTicketIds) {
            newTicketsByRole.forEach { (r, ticketId) ->
                batch.set(
                    col("loginIndex").document(ticketId),
                    loginTicketPayload(uid, userDocId, r, oldUser.userId, oldUser.className, oldUser.section)
                )
            }
            if (isOwnMember) {
                batch.update(
                    memberRef,
                    mapOf(
                        "loginTicketId" to (newTicketsByRole[activeMemberRole] ?: newTicketIds.first()),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            (oldTicketIds - newTicketIds).forEach { oldId ->
                if (oldId.isNotBlank()) batch.delete(col("loginIndex").document(oldId))
            }
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/users/$userDocId")
    }

    suspend fun deleteDocument(collectionName: String, docId: String): Result<Unit> = runCatching {
        requireUserId()
        col(collectionName).document(docId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "schools/$schoolId/$collectionName/$docId")
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
        val cleanVersionCode = config.latestVersionCode.coerceAtLeast(1L)
        val cleanVersionName = config.latestVersionName.trim().ifBlank { "1.1" }.take(30)
        val cleanApkUrl = config.apkDownloadUrl.trim().take(500)
        val cleanRepoSlug = config.githubRepoSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
            .take(160)
        val cleanUpdateNotes = config.updateNotes.trim().take(400)
        val gwCol = col("gatewayConfig")
        val existingDoc = gwCol.limit(1).get().await().documents.firstOrNull()
        if (existingDoc == null) {
            val docId = safeDocId("${schoolId}_gw_main")
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
                    latestVersionCode = cleanVersionCode,
                    latestVersionName = cleanVersionName,
                    apkDownloadUrl = cleanApkUrl,
                    githubRepoSlug = cleanRepoSlug,
                    updateNotes = cleanUpdateNotes,
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
                    "latestVersionCode" to cleanVersionCode,
                    "latestVersionName" to cleanVersionName,
                    "apkDownloadUrl" to cleanApkUrl,
                    "githubRepoSlug" to cleanRepoSlug,
                    "updateNotes" to cleanUpdateNotes,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/gatewayConfig")
    }

    suspend fun fetchLatestGitHubRelease(repoSlug: String): Result<GitHubReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanSlug = repoSlug.trim()
                    .removePrefix("https://github.com/")
                    .removePrefix("http://github.com/")
                    .trim('/')
                require(cleanSlug.contains('/')) { "Enter GitHub repository as owner/repo." }
                val apiUrl = "https://api.github.com/repos/$cleanSlug/releases/latest"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "DoonPublicSchoolEtah-Android")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("GitHub returned HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name").trim()
                val releaseName = json.optString("name").ifBlank { tagName }
                val htmlUrl = json.optString("html_url").trim()
                val notes = json.optString("body").trim().take(360)
                var apkUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").trim()
                            break
                        }
                    }
                }
                GitHubReleaseInfo(
                    tagName = tagName,
                    releaseName = releaseName,
                    apkDownloadUrl = apkUrl,
                    releasePageUrl = htmlUrl,
                    notes = notes,
                )
            }
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
            targetWing = circular.targetWing.trim().take(60),
            targetClass = circular.targetClass.trim().take(30),
            targetSection = circular.targetSection.trim().take(10),
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
        val docRef = col("circulars").document()
        docRef.set(sanitized).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/circulars")
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

        val safeKey = safeDocId("${schoolId}_${cleanDate}_${cleanClass}_${cleanSection}_${cleanSubject}_${cleanAssessmentType}")
        val targetDocId = safeDocId(dpt.id.ifBlank { safeKey })

        val dptCol = col("dptDaily")
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
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/dptDaily")
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

        val safeKey = safeDocId("${schoolId}_${cleanWeek}_${cleanClass}_${cleanSection}_${cleanSubject}_${cleanStudentDocId}")
        val targetDocId = safeDocId(entry.id.ifBlank { safeKey })

        val weeklyCol = col("dptWeekly")
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
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/dptWeekly")
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

        val safeKey = safeDocId("${schoolId}_att_${cleanDate}_${cleanStudentDocId}")
        val targetDocId = safeDocId(record.id.ifBlank { safeKey })

        val attCol = col("attendance")
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
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/attendance")
    }

    suspend fun submitLeave(leave: LeaveRequest): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("leaves").document()
        docRef.set(leave.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/leaves")
    }

    suspend fun updateLeaveStatus(leaveId: String, status: String, remark: String): Result<Unit> = runCatching {
        requireUserId()
        col("leaves").document(leaveId).update(
            mapOf(
                "status" to status,
                "teacherRemark" to remark,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/leaves/$leaveId")
    }

    // --- Homework & Term Marks ---

    suspend fun addHomework(hw: HomeworkItem): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("homework").document()
        docRef.set(hw.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/homework")
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

        val safeKey = safeDocId("${schoolId}_${cleanTerm}_${cleanStudentDocId}")
        val targetDocId = safeDocId(card.id.ifBlank { safeKey })

        val marksCol = col("termMarks")
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
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/termMarks")
    }

    // --- Per-Class-Section Editable Timetable ---

    suspend fun saveTimetable(
        className: String,
        section: String,
        slots: List<Map<String, String>>,
        updatedBy: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanClass = className.trim().take(30)
        val cleanSection = section.trim().take(10)
        val cleanSlots = slots.take(16)
        require(cleanClass.isNotBlank() && cleanSection.isNotBlank() && cleanSlots.isNotEmpty()) {
            "Class, section, and timetable periods are required."
        }
        val docId = safeDocId("tt_${cleanClass}_${cleanSection}")
        val docRef = col("timetables").document(docId)
        val existing = docRef.get().await()
        if (!existing.exists()) {
            docRef.set(
                ClassTimetable(
                    id = "",
                    ownerUid = uid,
                    className = cleanClass,
                    section = cleanSection,
                    slots = cleanSlots,
                    updatedBy = updatedBy.trim().ifBlank { "Teacher" }.take(100),
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "slots" to cleanSlots,
                    "updatedBy" to updatedBy.trim().ifBlank { "Teacher" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/timetables")
    }

    // --- Daily Campus Posters / Event Photos ---

    suspend fun addCampusPoster(poster: CampusPoster): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("campusPosters").document()
        val sanitized = poster.copy(
            id = "",
            ownerUid = uid,
            title = poster.title.trim().take(140),
            subtitle = poster.subtitle.trim().take(180),
            date = poster.date.trim().ifBlank { SchoolCatalog.todayIso() }.take(30),
            imageData = poster.imageData.take(455_000),
            postedBy = poster.postedBy.trim().ifBlank { "Principal" }.take(100),
            createdAt = null,
            updatedAt = null,
        )
        docRef.set(sanitized).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/campusPosters")
    }

    // --- Fees & Payments ---

    suspend fun addFeeItem(item: FeeItem): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("feeItems").document()
        docRef.set(item.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/feeItems")
    }

    suspend fun submitPaymentProof(payment: FeePayment, feeItemIds: List<String>): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("payments").document()
        val batch = db.batch()
        batch.set(docRef, payment.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null))
        feeItemIds.forEach { fid ->
            batch.update(
                col("feeItems").document(fid),
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
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/payments")
    }

    suspend fun verifyPayment(
        payment: FeePayment,
        receiptNo: String,
        verifierName: String,
        verifiedAtStr: String,
    ): Result<Unit> = runCatching {
        requireUserId()
        val batch = db.batch()
        batch.update(
            col("payments").document(payment.id),
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
                col("feeItems").document(fid),
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
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/payments/${payment.id}")
    }

    suspend fun rejectPayment(
        payment: FeePayment,
        reason: String,
        verifierName: String,
        verifiedAtStr: String,
    ): Result<Unit> = runCatching {
        requireUserId()
        val batch = db.batch()
        batch.update(
            col("payments").document(payment.id),
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
                col("feeItems").document(fid),
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
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/payments/${payment.id}")
    }

    // --- Doubts ---

    suspend fun submitDoubt(doubt: DoubtMessage): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("doubts").document()
        docRef.set(doubt.copy(id = "", ownerUid = uid, createdAt = null, updatedAt = null)).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/doubts")
    }

    suspend fun replyDoubt(doubtId: String, reply: String, repliedBy: String): Result<Unit> = runCatching {
        requireUserId()
        col("doubts").document(doubtId).update(
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
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/doubts/$doubtId")
    }

    // --- Craft Ideas & Unique Per-User Likes ---

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
        val docRef = col("craftProjects").document()
        docRef.set(sanitized).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/craftProjects")
    }

    suspend fun updateCraftProjectAiImage(
        projectId: String,
        aiImageData: String,
        aiPromptUsed: String,
    ): Result<Unit> = runCatching {
        requireUserId()
        col("craftProjects").document(projectId).update(
            mapOf(
                "aiImageData" to aiImageData.take(455_000),
                "aiPromptUsed" to aiPromptUsed.trim().take(600),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/craftProjects/$projectId")
    }

    /**
     * Atomically toggles a unique like per school user (max 1 like per user per post).
     * Returns true if liked, false if unliked.
     */
    suspend fun toggleCraftProjectLike(projectId: String, likerUserDocId: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val safeProjId = safeDocId(projectId)
        val safeLikerId = safeDocId(likerUserDocId)
        require(safeProjId.isNotBlank() && safeLikerId.isNotBlank()) { "Valid project and user ID required" }

        val likeDocId = "${safeProjId}__${safeLikerId}"
        val projRef = col("craftProjects").document(safeProjId)
        val likeRef = col("craftLikes").document(likeDocId)

        db.runTransaction { tx ->
            val projSnap = tx.get(projRef)
            val likeSnap = tx.get(likeRef)
            val currentLikes = (projSnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
            if (likeSnap.exists()) {
                tx.delete(likeRef)
                tx.update(
                    projRef,
                    mapOf(
                        "likesCount" to (currentLikes - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                false
            } else {
                tx.set(
                    likeRef,
                    mapOf(
                        "ownerUid" to uid,
                        "projectId" to safeProjId,
                        "likerId" to safeLikerId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tx.update(
                    projRef,
                    mapOf(
                        "likesCount" to (currentLikes + 1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                true
            }
        }.await()
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/craftLikes")
    }

    // --- Library Management ---

    suspend fun issueLibraryBook(issue: LibraryIssue): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = col("libraryIssues").document()
        docRef.set(
            issue.copy(
                id = "",
                ownerUid = uid,
                bookTitle = issue.bookTitle.trim().take(160),
                bookCode = issue.bookCode.trim().take(60),
                author = issue.author.trim().take(100),
                borrowerDocId = issue.borrowerDocId.trim().take(128),
                borrowerUserId = issue.borrowerUserId.trim().take(40),
                borrowerName = issue.borrowerName.trim().take(100),
                borrowerRole = issue.borrowerRole.trim().ifBlank { "STUDENT" },
                borrowerClassInfo = issue.borrowerClassInfo.trim().take(60),
                issueDate = issue.issueDate.trim().ifBlank { SchoolCatalog.todayIso() }.take(20),
                dueDate = issue.dueDate.trim().ifBlank { SchoolCatalog.isoDaysFromToday(14) }.take(20),
                returnedDate = "",
                status = "ISSUED",
                issuedBy = issue.issuedBy.trim().take(100),
                notes = issue.notes.trim().take(300),
                createdAt = null,
                updatedAt = null,
            )
        ).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId/libraryIssues")
    }

    suspend fun returnLibraryBook(issueId: String): Result<Unit> = runCatching {
        requireUserId()
        col("libraryIssues").document(issueId).update(
            mapOf(
                "status" to "RETURNED",
                "returnedDate" to SchoolCatalog.todayIso(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/libraryIssues/$issueId")
    }

    // --- Automated Parent Absence Notice ---

    suspend fun upsertAbsenceNoticeForStudent(
        student: SchoolUser,
        date: String,
        markedByName: String,
        senderRole: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = safeDocId("abs_${date}_${student.id}")
        val parentLabel = student.parentName.ifBlank { "Parent / Guardian" }
        val title = "Absence Notice: ${student.fullName} ($date)".take(150)
        val content = "Dear $parentLabel, your ward ${student.fullName} (${student.className}-${student.section}, Roll/ID ${student.userId}) was marked ABSENT on $date by $markedByName. If your ward is on leave, please submit a leave application in the app or contact the school office."
        val docRef = col("circulars").document(docId)
        val existing = docRef.get().await()
        if (!existing.exists()) {
            docRef.set(
                Circular(
                    id = "",
                    ownerUid = uid,
                    title = title,
                    category = "Absence Alert",
                    audience = "Parent of ${student.fullName}",
                    targetWing = student.wing,
                    targetClass = student.className,
                    targetSection = student.section,
                    date = date,
                    content = content,
                    postedBy = markedByName.ifBlank { "Class Teacher" },
                    recipientDocId = student.id,
                    recipientName = student.fullName,
                    senderRole = if (senderRole == "ADMIN") "ADMIN" else "TEACHER",
                    createdAt = null,
                    updatedAt = null,
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "title" to title,
                    "content" to content,
                    "postedBy" to markedByName.ifBlank { "Class Teacher" },
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "schools/$schoolId/circulars")
    }

    suspend fun removeAbsenceNoticeForStudent(studentDocId: String, date: String): Result<Unit> = runCatching {
        requireUserId()
        val docId = safeDocId("abs_${date}_$studentDocId")
        val docRef = col("circulars").document(docId)
        if (docRef.get().await().exists()) {
            docRef.delete().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "schools/$schoolId/circulars")
    }
}
