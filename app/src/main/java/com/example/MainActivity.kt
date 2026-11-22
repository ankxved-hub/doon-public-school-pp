package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.data.model.GatewayConfig
import com.example.data.model.SchoolInfo
import com.example.data.model.effectiveRoles
import com.example.data.preferences.ThemePreferences
import com.example.data.remote.CloudflareApiClient
import com.example.data.repository.SchoolRepository
import com.example.ui.auth.GoogleAuthGateScreen
import com.example.ui.auth.signOutGoogle
import com.example.ui.components.AppUpdateBannerCard
import com.example.ui.components.AppUpdatePromptDialog
import com.example.ui.components.ProfileEditDialog
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AppCreditsScreen
import com.example.ui.screens.FeeManagerScreen
import com.example.ui.screens.SchoolRoleLoginScreen
import com.example.ui.screens.StudentScreen
import com.example.ui.screens.TeacherScreen
import com.example.ui.theme.DpsEtahTheme
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyRoyal
import com.example.ui.viewmodel.AppUpdateState
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.SchoolViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAppCheck(this, intent)
        setContent {
            val appContext = applicationContext
            val themePrefs = remember(appContext) { ThemePreferences(appContext) }
            val isDarkMode by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
            val scope = rememberCoroutineScope()

            DpsEtahTheme(darkTheme = isDarkMode) {
                DpsEtahAppRoot(
                    themePrefs = themePrefs,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { enabled ->
                        scope.launch { themePrefs.setDarkMode(enabled) }
                    },
                )
            }
        }
    }
}

private fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val auth = Firebase.auth
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
}

