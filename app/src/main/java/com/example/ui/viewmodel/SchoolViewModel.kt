package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.preferences.SavedRoleSession
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.SchoolRepository
import com.example.ui.components.encodeBitmapToBase64
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

data class AiChatMessage(
    val isUser: Boolean,
    val text: String,
)

enum class AiTutorPersona(val label: String, val systemRole: String) {
    STUDY_TUTOR(
        label = "CBSE Study Tutor",
        systemRole = "You are a patient, crystal-clear CBSE Study Tutor for Doon Public School, Etah. Explain Maths, Science, English, and Social Science concepts step-by-step aligned to NCERT."
    ),
    CRAFT_MENTOR(
        label = "Craft & Project Mentor",
        systemRole = "You are a creative Art, Craft & STEM Exhibition Mentor for Doon Public School, Etah. Suggest safe low-cost materials, step-by-step model building guides, and science fair presentation tips."
    ),
    DPT_COACH(
        label = "DPT Quiz Coach",
        systemRole = "You are a fast-paced Daily Practice Test (DPT) & Board Exam Coach for Doon Public School, Etah. Generate concise practice MCQs, highlight common mistakes, and test student understanding."
    ),
}

sealed interface AiTutorUiState {
    data object Idle : AiTutorUiState
    data object Loading : AiTutorUiState
    data class Streaming(val partialText: String) : AiTutorUiState
    data class Error(val message: String) : AiTutorUiState
}

sealed interface CraftAiImageState {
    data object Idle : CraftAiImageState
    data object Loading : CraftAiImageState
    data class Success(val base64Data: String, val promptUsed: String) : CraftAiImageState
    data class Error(val message: String) : CraftAiImageState
}

