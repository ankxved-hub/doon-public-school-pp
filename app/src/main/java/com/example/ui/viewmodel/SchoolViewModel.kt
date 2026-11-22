package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.*
import com.example.data.model.SchoolCatalog.toSlotMap
import com.example.data.preferences.SavedRoleSession
import com.example.data.preferences.ThemePreferences
import com.example.data.remote.CloudflareApiClient
import com.example.data.remote.CloudflareDashboardData
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

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Int) : AppUpdateState
    data class UpdateAvailable(
        val latestVersionName: String,
        val latestVersionCode: Long,
        val downloadUrl: String,
        val releaseNotes: String,
    ) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

class SchoolViewModel(
    private val repository: SchoolRepository,
    private val ownerUid: String,
    private val sessionPrefs: ThemePreferences,
) : ViewModel() {

    val isCloudflareMode: Boolean = CloudflareApiClient.isEnabled()

    private val _syncGeneration = MutableStateFlow(0)
    private val _isMemberReady = MutableStateFlow(false)
    val isMemberReady: StateFlow<Boolean> = _isMemberReady.asStateFlow()
    private var autoRetryCount = 0

    private val _cfToken = MutableStateFlow<String?>(null)
    private val _cfDashboard = MutableStateFlow<CloudflareDashboardData?>(null)
    private val _needsCloudflareAdminSetup = MutableStateFlow(false)
    val needsCloudflareAdminSetup: StateFlow<Boolean> = _needsCloudflareAdminSetup.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeStateFlow(
        label: String,
        cfSelector: (CloudflareDashboardData) -> List<T>,
        flowProvider: () -> Flow<List<T>>,
    ): StateFlow<DataUiState<List<T>>> =
        if (isCloudflareMode) {
            combine(_isMemberReady, _cfDashboard) { ready, dash ->
                if (!ready || dash == null) {
                    DataUiState.Success(emptyList())
                } else {
                    DataUiState.Success(cfSelector(dash))
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading,
            )
        } else {
            combine(_isMemberReady, _syncGeneration) { ready, gen -> ready to gen }
                .flatMapLatest { (ready, _) ->
                    if (!ready) {
                        flowOf<DataUiState<List<T>>>(DataUiState.Success(emptyList()))
                    } else {
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
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = DataUiState.Loading,
                )
        }

    val usersState: StateFlow<DataUiState<List<SchoolUser>>> = observeStateFlow("users", { it.users }) { repository.observeUsers() }
    val gatewayState: StateFlow<DataUiState<List<GatewayConfig>>> = observeStateFlow("gatewayConfig", { listOf(it.gateway) }) { repository.observeGatewayConfig() }
    val circularsState: StateFlow<DataUiState<List<Circular>>> = observeStateFlow("circulars", { it.circulars }) { repository.observeCirculars() }
    val dptDailyState: StateFlow<DataUiState<List<DptDaily>>> = observeStateFlow("dptDaily", { it.dptDaily }) { repository.observeDptDaily() }
    val dptWeeklyState: StateFlow<DataUiState<List<DptWeekly>>> = observeStateFlow("dptWeekly", { it.dptWeekly }) { repository.observeDptWeekly() }
    val attendanceState: StateFlow<DataUiState<List<AttendanceRecord>>> = observeStateFlow("attendance", { it.attendance }) { repository.observeAttendance() }
    val leavesState: StateFlow<DataUiState<List<LeaveRequest>>> = observeStateFlow("leaves", { it.leaves }) { repository.observeLeaves() }
    val homeworkState: StateFlow<DataUiState<List<HomeworkItem>>> = observeStateFlow("homework", { it.homework }) { repository.observeHomework() }
    val termMarksState: StateFlow<DataUiState<List<TermMarkCard>>> = observeStateFlow("termMarks", { it.termMarks }) { repository.observeTermMarks() }
    val feeItemsState: StateFlow<DataUiState<List<FeeItem>>> = observeStateFlow("feeItems", { it.feeItems }) { repository.observeFeeItems() }
    val paymentsState: StateFlow<DataUiState<List<FeePayment>>> = observeStateFlow("payments", { it.payments }) { repository.observePayments() }
    val doubtsState: StateFlow<DataUiState<List<DoubtMessage>>> = observeStateFlow("doubts", { it.doubts }) { repository.observeDoubts() }
    val craftProjectsState: StateFlow<DataUiState<List<CraftProject>>> = observeStateFlow("craftProjects", { it.craftProjects }) { repository.observeCraftProjects() }
    val craftLikesState: StateFlow<DataUiState<List<CraftLike>>> = observeStateFlow("craftLikes", { it.craftLikes }) { repository.observeCraftLikes() }
    val campusPostersState: StateFlow<DataUiState<List<CampusPoster>>> = observeStateFlow("campusPosters", { it.campusPosters }) { repository.observeCampusPosters() }
    val timetablesState: StateFlow<DataUiState<List<ClassTimetable>>> = observeStateFlow("timetables", { it.timetables }) { repository.observeTimetables() }

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isSeeding = MutableStateFlow(true)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    private val _likingProjectIds = MutableStateFlow<Set<String>>(emptySet())
    val likingProjectIds: StateFlow<Set<String>> = _likingProjectIds.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    private sealed interface SessionRestoreState {
        data object Checking : SessionRestoreState
        data class Ready(val session: SavedRoleSession?) : SessionRestoreState
    }

    private val sessionRestoreState: StateFlow<SessionRestoreState> =
        sessionPrefs.savedRoleSession(ownerUid)
            .map<SavedRoleSession?, SessionRestoreState> { SessionRestoreState.Ready(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SessionRestoreState.Checking)

    private val _activeSchoolUserDocId = MutableStateFlow<String?>(null)
    private val _activeRoleOverride = MutableStateFlow<String?>(null)
    val activeSchoolUser: StateFlow<SchoolUser?> =
        if (isCloudflareMode) {
            combine(_cfDashboard, _isMemberReady) { dash, ready ->
                if (!ready) null else dash?.currentUser
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
        } else {
            combine(_activeSchoolUserDocId, _activeRoleOverride, sessionRestoreState, usersState, _isMemberReady) { manualDocId, roleOverride, restoreState, usersUi, memberReady ->
                if (!memberReady) return@combine null
                val list = (usersUi as? DataUiState.Success)?.data ?: return@combine null
                val persisted = (restoreState as? SessionRestoreState.Ready)?.session
                val matched = if (manualDocId != null) {
                    list.firstOrNull { it.id == manualDocId }
                } else if (persisted != null) {
                    list.firstOrNull { persisted.docId.isNotBlank() && it.id == persisted.docId }
                        ?: list.firstOrNull {
                            persisted.role in it.effectiveRoles() &&
                                it.userId.trim().equals(persisted.userId.trim(), ignoreCase = true)
                        }
                } else {
                    null
                }
                matched?.let { u ->
                    val effectiveList = u.effectiveRoles()
                    val resolvedRole = when {
                        roleOverride != null && roleOverride in effectiveList -> roleOverride
                        persisted != null && persisted.role in effectiveList -> persisted.role
                        else -> u.role
                    }
                    u.copy(
                        role = resolvedRole,
                        primaryRole = u.primaryRole.ifBlank { u.role },
                        roles = effectiveList,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
        }

    val isRestoringSavedSession: StateFlow<Boolean> =
        combine(sessionRestoreState, usersState, _isSeeding, _isMemberReady) { restoreState, usersUi, seeding, memberReady ->
            if (isCloudflareMode) {
                seeding && restoreState is SessionRestoreState.Ready && restoreState.session?.cloudflareToken?.isNotBlank() == true
            } else {
                when (restoreState) {
                    is SessionRestoreState.Checking -> true
                    is SessionRestoreState.Ready ->
                        restoreState.session != null && (seeding || (memberReady && usersUi is DataUiState.Loading))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val likedProjectIds: StateFlow<Set<String>> =
        combine(craftLikesState, activeSchoolUser) { likesUi, user ->
            val currentDocId = user?.id.orEmpty()
            if (currentDocId.isBlank()) return@combine emptySet()
            val likes = (likesUi as? DataUiState.Success)?.data ?: return@combine emptySet()
            likes.filter { it.likerId == currentDocId }.map { it.projectId }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    // Gemini Multi-Turn AI Study Tutor & Mentor State (retained for signature compatibility)
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
                text = "Namaste! Welcome to Doon Public School, Etah."
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

    private suspend fun refreshCloudflareDashboard(tokenOverride: String? = null): Result<CloudflareDashboardData> {
        val tok = tokenOverride ?: _cfToken.value
        if (tok.isNullOrBlank()) return Result.failure(IllegalStateException("Not signed in"))
        val res = CloudflareApiClient.fetchDashboard(tok)
        res.onSuccess { dash -> _cfDashboard.value = dash }
        return res
    }

    init {
        viewModelScope.launch {
            _isSeeding.value = true
            if (isCloudflareMode) {
                CloudflareApiClient.getBootstrapStatus()
                    .onSuccess { boot ->
                        _needsCloudflareAdminSetup.value = boot.needsSetup
                        checkForAppUpdate(boot.appUpdate, manual = false)
                    }
                    .onFailure { e -> Log.w(TAG, "Cloudflare bootstrap check failed", e) }

                val saved = sessionPrefs.savedRoleSession(ownerUid).first()
                if (saved != null && saved.cloudflareToken.isNotBlank()) {
                    _cfToken.value = saved.cloudflareToken
                    val dashRes = refreshCloudflareDashboard(saved.cloudflareToken)
                    if (dashRes.isSuccess) {
                        _activeSchoolUserDocId.value = dashRes.getOrThrow().currentUser.id
                        _isMemberReady.value = true
                    } else {
                        _cfToken.value = null
                        _isMemberReady.value = false
                        sessionPrefs.clearRoleSession()
                    }
                } else {
                    _isMemberReady.value = false
                }
                _isSeeding.value = false
                return@launch
            }

            try {
                Firebase.auth.currentUser?.getIdToken(true)?.await()
            } catch (_: Exception) {
            }
            var initResult = repository.ensureSchoolInitialized()
            if (initResult.isFailure) {
                delay(1200L)
                try {
                    Firebase.auth.currentUser?.getIdToken(true)?.await()
                } catch (_: Exception) {
                }
                initResult = repository.ensureSchoolInitialized()
            }
            initResult.onFailure { e ->
                Log.w(TAG, "Initial school initialization failed", e)
            }

            val memberReady = repository.isCurrentMemberReady()
            if (memberReady) {
                _isMemberReady.value = true
                _syncGeneration.value += 1
            } else {
                _isMemberReady.value = false
                // Clear any stale saved session from an earlier local install if Firestore member doc isn't present
                sessionPrefs.clearRoleSession()
            }
            _isSeeding.value = false
        }

        // Automatically recover if any transient auth/network error occurs while active member (Firestore mode)
        if (!isCloudflareMode) {
            viewModelScope.launch {
                usersState.collect { state ->
                    if (_isMemberReady.value && state is DataUiState.Error && autoRetryCount < 2) {
                        autoRetryCount += 1
                        delay(1200L * autoRetryCount)
                        retryCloudSync(userInitiated = false)
                    } else if (state is DataUiState.Success) {
                        autoRetryCount = 0
                    }
                }
            }
        }
    }

    fun retryCloudSync(userInitiated: Boolean = true) {
        viewModelScope.launch {
            _isSeeding.value = true
            if (isCloudflareMode) {
                val bootRes = CloudflareApiClient.getBootstrapStatus()
                    .onSuccess { boot ->
                        _needsCloudflareAdminSetup.value = boot.needsSetup
                        checkForAppUpdate(boot.appUpdate, manual = false)
                    }
                val tok = _cfToken.value
                if (!tok.isNullOrBlank()) {
                    val dashRes = refreshCloudflareDashboard(tok)
                    _isSeeding.value = false
                    if (userInitiated) {
                        dashRes
                            .onSuccess { _toastMessage.value = "Synced with Doon Public School Cloudflare Edge!" }
                            .onFailure { _toastMessage.value = it.localizedMessage ?: "Unable to sync with Cloudflare." }
                    }
                } else {
                    _isSeeding.value = false
                    if (userInitiated) {
                        bootRes
                            .onSuccess { _toastMessage.value = "Connected to Doon Public School Cloudflare server." }
                            .onFailure { _toastMessage.value = "Please check your internet connection and tap Reconnect." }
                    }
                }
                return@launch
            }

            try {
                Firebase.auth.currentUser?.getIdToken(true)?.await()
            } catch (_: Exception) {
            }
            val result = repository.ensureSchoolInitialized()
            val memberReady = repository.isCurrentMemberReady()
            _isMemberReady.value = memberReady
            _syncGeneration.value += 1
            _isSeeding.value = false
            if (userInitiated) {
                result
                    .onSuccess { _toastMessage.value = "Connected to Doon Public School cloud server." }
                    .onFailure { _toastMessage.value = "Please check your internet connection and tap Reconnect." }
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showMessage(msg: String) {
        _toastMessage.value = msg
    }

    suspend fun claimCloudflareAdmin(
        fullName: String,
        userId: String,
        phone: String,
        password: String,
    ): String? {
        if (!isCloudflareMode) return "Cloudflare backend is not enabled."
        val cleanPw = password.trim()
        if (cleanPw.length < 6) return "Principal password must be at least 6 characters."
        val res = CloudflareApiClient.claimAdmin(fullName, userId, phone, cleanPw)
        return res.fold(
            onSuccess = { session ->
                _cfToken.value = session.token
                _needsCloudflareAdminSetup.value = false
                refreshCloudflareDashboard(session.token)
                _activeSchoolUserDocId.value = session.user.id
                sessionPrefs.saveRoleSession(
                    ownerUid = ownerUid,
                    docId = session.user.id,
                    userId = session.user.userId.trim(),
                    role = session.user.role,
                    cloudflareToken = session.token,
                )
                _isMemberReady.value = true
                null
            },
            onFailure = { it.localizedMessage ?: "Admin setup failed." },
        )
    }

    suspend fun loginSchoolRole(role: String, userId: String, password: String): String? {
        val cleanId = userId.trim()
        val cleanPw = password.trim()
        if (cleanId.isEmpty() || cleanPw.isEmpty()) {
            return "Please enter both your ID (e.g. Roll / Admission No) and Password."
        }
        if (isCloudflareMode) {
            val res = CloudflareApiClient.login(role, cleanId, cleanPw)
            return res.fold(
                onSuccess = { session ->
                    _cfToken.value = session.token
                    val dashRes = refreshCloudflareDashboard(session.token)
                    if (dashRes.isFailure) {
                        return dashRes.exceptionOrNull()?.localizedMessage ?: "Signed in, but failed to load dashboard."
                    }
                    _activeSchoolUserDocId.value = session.user.id
                    sessionPrefs.saveRoleSession(
                        ownerUid = ownerUid,
                        docId = session.user.id,
                        userId = session.user.userId.trim(),
                        role = session.user.role,
                        cloudflareToken = session.token,
                    )
                    _isMemberReady.value = true
                    null
                },
                onFailure = { error ->
                    error.localizedMessage ?: "Invalid ID or password for ${role.replace('_', ' ')}."
                },
            )
        }

        val result = repository.loginWithCredentials(role, cleanId, cleanPw)
        return result.fold(
            onSuccess = { matchedUser ->
                _activeSchoolUserDocId.value = matchedUser.id
                _activeRoleOverride.value = matchedUser.role
                sessionPrefs.saveRoleSession(
                    ownerUid = ownerUid,
                    docId = matchedUser.id,
                    userId = matchedUser.userId.trim(),
                    role = matchedUser.role,
                )
                _isMemberReady.value = true
                _syncGeneration.value += 1
                null
            },
            onFailure = { error ->
                error.localizedMessage
                    ?: "Invalid ID or password for ${role.replace('_', ' ')}. Please check your credentials or contact school office."
            }
        )
    }

    fun switchActiveRole(targetRole: String) {
        val current = activeSchoolUser.value ?: return
        if (targetRole == current.role || targetRole !in current.effectiveRoles()) return
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.switchRole(tok, targetRole)
                    .onSuccess { session ->
                        _cfToken.value = session.token
                        refreshCloudflareDashboard(session.token)
                        sessionPrefs.saveRoleSession(
                            ownerUid = ownerUid,
                            docId = session.user.id,
                            userId = session.user.userId.trim(),
                            role = session.user.role,
                            cloudflareToken = session.token,
                        )
                        _toastMessage.value = "Switched portal to ${targetRole.replace('_', ' ')}!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Unable to switch role." }
            } else {
                repository.switchActiveMemberRole(current, targetRole)
                    .onSuccess { updated ->
                        _activeRoleOverride.value = updated.role
                        sessionPrefs.saveRoleSession(
                            ownerUid = ownerUid,
                            docId = updated.id,
                            userId = updated.userId.trim(),
                            role = updated.role,
                        )
                        _toastMessage.value = "Switched portal to ${targetRole.replace('_', ' ')}!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Unable to switch role." }
            }
        }
    }

    fun logoutSchoolRole() {
        _activeSchoolUserDocId.value = null
        _activeRoleOverride.value = null
        _isMemberReady.value = false
        _cfToken.value = null
        _cfDashboard.value = null
        viewModelScope.launch {
            sessionPrefs.clearRoleSession()
            if (!isCloudflareMode) {
                repository.clearCurrentMemberSession()
            }
        }
    }

    // --- Profile Editing for ALL Roles (Admin, Teacher, Fee Manager, Student) ---
    fun updateMyProfile(userDocId: String, newFullName: String, newPhone: String, newPassword: String) {
        if (isCloudflareMode) {
            if (newFullName.isBlank() || newPhone.isBlank()) {
                _toastMessage.value = "Full name and phone number cannot be empty."
                return
            }
            val tok = _cfToken.value ?: return
            viewModelScope.launch {
                CloudflareApiClient.updateProfile(tok, newFullName, newPhone, newPassword)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Name, phone number & password updated successfully!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update profile." }
            }
            return
        }

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

    // --- Account Management (Admin full control + Teacher strictly own class-section) ---
    fun saveUserAccount(user: SchoolUser) {
        viewModelScope.launch {
            val allUsers = (usersState.value as? DataUiState.Success)?.data ?: emptyList()
            val editor = activeSchoolUser.value
            val editorRole = editor?.role ?: "ADMIN"

            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                if (user.fullName.isBlank()) {
                    _toastMessage.value = "Full name is required."
                    return@launch
                }
                if (user.id.isBlank() && user.password.trim().length < 4) {
                    _toastMessage.value = "Password (min 4 characters) is required for new accounts."
                    return@launch
                }
                CloudflareApiClient.saveUser(tok, user)
                    .onSuccess { saved ->
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = if (saved.role == "STUDENT") {
                            "Student ${saved.fullName} (Roll ${saved.userId}) saved to Cloudflare!"
                        } else {
                            "${saved.role.replace('_', ' ')} account saved & live!"
                        }
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save account." }
                return@launch
            }

            val preparedUser = if (editorRole == "TEACHER") {
                if (editor == null || editor.className.isBlank() || editor.section.isBlank()) {
                    _toastMessage.value = "Your teacher profile does not have an assigned class & section."
                    return@launch
                }
                val autoId = if (user.id.isBlank() && user.userId.isBlank()) {
                    SchoolCatalog.nextStudentRollNumber(allUsers)
                } else user.userId.trim()
                user.copy(
                    role = "STUDENT",
                    userId = autoId,
                    wing = SchoolCatalog.wingForClass(editor.className),
                    className = editor.className,
                    section = editor.section,
                    subject = "",
                )
            } else {
                val autoId = if (user.role == "STUDENT" && user.id.isBlank() && user.userId.isBlank()) {
                    SchoolCatalog.nextStudentRollNumber(allUsers)
                } else user.userId.trim()
                user.copy(userId = autoId)
            }

            if (preparedUser.fullName.isBlank() || preparedUser.userId.isBlank() || preparedUser.password.isBlank()) {
                _toastMessage.value = "Full name, Roll/User ID, and password are required."
                return@launch
            }

            val dup = allUsers.firstOrNull {
                it.userId.equals(preparedUser.userId.trim(), ignoreCase = true) && it.id != preparedUser.id
            }
            if (dup != null) {
                _toastMessage.value = "User ID '${preparedUser.userId}' already exists!"
                return@launch
            }
            repository.saveUser(preparedUser, editorRole)
                .onSuccess {
                    _toastMessage.value = if (preparedUser.role == "STUDENT") {
                        "Student ${preparedUser.fullName} (Roll ${preparedUser.userId}) saved to cloud!"
                    } else {
                        "${preparedUser.role.replace('_', ' ')} account saved & live!"
                    }
                }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save account." }
        }
    }

    fun deleteUser(user: SchoolUser) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.deleteUser(tok, user.id)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "${user.fullName} account removed."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to remove account." }
                return@launch
            }
            repository.deleteUserAccount(user)
                .onSuccess { _toastMessage.value = "${user.fullName} account removed." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to remove account." }
        }
    }

    fun deleteRecord(collectionName: String, docId: String, label: String = "Record") {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.deleteRecord(tok, collectionName, docId)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "$label deleted."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Delete failed." }
                return@launch
            }
            repository.deleteDocument(collectionName, docId)
                .onSuccess { _toastMessage.value = "$label deleted." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Delete failed." }
        }
    }

    fun postCircular(circular: Circular) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.postCircular(tok, circular)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        val senderTag = if (circular.senderRole == "TEACHER") "Notice" else "Circular"
                        _toastMessage.value = "$senderTag published to ${circular.audience}!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish notice." }
                return@launch
            }
            repository.addCircular(circular)
                .onSuccess {
                    val senderTag = if (circular.senderRole == "TEACHER") "Notice" else "Circular"
                    _toastMessage.value = "$senderTag published to ${circular.audience}!"
                }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish notice." }
        }
    }

    fun publishCampusPoster(poster: CampusPoster) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.postCampusPoster(tok, poster)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Daily campus poster published for all students!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish campus poster." }
                return@launch
            }
            repository.addCampusPoster(poster)
                .onSuccess { _toastMessage.value = "Daily campus poster published for all students!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish campus poster." }
        }
    }

    // --- Teacher Actions ---
    fun saveDailyDpt(dpt: DptDaily) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.saveDptDaily(tok, dpt)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Daily DPT status published live!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save DPT status." }
                return@launch
            }
            repository.saveDptDaily(dpt)
                .onSuccess { _toastMessage.value = "Daily DPT status published live!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save DPT status." }
        }
    }

    fun saveWeeklyDptMarks(entries: List<DptWeekly>) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.saveDptWeeklyBatch(tok, entries)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Weekly DPT marks saved & class ranks updated!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed saving weekly marks." }
                return@launch
            }
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

    fun saveTimetable(
        className: String,
        section: String,
        slots: List<SchoolCatalog.TimetableSlot>,
        updatedBy: String,
    ) {
        viewModelScope.launch {
            val slotMaps = slots.map { it.toSlotMap() }
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.saveTimetable(tok, className, section, slotMaps)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Timetable saved for $className-$section!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save timetable." }
                return@launch
            }
            repository.saveTimetable(className, section, slotMaps, updatedBy)
                .onSuccess { _toastMessage.value = "Timetable saved for $className-$section!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save timetable." }
        }
    }

    fun markStudentAttendance(record: AttendanceRecord) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.saveAttendanceBatch(tok, listOf(record))
                    .onSuccess { refreshCloudflareDashboard(tok) }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to mark attendance." }
                return@launch
            }
            repository.markAttendance(record)
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to mark attendance." }
        }
    }

    fun markAllPresent(students: List<SchoolUser>, date: String, className: String, section: String, markedBy: String) {
        viewModelScope.launch {
            val records = students.map { st ->
                AttendanceRecord(
                    date = date,
                    className = className,
                    section = section,
                    studentDocId = st.id,
                    status = "Present",
                    markedBy = markedBy,
                )
            }
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.saveAttendanceBatch(tok, records)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Marked ${records.size} students Present for $date"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to mark attendance." }
                return@launch
            }
            var failed: Throwable? = null
            var updatedCount = 0
            records.forEach { rec ->
                repository.markAttendance(rec).onSuccess { updatedCount++ }.onFailure { failed = it }
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
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.updateLeaveStatus(tok, leaveId, status, remark)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Leave marked $status."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update leave." }
                return@launch
            }
            repository.updateLeaveStatus(leaveId, status, remark)
                .onSuccess { _toastMessage.value = "Leave marked $status." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update leave." }
        }
    }

    fun postHomework(hw: HomeworkItem) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.postHomework(tok, hw)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Homework published to student diary!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post homework." }
                return@launch
            }
            repository.addHomework(hw)
                .onSuccess { _toastMessage.value = "Homework published to student diary!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post homework." }
        }
    }

    fun publishTermMarks(card: TermMarkCard) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.publishTermMarks(tok, card)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Term report card published for ${card.studentName}!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish term marks." }
                return@launch
            }
            repository.saveTermMarkCard(card)
                .onSuccess { _toastMessage.value = "Term report card published for ${card.studentName}!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish term marks." }
        }
    }

    fun replyDoubt(doubtId: String, reply: String, repliedBy: String) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.replyDoubt(tok, doubtId, reply)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Reply sent to student/parent!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to send reply." }
                return@launch
            }
            repository.replyDoubt(doubtId, reply, repliedBy)
                .onSuccess { _toastMessage.value = "Reply sent to student/parent!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to send reply." }
        }
    }

    // --- Fee Manager & App Update Actions ---
    fun saveGatewayConfig(config: GatewayConfig, successToast: String = "School configuration saved live!") {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.saveGatewayConfig(tok, config)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = successToast
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save configuration." }
                return@launch
            }
            repository.saveGatewayConfig(config)
                .onSuccess { _toastMessage.value = successToast }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save configuration." }
        }
    }

    private fun parseNormalizedVersion(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        return Regex("\\d+").findAll(cleaned).map { it.value.toIntOrNull() ?: 0 }.toList()
    }

    private fun isRemoteVersionNewer(remoteTag: String, currentName: String): Boolean {
        val remoteParts = parseNormalizedVersion(remoteTag)
        val currentParts = parseNormalizedVersion(currentName)
        if (remoteParts.isEmpty()) return false
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun reportPlayStoreUpdateAvailable(availableVersionCode: Long) {
        val current = _appUpdateState.value as? AppUpdateState.UpdateAvailable
        if (current != null && current.latestVersionCode >= availableVersionCode) return
        _appUpdateState.value = AppUpdateState.UpdateAvailable(
            latestVersionName = "Latest (Play Store)",
            latestVersionCode = maxOf(availableVersionCode, BuildConfig.VERSION_CODE.toLong() + 1L),
            downloadUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
            releaseNotes = "Official Google Play update ready to install directly inside the app.",
        )
    }

    fun checkForAppUpdate(gateway: GatewayConfig, manual: Boolean = true) {
        if (isCloudflareMode && gateway.apkDownloadUrl.isBlank() && gateway.githubRepoSlug.isBlank()) {
            if (!manual) return
            viewModelScope.launch {
                _appUpdateState.value = AppUpdateState.Checking
                CloudflareApiClient.fetchPublicAppUpdate()
                    .onSuccess { pubGw ->
                        if (pubGw.apkDownloadUrl.isBlank() && pubGw.githubRepoSlug.isBlank()) {
                            _appUpdateState.value = AppUpdateState.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                        } else {
                            checkForAppUpdate(pubGw, manual = true)
                        }
                    }
                    .onFailure { e ->
                        _appUpdateState.value = AppUpdateState.Error(e.localizedMessage ?: "Unable to check for updates right now.")
                    }
            }
            return
        }

        val installedCode = BuildConfig.VERSION_CODE.toLong()
        val installedName = BuildConfig.VERSION_NAME
        val cloudHasNewerCode = gateway.latestVersionCode > installedCode
        val cloudHasNewerName = isRemoteVersionNewer(gateway.latestVersionName, installedName)
        val cloudDirectUrl = gateway.apkDownloadUrl.trim()

        if ((cloudHasNewerCode || cloudHasNewerName) && cloudDirectUrl.isNotBlank()) {
            _appUpdateState.value = AppUpdateState.UpdateAvailable(
                latestVersionName = gateway.latestVersionName.ifBlank { "Latest" },
                latestVersionCode = maxOf(gateway.latestVersionCode, installedCode + 1L),
                downloadUrl = cloudDirectUrl,
                releaseNotes = gateway.updateNotes.ifBlank { "New features, performance & bug fixes for Doon Public School, Etah." },
            )
            return
        }

        val repoSlug = gateway.githubRepoSlug.trim()
        if (repoSlug.isBlank()) {
            if (manual) {
                _appUpdateState.value = AppUpdateState.UpToDate(installedName, BuildConfig.VERSION_CODE)
            } else if (_appUpdateState.value !is AppUpdateState.UpdateAvailable) {
                _appUpdateState.value = AppUpdateState.Idle
            }
            return
        }

        viewModelScope.launch {
            if (manual) {
                _appUpdateState.value = AppUpdateState.Checking
            }
            repository.fetchLatestGitHubRelease(repoSlug)
                .onSuccess { rel ->
                    val newer = isRemoteVersionNewer(rel.tagName, installedName)
                    if (newer && rel.apkDownloadUrl.isNotBlank()) {
                        _appUpdateState.value = AppUpdateState.UpdateAvailable(
                            latestVersionName = rel.tagName.ifBlank { rel.releaseName },
                            latestVersionCode = maxOf(gateway.latestVersionCode, installedCode + 1L),
                            downloadUrl = rel.apkDownloadUrl,
                            releaseNotes = rel.notes.ifBlank { gateway.updateNotes.ifBlank { "Latest verified APK release available." } },
                        )
                    } else if (manual) {
                        _appUpdateState.value = AppUpdateState.UpToDate(installedName, BuildConfig.VERSION_CODE)
                    }
                }
                .onFailure { e ->
                    if (manual) {
                        _appUpdateState.value = AppUpdateState.Error(e.localizedMessage ?: "Unable to check for updates right now.")
                    }
                }
        }
    }

    fun assignFeeDemands(request: FeeBillRequest) {
        val cleanMonth = request.month.trim()
        if (cleanMonth.isBlank()) {
            _toastMessage.value = "Please enter a billing month or term (e.g. December 2026)."
            return
        }
        if (request.scope == FeeBillScope.CLASSES && request.selectedClasses.isEmpty()) {
            _toastMessage.value = "Please select at least one class to bill."
            return
        }
        if (request.scope == FeeBillScope.STUDENTS && request.selectedStudentDocIds.isEmpty()) {
            _toastMessage.value = "Please select at least one student to bill."
            return
        }
        if (request.billType == FeeBillType.MISC && (request.miscTitle.isBlank() || request.miscAmount <= 0L)) {
            _toastMessage.value = "Enter a valid Extra Fee title and positive amount."
            return
        }

        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.triggerFeeBills(tok, request)
                    .onSuccess { res ->
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = if (res.itemsCreated > 0) {
                            "Billed ${res.itemsCreated} fee head(s) across ${res.studentsBilled} student(s) (${res.skippedDuplicates} duplicates skipped)."
                        } else {
                            "0 new bills created (${res.skippedDuplicates} already billed for $cleanMonth, or ₹0 account fee)."
                        }
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed triggering fee bills." }
                return@launch
            }

            val allStudents = ((usersState.value as? DataUiState.Success)?.data ?: emptyList())
                .filter { it.role == "STUDENT" }
            val matchedStudents = when (request.scope) {
                FeeBillScope.CLASSES -> allStudents.filter { st ->
                    st.className in request.selectedClasses &&
                        (request.sectionFilter.isBlank() || st.section.equals(request.sectionFilter, ignoreCase = true))
                }
                FeeBillScope.STUDENTS -> allStudents.filter { it.id in request.selectedStudentDocIds }
                FeeBillScope.ENTIRE_SCHOOL -> allStudents
            }
            if (matchedStudents.isEmpty()) {
                _toastMessage.value = "No enrolled students matched your selected target."
                return@launch
            }

            val existingFees = (feeItemsState.value as? DataUiState.Success)?.data ?: emptyList()
            val existingKeys = existingFees
                .map { "${it.studentDocId}:::${it.title.trim().lowercase()}" }
                .toMutableSet()

            val toCreate = mutableListOf<FeeItem>()
            val billedStudents = mutableSetOf<String>()
            var skippedDuplicates = 0

            fun queueIfValid(student: SchoolUser, title: String, amount: Long) {
                if (amount <= 0L) return
                val key = "${student.id}:::${title.trim().lowercase()}"
                if (!existingKeys.add(key)) {
                    skippedDuplicates += 1
                    return
                }
                billedStudents += student.id
                toCreate += FeeItem(
                    studentDocId = student.id,
                    title = title,
                    month = cleanMonth,
                    amount = amount,
                    status = "UNPAID",
                    paymentId = "",
                )
            }

            for (st in matchedStudents) {
                when (request.billType) {
                    FeeBillType.BOTH -> {
                        queueIfValid(st, "Monthly Tuition Fee ($cleanMonth)", st.monthlyTuition)
                        queueIfValid(st, "Transport / Bus Fee ($cleanMonth)", st.transportFee)
                    }
                    FeeBillType.TUITION -> {
                        queueIfValid(st, "Monthly Tuition Fee ($cleanMonth)", st.monthlyTuition)
                    }
                    FeeBillType.TRANSPORT -> {
                        queueIfValid(st, "Transport / Bus Fee ($cleanMonth)", st.transportFee)
                    }
                    FeeBillType.MISC -> {
                        queueIfValid(st, "${request.miscTitle.trim()} ($cleanMonth)", request.miscAmount)
                    }
                }
            }

            var failed: Throwable? = null
            toCreate.forEach { item ->
                repository.addFeeItem(item).onFailure { failed = it }
            }
            _toastMessage.value = when {
                failed != null -> failed?.localizedMessage ?: "Failed assigning some fee demands."
                toCreate.isNotEmpty() -> "Billed ${toCreate.size} fee head(s) across ${billedStudents.size} student(s) using saved Account fees ($skippedDuplicates duplicates skipped)."
                else -> "0 new bills created ($skippedDuplicates already billed for $cleanMonth, or ₹0 account fee)."
            }
        }
    }

    fun verifyPayment(payment: FeePayment, verifierName: String, verifiedAtStr: String, existingVerifiedCount: Int) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.verifyOrRejectPayment(tok, payment.id, "VERIFY")
                    .onSuccess { receiptNo ->
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Verified! Official Receipt $receiptNo issued."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to verify payment." }
                return@launch
            }
            val receiptNo = "DPS-RCP-2026-${(existingVerifiedCount + 102).toString().padStart(4, '0')}"
            repository.verifyPayment(payment, receiptNo, verifierName, verifiedAtStr)
                .onSuccess { _toastMessage.value = "Verified! Official Receipt $receiptNo issued." }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to verify payment." }
        }
    }

    fun rejectPayment(payment: FeePayment, reason: String, verifierName: String, verifiedAtStr: String) {
        viewModelScope.launch {
            val cleanReason = reason.ifBlank { "UTR did not match bank ledger" }
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.verifyOrRejectPayment(tok, payment.id, "REJECT", cleanReason)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Payment rejected & dues restored to UNPAID."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to reject payment." }
                return@launch
            }
            repository.rejectPayment(payment, cleanReason, verifierName, verifiedAtStr)
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
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.submitPaymentProof(tok, selectedFees.map { it.id }, mode, cleanRef, upiApp)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "UTR submitted! Fee Manager will verify & issue your receipt."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Submission failed." }
                return@launch
            }
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
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.submitLeave(tok, leave)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Leave application sent to Class Teacher!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to submit leave." }
                return@launch
            }
            repository.submitLeave(leave)
                .onSuccess { _toastMessage.value = "Leave application sent to Class Teacher!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to submit leave." }
        }
    }

    fun submitDoubt(doubt: DoubtMessage) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.submitDoubt(tok, doubt)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _toastMessage.value = "Doubt posted to teacher helpdesk!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post doubt." }
                return@launch
            }
            repository.submitDoubt(doubt)
                .onSuccess { _toastMessage.value = "Doubt posted to teacher helpdesk!" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to post doubt." }
        }
    }

    // --- Craft & Project Skill Studio (Unique 1-Like-Per-User via Firestore Transaction) ---

    fun selectAiPersona(persona: AiTutorPersona) {
        _aiPersona.value = persona
    }

    fun clearAiChatHistory() {
        _aiMessages.value = listOf(
            AiChatMessage(
                isUser = false,
                text = "New conversation started."
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
                    _craftAiImageState.value = CraftAiImageState.Error("Model did not return an image.")
                }
            } catch (e: TimeoutCancellationException) {
                _craftAiImageState.value = CraftAiImageState.Error("Image generation timed out.")
            } catch (e: QuotaExceededException) {
                _craftAiImageState.value = CraftAiImageState.Error("Quota limit reached.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _craftAiImageState.value = CraftAiImageState.Error(e.localizedMessage ?: "Failed to generate craft image.")
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
                        .onSuccess { _toastMessage.value = "Concept image added to \"${project.title}\"!" }
                        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed saving image." }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastMessage.value = e.localizedMessage ?: "Image generation failed."
            } finally {
                _generatingCraftPostId.value = null
            }
        }
    }

    fun publishCraftProject(project: CraftProject) {
        viewModelScope.launch {
            if (isCloudflareMode) {
                val tok = _cfToken.value ?: return@launch
                CloudflareApiClient.postCraftProject(tok, project)
                    .onSuccess {
                        refreshCloudflareDashboard(tok)
                        _craftAiImageState.value = CraftAiImageState.Idle
                        _toastMessage.value = "Craft Idea & Project Skill published to School Showcase!"
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish craft idea." }
                return@launch
            }
            repository.addCraftProject(project)
                .onSuccess {
                    _craftAiImageState.value = CraftAiImageState.Idle
                    _toastMessage.value = "Craft Idea & Project Skill published to School Showcase!"
                }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to publish craft idea." }
        }
    }

    fun applaudCraftProject(project: CraftProject) {
        val currentUser = activeSchoolUser.value ?: return
        if (project.id in _likingProjectIds.value) return
        _likingProjectIds.value += project.id
        viewModelScope.launch {
            try {
                if (isCloudflareMode) {
                    val tok = _cfToken.value ?: return@launch
                    CloudflareApiClient.toggleCraftLike(tok, project.id)
                        .onSuccess { liked ->
                            refreshCloudflareDashboard(tok)
                            _toastMessage.value = if (liked) "Liked! (1 like per account)" else "Like removed."
                        }
                        .onFailure { _toastMessage.value = it.localizedMessage ?: "Could not update like." }
                    return@launch
                }
                repository.toggleCraftProjectLike(project.id, currentUser.id)
                    .onSuccess { liked ->
                        _toastMessage.value = if (liked) "Liked! (1 like per account)" else "Like removed."
                    }
                    .onFailure { _toastMessage.value = it.localizedMessage ?: "Could not update like." }
            } finally {
                _likingProjectIds.value -= project.id
            }
        }
    }

    fun askAiTutor(student: SchoolUser, question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _aiTutorState.value is AiTutorUiState.Loading || _aiTutorState.value is AiTutorUiState.Streaming) {
            return
        }
        val priorHistory = _aiMessages.value.drop(1).map { msg ->
            content(if (msg.isUser) "user" else "model") { text(msg.text) }
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
                    _aiTutorState.value = AiTutorUiState.Error("Empty response.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiTutorState.value = AiTutorUiState.Error(e.localizedMessage ?: "Request failed.")
            }
        }
    }

    private companion object {
        const val TAG = "SchoolViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
