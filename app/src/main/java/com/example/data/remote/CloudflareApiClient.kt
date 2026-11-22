package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

data class CloudflareAuthSession(
    val token: String,
    val user: SchoolUser,
)

data class CloudflareBootstrapStatus(
    val needsSetup: Boolean,
    val appUpdate: GatewayConfig = GatewayConfig(),
)

data class CloudflareDashboardData(
    val currentUser: SchoolUser,
    val gateway: GatewayConfig,
    val users: List<SchoolUser>,
    val circulars: List<Circular>,
    val dptDaily: List<DptDaily>,
    val dptWeekly: List<DptWeekly>,
    val attendance: List<AttendanceRecord>,
    val leaves: List<LeaveRequest>,
    val homework: List<HomeworkItem>,
    val termMarks: List<TermMarkCard>,
    val feeItems: List<FeeItem>,
    val payments: List<FeePayment>,
    val doubts: List<DoubtMessage>,
    val craftProjects: List<CraftProject>,
    val craftLikes: List<CraftLike>,
    val campusPosters: List<CampusPoster>,
    val timetables: List<ClassTimetable>,
)

object CloudflareApiClient {

    @Volatile
    private var runtimeOverrideUrl: String = ""

    fun normalizeServerUrl(rawInput: String): String {
        var cleaned = rawInput.trim().trim('"', '\'').trim()
        if (cleaned.isEmpty()) return ""
        if (!cleaned.startsWith("http://", ignoreCase = true) && !cleaned.startsWith("https://", ignoreCase = true)) {
            cleaned = "https://$cleaned"
        }
        val parsed = runCatching { URI(cleaned) }.getOrNull() ?: return ""
        val scheme = parsed.scheme?.lowercase() ?: return ""
        val host = parsed.host?.trim()?.lowercase() ?: return ""
        if (host.isEmpty() || host.any { it.isWhitespace() }) return ""
        val portPart = if (parsed.port != -1) ":${parsed.port}" else ""
        val origin = "$scheme://$host$portPart"
        val isHttps = scheme == "https"
        val isLocalHttp = scheme == "http" && (host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost")
        return if (isHttps || isLocalHttp) origin else ""
    }

    fun setRuntimeServerUrl(url: String) {
        runtimeOverrideUrl = normalizeServerUrl(url)
    }

    fun isUsingRuntimeOverride(): Boolean = runtimeOverrideUrl.isNotBlank()

    fun activeServerUrl(): String =
        runtimeOverrideUrl.ifBlank { normalizeServerUrl(BuildConfig.CLOUDFLARE_API_URL) }

    fun isEnabled(): Boolean = activeServerUrl().isNotBlank()

    suspend fun verifyServerUrl(candidateUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeServerUrl(candidateUrl)
            require(normalized.isNotBlank()) {
                "Enter a valid HTTPS Cloudflare Worker URL (e.g. https://doon-public-school-etah.yourname.workers.dev)."
            }
            val parsed = try {
                requestJsonWithBase(normalized, "GET", "/api/bootstrap/status")
            } catch (_: UnknownHostException) {
                throw IllegalStateException("Server address not found. Check spelling of your workers.dev URL and internet connection.")
            } catch (_: SocketTimeoutException) {
                throw IllegalStateException("Cloudflare server timed out. Check your internet connection and try again.")
            } catch (_: ConnectException) {
                throw IllegalStateException("Could not connect to $normalized. Ensure your Cloudflare Worker is deployed.")
            }
            if (!parsed.has("needsSetup")) {
                throw IllegalStateException("Connected to $normalized, but it did not respond as the Doon Public School Cloudflare Worker.")
            }
            normalized
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val baseUrl = activeServerUrl()
        require(baseUrl.isNotBlank()) { "Cloudflare API URL is not configured." }
        requestJsonWithBase(baseUrl, method, path, body, token)
    }

    private fun requestJsonWithBase(
        baseUrl: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val parsed = if (text.isNotBlank()) runCatching { JSONObject(text) }.getOrNull() ?: JSONObject() else JSONObject()
            if (code !in 200..299) {
                val err = parsed.optString("error").ifBlank { "Server returned HTTP $code" }
                throw IllegalStateException(err)
            }
            parsed
        } finally {
            conn.disconnect()
        }
    }

