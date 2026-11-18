package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiTutorPersona
import com.example.ui.viewmodel.AiTutorUiState
import com.example.ui.viewmodel.CraftAiImageState

@Composable
fun StudentScreen(
    activeTab: Int,
    student: SchoolUser,
    allUsers: List<SchoolUser>,
    gateway: GatewayConfig,
    circulars: List<Circular>,
    dptDaily: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    attendance: List<AttendanceRecord>,
    leaves: List<LeaveRequest>,
    homework: List<HomeworkItem>,
    termMarks: List<TermMarkCard>,
    feeItems: List<FeeItem>,
    payments: List<FeePayment>,
    doubts: List<DoubtMessage>,
    timetables: List<ClassTimetable> = emptyList(),
    campusPosters: List<CampusPoster> = emptyList(),
    craftProjects: List<CraftProject> = emptyList(),
    likedProjectIds: Set<String> = emptySet(),
    likingProjectIds: Set<String> = emptySet(),
    aiMessages: List<AiChatMessage> = emptyList(),
    aiTutorState: AiTutorUiState = AiTutorUiState.Idle,
    aiPersona: AiTutorPersona = AiTutorPersona.STUDY_TUTOR,
    craftAiImageState: CraftAiImageState = CraftAiImageState.Idle,
    generatingCraftPostId: String? = null,
    onSubmitPaymentProof: (SchoolUser, List<FeeItem>, String, String, String) -> Unit,
    onSubmitLeave: (LeaveRequest) -> Unit,
    onSubmitDoubt: (DoubtMessage) -> Unit,
    onAskAiTutor: (String) -> Unit = {},
    onSelectAiPersona: (AiTutorPersona) -> Unit = {},
    onClearAiChat: () -> Unit = {},
    onGenerateCraftAiImage: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onClearCraftPreview: () -> Unit = {},
    onPublishCraftProject: (CraftProject) -> Unit = {},
    onGenerateCraftImageForPost: (CraftProject) -> Unit = {},
    onApplaudCraftProject: (CraftProject) -> Unit = {},
    onDeleteCraftProject: (CraftProject) -> Unit = {},
    onShowMessage: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIdCard by remember { mutableStateOf(false) }
    var openFeeHistoryInitially by remember { mutableStateOf(false) }
    var showNoticesScreen by remember { mutableStateOf(false) }

    val visibleCirculars = remember(circulars, student.id, student.className, student.section, student.wing) {
        circulars.filter { c ->
            when {
                c.recipientDocId.isNotBlank() -> c.recipientDocId == student.id
                c.targetClass.isNotBlank() ->
                    c.targetClass == student.className &&
                        (c.targetSection.isBlank() || c.targetSection == student.section)
                c.targetWing.isNotBlank() ->
                    c.targetWing == student.wing ||
                        c.targetWing == SchoolCatalog.wingForClass(student.className)
                else -> true
            }
        }.sortedWith(
            compareByDescending<Circular> { it.date }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
        )
    }

    if (showNoticesScreen) {
        BackHandler { showNoticesScreen = false }
        StudentNoticesScreen(
            student = student,
            circulars = visibleCirculars,
            onBack = { showNoticesScreen = false },
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
        return
    }

    when (activeTab) {
        0 -> StudentHomeTab(
            student = student,
            circulars = visibleCirculars,
            campusPosters = campusPosters,
            dptDaily = dptDaily.filter { it.className == student.className && it.section == student.section },
            dptWeekly = dptWeekly,
            attendance = attendance.filter { it.studentDocId == student.id },
            feeItems = feeItems.filter { it.studentDocId == student.id },
            onOpenIdCard = { showIdCard = true },
            onOpenProfileEdit = onOpenProfileEdit,
            onOpenNotices = { showNoticesScreen = true },
            onGoToDptHub = { onNavigateToTab(1) },
            onGoToFeesPay = {
                openFeeHistoryInitially = false
                onNavigateToTab(2)
            },
            onGoToFeePayments = {
                openFeeHistoryInitially = true
                onNavigateToTab(2)
            },
            onGoToAcademics = { onNavigateToTab(3) },
            onGoToCraftStudio = { onNavigateToTab(4) },
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
        1 -> StudentDptHubTab(
            student = student,
            classmates = allUsers.filter {
                it.role == "STUDENT" && it.className == student.className && it.section == student.section
            }.sortedBy { it.userId },
            dptDaily = dptDaily.filter { it.className == student.className && it.section == student.section },
            dptWeekly = dptWeekly,
            termMarks = termMarks.filter { it.studentDocId == student.id },
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
        2 -> StudentFeesTab(
            student = student,
            gateway = gateway,
            feeItems = feeItems.filter { it.studentDocId == student.id },
            payments = payments.filter { it.studentDocId == student.id },
            openHistoryInitially = openFeeHistoryInitially,
            onConsumeHistoryInitial = { openFeeHistoryInitially = false },
            onSubmitPaymentProof = onSubmitPaymentProof,
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
        3 -> StudentAcademicsTab(
            student = student,
            homework = homework.filter { it.className == student.className && it.section == student.section },
            leaves = leaves.filter { it.studentDocId == student.id },
            doubts = doubts.filter { it.studentDocId == student.id },
            timetables = timetables,
            onSubmitLeave = onSubmitLeave,
            onSubmitDoubt = onSubmitDoubt,
            modifier = modifier,
        )
        else -> CraftStudioScreen(
            currentUser = student,
            projects = craftProjects,
            likedProjectIds = likedProjectIds,
            likingProjectIds = likingProjectIds,
            craftAiImageState = craftAiImageState,
            generatingPostId = generatingCraftPostId,
            onGeneratePreviewImage = onGenerateCraftAiImage,
            onClearPreviewImage = onClearCraftPreview,
            onPublishProject = onPublishCraftProject,
            onGenerateImageForPost = onGenerateCraftImageForPost,
            onApplaudPost = onApplaudCraftProject,
            onDeletePost = onDeleteCraftProject,
            onShowMessage = onShowMessage,
            modifier = modifier,
        )
    }

    if (showIdCard) {
        StudentIdCardDialog(student = student, onDismiss = { showIdCard = false })
    }
}

@Composable
private fun StudentNoticesScreen(
    student: SchoolUser,
    circulars: List<Circular>,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf(
        "All",
        "Holidays",
        "Notices",
        "Exam Datesheet",
        "From Teachers",
        "From Principal",
        "Personal",
    )

    val filteredCirculars = remember(circulars, selectedFilter, student.id) {
        when (selectedFilter) {
            "Holidays" -> circulars.filter {
                it.category.contains("Holiday", ignoreCase = true) ||
                    it.title.contains("Holiday", ignoreCase = true)
            }
            "Notices" -> circulars.filter {
                it.category.equals("Notice", ignoreCase = true) ||
                    it.category.equals("Class Notice", ignoreCase = true)
            }
            "Exam Datesheet" -> circulars.filter { it.category.contains("Exam", ignoreCase = true) }
            "From Teachers" -> circulars.filter { it.senderRole == "TEACHER" }
            "From Principal" -> circulars.filter { it.senderRole != "TEACHER" }
            "Personal" -> circulars.filter { it.recipientDocId == student.id }
            else -> circulars
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = NavyDeep, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("student_notices_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAmber)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notices & Holidays Board", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "${student.className}-${student.section} • ${circulars.size} circular(s) from Principal & Teachers",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAmber,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            filterOptions.forEach { option ->
                FilterChip(
                    selected = selectedFilter == option,
                    onClick = { selectedFilter = option },
                    label = { Text(option) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredCirculars.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("No matching notices or holidays", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Notices and holiday announcements published by the Principal or Teachers will appear here automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredCirculars, key = { it.id }) { c ->
                    CircularDisplayCard(circular = c, studentId = student.id, onShowMessage = onShowMessage)
                }
            }
        }
    }
}

@Composable
private fun CircularDisplayCard(
    circular: Circular,
    studentId: String,
    onShowMessage: (String) -> Unit,
) {
    val isPersonal = circular.recipientDocId == studentId
    val isHoliday = circular.category.contains("Holiday", ignoreCase = true)
    val isTeacher = circular.senderRole == "TEACHER"
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                isPersonal -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                isHoliday -> GoldAmber.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (isHoliday) Color(0xFFFEF3C7) else GoldSoft,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        circular.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHoliday) GoldAmberDark else NavyDeep,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Surface(
                    color = if (isTeacher) Color(0xFFE0E7FF) else EmeraldContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        if (isTeacher) "TEACHER" else "PRINCIPAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isTeacher) IndigoAccent else EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (isPersonal) {
                    Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "Personal Notice",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Text(
                        circular.audience,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(circular.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(circular.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("From: ${circular.postedBy} • ${circular.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AttachmentPreviewBlock(
                attachmentName = circular.attachmentName,
                attachmentMimeType = circular.attachmentMimeType,
                attachmentData = circular.attachmentData,
                onShowMessage = onShowMessage,
            )
        }
    }
}

@Composable
private fun StudentHomeTab(
    student: SchoolUser,
    circulars: List<Circular>,
    campusPosters: List<CampusPoster>,
    dptDaily: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    attendance: List<AttendanceRecord>,
    feeItems: List<FeeItem>,
    onOpenIdCard: () -> Unit,
    onOpenProfileEdit: () -> Unit,
    onOpenNotices: () -> Unit,
    onGoToDptHub: () -> Unit,
    onGoToFeesPay: () -> Unit,
    onGoToFeePayments: () -> Unit,
    onGoToAcademics: () -> Unit,
    onGoToCraftStudio: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { SchoolCatalog.todayIso() }
    val todayDpts = remember(dptDaily, today) { dptDaily.filter { it.date == today } }
    val presentCount = attendance.count { it.status == "Present" }
    val attPct = if (attendance.isNotEmpty()) (presentCount * 100) / attendance.size else 100

    val pendingFeeTotal = feeItems.filter { it.status != "PAID" }.sumOf { it.amount }
    val latestWeek = SchoolCatalog.dptWeeks[1]
    val myWeekly = dptWeekly.filter { it.studentDocId == student.id && it.week == latestWeek }
    val weeklyPct = if (myWeekly.sumOf { it.maxMarks } > 0) {
        (myWeekly.sumOf { it.scored } * 100) / myWeekly.sumOf { it.maxMarks }
    } else 0L

    val holidayCount = remember(circulars) {
        circulars.count { it.category.contains("Holiday", ignoreCase = true) || it.title.contains("Holiday", ignoreCase = true) }
    }
    val teacherNoticeCount = remember(circulars) { circulars.count { it.senderRole == "TEACHER" } }
    val sortedPosters = remember(campusPosters) {
        campusPosters.sortedWith(
            compareByDescending<CampusPoster> { it.date }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
        )
    }

    var zoomPoster by remember { mutableStateOf<CampusPoster?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${student.wing.uppercase()} • ${student.className}-${student.section}", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                            Text(student.fullName, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text("Roll / Adm No: ${student.userId} • Guardian: ${student.parentName}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                            Text("Registered Mobile: ${student.phone}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onOpenIdCard,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldAmber, contentColor = NavyDeep),
                            modifier = Modifier.testTag("student_id_card_btn"),
                        ) {
                            Icon(Icons.Filled.Badge, contentDescription = "Digital ID Card", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Digital ID Card")
                        }
                        OutlinedButton(
                            onClick = onOpenProfileEdit,
                            border = BorderStroke(1.dp, GoldAmber),
                            modifier = Modifier.testTag("student_edit_profile_btn"),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = GoldAmber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Phone / PW", color = GoldAmber)
                        }
                    }
                }
            }
        }

        // Prominent Notices & Holidays Board + Timetable Quick Access
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, GoldAmber.copy(alpha = 0.75f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = GoldSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column {
                                Text("Notices & Holidays Board", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${circulars.size} Total • $holidayCount Holidays • $teacherNoticeCount Teacher Notices",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    circulars.firstOrNull()?.let { latest ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "LATEST (${latest.category.uppercase()} • ${latest.date})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(latest.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(latest.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onOpenNotices,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("student_open_notices_btn"),
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Notices & Holidays")
                        }
                        OutlinedButton(
                            onClick = onGoToAcademics,
                            modifier = Modifier.height(48.dp).testTag("student_home_timetable_btn"),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Timetable")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Attendance",
                    value = "$attPct%",
                    subtitle = "$presentCount/${attendance.size} days present",
                    icon = Icons.Filled.FactCheck,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Weekly DPT",
                    value = "$weeklyPct%",
                    subtitle = "${myWeekly.size} subject score(s)",
                    icon = Icons.Filled.Quiz,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Quick My DPT Results + Pay Fees + Fee Payments History Action Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (pendingFeeTotal > 0L) RoseError.copy(alpha = 0.45f) else EmeraldSuccess.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SCHOOL FEE PAYMENT SYSTEM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatInr(pendingFeeTotal),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (pendingFeeTotal > 0L) RoseError else EmeraldSuccess,
                            )
                            Text(
                                if (pendingFeeTotal == 0L) "All fee dues cleared! View receipts & fee payments."
                                else "Pay instantly via Official School UPI QR, UPI App, or Razorpay.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onGoToFeesPay,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            modifier = Modifier.weight(1f).testTag("student_home_pay_now_btn"),
                        ) {
                            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pendingFeeTotal > 0L) "Pay Now" else "Pay Portal")
                        }
                        OutlinedButton(
                            onClick = onGoToFeePayments,
                            modifier = Modifier.weight(1f).testTag("student_home_fee_payments_btn"),
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fee Payments")
                        }
                    }
                    if (myWeekly.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Latest Weekly DPT ($latestWeek)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            TextButton(onClick = onGoToDptHub) { Text("Progress Line Chart") }
                        }
                        myWeekly.forEach { entry ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("${entry.scored} / ${entry.maxMarks}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Craft & Project Showcase CTA Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("SCHOOL CRAFT & PROJECT SHOWCASE", style = MaterialTheme.typography.labelSmall, color = GoldAmberDark)
                        Text("Share Art, Craft & Science Models", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Post your school exhibition projects and attach real project photos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onGoToCraftStudio,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldSoft, contentColor = NavyDeep),
                        modifier = Modifier.testTag("student_home_craft_studio_btn"),
                    ) {
                        Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Projects")
                    }
                }
            }
        }

        // Dynamic Admin-Uploaded Daily Campus Posters Gallery
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DAILY CAMPUS CELEBRATIONS & POSTERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sortedPosters.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Campus posters uploaded by Principal will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    ) {
                        sortedPosters.forEach { poster ->
                            val bitmap = remember(poster.id, poster.imageData) { decodeAttachmentBitmap(poster.imageData) }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .width(230.dp)
                                    .clickable { zoomPoster = poster },
                            ) {
                                Column {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = poster.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(145.dp)
                                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                        )
                                    }
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(poster.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${poster.date}${if (poster.subtitle.isNotBlank()) " • ${poster.subtitle}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("TODAY'S DPT & EXAM STATUS ($today)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (todayDpts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("No DPT status published for today yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                }
            }
        } else {
            items(todayDpts, key = { it.id }) { d ->
                DptStatusCard(dpt = d, onShowMessage = onShowMessage)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RECENT NOTICES & HOLIDAYS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (circulars.isNotEmpty()) {
                    TextButton(onClick = onOpenNotices) { Text("View All (${circulars.size})") }
                }
            }
        }

        if (circulars.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("No circulars or holiday notices published for your class yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                }
            }
        } else {
            items(circulars.take(5), key = { it.id }) { c ->
                CircularDisplayCard(circular = c, studentId = student.id, onShowMessage = onShowMessage)
            }
        }
    }

    zoomPoster?.let { poster ->
        val fullBitmap = remember(poster.id, poster.imageData) { decodeAttachmentBitmap(poster.imageData) }
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
                            Text("${poster.date} • ${poster.postedBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { zoomPoster = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close poster preview")
                        }
                    }
                    if (fullBitmap != null) {
                        Image(
                            bitmap = fullBitmap,
                            contentDescription = poster.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    if (poster.subtitle.isNotBlank()) {
                        Text(poster.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DptStatusCard(
    dpt: DptDaily,
    onShowMessage: (String) -> Unit = {},
) {
    val isExam = dpt.assessmentType == "EXAM"
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isExam) IndigoAccent else if (dpt.held) EmeraldSuccess.copy(alpha = 0.45f) else GoldAmber),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Surface(
                        color = if (isExam) Color(0xFFE0E7FF) else GoldSoft,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = if (isExam) "EXAM" else "DPT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isExam) NavyDeep else GoldAmberDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(dpt.subject, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Surface(color = if (dpt.held) EmeraldContainer else GoldSoft, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = if (dpt.held) "HELD • ${dpt.maxMarks} Marks" else "NOT HELD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (dpt.held) EmeraldSuccess else GoldAmberDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (dpt.held) {
                Text("Topic: ${dpt.topic}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (dpt.notes.isNotBlank()) {
                    Text(dpt.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AttachmentPreviewBlock(
                    attachmentName = dpt.attachmentName,
                    attachmentMimeType = dpt.attachmentMimeType,
                    attachmentData = dpt.attachmentData,
                    onShowMessage = onShowMessage,
                )
            } else {
                Text("Reason: ${dpt.reason}", style = MaterialTheme.typography.bodyMedium, color = GoldAmberDark)
            }
            Text("Teacher: ${dpt.teacherName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudentDptHubTab(
    student: SchoolUser,
    classmates: List<SchoolUser>,
    dptDaily: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    termMarks: List<TermMarkCard>,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProgressDashboard by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = showProgressDashboard,
                onClick = { showProgressDashboard = true },
                label = { Text("Progress Dashboard") },
                leadingIcon = { Icon(Icons.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("student_tab_progress_dashboard"),
            )
            FilterChip(
                selected = !showProgressDashboard,
                onClick = { showProgressDashboard = false },
                label = { Text("Class Rank & Weekly") },
                leadingIcon = { Icon(Icons.Filled.Leaderboard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("student_tab_class_rank"),
            )
        }

        if (showProgressDashboard) {
            DptResultDashboardScreen(
                primaryStudent = student,
                allStudents = classmates,
                dptDaily = dptDaily,
                dptWeekly = dptWeekly,
                termMarks = termMarks,
                allowStudentPicker = false,
                onShowMessage = onShowMessage,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        val context = LocalContext.current
        var selectedDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
        var selectedWeek by remember { mutableStateOf(SchoolCatalog.dptWeeks[1]) }

        val dailyFiltered = remember(dptDaily, selectedDate) {
            dptDaily.filter { it.date == selectedDate }
        }
        val myWeekly = remember(dptWeekly, student.id, selectedWeek) {
            dptWeekly.filter { it.studentDocId == student.id && it.week == selectedWeek }
        }

        val classRanked = remember(classmates, dptWeekly, selectedWeek, student) {
            val pool = if (classmates.any { it.id == student.id }) classmates else (classmates + student)
            pool.map { st ->
                val entries = dptWeekly.filter {
                    it.week == selectedWeek && it.studentDocId == st.id
                }
                val max = entries.sumOf { it.maxMarks }
                val pct = if (max > 0L) (entries.sumOf { it.scored } * 100.0) / max else -1.0
                Triple(st, entries, pct)
            }.sortedByDescending { it.third }
        }

        val myRankIndex = classRanked.indexOfFirst { it.first.id == student.id }
        val classRank = if (myRankIndex >= 0 && classRanked[myRankIndex].third >= 0.0) "#${myRankIndex + 1} of ${classRanked.size}" else "N/A"

        val totalScored = myWeekly.sumOf { it.scored }
        val totalMax = myWeekly.sumOf { it.maxMarks }
        val weeklyPct = if (totalMax > 0) (totalScored * 100.0 / totalMax) else 0.0

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = selectedDate,
                    onValueChange = { selectedDate = it },
                    label = { Text("Filter Daily DPT Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (dailyFiltered.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("No DPT records on $selectedDate.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                    }
                }
            } else {
                items(dailyFiltered, key = { it.id }) { d -> DptStatusCard(dpt = d, onShowMessage = onShowMessage) }
            }

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
                        Text("My Weekly DPT Report Card", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        FilledTonalButton(
                            onClick = {
                                val rowsHtml = myWeekly.joinToString("") {
                                    val p = if (it.maxMarks > 0) (it.scored * 100 / it.maxMarks) else 0
                                    "<tr><td style='padding:6px;border:1px solid #cbd5e1;'>${it.subject}</td><td style='padding:6px;border:1px solid #cbd5e1;'>${it.scored} / ${it.maxMarks}</td><td style='padding:6px;border:1px solid #cbd5e1;'>$p%</td></tr>"
                                }
                                val html = """
                                    <html><body style="font-family:sans-serif;padding:24px;color:#0f172a;">
                                    <h2>${SchoolInfo.NAME}</h2>
                                    <p style="color:#475569;">${SchoolInfo.AFFILIATION} • Weekly DPT Report Card</p>
                                    <p><b>Student:</b> ${student.fullName} (${student.userId}) | <b>Class:</b> ${student.className}-${student.section}</p>
                                    <p><b>Week:</b> $selectedWeek | <b>Rank:</b> $classRank | <b>Overall:</b> $totalScored/$totalMax (${"%.1f".format(weeklyPct)}%)</p>
                                    <table style="width:100%;border-collapse:collapse;margin-top:12px;">
                                      <tr style="background:#0f172a;color:#f59e0b;"><th style="padding:8px;">Subject</th><th style="padding:8px;">Marks</th><th style="padding:8px;">%</th></tr>
                                      $rowsHtml
                                    </table></body></html>
                                """.trimIndent()
                                printHtmlDocument(context, "DPS_DPT_${student.userId}", html)
                            },
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Print DPT Report", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SchoolCatalog.dptWeeks.forEach { wk ->
                            FilterChip(selected = selectedWeek == wk, onClick = { selectedWeek = wk }, label = { Text(wk) })
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("WEEKLY TOTAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalScored / $totalMax (${"%.1f".format(weeklyPct)}%)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Surface(color = GoldSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("CLASS RANK", style = MaterialTheme.typography.labelSmall, color = GoldAmberDark)
                                Text(classRank, style = MaterialTheme.typography.titleMedium, color = NavyDeep)
                            }
                        }
                    }

                    myWeekly.forEach { entry ->
                        val ratio = if (entry.maxMarks > 0) (entry.scored.toFloat() / entry.maxMarks.toFloat()).coerceIn(0f, 1f) else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${entry.scored} / ${entry.maxMarks}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                color = if (ratio >= 0.85f) EmeraldSuccess else if (ratio >= 0.60f) MaterialTheme.colorScheme.primary else GoldAmberDark,
                                trackColor = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Show each student's DPT result card in the class
        item {
            Text(
                "${student.className}-${student.section} STUDENT DPT RESULTS ($selectedWeek)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(classRanked.withIndex().toList(), key = { "stud_dpt_${it.value.first.id}" }) { indexed ->
            val rank = indexed.index + 1
            val (peer, entries, pct) = indexed.value
            StudentDptResultCard(
                student = peer,
                weekLabel = selectedWeek,
                studentEntries = entries,
                rankLabel = if (pct >= 0.0) "#$rank" else "—",
            )
        }

        if (termMarks.isNotEmpty()) {
            item {
                Text("TERM EXAM REPORT CARDS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(termMarks, key = { it.id }) { card ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(card.term, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        card.marks.forEach { (sub, vals) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("${vals["scored"] ?: 0} / ${vals["max"] ?: 100}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (card.remarks.isNotBlank()) {
                            Text("Remarks: ${card.remarks}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun StudentFeesTab(
    student: SchoolUser,
    gateway: GatewayConfig,
    feeItems: List<FeeItem>,
    payments: List<FeePayment>,
    openHistoryInitially: Boolean = false,
    onConsumeHistoryInitial: () -> Unit = {},
    onSubmitPaymentProof: (SchoolUser, List<FeeItem>, String, String, String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFeePaymentsHistory by remember { mutableStateOf(openHistoryInitially) }
    LaunchedEffect(openHistoryInitially) {
        if (openHistoryInitially) {
            showFeePaymentsHistory = true
            onConsumeHistoryInitial()
        }
    }
    BackHandler(enabled = showFeePaymentsHistory) {
        showFeePaymentsHistory = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showFeePaymentsHistory,
                onClick = { showFeePaymentsHistory = false },
                label = { Text("Pay Fees") },
                leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("student_fees_pay_chip"),
            )
            FilterChip(
                selected = showFeePaymentsHistory,
                onClick = { showFeePaymentsHistory = true },
                label = { Text("Fee Payments") },
                leadingIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("student_fees_history_chip"),
            )
        }

        if (showFeePaymentsHistory) {
            FeePaymentsScreen(
                payments = payments,
                feeItems = feeItems,
                students = listOf(student),
                isStudentView = true,
                onBack = { showFeePaymentsHistory = false },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current

        val unpaidItems = remember(feeItems) { feeItems.filter { it.status == "UNPAID" } }
        val selectedIds = remember { mutableStateMapOf<String, Boolean>() }

        LaunchedEffect(unpaidItems) {
            unpaidItems.forEach { item ->
                if (!selectedIds.containsKey(item.id)) {
                    selectedIds[item.id] = true
                }
            }
        }

        val chosenFees = unpaidItems.filter { selectedIds[it.id] == true }
        val selectedTotal = chosenFees.sumOf { it.amount }
        val upiUri = remember(gateway, selectedTotal, student.userId) {
            "upi://pay?pa=${Uri.encode(gateway.merchantUpi)}&pn=${Uri.encode(gateway.payeeName)}&am=$selectedTotal&cu=INR&tn=${Uri.encode("Fee_${student.userId}")}"
        }

        var mode by remember { mutableStateOf("UPI") }
        var upiApp by remember { mutableStateOf("Google Pay") }
        var utrInput by remember { mutableStateOf("") }
        var receiptPayment by remember { mutableStateOf<FeePayment?>(null) }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Itemized Fee Ledger", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (feeItems.isEmpty()) {
                        Text("No fee items assigned.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        feeItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = item.status == "UNPAID") {
                                        selectedIds[item.id] = !(selectedIds[item.id] ?: false)
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (item.status == "UNPAID") {
                                        Checkbox(
                                            checked = selectedIds[item.id] == true,
                                            onCheckedChange = { selectedIds[item.id] = it },
                                        )
                                    } else {
                                        Icon(
                                            if (item.status == "PAID") Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                                            contentDescription = item.status,
                                            tint = if (item.status == "PAID") EmeraldSuccess else GoldAmberDark,
                                            modifier = Modifier.padding(12.dp).size(20.dp),
                                        )
                                    }
                                    Column {
                                        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text(item.status.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(formatInr(item.amount), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (selectedTotal > 0L) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Pay ${formatInr(selectedTotal)} via Official School UPI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Merchant VPA: ${gateway.merchantUpi} (${gateway.payeeName})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                        QrCodeCanvas(data = upiUri, size = 180.dp)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(gateway.merchantUpi))
                                    onShowMessage("Merchant UPI ID copied!")
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy UPI ID", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy UPI")
                            }
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                        context.startActivity(intent)
                                    } catch (_: ActivityNotFoundException) {
                                        onShowMessage("No UPI app detected on this device; scan the QR from GPay/PhonePe and enter the 12-digit UTR below.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("open_upi_app_btn"),
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open UPI App")
                            }
                        }

                        if (gateway.razorpayKeyId.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    mode = "RAZORPAY"
                                    upiApp = "Razorpay"
                                    onShowMessage("Razorpay Key ${gateway.razorpayKeyId} active. Complete checkout and paste Payment ID below.")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Pay via Razorpay (${gateway.razorpayKeyId.take(14)})")
                            }
                        }

                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("BANK NEFT / IMPS TRANSFER DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("Bank: ${gateway.bankName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("A/C No: ${gateway.accountNo} • IFSC: ${gateway.ifsc}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("SUBMIT PAYMENT PROOF (12-DIGIT UTR / REF ID)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("Google Pay", "PhonePe", "Paytm", "BHIM UPI", "Razorpay").forEach { appName ->
                                FilterChip(
                                    selected = upiApp == appName,
                                    onClick = {
                                        upiApp = appName
                                        mode = if (appName == "Razorpay") "RAZORPAY" else "UPI"
                                    },
                                    label = { Text(appName) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = utrInput,
                            onValueChange = { utrInput = it },
                            label = { Text("12-digit UPI UTR / Razorpay Payment ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("student_utr_input"),
                        )

                        Button(
                            onClick = {
                                onSubmitPaymentProof(student, chosenFees, mode, utrInput, upiApp)
                                utrInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_utr_btn"),
                        ) { Text("Submit UTR for Fee Manager Verification") }
                    }
                }
            }
        }

        item {
            Text("PAYMENT HISTORY & RECEIPTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(payments, key = { it.id }) { pay ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${formatInr(pay.amount)} • ${pay.feeSummary}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("UTR: ${pay.utrOrRef} (${pay.upiApp}) • ${pay.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (pay.status == "REJECTED" && pay.rejectReason.isNotBlank()) {
                            Text("Reason: ${pay.rejectReason}", style = MaterialTheme.typography.labelSmall, color = RoseError)
                        }
                    }
                    if (pay.status == "VERIFIED") {
                        FilledTonalButton(onClick = { receiptPayment = pay }) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = "View Receipt", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Receipt")
                        }
                    }
                }
            }
        }
    }

    receiptPayment?.let { pay ->
        OfficialFeeReceiptDialog(payment = pay, student = student, onDismiss = { receiptPayment = null })
    }
    }
}

@Composable
private fun StudentAcademicsTab(
    student: SchoolUser,
    homework: List<HomeworkItem>,
    leaves: List<LeaveRequest>,
    doubts: List<DoubtMessage>,
    timetables: List<ClassTimetable>,
    onSubmitLeave: (LeaveRequest) -> Unit,
    onSubmitDoubt: (DoubtMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hwDoneMap = remember { mutableStateMapOf<String, Boolean>() }
    var fromDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var toDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var leaveReason by remember { mutableStateOf("") }

    val subjects = remember(student.className) { SchoolCatalog.subjectsForClass(student.className) }
    var doubtSubject by remember(subjects) { mutableStateOf(subjects.firstOrNull() ?: "Mathematics") }
    var doubtText by remember { mutableStateOf("") }

    val customTimetable = remember(timetables, student.className, student.section) {
        timetables.firstOrNull {
            it.className == student.className && it.section == student.section
        }
    }
    val timetableSlots = remember(customTimetable, student.className, student.section) {
        customTimetable?.slots?.map { SchoolCatalog.slotFromMap(it) }?.takeIf { it.isNotEmpty() }
            ?: SchoolCatalog.defaultTimetableSlotsFor(student.className, student.section)
    }
    var selectedDay by remember { mutableStateOf("Mon") }
    val dayChips = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Full Week")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("HOMEWORK DIARY (${student.className}-${student.section})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (homework.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                    Text("No homework assigned yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                }
            }
        } else {
            items(homework, key = { it.id }) { hw ->
                val done = hwDoneMap[hw.id] == true
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = done, onCheckedChange = { hwDoneMap[hw.id] = it })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${hw.subject}: ${hw.title}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(hw.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Due: ${hw.dueDate} • ${hw.teacherName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Per-Class-Section Timetable
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    "${student.className}-${student.section} TIMETABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (customTimetable != null && customTimetable.updatedBy.isNotBlank()) {
                            Text(
                                "Updated by ${customTimetable.updatedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            dayChips.forEach { day ->
                                FilterChip(
                                    selected = selectedDay == day,
                                    onClick = { selectedDay = day },
                                    label = { Text(day) },
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        timetableSlots.forEachIndexed { idx, slot ->
                            val daySubject = when (selectedDay) {
                                "Mon" -> slot.mon
                                "Tue" -> slot.tue
                                "Wed" -> slot.wed
                                "Thu" -> slot.thu
                                "Fri" -> slot.fri
                                "Sat" -> slot.sat
                                else -> "Mon: ${slot.mon} • Tue: ${slot.tue} • Wed: ${slot.wed} • Thu: ${slot.thu} • Fri: ${slot.fri} • Sat: ${slot.sat}"
                            }
                            if (selectedDay == "Full Week") {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${slot.period} (${slot.time})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(daySubject, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(slot.period, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(slot.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = GoldSoft, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            daySubject,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NavyDeep,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            if (idx < timetableSlots.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        // Leave Application
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Apply for Leave", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SchoolDatePickerField(
                            label = "From Date",
                            selectedDate = fromDate,
                            onDateSelected = { fromDate = it },
                            modifier = Modifier.weight(1f),
                            testTagName = "leave_from_date_btn",
                        )
                        SchoolDatePickerField(
                            label = "To Date",
                            selectedDate = toDate,
                            onDateSelected = { toDate = it },
                            modifier = Modifier.weight(1f),
                            testTagName = "leave_to_date_btn",
                        )
                    }
                    OutlinedTextField(value = leaveReason, onValueChange = { leaveReason = it }, label = { Text("Reason for Leave") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (leaveReason.isBlank()) return@Button
                            onSubmitLeave(
                                LeaveRequest(
                                    studentDocId = student.id,
                                    studentName = student.fullName,
                                    admissionNo = student.userId,
                                    className = student.className,
                                    section = student.section,
                                    fromDate = fromDate.trim(),
                                    toDate = toDate.trim(),
                                    reason = leaveReason.trim(),
                                    status = "Pending",
                                )
                            )
                            leaveReason = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Submit Leave Application") }
                }
            }
        }

        items(leaves, key = { it.id }) { lv ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${lv.fromDate} → ${lv.toDate}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(lv.status, style = MaterialTheme.typography.labelLarge, color = if (lv.status == "Approved") EmeraldSuccess else GoldAmberDark)
                    }
                    Text(lv.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lv.teacherRemark.isNotBlank()) Text("Teacher: ${lv.teacherRemark}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Parent-Teacher Doubt Box
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Parent-Teacher Doubt Box", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        subjects.forEach { s ->
                            FilterChip(selected = doubtSubject == s, onClick = { doubtSubject = s }, label = { Text(s) })
                        }
                    }
                    OutlinedTextField(value = doubtText, onValueChange = { doubtText = it }, label = { Text("Ask Subject Teacher a Question") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (doubtText.isBlank()) return@Button
                            onSubmitDoubt(
                                DoubtMessage(
                                    studentDocId = student.id,
                                    studentName = student.fullName,
                                    className = "${student.className}-${student.section}",
                                    subject = doubtSubject,
                                    message = doubtText.trim(),
                                    status = "OPEN",
                                    dateLabel = SchoolCatalog.todayIso(),
                                )
                            )
                            doubtText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyRoyal, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Post Question to Teacher") }
                }
            }
        }

        items(doubts, key = { it.id }) { dbt ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${dbt.subject} • ${dbt.status}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(dbt.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (dbt.reply.isNotBlank()) {
                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Reply (${dbt.repliedBy}): ${dbt.reply}", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}