private data class NavTab(
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun DpsEtahAppRoot(
    themePrefs: ThemePreferences,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useCloudflare = remember { CloudflareApiClient.isEnabled() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val uid = if (useCloudflare) "cloudflare_edge" else currentUser?.uid
    if (uid == null) {
        GoogleAuthGateScreen(onAuthSuccess = {})
        return
    }

    key(uid) {
        val repository = remember(context, uid) { SchoolRepository(context.applicationContext) }
        val viewModel: SchoolViewModel = viewModel(
            key = "school_vm_$uid",
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SchoolViewModel(repository, uid, themePrefs) as T
                }
            },
        )

        AuthenticatedSchoolShell(
            viewModel = viewModel,
            googleEmail = if (useCloudflare) null else currentUser?.email,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            onGoogleSignOut = {
                scope.launch { themePrefs.clearRoleSession() }
                if (!useCloudflare) {
                    signOutGoogle(credentialManager, scope) {}
                } else {
                    viewModel.logoutSchoolRole()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedSchoolShell(
    viewModel: SchoolViewModel,
    googleEmail: String?,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onGoogleSignOut: () -> Unit,
) {
    val activeUser by viewModel.activeSchoolUser.collectAsStateWithLifecycle()
    val isRestoringSavedSession by viewModel.isRestoringSavedSession.collectAsStateWithLifecycle()
    val isSeeding by viewModel.isSeeding.collectAsStateWithLifecycle()
    val needsCloudflareAdminSetup by viewModel.needsCloudflareAdminSetup.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val usersUi by viewModel.usersState.collectAsStateWithLifecycle()
    val gatewayUi by viewModel.gatewayState.collectAsStateWithLifecycle()
    val circularsUi by viewModel.circularsState.collectAsStateWithLifecycle()
    val dptDailyUi by viewModel.dptDailyState.collectAsStateWithLifecycle()
    val dptWeeklyUi by viewModel.dptWeeklyState.collectAsStateWithLifecycle()
    val attendanceUi by viewModel.attendanceState.collectAsStateWithLifecycle()
    val leavesUi by viewModel.leavesState.collectAsStateWithLifecycle()
    val homeworkUi by viewModel.homeworkState.collectAsStateWithLifecycle()
    val termMarksUi by viewModel.termMarksState.collectAsStateWithLifecycle()
    val feeItemsUi by viewModel.feeItemsState.collectAsStateWithLifecycle()
    val paymentsUi by viewModel.paymentsState.collectAsStateWithLifecycle()
    val doubtsUi by viewModel.doubtsState.collectAsStateWithLifecycle()
    val craftProjectsUi by viewModel.craftProjectsState.collectAsStateWithLifecycle()
    val campusPostersUi by viewModel.campusPostersState.collectAsStateWithLifecycle()
    val timetablesUi by viewModel.timetablesState.collectAsStateWithLifecycle()
    val likedProjectIds by viewModel.likedProjectIds.collectAsStateWithLifecycle()
    val likingProjectIds by viewModel.likingProjectIds.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val aiTutorState by viewModel.aiTutorState.collectAsStateWithLifecycle()
    val aiPersona by viewModel.aiPersona.collectAsStateWithLifecycle()
    val craftAiImageState by viewModel.craftAiImageState.collectAsStateWithLifecycle()
    val generatingCraftPostId by viewModel.generatingCraftPostId.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()

    val users = (usersUi as? DataUiState.Success)?.data ?: emptyList()
    val rawGateway = (gatewayUi as? DataUiState.Success)?.data?.firstOrNull()
    val gateway = rawGateway ?: GatewayConfig()
    val circulars = (circularsUi as? DataUiState.Success)?.data ?: emptyList()
    val dptDaily = (dptDailyUi as? DataUiState.Success)?.data ?: emptyList()
    val dptWeekly = (dptWeeklyUi as? DataUiState.Success)?.data ?: emptyList()
    val attendance = (attendanceUi as? DataUiState.Success)?.data ?: emptyList()
    val leaves = (leavesUi as? DataUiState.Success)?.data ?: emptyList()
    val homework = (homeworkUi as? DataUiState.Success)?.data ?: emptyList()
    val termMarks = (termMarksUi as? DataUiState.Success)?.data ?: emptyList()
    val feeItems = (feeItemsUi as? DataUiState.Success)?.data ?: emptyList()
    val payments = (paymentsUi as? DataUiState.Success)?.data ?: emptyList()
    val doubts = (doubtsUi as? DataUiState.Success)?.data ?: emptyList()
    val craftProjects = (craftProjectsUi as? DataUiState.Success)?.data ?: emptyList()
    val campusPosters = (campusPostersUi as? DataUiState.Success)?.data ?: emptyList()
    val timetables = (timetablesUi as? DataUiState.Success)?.data ?: emptyList()

    var promptDismissedCode by remember { mutableStateOf<Long?>(null) }
    var dismissedUpdateCode by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(
        rawGateway?.latestVersionCode,
        rawGateway?.latestVersionName,
        rawGateway?.apkDownloadUrl,
        rawGateway?.githubRepoSlug,
    ) {
        if (rawGateway != null) {
            viewModel.checkForAppUpdate(rawGateway, manual = false)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val user = activeUser
    var showWelcomeCreditsPage by remember(user?.id, user?.role) { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (user == null) {
            if (isRestoringSavedSession) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(NavyDeep, NavyRoyal, NavyDeep)))
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        CircularProgressIndicator(color = GoldAmber)
                        Text(
                            text = SchoolInfo.NAME,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            text = "Opening your saved portal...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoldAmber,
                        )
                    }
                }
            } else {
                SchoolRoleLoginScreen(
                    isSeeding = isSeeding,
                    isSyncError = usersUi is DataUiState.Error,
                    googleEmail = googleEmail,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onRetrySync = { viewModel.retryCloudSync(userInitiated = true) },
                    onLoginRole = { role, userId, password -> viewModel.loginSchoolRole(role, userId, password) },
                    onGoogleSignOut = onGoogleSignOut,
                    isCloudflareMode = viewModel.isCloudflareMode,
                    needsCloudflareAdminSetup = needsCloudflareAdminSetup,
                    onClaimCloudflareAdmin = { fn, uidVal, ph, pw -> viewModel.claimCloudflareAdmin(fn, uidVal, ph, pw) },
                )
            }
        } else if (showWelcomeCreditsPage) {
            AppCreditsScreen(
                user = user,
                onContinue = { showWelcomeCreditsPage = false },
            )
        } else {

    val tabs = remember(user.role) {
        when (user.role) {
            "ADMIN" -> listOf(
                NavTab("Dashboard", Icons.Filled.Dashboard),
                NavTab("Accounts", Icons.Filled.ManageAccounts),
                NavTab("Circulars", Icons.Filled.Campaign),
                NavTab("Payments", Icons.Filled.ReceiptLong),
                NavTab("Projects", Icons.Filled.Brush),
            )
            "TEACHER" -> listOf(
                NavTab("Daily DPT", Icons.Filled.Quiz),
                NavTab("Weekly DPT", Icons.Filled.Leaderboard),
                NavTab("Attendance", Icons.Filled.FactCheck),
                NavTab("Notices", Icons.Filled.Campaign),
                NavTab("Diary & Marks", Icons.Filled.MenuBook),
            )
            "FEE_MANAGER" -> listOf(
                NavTab("Verify", Icons.Filled.Verified),
                NavTab("Ledger", Icons.Filled.AccountBalanceWallet),
                NavTab("Gateway", Icons.Filled.QrCode2),
                NavTab("Payments", Icons.Filled.ReceiptLong),
            )
            else -> listOf(
                NavTab("Home", Icons.Filled.Home),
                NavTab("DPT Results", Icons.Filled.ShowChart),
                NavTab("Fees & Pay", Icons.Filled.AccountBalanceWallet),
                NavTab("Academics", Icons.Filled.MenuBook),
                NavTab("Projects", Icons.Filled.Brush),
            )
        }
    }

    var selectedTab by remember(user.id, user.role) { mutableIntStateOf(0) }
    var showProfileEditDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NavyDeep,
                        titleContentColor = Color.White,
                        actionIconContentColor = GoldAmber,
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.dps_real_logo),
                                    contentDescription = "Doon Public School Official Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Column {
                                Text(SchoolInfo.SHORT_NAME, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text(
                                    "${user.role.replace('_', ' ')} • ${user.userId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldAmber,
                                )
                            }
                        }
                    },
                    actions = {
                        val assignedRoles = remember(user) { user.effectiveRoles() }
                        if (assignedRoles.size > 1) {
                            var roleSwitchExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { roleSwitchExpanded = true },
                                    modifier = Modifier.testTag("topbar_portal_role_switch_btn"),
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch Assigned Role")
                                }
                                DropdownMenu(
                                    expanded = roleSwitchExpanded,
                                    onDismissRequest = { roleSwitchExpanded = false },
                                ) {
                                    assignedRoles.forEach { roleKey ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${roleKey.replace('_', ' ')}${if (roleKey == user.role) " (Active)" else ""}"
                                                )
                                            },
                                            leadingIcon = {
                                                if (roleKey == user.role) {
                                                    Icon(Icons.Filled.Check, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                roleSwitchExpanded = false
                                                viewModel.switchActiveRole(roleKey)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (viewModel.isCloudflareMode) {
                            IconButton(
                                onClick = { viewModel.retryCloudSync(userInitiated = true) },
                                modifier = Modifier.testTag("topbar_sync_btn"),
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = "Sync Cloudflare Cloud")
                            }
                        }
                        IconButton(
                            onClick = { onToggleDarkMode(!isDarkMode) },
                            modifier = Modifier.testTag("topbar_dark_mode_btn"),
                        ) {
                            Icon(
                                if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                            )
                        }
                        IconButton(
                            onClick = { showProfileEditDialog = true },
                            modifier = Modifier.testTag("topbar_edit_profile_btn"),
                        ) {
                            Icon(Icons.Filled.ManageAccounts, contentDescription = "Profile & App Settings")
                        }
                        IconButton(
                            onClick = { viewModel.logoutSchoolRole() },
                            modifier = Modifier.testTag("topbar_switch_role_btn"),
                        ) {
                            Icon(Icons.Filled.SwitchAccount, contentDescription = "Switch School Role")
                        }
                        if (!viewModel.isCloudflareMode) {
                            IconButton(
                                onClick = onGoogleSignOut,
                                modifier = Modifier.testTag("topbar_google_signout_btn"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Google Sign-Out")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEachIndexed { idx, tab ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = GoldAmber.copy(alpha = 0.22f),
                                ),
                                modifier = Modifier.testTag("nav_tab_$idx"),
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEachIndexed { idx, tab ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = GoldAmber.copy(alpha = 0.22f),
                                ),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val activeUpdate = appUpdateState as? AppUpdateState.UpdateAvailable
                    if (activeUpdate != null && dismissedUpdateCode != activeUpdate.latestVersionCode) {
                        AppUpdateBannerCard(
                            update = activeUpdate,
                            onDismiss = { dismissedUpdateCode = activeUpdate.latestVersionCode },
                            onShowMessage = { viewModel.showMessage(it) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (user.role) {
                        "ADMIN" -> AdminScreen(
                            activeTab = selectedTab,
                            adminUser = user,
                            users = users,
                            gateway = gateway,
                            circulars = circulars,
                            dptDaily = dptDaily,
                            dptWeekly = dptWeekly,
                            termMarks = termMarks,
                            attendance = attendance,
                            feeItems = feeItems,
                            payments = payments,
                            craftProjects = craftProjects,
                            likedProjectIds = likedProjectIds,
                            likingProjectIds = likingProjectIds,
                            campusPosters = campusPosters,
                            craftAiImageState = craftAiImageState,
                            generatingCraftPostId = generatingCraftPostId,
                            onSaveUser = { viewModel.saveUserAccount(it) },
                            onDeleteUser = { viewModel.deleteUser(it) },
                            onDeleteRecord = { col, id, lbl -> viewModel.deleteRecord(col, id, lbl) },
                            onPostCircular = { viewModel.postCircular(it) },
                            onPublishCampusPoster = { viewModel.publishCampusPoster(it) },
                            onGenerateCraftAiImage = { t, cat, desc, mat -> viewModel.generateCraftAiImage(t, cat, desc, mat) },
                            onClearCraftPreview = { viewModel.clearCraftAiImageState() },
                            onPublishCraftProject = { viewModel.publishCraftProject(it) },
                            onGenerateCraftImageForPost = { viewModel.generateAiImageForExistingCraft(it) },
                            onApplaudCraftProject = { viewModel.applaudCraftProject(it) },
                            onSaveGatewayConfig = { viewModel.saveGatewayConfig(it, "In-app update published live to all phones!") },
                            onNavigateToTab = { selectedTab = it },
                            onShowMessage = { viewModel.showMessage(it) },
                            onOpenProfileEdit = { showProfileEditDialog = true },
                        )
                        "TEACHER" -> TeacherScreen(
                            activeTab = selectedTab,
                            teacher = user,
                            users = users,
                            circulars = circulars,
                            dptDaily = dptDaily,
                            dptWeekly = dptWeekly,
                            termMarks = termMarks,
                            attendance = attendance,
                            leaves = leaves,
                            homework = homework,
                            doubts = doubts,
                            timetables = timetables,
                            craftProjects = craftProjects,
                            likedProjectIds = likedProjectIds,
                            likingProjectIds = likingProjectIds,
                            craftAiImageState = craftAiImageState,
                            generatingCraftPostId = generatingCraftPostId,
                            onSaveDailyDpt = { viewModel.saveDailyDpt(it) },
                            onSaveWeeklyDptMarks = { viewModel.saveWeeklyDptMarks(it) },
                            onMarkAttendance = { viewModel.markStudentAttendance(it) },
                            onMarkAllPresent = { sts, d, c, s, by -> viewModel.markAllPresent(sts, d, c, s, by) },
                            onUpdateLeaveStatus = { id, st, rem -> viewModel.updateLeaveStatus(id, st, rem) },
                            onPostNotice = { viewModel.postCircular(it) },
                            onPostHomework = { viewModel.postHomework(it) },
                            onPublishTermMarks = { viewModel.publishTermMarks(it) },
                            onReplyDoubt = { id, rep, by -> viewModel.replyDoubt(id, rep, by) },
                            onDeleteRecord = { col, id, lbl -> viewModel.deleteRecord(col, id, lbl) },
                            onSaveStudent = { viewModel.saveUserAccount(it) },
                            onDeleteStudent = { viewModel.deleteUser(it) },
                            onSaveTimetable = { cls, sec, slots, by -> viewModel.saveTimetable(cls, sec, slots, by) },
                            onGenerateCraftAiImage = { t, cat, desc, mat -> viewModel.generateCraftAiImage(t, cat, desc, mat) },
                            onClearCraftPreview = { viewModel.clearCraftAiImageState() },
                            onPublishCraftProject = { viewModel.publishCraftProject(it) },
                            onGenerateCraftImageForPost = { viewModel.generateAiImageForExistingCraft(it) },
                            onApplaudCraftProject = { viewModel.applaudCraftProject(it) },
                            onShowMessage = { viewModel.showMessage(it) },
                            onOpenProfileEdit = { showProfileEditDialog = true },
                        )
                        "FEE_MANAGER" -> FeeManagerScreen(
                            activeTab = selectedTab,
                            feeManager = user,
                            users = users,
                            gateway = gateway,
                            feeItems = feeItems,
                            payments = payments,
                            onVerifyPayment = { p, by, at, cnt -> viewModel.verifyPayment(p, by, at, cnt) },
                            onRejectPayment = { p, r, by, at -> viewModel.rejectPayment(p, r, by, at) },
                            onAssignFeeDemands = { req -> viewModel.assignFeeDemands(req) },
                            onDeleteFeeItem = { viewModel.deleteRecord("feeItems", it.id, it.title) },
                            onSaveGatewayConfig = { viewModel.saveGatewayConfig(it) },
                            onOpenProfileEdit = { showProfileEditDialog = true },
                        )
                        else -> StudentScreen(
                            activeTab = selectedTab,
                            student = user,
                            allUsers = users,
                            gateway = gateway,
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
                            timetables = timetables,
                            campusPosters = campusPosters,
                            craftProjects = craftProjects,
                            likedProjectIds = likedProjectIds,
                            likingProjectIds = likingProjectIds,
                            aiMessages = aiMessages,
                            aiTutorState = aiTutorState,
                            aiPersona = aiPersona,
                            craftAiImageState = craftAiImageState,
                            generatingCraftPostId = generatingCraftPostId,
                            onSubmitPaymentProof = { st, fees, mode, utr, app -> viewModel.submitPaymentProof(st, fees, mode, utr, app) },
                            onSubmitLeave = { viewModel.submitLeave(it) },
                            onSubmitDoubt = { viewModel.submitDoubt(it) },
                            onAskAiTutor = { q -> viewModel.askAiTutor(user, q) },
                            onSelectAiPersona = { p -> viewModel.selectAiPersona(p) },
                            onClearAiChat = { viewModel.clearAiChatHistory() },
                            onGenerateCraftAiImage = { t, cat, desc, mat -> viewModel.generateCraftAiImage(t, cat, desc, mat) },
                            onClearCraftPreview = { viewModel.clearCraftAiImageState() },
                            onPublishCraftProject = { viewModel.publishCraftProject(it) },
                            onGenerateCraftImageForPost = { viewModel.generateAiImageForExistingCraft(it) },
                            onApplaudCraftProject = { viewModel.applaudCraftProject(it) },
                            onDeleteCraftProject = { p -> viewModel.deleteRecord("craftProjects", p.id, p.title) },
                            onShowMessage = { viewModel.showMessage(it) },
                            onNavigateToTab = { selectedTab = it },
                            onOpenProfileEdit = { showProfileEditDialog = true },
                        )
                    }
                    }
                }
            }
        }
    }

    if (showProfileEditDialog) {
        ProfileEditDialog(
            user = user,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            onDismiss = { showProfileEditDialog = false },
            onSaveProfile = { newFullName, newPhone, newPassword ->
                viewModel.updateMyProfile(user.id, newFullName, newPhone, newPassword)
            },
            gateway = gateway,
            appUpdateState = appUpdateState,
            onCheckForUpdate = { viewModel.checkForAppUpdate(gateway, manual = true) },
            onShowMessage = { viewModel.showMessage(it) },
        )
        }
        }

        val activeUpdate = appUpdateState as? AppUpdateState.UpdateAvailable
        if (activeUpdate != null && promptDismissedCode != activeUpdate.latestVersionCode) {
            AppUpdatePromptDialog(
                update = activeUpdate,
                onDismiss = { promptDismissedCode = activeUpdate.latestVersionCode },
                onShowMessage = { viewModel.showMessage(it) },
            )
        }
    }
}