    suspend fun getBootstrapStatus(): Result<CloudflareBootstrapStatus> = runCatching {
        val res = requestJson("GET", "/api/bootstrap/status")
        val needsSetup = res.optBoolean("needsSetup", false)
        val appUpdate = parseGateway(res.optJSONObject("appUpdate"))
        CloudflareBootstrapStatus(needsSetup = needsSetup, appUpdate = appUpdate)
    }

    suspend fun fetchPublicAppUpdate(): Result<GatewayConfig> = runCatching {
        val res = requestJson("GET", "/api/app-update")
        parseGateway(res.optJSONObject("appUpdate"))
    }

    suspend fun claimAdmin(
        fullName: String,
        userId: String,
        phone: String,
        password: String,
    ): Result<CloudflareAuthSession> = runCatching {
        val body = JSONObject().apply {
            put("fullName", fullName.trim())
            put("userId", userId.trim().ifBlank { "ADMIN01" })
            put("phone", phone.trim())
            put("password", password.trim())
        }
        val res = requestJson("POST", "/api/auth/claim-admin", body = body)
        CloudflareAuthSession(
            token = res.getString("token"),
            user = parseUser(res.getJSONObject("user")),
        )
    }

    suspend fun login(role: String, userId: String, password: String): Result<CloudflareAuthSession> = runCatching {
        val body = JSONObject().apply {
            put("role", role.trim())
            put("userId", userId.trim())
            put("password", password.trim())
        }
        val res = requestJson("POST", "/api/auth/login", body = body)
        CloudflareAuthSession(
            token = res.getString("token"),
            user = parseUser(res.getJSONObject("user")),
        )
    }

    suspend fun switchRole(token: String, targetRole: String): Result<CloudflareAuthSession> = runCatching {
        val body = JSONObject().apply {
            put("role", targetRole.trim())
        }
        val res = requestJson("POST", "/api/auth/switch-role", body = body, token = token)
        CloudflareAuthSession(
            token = res.getString("token"),
            user = parseUser(res.getJSONObject("user")),
        )
    }

    suspend fun fetchDashboard(
        token: String,
        className: String = "",
        section: String = "",
    ): Result<CloudflareDashboardData> = runCatching {
        val params = buildList {
            if (className.isNotBlank()) add("className=${URLEncoder.encode(className, "UTF-8")}")
            if (section.isNotBlank()) add("section=${URLEncoder.encode(section, "UTF-8")}")
        }
        val suffix = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        val res = requestJson("GET", "/api/dashboard$suffix", token = token)

        val currentUser = parseUser(res.getJSONObject("currentUser"))
        val gateway = parseGateway(res.optJSONObject("gateway"))
        val users = parseList(res.optJSONArray("users"), ::parseUser)
        val circulars = parseList(res.optJSONArray("circulars"), ::parseCircular)
        val dptDaily = parseList(res.optJSONArray("dptDaily"), ::parseDptDaily)
        val dptWeekly = parseList(res.optJSONArray("dptWeekly"), ::parseDptWeekly)
        val attendance = parseList(res.optJSONArray("attendance"), ::parseAttendance)
        val leaves = parseList(res.optJSONArray("leaves"), ::parseLeave)
        val homework = parseList(res.optJSONArray("homework"), ::parseHomework)
        val termMarks = parseList(res.optJSONArray("termMarks"), ::parseTermMarks)
        val feeItems = parseList(res.optJSONArray("feeItems"), ::parseFeeItem)
        val payments = parseList(res.optJSONArray("payments"), ::parseFeePayment)
        val doubts = parseList(res.optJSONArray("doubts"), ::parseDoubt)
        val craftProjects = parseList(res.optJSONArray("craftProjects"), ::parseCraftProject)
        val campusPosters = parseList(res.optJSONArray("campusPosters"), ::parseCampusPoster)
        val timetables = parseList(res.optJSONArray("timetables"), ::parseTimetable)

        val likedIdsArr = res.optJSONArray("likedProjectIds")
        val craftLikes = buildList {
            if (likedIdsArr != null) {
                for (i in 0 until likedIdsArr.length()) {
                    val pid = likedIdsArr.optString(i)
                    if (pid.isNotBlank()) {
                        add(CraftLike(id = "${pid}__${currentUser.id}", projectId = pid, likerId = currentUser.id))
                    }
                }
            }
        }

        CloudflareDashboardData(
            currentUser = currentUser,
            gateway = gateway,
            users = users,
            circulars = circulars,
            dptDaily = dptDaily,
            dptWeekly = dptWeekly,
            attendance = attendance,
            leaves = leaves,
            homework = homework,
            termMarks = termMarks,
            feeItems = feeItems,
            payments = payments,
            doubts = doubts,
            craftProjects = craftProjects,
            craftLikes = craftLikes,
            campusPosters = campusPosters,
            timetables = timetables,
        )
    }