class SchoolViewModel(
    private val repository: SchoolRepository,
    private val ownerUid: String,
    private val sessionPrefs: ThemePreferences,
) : ViewModel() {

    private val _syncGeneration = MutableStateFlow(0)
    private var autoRetryCount = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeStateFlow(
        label: String,
        flowProvider: () -> Flow<List<T>>,
    ): StateFlow<DataUiState<List<T>>> =
        _syncGeneration
            .flatMapLatest {
                flowProvider()
                    .map<List<T>, DataUiState<List<T>>> { DataUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing $label", rootError)
                        emit(DataUiState.Error(rootError.message ?: "Unable to connect to $label"))
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading,
            )

    val usersState: StateFlow<DataUiState<List<SchoolUser>>> = observeStateFlow("users") { repository.observeUsers(ownerUid) }
    val gatewayState: StateFlow<DataUiState<List<GatewayConfig>>> = observeStateFlow("gatewayConfig") { repository.observeGatewayConfig(ownerUid) }
    val circularsState: StateFlow<DataUiState<List<Circular>>> = observeStateFlow("circulars") { repository.observeCirculars(ownerUid) }
    val dptDailyState: StateFlow<DataUiState<List<DptDaily>>> = observeStateFlow("dptDaily") { repository.observeDptDaily(ownerUid) }
    val dptWeeklyState: StateFlow<DataUiState<List<DptWeekly>>> = observeStateFlow("dptWeekly") { repository.observeDptWeekly(ownerUid) }
    val attendanceState: StateFlow<DataUiState<List<AttendanceRecord>>> = observeStateFlow("attendance") { repository.observeAttendance(ownerUid) }
    val leavesState: StateFlow<DataUiState<List<LeaveRequest>>> = observeStateFlow("leaves") { repository.observeLeaves(ownerUid) }
    val homeworkState: StateFlow<DataUiState<List<HomeworkItem>>> = observeStateFlow("homework") { repository.observeHomework(ownerUid) }
    val termMarksState: StateFlow<DataUiState<List<TermMarkCard>>> = observeStateFlow("termMarks") { repository.observeTermMarks(ownerUid) }
    val feeItemsState: StateFlow<DataUiState<List<FeeItem>>> = observeStateFlow("feeItems") { repository.observeFeeItems(ownerUid) }
    val paymentsState: StateFlow<DataUiState<List<FeePayment>>> = observeStateFlow("payments") { repository.observePayments(ownerUid) }
    val doubtsState: StateFlow<DataUiState<List<DoubtMessage>>> = observeStateFlow("doubts") { repository.observeDoubts(ownerUid) }
    val craftProjectsState: StateFlow<DataUiState<List<CraftProject>>> = observeStateFlow("craftProjects") { repository.observeCraftProjects(ownerUid) }

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isSeeding = MutableStateFlow(true)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    private sealed interface SessionRestoreState {
        data object Checking : SessionRestoreState
        data class Ready(val session: SavedRoleSession?) : SessionRestoreState
    }

    private val sessionRestoreState: StateFlow<SessionRestoreState> =
        sessionPrefs.savedRoleSession(ownerUid)
            .map<SavedRoleSession?, SessionRestoreState> { SessionRestoreState.Ready(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SessionRestoreState.Checking)

    val isRestoringSavedSession: StateFlow<Boolean> =
        combine(sessionRestoreState, usersState, _isSeeding) { restoreState, usersUi, seeding ->
            when (restoreState) {
                is SessionRestoreState.Checking -> true
                is SessionRestoreState.Ready ->
                    restoreState.session != null && (usersUi is DataUiState.Loading || seeding)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _activeSchoolUserDocId = MutableStateFlow<String?>(null)
    val activeSchoolUser: StateFlow<SchoolUser?> =
        combine(_activeSchoolUserDocId, sessionRestoreState, usersState) { manualDocId, restoreState, usersUi ->
            val list = (usersUi as? DataUiState.Success)?.data ?: return@combine null
            if (manualDocId != null) {
                list.firstOrNull { it.id == manualDocId }
            } else if (restoreState is SessionRestoreState.Ready && restoreState.session != null) {
                val persisted = restoreState.session
                list.firstOrNull { persisted.docId.isNotBlank() && it.id == persisted.docId }
                    ?: list.firstOrNull {
                        it.role == persisted.role &&
                            it.userId.trim().equals(persisted.userId.trim(), ignoreCase = true)
                    }
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    // Gemini Multi-Turn AI Study Tutor & Mentor State
    private val _aiPersona = MutableStateFlow(AiTutorPersona.STUDY_TUTOR)
    val aiPersona: StateFlow<AiTutorPersona> = _aiPersona.asStateFlow()

    private fun buildAiModel(persona: AiTutorPersona) = Firebase.ai.generativeModel(
        modelName = "gemini-3.8-flash",
        systemInstruction = content {
            text(
                """
                ${persona.systemRole}
                School Profile: ${SchoolInfo.NAME} (${SchoolInfo.AFFILIATION}).
                Trust: ${SchoolInfo.TRUST}. Motto: "${SchoolInfo.MOTTO}".
                Address: ${SchoolInfo.ADDRESS}. Phones: ${SchoolInfo.PHONES}. Email: ${SchoolInfo.EMAIL}.
                4 Academic Wings:
                1. Doon Kidz (Pre-Primary: Nursery, LKG, UKG)
                2. Lower School (Primary: Classes I to V)
                3. Middle School (Upper Primary: Classes VI to VIII)
                4. Upper School (Senior Secondary: Classes IX to XII)
                Always respond accurately, concisely, and encourage student curiosity.
                """.trimIndent()
            )
        }
    )

    private val craftImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            },
        )
    }

    private val _aiMessages = MutableStateFlow(
        listOf(
            AiChatMessage(
                isUser = false,
                text = "Namaste! I am your DPS Etah Gemini Assistant. Pick a role above (CBSE Study Tutor, Craft Mentor, or DPT Coach) and let's chat!"
            )
        )
    )
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _aiTutorState = MutableStateFlow<AiTutorUiState>(AiTutorUiState.Idle)
    val aiTutorState: StateFlow<AiTutorUiState> = _aiTutorState.asStateFlow()

    private val _craftAiImageState = MutableStateFlow<CraftAiImageState>(CraftAiImageState.Idle)
    val craftAiImageState: StateFlow<CraftAiImageState> = _craftAiImageState.asStateFlow()

    private val _generatingCraftPostId = MutableStateFlow<String?>(null)
    val generatingCraftPostId: StateFlow<String?> = _generatingCraftPostId.asStateFlow()

    init {
        viewModelScope.launch {
            _isSeeding.value = true
            try {
                Firebase.auth.currentUser?.getIdToken(true)?.await()
            } catch (_: Exception) {
            }
            var seedResult = repository.seedDemoDataIfEmpty(ownerUid)
            if (seedResult.isFailure) {
                delay(1200L)
                try {
                    Firebase.auth.currentUser?.getIdToken(true)?.await()
                } catch (_: Exception) {
                }
                _syncGeneration.value += 1
                seedResult = repository.seedDemoDataIfEmpty(ownerUid)
            }
            seedResult.onFailure { e ->
                Log.w(TAG, "Initial seed retry failed", e)
            }
            _isSeeding.value = false
        }

        // Automatically recover if any transient auth/network error occurs right after sign-in
        viewModelScope.launch {
            usersState.collect { state ->
                if (state is DataUiState.Error && autoRetryCount < 2) {
                    autoRetryCount += 1
                    delay(1200L * autoRetryCount)
                    retryCloudSync(userInitiated = false)
                } else if (state is DataUiState.Success) {
                    autoRetryCount = 0
                }
            }
        }
    }

    fun retryCloudSync(userInitiated: Boolean = true) {
        viewModelScope.launch {
            _isSeeding.value = true
            try {
                Firebase.auth.currentUser?.getIdToken(true)?.await()
            } catch (_: Exception) {
            }
            val result = repository.seedDemoDataIfEmpty(ownerUid)
            _syncGeneration.value += 1
            _isSeeding.value = false
            if (userInitiated) {
                result
                    .onSuccess { _toastMessage.value = "Connected to Doon Public School portal." }
                    .onFailure { _toastMessage.value = "Please check your internet connection and tap Retry." }
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showMessage(msg: String) {
        _toastMessage.value = msg
    }

    fun loginSchoolRole(role: String, userId: String, password: String): String? {
        val cleanId = userId.trim()
        val cleanPw = password.trim()
        if (cleanId.isEmpty() || cleanPw.isEmpty()) {
            return "Please enter both your ID (e.g. Roll / Admission No) and Password."
        }

        when (val state = usersState.value) {
            is DataUiState.Loading -> {
                return "Connecting to school portal. Please wait 2 seconds and tap Sign In again."
            }
            is DataUiState.Error -> {
                retryCloudSync(userInitiated = false)
                return "Refreshing school connection... Please wait 2 seconds and tap Sign In again."
            }
            is DataUiState.Success -> {
                val users = state.data
                if (users.isEmpty()) {
                    retryCloudSync(userInitiated = false)
                    return "Setting up school portal for your Google account. Please tap Sign In again in 2 seconds."
                }

                val match = users.firstOrNull {
                    it.role == role &&
                        it.userId.trim().equals(cleanId, ignoreCase = true) &&
                        it.password.trim() == cleanPw
                }
                if (match != null) {
                    _activeSchoolUserDocId.value = match.id
                    viewModelScope.launch {
                        sessionPrefs.saveRoleSession(
                            ownerUid = ownerUid,
                            docId = match.id,
                            userId = match.userId.trim(),
                            role = match.role,
                        )
                    }
                    return null
                }

                val sameIdSameRole = users.firstOrNull {
                    it.role == role && it.userId.trim().equals(cleanId, ignoreCase = true)
                }
                if (sameIdSameRole != null) {
                    return "Incorrect password for '$cleanId'. Please check spelling/capitals or contact school office."
                }

                val sameIdOtherRole = users.firstOrNull {
                    it.userId.trim().equals(cleanId, ignoreCase = true)
                }
                if (sameIdOtherRole != null) {
                    val roleLabel = when (sameIdOtherRole.role) {
                        "ADMIN" -> "Principal"
                        "TEACHER" -> "Teacher"
                        "FEE_MANAGER" -> "Accounts"
                        else -> "Student"
                    }
                    return "'$cleanId' is a $roleLabel ID. Please select the $roleLabel button above and tap Sign In."
                }

                return "ID '$cleanId' was not found. Parents: please enter the Admission No given by the school office (for example, DPS2026-001)."
            }
        }
    }

    fun logoutSchoolRole() {
        _activeSchoolUserDocId.value = null
        viewModelScope.launch {
            sessionPrefs.clearRoleSession()
        }
    }

    // --- Profile Editing for ALL Roles (Admin, Teacher, Fee Manager, Student) ---
    fun updateMyProfile(userDocId: String, newFullName: String, newPhone: String, newPassword: String) {
        if (newFullName.isBlank() || newPhone.isBlank() || newPassword.isBlank()) {
            _toastMessage.value = "Full name, phone number, and password cannot be empty."
            return
        }
        viewModelScope.launch {
            repository.updateUserProfile(userDocId, newFullName, newPhone, newPassword)
                .onSuccess {
                    _toastMessage.value = "Name, phone number & password updated successfully!"
                }
                .onFailure { e ->
                    _toastMessage.value = e.localizedMessage ?: "Failed to update profile."
                }
        }
    }

    // --- Admin Actions ---
    fun saveUserAccount(user: SchoolUser) {
        viewModelScope.launch {
            val allUsers = (usersState.value as? DataUiState.Success)?.data ?: emptyList()
            val dup = allUsers.firstOrNull {
                it.userId.equals(user.userId.trim(), ignoreCase = true) && it.id != user.id
            }
            if (dup != null) {
                _toastMessage.value = "User ID '${user.userId}' already exists!"
                return@launch
            }
            repository.saveUser(user)
                .onSuccess { _toastMessage.value = "${user.role.replace('_', ' ')} account saved & live!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save account." }
        }
    }

    fun deleteRecord(collectionName: String, docId: String, label: String = "Record") {
        viewModelScope.launch {
            repository.deleteDocument(collectionName, docId)
                .onSuccess { _toastMessage.value = "$label deleted." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Delete failed." }
        }
    }

    fun postCircular(circular: Circular) {
        viewModelScope.launch {
            repository.addCircular(circular)
                .onSuccess { _toastMessage.value = "Principal Circular published to all roles!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post circular." }
        }
    }

    // --- Teacher Actions ---
    fun saveDailyDpt(dpt: DptDaily) {
        viewModelScope.launch {
            repository.saveDptDaily(dpt)
                .onSuccess { _toastMessage.value = "Daily DPT status published live!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save DPT status." }
        }
    }

    fun saveWeeklyDptMarks(entries: List<DptWeekly>) {
        viewModelScope.launch {
            var failed: Throwable? = null
            for (entry in entries) {
                repository.saveDptWeekly(entry).onFailure { failed = it }
            }
            if (failed == null) {
                _toastMessage.value = "Weekly DPT marks saved & class ranks updated!"
            } else {
                _toastMessage.value = failed?.localizedMessage ?: "Failed saving some weekly marks."
            }
        }
    }

    fun markStudentAttendance(record: AttendanceRecord) {
        viewModelScope.launch {
            repository.markAttendance(record)
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to mark attendance." }
        }
    }

    fun markAllPresent(students: List<SchoolUser>, date: String, className: String, section: String, markedBy: String) {
        viewModelScope.launch {
            var failed: Throwable? = null
            var updatedCount = 0
            students.forEach { st ->
                repository.markAttendance(
                    AttendanceRecord(
                        date = date,
                        className = className,
                        section = section,
                        studentDocId = st.id,
                        status = "Present",
                        markedBy = markedBy,
                    )
                ).onSuccess { updatedCount++ }.onFailure { failed = it }
            }
            _toastMessage.value = if (failed == null) {
                "Marked $updatedCount students Present for $date"
            } else {
                failed?.localizedMessage ?: "Failed to mark attendance for some students."
            }
        }
    }

    fun updateLeaveStatus(leaveId: String, status: String, remark: String) {
        viewModelScope.launch {
            repository.updateLeaveStatus(leaveId, status, remark)
                .onSuccess { _toastMessage.value = "Leave marked $status." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update leave." }
        }
    }

    fun postHomework(hw: HomeworkItem) {
        viewModelScope.launch {
            repository.addHomework(hw)
                .onSuccess { _toastMessage.value = "Homework published to student diary!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post homework." }
        }
    }

    fun publishTermMarks(card: TermMarkCard) {
        viewModelScope.launch {
            repository.saveTermMarkCard(card)
                .onSuccess { _toastMessage.value = "Term report card published for ${card.studentName}!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish term marks." }
        }
    }

    fun replyDoubt(doubtId: String, reply: String, repliedBy: String) {
        viewModelScope.launch {
            repository.replyDoubt(doubtId, reply, repliedBy)
                .onSuccess { _toastMessage.value = "Reply sent to student/parent!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to send reply." }
        }
    }

    // --- Fee Manager Actions ---
    fun saveGatewayConfig(config: GatewayConfig) {
        viewModelScope.launch {
            repository.saveGatewayConfig(config)
                .onSuccess { _toastMessage.value = "Merchant UPI & Payment Gateway saved live!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save gateway config." }
        }
    }

    fun assignFeeDemands(
        targets: List<SchoolUser>,
        feeHead: String,
        month: String,
        customAmount: Long,
        useStudentDefaultIfBulk: Boolean,
    ) {
        viewModelScope.launch {
            var count = 0
            for (st in targets) {
                val amt = if (useStudentDefaultIfBulk) {
                    when (feeHead) {
                        "Monthly Tuition Fee" -> if (st.monthlyTuition > 0) st.monthlyTuition else customAmount
                        "Transport / Bus Fee" -> st.transportFee
                        else -> customAmount
                    }
                } else {
                    customAmount
                }
                if (amt <= 0L) continue
                repository.addFeeItem(
                    FeeItem(
                        studentDocId = st.id,
                        title = "$feeHead ($month)",
                        month = month,
                        amount = amt,
                        status = "UNPAID",
                        paymentId = "",
                    )
                )
                count++
            }
            _toastMessage.value = "Assigned fee demand to $count student(s)."
        }
    }

    fun verifyPayment(payment: FeePayment, verifierName: String, verifiedAtStr: String, existingVerifiedCount: Int) {
        viewModelScope.launch {
            val receiptNo = "DPS-RCP-2026-${(existingVerifiedCount + 102).toString().padStart(4, '0')}"
            repository.verifyPayment(payment, receiptNo, verifierName, verifiedAtStr)
                .onSuccess { _toastMessage.value = "Verified! Official Receipt $receiptNo issued." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to verify payment." }
        }
    }

    fun rejectPayment(payment: FeePayment, reason: String, verifierName: String, verifiedAtStr: String) {
        viewModelScope.launch {
            repository.rejectPayment(payment, reason.ifBlank { "UTR did not match bank ledger" }, verifierName, verifiedAtStr)
                .onSuccess { _toastMessage.value = "Payment rejected & dues restored to UNPAID." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to reject payment." }
        }
    }

    // --- Student Actions ---
    fun submitPaymentProof(
        student: SchoolUser,
        selectedFees: List<FeeItem>,
        mode: String,
        utrOrRef: String,
        upiApp: String,
    ) {
        if (selectedFees.isEmpty()) {
            _toastMessage.value = "Select at least one pending fee head."
            return
        }
        val cleanRef = utrOrRef.trim()
        if (cleanRef.length < 8) {
            _toastMessage.value = "Enter a valid 12-digit UPI UTR / Payment Reference ID."
            return
        }
        val total = selectedFees.sumOf { it.amount }
        viewModelScope.launch {
            repository.submitPaymentProof(
                payment = FeePayment(
                    studentDocId = student.id,
                    studentName = student.fullName,
                    admissionNo = student.userId,
                    className = "${student.className}-${student.section}",
                    feeItemIds = selectedFees.map { it.id },
                    feeSummary = selectedFees.joinToString(", ") { it.title },
                    amount = total,
                    mode = mode,
                    utrOrRef = cleanRef,
                    upiApp = upiApp,
                    status = "PENDING",
                ),
                feeItemIds = selectedFees.map { it.id },
            )
                .onSuccess { _toastMessage.value = "UTR submitted! Fee Manager will verify & issue your receipt." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Submission failed." }
        }
    }

    fun submitLeave(leave: LeaveRequest) {
        viewModelScope.launch {
            repository.submitLeave(leave)
                .onSuccess { _toastMessage.value = "Leave application sent to Class Teacher!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to submit leave." }
        }
    }

    fun submitDoubt(doubt: DoubtMessage) {
        viewModelScope.launch {
            repository.submitDoubt(doubt)
                .onSuccess { _toastMessage.value = "Doubt posted to teacher helpdesk!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post doubt." }
        }
    }

    // --- Craft & Project Skill AI Studio (Firebase AI Logic Nano Banana 2.1 + Firestore) ---

    fun selectAiPersona(persona: AiTutorPersona) {
        _aiPersona.value = persona
    }

    fun clearAiChatHistory() {
        _aiMessages.value = listOf(
            AiChatMessage(
                isUser = false,
                text = "New conversation started with ${_aiPersona.value.label}. Ask anything!"
            )
        )
        _aiTutorState.value = AiTutorUiState.Idle
    }

    fun clearCraftAiImageState() {
        _craftAiImageState.value = CraftAiImageState.Idle
    }

    private fun buildCraftVisualPrompt(
        title: String,
        category: String,
        description: String,
        materials: String,
    ): String = buildString {
        append("Studio exhibition photograph of a creative school $category craft/project model titled \"$title\" for Doon Public School, Etah. ")
        if (description.isNotBlank()) append("Design details: ${description.trim()}. ")
        if (materials.isNotBlank()) append("Crafted from materials: ${materials.trim()}. ")
        append("Displayed neatly on a bright wooden classroom science & art fair table, vivid educational craftsmanship, natural daylight, sharp focus, no text overlays.")
    }.take(580)

    fun generateCraftAiImage(
        title: String,
        category: String,
        description: String,
        materials: String,
    ) {
        if (title.isBlank() && description.isBlank()) {
            _craftAiImageState.value = CraftAiImageState.Error("Enter a craft/project title and idea description first.")
            return
        }
        val prompt = buildCraftVisualPrompt(title.trim(), category.trim(), description.trim(), materials.trim())
        viewModelScope.launch {
            _craftAiImageState.value = CraftAiImageState.Loading
            try {
                val bitmap = withTimeout(60_000L) {
                    val response = craftImageModel.generateContent(prompt)
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                        ?.image
                }
                if (bitmap != null) {
                    val base64 = withContext(Dispatchers.Default) {
                        encodeBitmapToBase64(bitmap)
                    }
                    _craftAiImageState.value = CraftAiImageState.Success(base64Data = base64, promptUsed = prompt)
                } else {
                    _craftAiImageState.value = CraftAiImageState.Error("School AI model did not return an image. Please refine your craft description and try again.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Craft AI image generation timed out", e)
                _craftAiImageState.value = CraftAiImageState.Error("Image generation timed out after 60 seconds. Please retry.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Craft AI image quota exceeded", e)
                _craftAiImageState.value = CraftAiImageState.Error("AI image quota/rate limit reached. Please try again shortly.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Craft AI image generation failed", e)
                _craftAiImageState.value = CraftAiImageState.Error(e.localizedMessage ?: "Failed to generate craft AI image.")
            }
        }
    }

    fun generateAiImageForExistingCraft(project: CraftProject) {
        if (_generatingCraftPostId.value != null) return
        val prompt = buildCraftVisualPrompt(project.title, project.category, project.description, project.materials)
        viewModelScope.launch {
            _generatingCraftPostId.value = project.id
            try {
                val bitmap = withTimeout(60_000L) {
                    val response = craftImageModel.generateContent(prompt)
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                        ?.image
                }
                if (bitmap != null) {
                    val base64 = withContext(Dispatchers.Default) {
                        encodeBitmapToBase64(bitmap)
                    }
                    repository.updateCraftProjectAiImage(project.id, base64, prompt)
                        .onSuccess { _toastMessage.value = "School AI concept image added to \"${project.title}\"!" }
                        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed saving AI image." }
                } else {
                    _toastMessage.value = "School AI model returned no image. Please try again."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Craft post AI image timed out", e)
                _toastMessage.value = "AI image generation timed out after 60s. Please retry."
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Craft post AI quota exceeded", e)
                _toastMessage.value = "AI image quota exceeded. Please try again later."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Craft post AI image failed", e)
                _toastMessage.value = e.localizedMessage ?: "AI image generation failed."
            } finally {
                _generatingCraftPostId.value = null
            }
        }
    }

    fun publishCraftProject(project: CraftProject) {
        viewModelScope.launch {
            repository.addCraftProject(project)
                .onSuccess {
                    _craftAiImageState.value = CraftAiImageState.Idle
                    _toastMessage.value = "Craft Idea & Project Skill published to School Showcase!"
                }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish craft idea." }
        }
    }

    fun applaudCraftProject(project: CraftProject) {
        viewModelScope.launch {
            repository.likeCraftProject(project.id, project.likesCount)
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Could not update applause." }
        }
    }

    // --- Firebase AI Logic (Multi-Turn Streaming Gemini 3.8 Flash Chat) ---
    fun askAiTutor(student: SchoolUser, question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _aiTutorState.value is AiTutorUiState.Loading || _aiTutorState.value is AiTutorUiState.Streaming) {
            return
        }

        val priorHistory = _aiMessages.value.drop(1).map { msg ->
            content(if (msg.isUser) "user" else "model") {
                text(msg.text)
            }
        }

        _aiMessages.value = _aiMessages.value + AiChatMessage(isUser = true, text = trimmed)
        _aiTutorState.value = AiTutorUiState.Loading

        viewModelScope.launch {
            val classDesc = if (student.className.isNotBlank()) "${student.className}-${student.section}" else student.wing
            val fullPrompt = "Student: ${student.fullName} ($classDesc). Question: $trimmed"
            val sb = StringBuilder()
            try {
                val activeModel = buildAiModel(_aiPersona.value)
                val chatSession = activeModel.startChat(history = priorHistory)
                withTimeout(60_000L) {
                    chatSession.sendMessageStream(fullPrompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            sb.append(piece)
                            _aiTutorState.value = AiTutorUiState.Streaming(sb.toString())
                        }
                    }
                }
                val finalAnswer = sb.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    _aiMessages.value = _aiMessages.value + AiChatMessage(isUser = false, text = finalAnswer)
                    _aiTutorState.value = AiTutorUiState.Idle
                } else {
                    _aiTutorState.value = AiTutorUiState.Error("AI Tutor returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini AI request timed out", e)
                _aiTutorState.value = AiTutorUiState.Error("Request timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI Logic error", e)
                _aiTutorState.value = AiTutorUiState.Error(e.localizedMessage ?: "Failed to reach Gemini AI.")
            }
        }
    }

    private companion object {
        const val TAG = "SchoolViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
