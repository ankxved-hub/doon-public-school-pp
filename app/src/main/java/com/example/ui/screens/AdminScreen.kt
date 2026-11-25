package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.data.model.*
import com.example.ui.components.AdminStaffLeaveApprovalsCard
import com.example.ui.components.AttachmentPickerChip
import com.example.ui.components.AttachmentPreviewBlock
import com.example.ui.components.PickedAttachment
import com.example.ui.components.SchoolDatePickerField
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StudentDptResultCard
import com.example.ui.components.decodeAttachmentBitmap
import com.example.ui.components.formatInr
import com.example.ui.components.readAttachmentFromUri
import com.example.ui.theme.*
import com.example.ui.viewmodel.CraftAiImageState

@Composable
fun AdminScreen(
    activeTab: Int,
    adminUser: SchoolUser,
    users: List<SchoolUser>,
    gateway: GatewayConfig,
    circulars: List<Circular>,
    dptDaily: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    termMarks: List<TermMarkCard>,
    attendance: List<AttendanceRecord>,
    leaves: List<LeaveRequest> = emptyList(),
    libraryIssues: List<LibraryIssue> = emptyList(),
    feeItems: List<FeeItem>,
    payments: List<FeePayment>,
    craftProjects: List<CraftProject> = emptyList(),
    likedProjectIds: Set<String> = emptySet(),
    likingProjectIds: Set<String> = emptySet(),
    campusPosters: List<CampusPoster> = emptyList(),
    craftAiImageState: CraftAiImageState = CraftAiImageState.Idle,
    generatingCraftPostId: String? = null,
    onSaveUser: (SchoolUser) -> Unit,
    onDeleteUser: (SchoolUser) -> Unit,
    onDeleteRecord: (collection: String, docId: String, label: String) -> Unit,
    onPostCircular: (Circular) -> Unit,
    onUpdateLeaveStatus: (String, String, String) -> Unit = { _, _, _ -> },
    onIssueBook: (LibraryIssue) -> Unit = {},
    onReturnBook: (LibraryIssue) -> Unit = {},
    onPublishCampusPoster: (CampusPoster) -> Unit = {},
    onGenerateCraftAiImage: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onGenerateCustomAiImage: (String) -> Unit = {},
    onEditCraftAiImage: (String, String) -> Unit = { _, _ -> },
    onClearCraftPreview: () -> Unit = {},
    onPublishCraftProject: (CraftProject) -> Unit = {},
    onGenerateCraftImageForPost: (CraftProject) -> Unit = {},
    onApplaudCraftProject: (CraftProject) -> Unit = {},
    onSaveGatewayConfig: (GatewayConfig) -> Unit = {},
    onNavigateToTab: (Int) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { SchoolCatalog.todayIso() }
    val students = remember(users) { users.filter { it.role == "STUDENT" }.sortedBy { it.userId } }
    val teachers = remember(users) { users.filter { it.role == "TEACHER" } }
    val feeManagers = remember(users) { users.filter { it.role == "FEE_MANAGER" } }
    val librarians = remember(users) { users.filter { it.role == "LIBRARIAN" } }
    var showStudentProgressDashboard by remember { mutableStateOf(false) }
    var showLibraryDeskOverlay by remember { mutableStateOf(false) }

    if (showLibraryDeskOverlay) {
        BackHandler { showLibraryDeskOverlay = false }
        LibraryManagementContent(
            actor = adminUser,
            allUsers = users,
            libraryIssues = libraryIssues,
            onIssueBook = onIssueBook,
            onReturnBook = onReturnBook,
            onDeleteLibraryIssue = { issue -> onDeleteRecord("libraryIssues", issue.id, issue.bookTitle) },
            onBack = { showLibraryDeskOverlay = false },
            modifier = modifier,
        )
        return
    }

    if (showStudentProgressDashboard && students.isNotEmpty()) {
        BackHandler { showStudentProgressDashboard = false }
        DptResultDashboardScreen(
            primaryStudent = students.first(),
            allStudents = students,
            dptDaily = dptDaily,
            dptWeekly = dptWeekly,
            termMarks = termMarks,
            allowStudentPicker = true,
            onBack = { showStudentProgressDashboard = false },
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
        return
    }

    when (activeTab) {
        0 -> AdminDashboardTab(
            adminUser = adminUser,
            students = students,
            teachers = teachers,
            feeManagers = feeManagers,
            librarians = librarians,
            gateway = gateway,
            todayDpts = dptDaily.filter { it.date == today },
            dptWeekly = dptWeekly,
            todayAttendance = attendance.filter { it.date == today },
            leaves = leaves,
            libraryIssues = libraryIssues,
            feeItems = feeItems,
            payments = payments,
            campusPosters = campusPosters,
            onUpdateLeaveStatus = onUpdateLeaveStatus,
            onOpenLibraryDesk = { showLibraryDeskOverlay = true },
            onPublishCampusPoster = onPublishCampusPoster,
            onDeleteCampusPoster = { p -> onDeleteRecord("campusPosters", p.id, p.title) },
            onSaveGatewayConfig = onSaveGatewayConfig,
            onOpenProgressDashboard = { showStudentProgressDashboard = true },
            onOpenFeePayments = { onNavigateToTab(3) },
            onOpenCraftStudio = { onNavigateToTab(4) },
            onShowMessage = onShowMessage,
            onOpenProfileEdit = onOpenProfileEdit,
            modifier = modifier,
        )
        1 -> AdminAccountsTab(
            users = users,
            onSaveUser = onSaveUser,
            onDeleteUser = onDeleteUser,
            modifier = modifier,
        )
        2 -> AdminCircularsTab(
            adminUser = adminUser,
            teachers = teachers,
            circulars = circulars,
            onPostCircular = onPostCircular,
            onDeleteCircular = { c -> onDeleteRecord("circulars", c.id, c.title) },
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
        3 -> FeePaymentsScreen(
            payments = payments,
            feeItems = feeItems,
            students = students,
            isStudentView = false,
            modifier = modifier,
        )
        else -> CraftStudioScreen(
            currentUser = adminUser,
            projects = craftProjects,
            likedProjectIds = likedProjectIds,
            likingProjectIds = likingProjectIds,
            craftAiImageState = craftAiImageState,
            generatingPostId = generatingCraftPostId,
            onGeneratePreviewImage = onGenerateCraftAiImage,
            onGenerateCustomAiImage = onGenerateCustomAiImage,
            onEditAiImage = onEditCraftAiImage,
            onClearPreviewImage = onClearCraftPreview,
            onPublishProject = onPublishCraftProject,
            onGenerateImageForPost = onGenerateCraftImageForPost,
            onApplaudPost = onApplaudCraftProject,
            onDeletePost = { p -> onDeleteRecord("craftProjects", p.id, p.title) },
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
    }
}

@Composable
private fun AdminDashboardTab(
    adminUser: SchoolUser,
    students: List<SchoolUser>,
    teachers: List<SchoolUser>,
    feeManagers: List<SchoolUser>,
    librarians: List<SchoolUser>,
    gateway: GatewayConfig,
    todayDpts: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    todayAttendance: List<AttendanceRecord>,
    leaves: List<LeaveRequest>,
    libraryIssues: List<LibraryIssue>,
    feeItems: List<FeeItem>,
    payments: List<FeePayment>,
    campusPosters: List<CampusPoster>,
    onUpdateLeaveStatus: (String, String, String) -> Unit,
    onOpenLibraryDesk: () -> Unit,
    onPublishCampusPoster: (CampusPoster) -> Unit,
    onDeleteCampusPoster: (CampusPoster) -> Unit,
    onSaveGatewayConfig: (GatewayConfig) -> Unit,
    onOpenProgressDashboard: () -> Unit,
    onOpenFeePayments: () -> Unit,
    onOpenCraftStudio: () -> Unit,
    onShowMessage: (String) -> Unit,
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentToday = todayAttendance.count { it.status == "Present" }
    val attendancePct = if (todayAttendance.isNotEmpty()) {
        (presentToday * 100) / todayAttendance.size
    } else 0

    val dptHeldCount = todayDpts.count { it.held }
    val dptCompliancePct = if (todayDpts.isNotEmpty()) {
        (dptHeldCount * 100) / todayDpts.size
    } else 0

    val totalCollected = feeItems.filter { it.status == "PAID" }.sumOf { it.amount }
    val totalPending = feeItems.filter { it.status != "PAID" }.sumOf { it.amount }
    val pendingVerificationCount = payments.count { it.status == "PENDING" }

    val context = LocalContext.current
    var selectedWeek by remember { mutableStateOf(SchoolCatalog.dptWeeks[1]) }
    var zoomPoster by remember { mutableStateOf<CampusPoster?>(null) }

    var posterTitle by remember { mutableStateOf("") }
    var posterSubtitle by remember { mutableStateOf("") }
    var posterDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var posterImageBase64 by remember { mutableStateOf("") }

    var updateVersionCodeStr by remember(gateway.latestVersionCode) {
        mutableStateOf(maxOf(gateway.latestVersionCode, BuildConfig.VERSION_CODE.toLong()).toString())
    }
    var updateVersionName by remember(gateway.latestVersionName) {
        mutableStateOf(gateway.latestVersionName.ifBlank { BuildConfig.VERSION_NAME })
    }
    var updateRepoSlug by remember(gateway.githubRepoSlug) {
        mutableStateOf(gateway.githubRepoSlug)
    }
    var updateApkUrl by remember(gateway.apkDownloadUrl) {
        mutableStateOf(gateway.apkDownloadUrl)
    }
    var updateReleaseNotes by remember(gateway.updateNotes) {
        mutableStateOf(gateway.updateNotes)
    }

    val posterPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            readAttachmentFromUri(context, uri, forceImage = true)
                .onSuccess {
                    posterImageBase64 = it.base64Data
                    onShowMessage("Campus poster photo selected!")
                }
                .onFailure { onShowMessage(it.localizedMessage ?: "Unable to read photo.") }
        }
    }

    val sortedPosters = remember(campusPosters) {
        campusPosters.sortedWith(
            compareByDescending<CampusPoster> { it.date }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
        )
    }

    val studentDptRanked = remember(students, dptWeekly, selectedWeek) {
        students.map { st ->
            val entries = dptWeekly.filter { it.studentDocId == st.id && it.week == selectedWeek }
            val max = entries.sumOf { it.maxMarks }
            val pct = if (max > 0L) (entries.sumOf { it.scored } * 100.0) / max else -1.0
            Triple(st, entries, pct)
        }.sortedByDescending { it.third }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Principal Profile Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PRINCIPAL COMMAND CENTER", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                        Spacer(Modifier.height(4.dp))
                        Text(adminUser.fullName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "ID: ${adminUser.userId} • Phone: ${adminUser.phone}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenProfileEdit,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GoldAmber,
                            contentColor = NavyDeep,
                        ),
                        modifier = Modifier.testTag("admin_edit_profile_btn"),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Profile")
                    }
                }
            }
        }

        // Live Counters
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Total Students",
                    value = "${students.size}",
                    subtitle = "Nursery to XII",
                    icon = Icons.Filled.School,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Staff & Library",
                    value = "${teachers.size + feeManagers.size + librarians.size}",
                    subtitle = "${teachers.size} Tch • ${feeManagers.size} Fee • ${librarians.size} Lib",
                    icon = Icons.Filled.Groups,
                    accentColor = IndigoAccent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Today Attendance",
                    value = "$attendancePct%",
                    subtitle = "$presentToday/${todayAttendance.size} marked present",
                    icon = Icons.Filled.FactCheck,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "DPT Compliance",
                    value = "$dptCompliancePct%",
                    subtitle = "$dptHeldCount/${todayDpts.size} DPTs held today",
                    icon = Icons.Filled.Quiz,
                    accentColor = GoldAmberDark,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Fee Collected",
                    value = formatInr(totalCollected),
                    subtitle = "Verified receipts",
                    icon = Icons.Filled.AccountBalanceWallet,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Pending Dues",
                    value = formatInr(totalPending),
                    subtitle = "$pendingVerificationCount UTR pending verify",
                    icon = Icons.Filled.PendingActions,
                    accentColor = RoseError,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // School Payment System Oversight Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("PAYMENT SYSTEM & FEE COLLECTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Surface(color = GoldSoft, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "$pendingVerificationCount Pending Verify",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyDeep,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text("Official Merchant UPI: ${gateway.merchantUpi} (${gateway.payeeName})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Bank: ${gateway.bankName} • A/C: ${gateway.accountNo} • IFSC: ${gateway.ifsc}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (payments.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                        payments.take(3).forEach { pay ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${pay.studentName} (${pay.className})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("${formatInr(pay.amount)} • ${pay.status}", style = MaterialTheme.typography.labelSmall, color = if (pay.status == "VERIFIED") EmeraldSuccess else GoldAmberDark)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        FilledTonalButton(
                            onClick = onOpenFeePayments,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            modifier = Modifier.weight(1f).testTag("admin_open_fee_payments_btn"),
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("All Fee Payments")
                        }
                        OutlinedButton(
                            onClick = onOpenCraftStudio,
                            modifier = Modifier.weight(1f).testTag("admin_open_craft_studio_btn"),
                        ) {
                            Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Craft & Projects")
                        }
                    }
                    val activeLibraryCount = libraryIssues.count { it.status != "RETURNED" }
                    OutlinedButton(
                        onClick = onOpenLibraryDesk,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_open_library_desk_btn"),
                    ) {
                        Icon(Icons.Filled.LocalLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("School Library Register ($activeLibraryCount Issued / Not Returned)")
                    }
                }
            }
        }

        // Staff Leave Approvals (Teachers, Fee Managers & Librarians)
        item {
            AdminStaffLeaveApprovalsCard(
                leaves = leaves,
                onUpdateLeaveStatus = onUpdateLeaveStatus,
            )
        }

        // Daily Campus Celebration / Event Poster Publisher & Live Gallery
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "DAILY CAMPUS POSTER & EVENT PHOTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAmberDark,
                    )
                    Text(
                        "Upload Today's Event, Celebration or Thought Poster",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = posterTitle,
                        onValueChange = { posterTitle = it.take(140) },
                        label = { Text("Poster / Celebration Title (e.g. Science Day)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_poster_title_input"),
                    )
                    OutlinedTextField(
                        value = posterSubtitle,
                        onValueChange = { posterSubtitle = it.take(180) },
                        label = { Text("Caption / Highlight (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_poster_subtitle_input"),
                    )
                    SchoolDatePickerField(
                        label = "Event / Poster Date",
                        selectedDate = posterDate,
                        onDateSelected = { posterDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "admin_poster_date_picker",
                    )
                    OutlinedButton(
                        onClick = {
                            posterPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("admin_poster_pick_photo_btn"),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Pick Campus Poster Photo", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (posterImageBase64.isBlank()) "Select Poster Photo from Gallery" else "Change Selected Poster Photo")
                    }

                    val previewBmp = remember(posterImageBase64) { decodeAttachmentBitmap(posterImageBase64) }
                    if (previewBmp != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GoldAmber),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Image(
                                bitmap = previewBmp,
                                contentDescription = "Selected poster preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (posterTitle.isBlank() || posterImageBase64.isBlank()) {
                                onShowMessage("Please enter a poster title and select a photo first.")
                                return@Button
                            }
                            onPublishCampusPoster(
                                CampusPoster(
                                    title = posterTitle.trim(),
                                    subtitle = posterSubtitle.trim(),
                                    date = posterDate.ifBlank { SchoolCatalog.todayIso() },
                                    imageData = posterImageBase64,
                                    postedBy = adminUser.fullName,
                                )
                            )
                            posterTitle = ""
                            posterSubtitle = ""
                            posterImageBase64 = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_poster_publish_btn"),
                    ) {
                        Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Publish Poster to Student Home")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(
                        "PUBLISHED CAMPUS POSTERS (${sortedPosters.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sortedPosters.isEmpty()) {
                        Text(
                            "No campus posters uploaded yet. Students will see each poster you publish here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            sortedPosters.forEach { poster ->
                                val bmp = remember(poster.imageData) { decodeAttachmentBitmap(poster.imageData) }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .width(220.dp)
                                        .clickable { zoomPoster = poster },
                                ) {
                                    Column {
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp,
                                                contentDescription = poster.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(poster.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    listOfNotNull(poster.date, poster.subtitle.takeIf { it.isNotBlank() }).joinToString(" • "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            IconButton(onClick = { onDeleteCampusPoster(poster) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete poster", tint = RoseError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-App App Update Publisher Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "IN-APP APP UPDATE PUBLISHER",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess,
                        )
                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "Installed v${BuildConfig.VERSION_NAME} (B${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        "Push New App Version to All Users Inside App",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "When published on Google Play, updates install natively inside the app with zero browser redirect. For direct website APKs, paste your https://doonpublicschooletah.in/...apk or Play Store link below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = updateVersionCodeStr,
                            onValueChange = { ch -> updateVersionCodeStr = ch.filter { it.isDigit() }.take(6) },
                            label = { Text("Version Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("admin_update_code_input"),
                        )
                        OutlinedTextField(
                            value = updateVersionName,
                            onValueChange = { updateVersionName = it.take(30) },
                            label = { Text("Version Name (e.g. 1.2)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f).testTag("admin_update_name_input"),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            updateApkUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Shop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use Official Google Play Store Link")
                    }
                    OutlinedTextField(
                        value = updateApkUrl,
                        onValueChange = { updateApkUrl = it.take(500) },
                        label = { Text("Play Store or Direct School APK URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_update_url_input"),
                    )
                    OutlinedTextField(
                        value = updateReleaseNotes,
                        onValueChange = { updateReleaseNotes = it.take(400) },
                        label = { Text("What's New / Update Notes") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("admin_update_notes_input"),
                    )
                    Button(
                        onClick = {
                            val parsedCode = updateVersionCodeStr.toLongOrNull()?.coerceAtLeast(1L)
                                ?: BuildConfig.VERSION_CODE.toLong()
                            val resolvedUrl = updateApkUrl.trim()
                            if (resolvedUrl.isBlank()) {
                                onShowMessage("Enter your Google Play Store link or direct school APK URL first.")
                                return@Button
                            }
                            onSaveGatewayConfig(
                                gateway.copy(
                                    latestVersionCode = parsedCode,
                                    latestVersionName = updateVersionName.trim().ifBlank { BuildConfig.VERSION_NAME },
                                    apkDownloadUrl = resolvedUrl,
                                    githubRepoSlug = "",
                                    updateNotes = updateReleaseNotes.trim(),
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_publish_update_btn"),
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Publish Update to All Phones")
                    }
                }
            }
        }

        // 4 Official Academic Wings Overview
        item {
            Text(
                text = "4 OFFICIAL ACADEMIC WINGS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SchoolCatalog.wings.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { wing ->
                            val wingStudentCount = students.count { it.wing == wing.name || it.className in wing.classes }
                            val wingTeacherCount = teachers.count { it.wing == wing.name || it.className in wing.classes }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(wing.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(wing.classesLabel, style = MaterialTheme.typography.labelSmall, color = GoldAmberDark)
                                    Text(
                                        "$wingStudentCount Students • $wingTeacherCount Teachers",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Student-by-Student DPT Results
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "EACH STUDENT'S DPT RESULTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (students.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onOpenProgressDashboard,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_open_progress_chart_btn"),
                        ) {
                            Icon(Icons.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Progress Line Chart", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    SchoolCatalog.dptWeeks.forEach { wk ->
                        FilterChip(selected = selectedWeek == wk, onClick = { selectedWeek = wk }, label = { Text(wk) })
                    }
                }
            }
        }

        items(studentDptRanked.withIndex().toList(), key = { "admin_dpt_${it.value.first.id}" }) { indexed ->
            val rankNum = indexed.index + 1
            val (student, entries, pct) = indexed.value
            StudentDptResultCard(
                student = student,
                weekLabel = selectedWeek,
                studentEntries = entries,
                rankLabel = if (pct >= 0.0) "#$rankNum" else "—",
            )
        }

        // Today's DPT Oversight
        item {
            Text(
                text = "TODAY'S DPT OVERSIGHT (${SchoolCatalog.todayIso()})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (todayDpts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No Daily Practice Tests logged for today yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(todayDpts, key = { it.id }) { dpt ->
                val isExam = dpt.assessmentType == "EXAM"
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isExam) IndigoAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(
                                        color = if (isExam) IndigoAccent else NavyDeep,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = if (isExam) "EXAM" else "DPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExam) Color.White else GoldAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        "${dpt.className}-${dpt.section} • ${dpt.subject}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    if (dpt.held) "Topic: ${dpt.topic} (${dpt.maxMarks} Marks)"
                                    else "Reason: ${dpt.reason}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (dpt.notes.isNotBlank()) {
                                    Text(
                                        dpt.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "Teacher: ${dpt.teacherName} (${dpt.teacherId})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                color = if (dpt.held) EmeraldContainer else GoldSoft,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = if (dpt.held) "HELD" else "NOT HELD",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dpt.held) EmeraldSuccess else GoldAmberDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                        if (dpt.attachmentData.isNotBlank()) {
                            AttachmentPreviewBlock(
                                attachmentName = dpt.attachmentName,
                                attachmentMimeType = dpt.attachmentMimeType,
                                attachmentData = dpt.attachmentData,
                                onShowMessage = onShowMessage,
                            )
                        }
                    }
                }
            }
        }
    }

    zoomPoster?.let { poster ->
        val zoomBmp = remember(poster.imageData) { decodeAttachmentBitmap(poster.imageData) }
        if (zoomBmp != null) {
            Dialog(onDismissRequest = { zoomPoster = null }) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(poster.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(poster.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { zoomPoster = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close poster preview")
                            }
                        }
                        Image(
                            bitmap = zoomBmp,
                            contentDescription = poster.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAccountsTab(
    users: List<SchoolUser>,
    onSaveUser: (SchoolUser) -> Unit,
    onDeleteUser: (SchoolUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingDocId by remember { mutableStateOf("") }
    var editingExistingCreatedAt by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var selectedRole by remember { mutableStateOf("STUDENT") }
    var extraRoles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fullName by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("Class X") }
    var section by remember { mutableStateOf("A") }
    var subject by remember { mutableStateOf("Mathematics") }
    var parentName by remember { mutableStateOf("") }
    var monthlyTuition by remember { mutableStateOf("2800") }
    var transportFee by remember { mutableStateOf("900") }
    var searchQuery by remember { mutableStateOf("") }
    var filterRole by remember { mutableStateOf("ALL") }

    var classMenuExpanded by remember { mutableStateOf(false) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedRole, editingDocId, users) {
        if (editingDocId.isBlank() && selectedRole == "STUDENT") {
            userId = SchoolCatalog.nextStudentRollNumber(users)
        }
    }

    fun resetForm() {
        editingDocId = ""
        editingExistingCreatedAt = null
        extraRoles = emptySet()
        fullName = ""
        userId = if (selectedRole == "STUDENT") SchoolCatalog.nextStudentRollNumber(users) else ""
        password = ""
        phone = ""
        parentName = ""
    }

    val filteredUsers = remember(users, searchQuery, filterRole) {
        users.filter { u ->
            val roleOk = filterRole == "ALL" || u.role == filterRole
            val q = searchQuery.trim().lowercase()
            val queryOk = q.isEmpty() ||
                u.fullName.lowercase().contains(q) ||
                u.userId.lowercase().contains(q) ||
                u.className.lowercase().contains(q)
            roleOk && queryOk
        }.sortedBy { it.role }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (editingDocId.isBlank()) "Create New Login Account" else "Edit Account",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (editingDocId.isNotBlank()) {
                            TextButton(onClick = { resetForm() }) { Text("Cancel Edit") }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    ) {
                        listOf(
                            "STUDENT" to "Student",
                            "TEACHER" to "Teacher",
                            "LIBRARIAN" to "Librarian",
                            "FEE_MANAGER" to "Fee Mgr",
                            "ADMIN" to "Principal / Admin",
                        ).forEach { (rKey, label) ->
                            FilterChip(
                                selected = selectedRole == rKey,
                                onClick = {
                                    selectedRole = rKey
                                    if (rKey != "TEACHER") extraRoles = emptySet()
                                    if (editingDocId.isBlank()) {
                                        userId = if (rKey == "STUDENT") SchoolCatalog.nextStudentRollNumber(users) else ""
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("admin_create_role_$rKey"),
                            )
                        }
                    }

                    if (selectedRole == "TEACHER") {
                        Text(
                            "EXTRA ROLES FOR THIS TEACHER (MULTI-ROLE ACCESS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Teacher (Primary)") },
                                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                            listOf(
                                "LIBRARIAN" to "Librarian",
                                "FEE_MANAGER" to "Fee Manager",
                                "ADMIN" to "Principal / Admin",
                            ).forEach { (extraKey, extraLabel) ->
                                val picked = extraKey in extraRoles
                                FilterChip(
                                    selected = picked,
                                    onClick = {
                                        extraRoles = if (picked) extraRoles - extraKey else extraRoles + extraKey
                                    },
                                    label = { Text(extraLabel) },
                                    leadingIcon = if (picked) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("admin_teacher_extra_role_$extraKey"),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_fullname_input"),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = userId,
                            onValueChange = { userId = it },
                            label = {
                                Text(
                                    when (selectedRole) {
                                        "STUDENT" -> "Roll / Adm No (Auto)"
                                        "TEACHER" -> "Employee ID"
                                        "LIBRARIAN" -> "Librarian ID"
                                        "ADMIN" -> "Admin ID"
                                        else -> "Staff ID"
                                    }
                                )
                            },
                            trailingIcon = if (selectedRole == "STUDENT" && editingDocId.isBlank()) {
                                {
                                    IconButton(onClick = { userId = SchoolCatalog.nextStudentRollNumber(users) }) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Generate Next Roll No")
                                    }
                                }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("admin_userid_input"),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Login Password") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("admin_password_input"),
                        )
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("admin_phone_input"),
                    )

                    if (selectedRole == "STUDENT" || selectedRole == "TEACHER") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { classMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("$className (${SchoolCatalog.wingForClass(className)})")
                                }
                                DropdownMenu(
                                    expanded = classMenuExpanded,
                                    onDismissRequest = { classMenuExpanded = false },
                                ) {
                                    SchoolCatalog.allClasses.forEach { cls ->
                                        DropdownMenuItem(
                                            text = { Text("$cls • ${SchoolCatalog.wingForClass(cls)}") },
                                            onClick = {
                                                className = cls
                                                classMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SchoolCatalog.sections.forEach { sec ->
                                    FilterChip(
                                        selected = section == sec,
                                        onClick = { section = sec },
                                        label = { Text(sec) },
                                    )
                                }
                            }
                        }
                    }

                    if (selectedRole == "TEACHER") {
                        val suggestedSubjects = remember(className) {
                            (SchoolCatalog.subjectsForClass(className) + SchoolCatalog.allSubjects).distinct()
                        }
                        val selectedSubjectTokens = remember(subject) {
                            subject.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { subjectMenuExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_pick_subject_btn"),
                            ) {
                                Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Subjects (Multi-Select): ${subject.ifBlank { "Choose" }}")
                            }
                            DropdownMenu(
                                expanded = subjectMenuExpanded,
                                onDismissRequest = { subjectMenuExpanded = false },
                            ) {
                                suggestedSubjects.forEach { sub ->
                                    val isChecked = sub in selectedSubjectTokens
                                    DropdownMenuItem(
                                        text = { Text(sub) },
                                        leadingIcon = {
                                            Checkbox(checked = isChecked, onCheckedChange = null)
                                        },
                                        onClick = {
                                            val updated = if (isChecked) {
                                                selectedSubjectTokens - sub
                                            } else {
                                                selectedSubjectTokens + sub
                                            }
                                            subject = updated.joinToString(", ")
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Teaching Subject(s) (comma-separated)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (selectedRole == "STUDENT") {
                        OutlinedTextField(
                            value = parentName,
                            onValueChange = { parentName = it },
                            label = { Text("Father / Mother Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = monthlyTuition,
                                onValueChange = { monthlyTuition = it },
                                label = { Text("Monthly Tuition (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = transportFee,
                                onValueChange = { transportFee = it },
                                label = { Text("Bus Fee (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (fullName.isBlank() || userId.isBlank() || password.isBlank()) return@Button
                            val wing = when (selectedRole) {
                                "FEE_MANAGER" -> "Accounts Office"
                                "LIBRARIAN" -> "School Library"
                                "ADMIN" -> "All Wings"
                                else -> SchoolCatalog.wingForClass(className)
                            }
                            val finalExtraRoles = if (selectedRole == "TEACHER") extraRoles.toList() else emptyList()
                            onSaveUser(
                                SchoolUser(
                                    id = editingDocId,
                                    role = selectedRole,
                                    primaryRole = selectedRole,
                                    roles = (listOf(selectedRole) + finalExtraRoles).distinct(),
                                    extraRoles = finalExtraRoles,
                                    userId = userId.trim(),
                                    password = password.trim(),
                                    fullName = fullName.trim(),
                                    phone = phone.trim(),
                                    wing = wing,
                                    className = if (selectedRole == "FEE_MANAGER" || selectedRole == "LIBRARIAN" || selectedRole == "ADMIN") "" else className,
                                    section = if (selectedRole == "FEE_MANAGER" || selectedRole == "LIBRARIAN" || selectedRole == "ADMIN") "" else section,
                                    subject = when (selectedRole) {
                                        "TEACHER" -> subject.trim()
                                        "LIBRARIAN" -> "Library"
                                        "ADMIN" -> "Administration"
                                        else -> ""
                                    },
                                    parentName = if (selectedRole == "STUDENT") parentName.trim() else "",
                                    monthlyTuition = if (selectedRole == "STUDENT") (monthlyTuition.toLongOrNull() ?: 0L) else 0L,
                                    transportFee = if (selectedRole == "STUDENT") (transportFee.toLongOrNull() ?: 0L) else 0L,
                                    createdAt = editingExistingCreatedAt,
                                )
                            )
                            resetForm()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_save_account_btn"),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (editingDocId.isBlank()) "Create Live Account" else "Save Account Changes")
                    }
                }
            }
        }

        // Directory Search & Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Directory (Name, ID, Class)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf(
                        "ALL" to "All",
                        "STUDENT" to "Students",
                        "TEACHER" to "Teachers",
                        "LIBRARIAN" to "Librarians",
                        "FEE_MANAGER" to "Fee Managers",
                        "ADMIN" to "Admins",
                    )
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = filterRole == key,
                                onClick = { filterRole = key },
                                label = { Text(label) },
                            )
                        }
                }
            }
        }

        items(filteredUsers, key = { it.id }) { u ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(u.fullName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Surface(color = GoldSoft, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    u.effectiveRoles().joinToString(" + ") { it.replace('_', ' ') },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyDeep,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text("ID: ${u.userId} | PW: ${u.password} | Ph: ${u.phone}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (u.className.isNotBlank()) {
                            Text(
                                "${u.className}-${u.section} (${u.wing})" +
                                    if (u.subject.isNotBlank()) " • ${u.subject}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row {
                        IconButton(
                            onClick = {
                                editingDocId = u.id
                                editingExistingCreatedAt = u.createdAt
                                selectedRole = u.primaryRole.ifBlank { u.role }
                                extraRoles = u.extraRoles.toSet()
                                fullName = u.fullName
                                userId = u.userId
                                password = u.password
                                phone = u.phone
                                if (u.className.isNotBlank()) className = u.className
                                if (u.section.isNotBlank()) section = u.section
                                if (u.subject.isNotBlank()) subject = u.subject
                                parentName = u.parentName
                                monthlyTuition = u.monthlyTuition.toString()
                                transportFee = u.transportFee.toString()
                            },
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${u.fullName}", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (u.role != "ADMIN") {
                            IconButton(onClick = { onDeleteUser(u) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${u.fullName}", tint = RoseError)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AdminNewspaperTarget(val label: String) {
    SCHOOL_OR_CLASS("School / Class"),
    ALL_TEACHERS("All Teachers"),
    SINGLE_TEACHER("Individual Teacher"),
}

@Composable
private fun AdminCircularsTab(
    adminUser: SchoolUser,
    teachers: List<SchoolUser>,
    circulars: List<Circular>,
    onPostCircular: (Circular) -> Unit,
    onDeleteCircular: (Circular) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Newspaper") }
    var targetMode by remember { mutableStateOf(AdminNewspaperTarget.SCHOOL_OR_CLASS) }
    var selectedTargetScope by remember { mutableStateOf("All Classes") }
    var selectedTargetSection by remember { mutableStateOf("ALL") }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val sortedTeachers = remember(teachers) { teachers.sortedBy { it.fullName } }
    var teacherSearch by remember { mutableStateOf("") }
    var teacherMenuOpen by remember { mutableStateOf(false) }
    var selectedTeacher by remember(sortedTeachers) { mutableStateOf(sortedTeachers.firstOrNull()) }
    val filteredTeachers = remember(sortedTeachers, teacherSearch) {
        val q = teacherSearch.trim()
        if (q.isBlank()) sortedTeachers
        else sortedTeachers.filter {
            it.fullName.contains(q, ignoreCase = true) ||
                it.userId.contains(q, ignoreCase = true) ||
                it.subject.contains(q, ignoreCase = true)
        }
    }

    var date by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var content by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<PickedAttachment?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                readAttachmentFromUri(context, uri, forceImage = true)
                    .onSuccess { pendingAttachment = it }
                    .onFailure { onShowMessage(it.localizedMessage ?: "Unable to read photo.") }
            }
        },
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                readAttachmentFromUri(context, uri, forceImage = false)
                    .onSuccess { pendingAttachment = it }
                    .onFailure { onShowMessage(it.localizedMessage ?: "Unable to read document.") }
            }
        },
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "School Newspaper, Circulars & Teacher Messages",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Send announcements to the entire school, a class/wing, all teachers, or directly to an individual teacher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Audience Mode Selector: School/Class, All Teachers, Individual Teacher
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AdminNewspaperTarget.entries.forEach { mode ->
                            FilterChip(
                                selected = targetMode == mode,
                                onClick = { targetMode = mode },
                                leadingIcon = {
                                    Icon(
                                        when (mode) {
                                            AdminNewspaperTarget.SCHOOL_OR_CLASS -> Icons.Filled.School
                                            AdminNewspaperTarget.ALL_TEACHERS -> Icons.Filled.Groups
                                            AdminNewspaperTarget.SINGLE_TEACHER -> Icons.Filled.Person
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("admin_target_${mode.name.lowercase()}"),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Headline / Circular Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("circular_title_input"),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf("Newspaper", "Staff Notice", "Notice", "Holiday", "Exam Datesheet", "Event").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }

                    when (targetMode) {
                        AdminNewspaperTarget.ALL_TEACHERS -> {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = EmeraldSuccess)
                                    Text(
                                        "Broadcast to ALL Teachers (${sortedTeachers.size} faculty members). Hidden from students.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldSuccess,
                                    )
                                }
                            }
                        }
                        AdminNewspaperTarget.SINGLE_TEACHER -> {
                            OutlinedTextField(
                                value = teacherSearch,
                                onValueChange = { teacherSearch = it },
                                label = { Text("Search Teacher (Name, Employee ID, Subject)") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_teacher_search_input"),
                            )
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { teacherMenuOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("admin_teacher_picker_dropdown"),
                                ) {
                                    Icon(Icons.Filled.PersonPin, contentDescription = "Select Teacher", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Recipient Teacher", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = selectedTeacher?.let { t ->
                                                val meta = listOfNotNull(
                                                    t.userId.takeIf { it.isNotBlank() },
                                                    t.subject.takeIf { it.isNotBlank() },
                                                ).joinToString(" • ")
                                                "${t.fullName}${if (meta.isNotBlank()) " ($meta)" else ""}"
                                            } ?: "No teachers available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = teacherMenuOpen,
                                    onDismissRequest = { teacherMenuOpen = false },
                                ) {
                                    if (filteredTeachers.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No matching teacher found") },
                                            onClick = { teacherMenuOpen = false },
                                        )
                                    } else {
                                        filteredTeachers.forEach { t ->
                                            val subLabel = t.subject.ifBlank { t.className.ifBlank { "Faculty" } }
                                            DropdownMenuItem(
                                                text = { Text("${t.fullName} (${t.userId} • $subLabel)") },
                                                onClick = {
                                                    selectedTeacher = t
                                                    teacherMenuOpen = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AdminNewspaperTarget.SCHOOL_OR_CLASS -> {
                            // Target Class / Wing Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { classDropdownExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("admin_circular_class_dropdown"),
                                ) {
                                    Icon(Icons.Filled.Class, contentDescription = "Select Class", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Target Class / Wing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = if (selectedTargetScope in SchoolCatalog.allClasses) {
                                                val secLabel = if (selectedTargetSection == "ALL") "All Sections" else "Section $selectedTargetSection"
                                                "$selectedTargetScope ($secLabel)"
                                            } else {
                                                selectedTargetScope
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = classDropdownExpanded,
                                    onDismissRequest = { classDropdownExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Classes (Entire School)") },
                                        onClick = {
                                            selectedTargetScope = "All Classes"
                                            classDropdownExpanded = false
                                        },
                                    )
                                    SchoolCatalog.wings.forEach { wing ->
                                        DropdownMenuItem(
                                            text = { Text("Wing: ${wing.name} (${wing.classesLabel})") },
                                            onClick = {
                                                selectedTargetScope = "Wing: ${wing.name}"
                                                classDropdownExpanded = false
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    SchoolCatalog.allClasses.forEach { cls ->
                                        DropdownMenuItem(
                                            text = { Text("$cls (${SchoolCatalog.wingForClass(cls)})") },
                                            onClick = {
                                                selectedTargetScope = cls
                                                classDropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }

                            if (selectedTargetScope in SchoolCatalog.allClasses) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    FilterChip(
                                        selected = selectedTargetSection == "ALL",
                                        onClick = { selectedTargetSection = "ALL" },
                                        label = { Text("All Sections") },
                                    )
                                    SchoolCatalog.sections.forEach { sec ->
                                        FilterChip(
                                            selected = selectedTargetSection == sec,
                                            onClick = { selectedTargetSection = sec },
                                            label = { Text("Sec $sec") },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SchoolDatePickerField(
                        label = "Publish Date",
                        selectedDate = date,
                        onDateSelected = { date = it },
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "admin_circular_date_picker",
                    )

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Newspaper / Message Details") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("circular_content_input"),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Attach Photo", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "text/plain",
                                        "image/*",
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Attach Doc/PDF", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    AttachmentPickerChip(
                        attachment = pendingAttachment,
                        onClear = { pendingAttachment = null },
                    )

                    Button(
                        onClick = {
                            if (title.isBlank() || content.isBlank()) {
                                onShowMessage("Please enter a headline and message details.")
                                return@Button
                            }
                            if (targetMode == AdminNewspaperTarget.SINGLE_TEACHER && selectedTeacher == null) {
                                onShowMessage("Please select an individual teacher to message.")
                                return@Button
                            }

                            val circular = when (targetMode) {
                                AdminNewspaperTarget.ALL_TEACHERS -> Circular(
                                    title = title.trim(),
                                    category = category,
                                    audience = "All Teachers",
                                    targetWing = "",
                                    targetClass = "",
                                    targetSection = "",
                                    date = date.ifBlank { SchoolCatalog.todayIso() },
                                    content = content.trim(),
                                    postedBy = adminUser.fullName,
                                    recipientDocId = "",
                                    recipientName = "All Teachers",
                                    senderRole = "ADMIN",
                                    attachmentName = pendingAttachment?.name ?: "",
                                    attachmentMimeType = pendingAttachment?.mimeType ?: "",
                                    attachmentData = pendingAttachment?.base64Data ?: "",
                                )
                                AdminNewspaperTarget.SINGLE_TEACHER -> {
                                    val rec = selectedTeacher!!
                                    Circular(
                                        title = title.trim(),
                                        category = category,
                                        audience = "Direct to Teacher",
                                        targetWing = "",
                                        targetClass = "",
                                        targetSection = "",
                                        date = date.ifBlank { SchoolCatalog.todayIso() },
                                        content = content.trim(),
                                        postedBy = adminUser.fullName,
                                        recipientDocId = rec.id,
                                        recipientName = "${rec.fullName} (${rec.userId})",
                                        senderRole = "ADMIN",
                                        attachmentName = pendingAttachment?.name ?: "",
                                        attachmentMimeType = pendingAttachment?.mimeType ?: "",
                                        attachmentData = pendingAttachment?.base64Data ?: "",
                                    )
                                }
                                AdminNewspaperTarget.SCHOOL_OR_CLASS -> {
                                    val tClass = if (selectedTargetScope in SchoolCatalog.allClasses) selectedTargetScope else ""
                                    val tSection = if (tClass.isNotBlank() && selectedTargetSection != "ALL") selectedTargetSection else ""
                                    val tWing = when {
                                        tClass.isNotBlank() -> SchoolCatalog.wingForClass(tClass)
                                        selectedTargetScope.startsWith("Wing: ") -> selectedTargetScope.removePrefix("Wing: ").trim()
                                        else -> ""
                                    }
                                    val audLabel = when {
                                        tClass.isNotBlank() && tSection.isNotBlank() -> "$tClass-$tSection"
                                        tClass.isNotBlank() -> "$tClass (All Sections)"
                                        tWing.isNotBlank() -> tWing
                                        else -> "All Classes"
                                    }
                                    Circular(
                                        title = title.trim(),
                                        category = category,
                                        audience = audLabel,
                                        targetWing = tWing,
                                        targetClass = tClass,
                                        targetSection = tSection,
                                        date = date.ifBlank { SchoolCatalog.todayIso() },
                                        content = content.trim(),
                                        postedBy = adminUser.fullName,
                                        senderRole = "ADMIN",
                                        attachmentName = pendingAttachment?.name ?: "",
                                        attachmentMimeType = pendingAttachment?.mimeType ?: "",
                                        attachmentData = pendingAttachment?.base64Data ?: "",
                                    )
                                }
                            }

                            onPostCircular(circular)
                            title = ""
                            content = ""
                            pendingAttachment = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("circular_publish_btn"),
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (targetMode) {
                                AdminNewspaperTarget.ALL_TEACHERS -> "Send Message to All Teachers"
                                AdminNewspaperTarget.SINGLE_TEACHER -> "Send Message to ${selectedTeacher?.fullName ?: "Teacher"}"
                                AdminNewspaperTarget.SCHOOL_OR_CLASS -> "Publish Newspaper / Circular"
                            }
                        )
                    }
                }
            }
        }

        items(circulars, key = { it.id }) { c ->
            val isAllTeachers = c.audience.equals("All Teachers", ignoreCase = true)
            val isDirectTeacher = c.recipientDocId.isNotBlank()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    when {
                        isDirectTeacher -> IndigoAccent.copy(alpha = 0.55f)
                        isAllTeachers -> EmeraldSuccess.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        ) {
                            Surface(color = GoldSoft, shape = RoundedCornerShape(6.dp)) {
                                Text(c.category, style = MaterialTheme.typography.labelSmall, color = NavyDeep, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            if (isDirectTeacher) {
                                Surface(color = IndigoAccent.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "Direct: ${c.recipientName.ifBlank { "Teacher" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            } else if (isAllTeachers) {
                                Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "ALL TEACHERS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text("${c.audience} • ${c.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteCircular(c) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete circular", tint = RoseError)
                        }
                    }
                    Text(c.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(c.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.attachmentData.isNotBlank()) {
                        AttachmentPreviewBlock(
                            attachmentName = c.attachmentName,
                            attachmentMimeType = c.attachmentMimeType,
                            attachmentData = c.attachmentData,
                            onShowMessage = onShowMessage,
                        )
                    }
                    Text(
                        "Posted by: ${c.postedBy} (${c.senderRole})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