    suspend fun updateProfile(token: String, fullName: String, phone: String, password: String): Result<SchoolUser> = runCatching {
        val body = JSONObject().apply {
            put("fullName", fullName.trim())
            put("phone", phone.trim())
            put("password", password.trim())
        }
        val res = requestJson("PUT", "/api/profile", body = body, token = token)
        parseUser(res.getJSONObject("user"))
    }

    suspend fun saveUser(token: String, user: SchoolUser): Result<SchoolUser> = runCatching {
        val body = JSONObject().apply {
            if (user.id.isNotBlank()) put("id", user.id)
            put("role", user.primaryRole.ifBlank { user.role })
            put("extraRoles", JSONArray(user.extraRoles))
            put("userId", user.userId)
            put("password", user.password)
            put("fullName", user.fullName)
            put("phone", user.phone)
            put("wing", user.wing)
            put("className", user.className)
            put("section", user.section)
            put("subject", user.subject)
            put("parentName", user.parentName)
            put("monthlyTuition", user.monthlyTuition)
            put("transportFee", user.transportFee)
        }
        val res = requestJson("POST", "/api/users", body = body, token = token)
        parseUser(res.getJSONObject("user"))
    }

    suspend fun deleteUser(token: String, docId: String): Result<Unit> = runCatching {
        requestJson("DELETE", "/api/users/${URLEncoder.encode(docId, "UTF-8")}", token = token)
    }

    suspend fun deleteRecord(token: String, collectionName: String, docId: String): Result<Unit> = runCatching {
        val route = when (collectionName) {
            "circulars" -> "/api/circulars/${URLEncoder.encode(docId, "UTF-8")}"
            "campusPosters" -> "/api/posters/${URLEncoder.encode(docId, "UTF-8")}"
            "craftProjects" -> "/api/craft/${URLEncoder.encode(docId, "UTF-8")}"
            "homework" -> "/api/homework/${URLEncoder.encode(docId, "UTF-8")}"
            "dptDaily" -> "/api/dpt-daily/${URLEncoder.encode(docId, "UTF-8")}"
            "feeItems" -> "/api/fee-items/${URLEncoder.encode(docId, "UTF-8")}"
            else -> throw IllegalArgumentException("Unsupported collection delete: $collectionName")
        }
        requestJson("DELETE", route, token = token)
    }

    suspend fun saveGatewayConfig(token: String, config: GatewayConfig): Result<GatewayConfig> = runCatching {
        val body = JSONObject().apply {
            put("merchantUpi", config.merchantUpi)
            put("payeeName", config.payeeName)
            put("bankName", config.bankName)
            put("accountNo", config.accountNo)
            put("ifsc", config.ifsc)
            put("razorpayKeyId", config.razorpayKeyId)
            put("latestVersionCode", config.latestVersionCode)
            put("latestVersionName", config.latestVersionName)
            put("apkDownloadUrl", config.apkDownloadUrl)
            put("githubRepoSlug", config.githubRepoSlug)
            put("updateNotes", config.updateNotes)
        }
        val res = requestJson("PUT", "/api/gateway", body = body, token = token)
        parseGateway(res.optJSONObject("gateway"))
    }

    suspend fun postCircular(token: String, c: Circular): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("title", c.title)
            put("category", c.category)
            put("audience", c.audience)
            put("targetWing", c.targetWing)
            put("targetClass", c.targetClass)
            put("targetSection", c.targetSection)
            put("date", c.date)
            put("content", c.content)
            put("recipientDocId", c.recipientDocId)
            put("recipientName", c.recipientName)
            put("attachmentName", c.attachmentName)
            put("attachmentMimeType", c.attachmentMimeType)
            put("attachmentData", c.attachmentData)
        }
        requestJson("POST", "/api/circulars", body = body, token = token)
    }

    suspend fun postCampusPoster(token: String, p: CampusPoster): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("title", p.title)
            put("subtitle", p.subtitle)
            put("date", p.date)
            put("imageData", p.imageData)
        }
        requestJson("POST", "/api/posters", body = body, token = token)
    }

    suspend fun saveTimetable(token: String, className: String, section: String, slots: List<Map<String, String>>): Result<Unit> = runCatching {
        val arr = JSONArray()
        slots.forEach { slot ->
            arr.put(JSONObject().apply {
                slot.forEach { (k, v) -> put(k, v) }
            })
        }
        val body = JSONObject().apply {
            put("className", className)
            put("section", section)
            put("slots", arr)
        }
        requestJson("POST", "/api/timetables", body = body, token = token)
    }

    suspend fun saveDptDaily(token: String, d: DptDaily): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("date", d.date)
            put("className", d.className)
            put("section", d.section)
            put("subject", d.subject)
            put("assessmentType", d.assessmentType)
            put("held", d.held)
            put("topic", d.topic)
            put("maxMarks", d.maxMarks)
            put("notes", d.notes)
            put("reason", d.reason)
            put("attachmentName", d.attachmentName)
            put("attachmentMimeType", d.attachmentMimeType)
            put("attachmentData", d.attachmentData)
        }
        requestJson("POST", "/api/dpt-daily", body = body, token = token)
    }

    suspend fun saveDptWeeklyBatch(token: String, entries: List<DptWeekly>): Result<Unit> = runCatching {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("week", e.week)
                put("className", e.className)
                put("section", e.section)
                put("subject", e.subject)
                put("studentDocId", e.studentDocId)
                put("studentName", e.studentName)
                put("admissionNo", e.admissionNo)
                put("scored", e.scored)
                put("maxMarks", e.maxMarks)
            })
        }
        requestJson("POST", "/api/dpt-weekly", body = JSONObject().apply { put("entries", arr) }, token = token)
    }

    suspend fun saveAttendanceBatch(token: String, records: List<AttendanceRecord>): Result<Unit> = runCatching {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("date", r.date)
                put("className", r.className)
                put("section", r.section)
                put("studentDocId", r.studentDocId)
                put("status", r.status)
            })
        }
        requestJson("POST", "/api/attendance", body = JSONObject().apply { put("records", arr) }, token = token)
    }

    suspend fun postHomework(token: String, hw: HomeworkItem): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("className", hw.className)
            put("section", hw.section)
            put("subject", hw.subject)
            put("title", hw.title)
            put("description", hw.description)
            put("dueDate", hw.dueDate)
        }
        requestJson("POST", "/api/homework", body = body, token = token)
    }

    suspend fun publishTermMarks(token: String, card: TermMarkCard): Result<Unit> = runCatching {
        val marksObj = JSONObject()
        card.marks.forEach { (sub, vals) ->
            marksObj.put(sub, JSONObject().apply {
                put("scored", vals["scored"] ?: 0L)
                put("max", vals["max"] ?: 100L)
            })
        }
        val body = JSONObject().apply {
            put("term", card.term)
            put("className", card.className)
            put("section", card.section)
            put("studentDocId", card.studentDocId)
            put("studentName", card.studentName)
            put("admissionNo", card.admissionNo)
            put("marks", marksObj)
            put("remarks", card.remarks)
        }
        requestJson("POST", "/api/term-marks", body = body, token = token)
    }

    suspend fun submitLeave(token: String, leave: LeaveRequest): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("fromDate", leave.fromDate)
            put("toDate", leave.toDate)
            put("reason", leave.reason)
        }
        requestJson("POST", "/api/leaves", body = body, token = token)
    }

    suspend fun updateLeaveStatus(token: String, leaveId: String, status: String, remark: String): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("status", status)
            put("remark", remark)
        }
        requestJson("PUT", "/api/leaves/${URLEncoder.encode(leaveId, "UTF-8")}", body = body, token = token)
    }

    suspend fun submitDoubt(token: String, doubt: DoubtMessage): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("subject", doubt.subject)
            put("message", doubt.message)
        }
        requestJson("POST", "/api/doubts", body = body, token = token)
    }

    suspend fun replyDoubt(token: String, doubtId: String, reply: String): Result<Unit> = runCatching {
        requestJson("PUT", "/api/doubts/${URLEncoder.encode(doubtId, "UTF-8")}", body = JSONObject().apply { put("reply", reply) }, token = token)
    }

    suspend fun triggerFeeBills(token: String, request: FeeBillRequest): Result<FeeBillResult> = runCatching {
        val classesArr = JSONArray().apply {
            request.selectedClasses.forEach { put(it) }
        }
        val studentIdsArr = JSONArray().apply {
            request.selectedStudentDocIds.forEach { put(it) }
        }
        val body = JSONObject().apply {
            put("scope", request.scope.apiValue)
            put("classes", classesArr)
            put("section", request.sectionFilter)
            put("studentDocIds", studentIdsArr)
            put("billType", request.billType.apiValue)
            put("month", request.month.trim())
            if (request.billType == FeeBillType.MISC) {
                put("miscTitle", request.miscTitle.trim())
                put("miscAmount", request.miscAmount)
            }
        }
        val res = requestJson("POST", "/api/fee-bills", body = body, token = token)
        FeeBillResult(
            itemsCreated = res.optInt("itemsCreated", 0),
            studentsBilled = res.optInt("studentsBilled", 0),
            skippedDuplicates = res.optInt("skippedDuplicates", 0),
            totalStudentsMatched = res.optInt("totalStudentsMatched", 0),
        )
    }

    suspend fun assignFeeItems(token: String, items: List<FeeItem>): Result<Int> = runCatching {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("studentDocId", item.studentDocId)
                put("title", item.title)
                put("month", item.month)
                put("amount", item.amount)
            })
        }
        val res = requestJson("POST", "/api/fee-items", body = JSONObject().apply { put("items", arr) }, token = token)
        res.optInt("count", items.size)
    }

    suspend fun submitPaymentProof(
        token: String,
        feeItemIds: List<String>,
        mode: String,
        utrOrRef: String,
        upiApp: String,
    ): Result<Unit> = runCatching {
        val arr = JSONArray()
        feeItemIds.forEach { arr.put(it) }
        val body = JSONObject().apply {
            put("feeItemIds", arr)
            put("mode", mode)
            put("utrOrRef", utrOrRef)
            put("upiApp", upiApp)
        }
        requestJson("POST", "/api/payments", body = body, token = token)
    }

    suspend fun verifyOrRejectPayment(token: String, paymentId: String, action: String, reason: String = ""): Result<String> = runCatching {
        val body = JSONObject().apply {
            put("action", action)
            if (reason.isNotBlank()) put("reason", reason)
        }
        val res = requestJson("PUT", "/api/payments/${URLEncoder.encode(paymentId, "UTF-8")}", body = body, token = token)
        res.optString("receiptNo")
    }

    suspend fun postCraftProject(token: String, p: CraftProject): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("title", p.title)
            put("category", p.category)
            put("description", p.description)
            put("materials", p.materials)
            put("aiImageData", p.aiImageData)
        }
        requestJson("POST", "/api/craft", body = body, token = token)
    }

    suspend fun toggleCraftLike(token: String, projectId: String): Result<Boolean> = runCatching {
        val res = requestJson("POST", "/api/craft/${URLEncoder.encode(projectId, "UTF-8")}/like", body = JSONObject(), token = token)
        res.optBoolean("liked", false)
    }

    // --- JSON Parsing Helpers (supports both camelCase & snake_case) ---

    private fun JSONObject.str(camel: String, snake: String = camel, fallback: String = ""): String =
        when {
            has(camel) && !isNull(camel) -> optString(camel, fallback)
            has(snake) && !isNull(snake) -> optString(snake, fallback)
            else -> fallback
        }

    private fun JSONObject.num(camel: String, snake: String = camel, fallback: Long = 0L): Long =
        when {
            has(camel) && !isNull(camel) -> optLong(camel, fallback)
            has(snake) && !isNull(snake) -> optLong(snake, fallback)
            else -> fallback
        }

    private fun epochToTimestamp(ms: Long): Timestamp? =
        if (ms > 0L) Timestamp(ms / 1000L, ((ms % 1000L) * 1_000_000L).toInt()) else null

    private fun <T> parseList(arr: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(mapper(obj))
            }
        }
    }

    private fun JSONObject.strList(camel: String, snake: String = camel): List<String> {
        val arr = optJSONArray(camel) ?: optJSONArray(snake)
        if (arr != null) {
            return buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotBlank()) add(v)
                }
            }
        }
        val raw = str(camel, snake)
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun parseUser(o: JSONObject): SchoolUser = SchoolUser(
        id = o.str("id"),
        role = o.str("role", fallback = "STUDENT"),
        primaryRole = o.str("primaryRole", "primary_role", o.str("role", fallback = "STUDENT")),
        roles = o.strList("roles"),
        extraRoles = o.strList("extraRoles", "extra_roles"),
        userId = o.str("userId", "user_id"),
        password = "",
        fullName = o.str("fullName", "full_name"),
        phone = o.str("phone"),
        wing = o.str("wing"),
        className = o.str("className", "class_name"),
        section = o.str("section"),
        subject = o.str("subject"),
        parentName = o.str("parentName", "parent_name"),
        monthlyTuition = o.num("monthlyTuition", "monthly_tuition"),
        transportFee = o.num("transportFee", "transport_fee"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
        updatedAt = epochToTimestamp(o.num("updatedAt", "updated_at")),
    )

    private fun parseGateway(o: JSONObject?): GatewayConfig {
        if (o == null) return GatewayConfig()
        return GatewayConfig(
            id = o.str("id", fallback = "dps_etah_gw_main"),
            merchantUpi = o.str("merchantUpi", "merchant_upi", "doonpublicetah@sbi"),
            payeeName = o.str("payeeName", "payee_name", SchoolInfo.NAME),
            bankName = o.str("bankName", "bank_name"),
            accountNo = o.str("accountNo", "account_no"),
            ifsc = o.str("ifsc"),
            razorpayKeyId = o.str("razorpayKeyId", "razorpay_key_id"),
            latestVersionCode = o.num("latestVersionCode", "latest_version_code", 3L),
            latestVersionName = o.str("latestVersionName", "latest_version_name", "1.2"),
            apkDownloadUrl = o.str("apkDownloadUrl", "apk_download_url"),
            githubRepoSlug = o.str("githubRepoSlug", "github_repo_slug"),
            updateNotes = o.str("updateNotes", "update_notes"),
        )
    }

    private fun parseCircular(o: JSONObject): Circular = Circular(
        id = o.str("id"),
        title = o.str("title"),
        category = o.str("category", fallback = "Notice"),
        audience = o.str("audience", fallback = "All Wings"),
        targetWing = o.str("targetWing", "target_wing"),
        targetClass = o.str("targetClass", "target_class"),
        targetSection = o.str("targetSection", "target_section"),
        date = o.str("date"),
        content = o.str("content"),
        postedBy = o.str("postedBy", "posted_by"),
        recipientDocId = o.str("recipientDocId", "recipient_doc_id"),
        recipientName = o.str("recipientName", "recipient_name"),
        senderRole = o.str("senderRole", "sender_role", "ADMIN"),
        attachmentName = o.str("attachmentName", "attachment_name"),
        attachmentMimeType = o.str("attachmentMimeType", "attachment_mime_type"),
        attachmentData = o.str("attachmentData", "attachment_data"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseDptDaily(o: JSONObject): DptDaily = DptDaily(
        id = o.str("id"),
        date = o.str("date"),
        className = o.str("className", "class_name"),
        section = o.str("section"),
        subject = o.str("subject"),
        assessmentType = o.str("assessmentType", "assessment_type", "DPT"),
        held = if (o.has("held")) (o.opt("held") == true || o.optInt("held", 1) == 1) else true,
        topic = o.str("topic"),
        maxMarks = o.num("maxMarks", "max_marks"),
        notes = o.str("notes"),
        reason = o.str("reason"),
        attachmentName = o.str("attachmentName", "attachment_name"),
        attachmentMimeType = o.str("attachmentMimeType", "attachment_mime_type"),
        attachmentData = o.str("attachmentData", "attachment_data"),
        teacherId = o.str("teacherId", "teacher_id"),
        teacherName = o.str("teacherName", "teacher_name"),
        updatedAt = epochToTimestamp(o.num("updatedAt", "updated_at")),
    )

    private fun parseDptWeekly(o: JSONObject): DptWeekly = DptWeekly(
        id = o.str("id"),
        week = o.str("week"),
        className = o.str("className", "class_name"),
        section = o.str("section"),
        subject = o.str("subject"),
        studentDocId = o.str("studentDocId", "student_doc_id"),
        studentName = o.str("studentName", "student_name"),
        admissionNo = o.str("admissionNo", "admission_no"),
        scored = o.num("scored"),
        maxMarks = o.num("maxMarks", "max_marks", 20L),
        teacherId = o.str("teacherId", "teacher_id"),
        updatedAt = epochToTimestamp(o.num("updatedAt", "updated_at")),
    )

    private fun parseAttendance(o: JSONObject): AttendanceRecord = AttendanceRecord(
        id = o.str("id"),
        date = o.str("date"),
        className = o.str("className", "class_name"),
        section = o.str("section"),
        studentDocId = o.str("studentDocId", "student_doc_id"),
        status = o.str("status", fallback = "Present"),
        markedBy = o.str("markedBy", "marked_by"),
        updatedAt = epochToTimestamp(o.num("updatedAt", "updated_at")),
    )

    private fun parseLeave(o: JSONObject): LeaveRequest = LeaveRequest(
        id = o.str("id"),
        studentDocId = o.str("studentDocId", "student_doc_id"),
        studentName = o.str("studentName", "student_name"),
        admissionNo = o.str("admissionNo", "admission_no"),
        className = o.str("className", "class_name"),
        section = o.str("section"),
        fromDate = o.str("fromDate", "from_date"),
        toDate = o.str("toDate", "to_date"),
        reason = o.str("reason"),
        status = o.str("status", fallback = "Pending"),
        teacherRemark = o.str("teacherRemark", "teacher_remark"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseHomework(o: JSONObject): HomeworkItem = HomeworkItem(
        id = o.str("id"),
        className = o.str("className", "class_name"),
        section = o.str("section"),
        subject = o.str("subject"),
        title = o.str("title"),
        description = o.str("description"),
        dueDate = o.str("dueDate", "due_date"),
        teacherName = o.str("teacherName", "teacher_name"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseTermMarks(o: JSONObject): TermMarkCard {
        val rawMarks = o.optJSONObject("marks") ?: runCatching {
            JSONObject(o.str("marksJson", "marks_json", "{}"))
        }.getOrElse { JSONObject() }
        val marksMap = buildMap<String, Map<String, Long>> {
            val keys = rawMarks.keys()
            while (keys.hasNext()) {
                val sub = keys.next()
                val subObj = rawMarks.optJSONObject(sub) ?: continue
                put(sub, mapOf("scored" to subObj.optLong("scored", 0L), "max" to subObj.optLong("max", 100L)))
            }
        }
        return TermMarkCard(
            id = o.str("id"),
            term = o.str("term"),
            className = o.str("className", "class_name"),
            section = o.str("section"),
            studentDocId = o.str("studentDocId", "student_doc_id"),
            studentName = o.str("studentName", "student_name"),
            admissionNo = o.str("admissionNo", "admission_no"),
            marks = marksMap,
            remarks = o.str("remarks"),
            publishedBy = o.str("publishedBy", "published_by"),
            updatedAt = epochToTimestamp(o.num("updatedAt", "updated_at")),
        )
    }

    private fun parseFeeItem(o: JSONObject): FeeItem = FeeItem(
        id = o.str("id"),
        studentDocId = o.str("studentDocId", "student_doc_id"),
        title = o.str("title"),
        month = o.str("month"),
        amount = o.num("amount"),
        status = o.str("status", fallback = "UNPAID"),
        paymentId = o.str("paymentId", "payment_id"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseFeePayment(o: JSONObject): FeePayment {
        val arr = o.optJSONArray("feeItemIds") ?: runCatching {
            JSONArray(o.str("feeItemIdsJson", "fee_item_ids_json", "[]"))
        }.getOrElse { JSONArray() }
        val ids = buildList {
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) add(id)
            }
        }
        return FeePayment(
            id = o.str("id"),
            studentDocId = o.str("studentDocId", "student_doc_id"),
            studentName = o.str("studentName", "student_name"),
            admissionNo = o.str("admissionNo", "admission_no"),
            className = o.str("className", "class_name"),
            feeItemIds = ids,
            feeSummary = o.str("feeSummary", "fee_summary"),
            amount = o.num("amount"),
            mode = o.str("mode", fallback = "UPI"),
            utrOrRef = o.str("utrOrRef", "utr_or_ref"),
            upiApp = o.str("upiApp", "upi_app"),
            status = o.str("status", fallback = "PENDING"),
            receiptNo = o.str("receiptNo", "receipt_no"),
            rejectReason = o.str("rejectReason", "reject_reason"),
            verifiedBy = o.str("verifiedBy", "verified_by"),
            verifiedAtStr = o.str("verifiedAtStr", "verified_at_str"),
            createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
        )
    }

    private fun parseDoubt(o: JSONObject): DoubtMessage = DoubtMessage(
        id = o.str("id"),
        studentDocId = o.str("studentDocId", "student_doc_id"),
        studentName = o.str("studentName", "student_name"),
        className = o.str("className", "class_name"),
        subject = o.str("subject"),
        message = o.str("message"),
        reply = o.str("reply"),
        repliedBy = o.str("repliedBy", "replied_by"),
        status = o.str("status", fallback = "OPEN"),
        dateLabel = o.str("dateLabel", "date_label"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseCraftProject(o: JSONObject): CraftProject = CraftProject(
        id = o.str("id"),
        title = o.str("title"),
        category = o.str("category", fallback = "Art & Craft"),
        description = o.str("description"),
        materials = o.str("materials"),
        authorDocId = o.str("authorDocId", "author_doc_id"),
        authorName = o.str("authorName", "author_name"),
        authorUserId = o.str("authorUserId", "author_user_id"),
        authorRole = o.str("authorRole", "author_role", "STUDENT"),
        classInfo = o.str("classInfo", "class_info"),
        dateLabel = o.str("dateLabel", "date_label"),
        aiImageData = o.str("aiImageData", "ai_image_data"),
        aiPromptUsed = o.str("aiPromptUsed", "ai_prompt_used"),
        likesCount = o.num("likesCount", "likes_count"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseCampusPoster(o: JSONObject): CampusPoster = CampusPoster(
        id = o.str("id"),
        title = o.str("title"),
        subtitle = o.str("subtitle"),
        date = o.str("date"),
        imageData = o.str("imageData", "image_data"),
        postedBy = o.str("postedBy", "posted_by"),
        createdAt = epochToTimestamp(o.num("createdAt", "created_at")),
    )

    private fun parseTimetable(o: JSONObject): ClassTimetable {
        val arr = o.optJSONArray("slots") ?: runCatching {
            JSONArray(o.str("slotsJson", "slots_json", "[]"))
        }.getOrElse { JSONArray() }
        val slots = buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                add(buildMap {
                    val keys = s.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, s.optString(k))
                    }
                })
            }
        }
        return ClassTimetable(
            id = o.str("id"),
            className = o.str("className", "class_name"),
            section = o.str("section"),
            slots = slots,
            updatedBy = o.str("updatedBy", "updated_by"),
            updatedAt = epochToTimestamp(o.num("updatedAt", "updated_at")),
        )
    }
}
