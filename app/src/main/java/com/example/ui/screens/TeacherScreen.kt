package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.AttachmentPickerChip
import com.example.ui.components.AttachmentPreviewBlock
import com.example.ui.components.PickedAttachment
import com.example.ui.components.StudentDptResultCard
import com.example.ui.components.readAttachmentFromUri
import com.example.ui.theme.*
import com.example.ui.viewmodel.CraftAiImageState

@Composable
fun TeacherScreen(
    activeTab: Int,
    teacher: SchoolUser,
    users: List<SchoolUser>,
    circulars: List<Circular>,
    dptDaily: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    termMarks: List<TermMarkCard>,
    attendance: List<AttendanceRecord>,
    leaves: List<LeaveRequest>,
    homework: List<HomeworkItem>,
    doubts: List<DoubtMessage>,
    craftProjects: List<CraftProject> = emptyList(),
    craftAiImageState: CraftAiImageState = CraftAiImageState.Idle,
    generatingCraftPostId: String? = null,
    onSaveDailyDpt: (DptDaily) -> Unit,
    onSaveWeeklyDptMarks: (List<DptWeekly>) -> Unit,
    onMarkAttendance: (AttendanceRecord) -> Unit,
    onMarkAllPresent: (List<SchoolUser>, String, String, String, String) -> Unit,
    onUpdateLeaveStatus: (String, String, String) -> Unit,
    onPostNotice: (Circular) -> Unit,
    onPostHomework: (HomeworkItem) -> Unit,
    onPublishTermMarks: (TermMarkCard) -> Unit,
    onReplyDoubt: (String, String, String) -> Unit,
    onDeleteRecord: (String, String, String) -> Unit,
    onGenerateCraftAiImage: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onClearCraftPreview: () -> Unit = {},
    onPublishCraftProject: (CraftProject) -> Unit = {},
    onGenerateCraftImageForPost: (CraftProject) -> Unit = {},
    onApplaudCraftProject: (CraftProject) -> Unit = {},
    onShowMessage: (String) -> Unit,
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedClass by remember(teacher.id) { mutableStateOf(teacher.className.ifBlank { "Class X" }) }
    var selectedSection by remember(teacher.id) { mutableStateOf(teacher.section.ifBlank { "A" }) }
    val subjectsForCls = remember(selectedClass) { SchoolCatalog.subjectsForClass(selectedClass) }
    var selectedSubject by remember(selectedClass, teacher.subject) {
        mutableStateOf(
            if (teacher.subject in subjectsForCls) teacher.subject
            else subjectsForCls.firstOrNull() ?: "Mathematics"
        )
    }
    var selectedDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var showStudentProgressDashboard by remember { mutableStateOf(false) }
    var showCraftStudioOverlay by remember { mutableStateOf(false) }

    val allStudents = remember(users) {
        users.filter { it.role == "STUDENT" }.sortedBy { it.userId }
    }
    val classStudents = remember(allStudents, selectedClass, selectedSection) {
        allStudents.filter {
            it.className == selectedClass && it.section == selectedSection
        }
    }

    if (showCraftStudioOverlay) {
        BackHandler { showCraftStudioOverlay = false }
        CraftStudioScreen(
            currentUser = teacher,
            projects = craftProjects,
            craftAiImageState = craftAiImageState,
            generatingPostId = generatingCraftPostId,
            onGeneratePreviewImage = onGenerateCraftAiImage,
            onClearPreviewImage = onClearCraftPreview,
            onPublishProject = onPublishCraftProject,
            onGenerateImageForPost = onGenerateCraftImageForPost,
            onApplaudPost = onApplaudCraftProject,
            onDeletePost = { p -> onDeleteRecord("craftProjects", p.id, p.title) },
            onShowMessage = onShowMessage,
            onBack = { showCraftStudioOverlay = false },
            modifier = modifier,
        )
        return
    }

    if (showStudentProgressDashboard) {
        BackHandler { showStudentProgressDashboard = false }
        val anchorStudent = classStudents.firstOrNull() ?: allStudents.firstOrNull()
        if (anchorStudent != null) {
            DptResultDashboardScreen(
                primaryStudent = anchorStudent,
                allStudents = allStudents,
                dptDaily = dptDaily,
                dptWeekly = dptWeekly,
                termMarks = termMarks,
                allowStudentPicker = true,
                onBack = { showStudentProgressDashboard = false },
                onShowMessage = onShowMessage,
                modifier = modifier,
            )
            return
        } else {
            showStudentProgressDashboard = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Teacher Profile + Shared Class/Section/Subject Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(teacher.fullName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${teacher.userId} • ${teacher.subject.ifBlank { "Faculty" }} • Ph: ${teacher.phone}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { showCraftStudioOverlay = true },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldSoft, contentColor = NavyDeep),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("teacher_open_craft_studio_btn"),
                        ) {
                            Icon(Icons.Filled.Brush, contentDescription = "Craft & Projects", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Projects", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onOpenProfileEdit,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("teacher_edit_profile_btn"),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Profile", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Class / Section / Subject selectors
                var classMenu by remember { mutableStateOf(false) }
                var subjectMenu by remember { mutableStateOf(false) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Box {
                        AssistChip(
                            onClick = { classMenu = true },
                            label = { Text("$selectedClass-$selectedSection") },
                            leadingIcon = { Icon(Icons.Filled.Class, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        DropdownMenu(expanded = classMenu, onDismissRequest = { classMenu = false }) {
                            SchoolCatalog.allClasses.forEach { cls ->
                                SchoolCatalog.sections.forEach { sec ->
                                    DropdownMenuItem(
                                        text = { Text("$cls-$sec (${SchoolCatalog.wingForClass(cls)})") },
                                        onClick = {
                                            selectedClass = cls
                                            selectedSection = sec
                                            classMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        AssistChip(
                            onClick = { subjectMenu = true },
                            label = { Text(selectedSubject) },
                            leadingIcon = { Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        DropdownMenu(expanded = subjectMenu, onDismissRequest = { subjectMenu = false }) {
                            subjectsForCls.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        selectedSubject = sub
                                        subjectMenu = false
                                    },
                                )
                            }
                        }
                    }
                    AssistChip(
                        onClick = { selectedDate = SchoolCatalog.todayIso() },
                        label = { Text(selectedDate) },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }

        when (activeTab) {
            0 -> DailyDptTab(
                teacher = teacher,
                selectedDate = selectedDate,
                onDateChange = { selectedDate = it },
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                selectedSubject = selectedSubject,
                dptDaily = dptDaily,
                onSaveDailyDpt = onSaveDailyDpt,
                onDeleteDpt = { onDeleteRecord("dptDaily", it.id, "DPT Log") },
                onShowMessage = onShowMessage,
            )
            1 -> WeeklyDptTab(
                teacher = teacher,
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                selectedSubject = selectedSubject,
                classStudents = classStudents,
                dptWeekly = dptWeekly,
                onSaveWeeklyDptMarks = onSaveWeeklyDptMarks,
                onOpenProgressDashboard = { showStudentProgressDashboard = true },
            )
            2 -> AttendanceAndLeaveTab(
                teacher = teacher,
                selectedDate = selectedDate,
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                classStudents = classStudents,
                attendance = attendance,
                leaves = leaves,
                onMarkAttendance = onMarkAttendance,
                onMarkAllPresent = onMarkAllPresent,
                onUpdateLeaveStatus = onUpdateLeaveStatus,
            )
            3 -> TeacherNoticesTab(
                teacher = teacher,
                allUsers = users,
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                circulars = circulars,
                onPostNotice = onPostNotice,
                onDeleteNotice = { onDeleteRecord("circulars", it.id, it.title) },
                onShowMessage = onShowMessage,
            )
            else -> TeacherDiaryOrCraftTab(
                teacher = teacher,
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                selectedSubject = selectedSubject,
                subjectsForCls = subjectsForCls,
                classStudents = classStudents,
                homework = homework,
                doubts = doubts,
                craftProjects = craftProjects,
                craftAiImageState = craftAiImageState,
                generatingCraftPostId = generatingCraftPostId,
                onPostHomework = onPostHomework,
                onPublishTermMarks = onPublishTermMarks,
                onReplyDoubt = onReplyDoubt,
                onDeleteHomework = { onDeleteRecord("homework", it.id, it.title) },
                onGenerateCraftAiImage = onGenerateCraftAiImage,
                onClearCraftPreview = onClearCraftPreview,
                onPublishCraftProject = onPublishCraftProject,
                onGenerateCraftImageForPost = onGenerateCraftImageForPost,
                onApplaudCraftProject = onApplaudCraftProject,
                onDeleteCraftProject = { onDeleteRecord("craftProjects", it.id, it.title) },
                onShowMessage = onShowMessage,
            )
        }
    }
}

@Composable
private fun TeacherDiaryOrCraftTab(
    teacher: SchoolUser,
    selectedClass: String,
    selectedSection: String,
    selectedSubject: String,
    subjectsForCls: List<String>,
    classStudents: List<SchoolUser>,
    homework: List<HomeworkItem>,
    doubts: List<DoubtMessage>,
    craftProjects: List<CraftProject>,
    craftAiImageState: CraftAiImageState,
    generatingCraftPostId: String?,
    onPostHomework: (HomeworkItem) -> Unit,
    onPublishTermMarks: (TermMarkCard) -> Unit,
    onReplyDoubt: (String, String, String) -> Unit,
    onDeleteHomework: (HomeworkItem) -> Unit,
    onGenerateCraftAiImage: (String, String, String, String) -> Unit,
    onClearCraftPreview: () -> Unit,
    onPublishCraftProject: (CraftProject) -> Unit,
    onGenerateCraftImageForPost: (CraftProject) -> Unit,
    onApplaudCraftProject: (CraftProject) -> Unit,
    onDeleteCraftProject: (CraftProject) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    var showCraftSubTab by remember { mutableStateOf(false) }
    BackHandler(enabled = showCraftSubTab) { showCraftSubTab = false }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showCraftSubTab,
                onClick = { showCraftSubTab = false },
                label = { Text("Diary & Marks") },
                leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("teacher_subtab_diary"),
            )
            FilterChip(
                selected = showCraftSubTab,
                onClick = { showCraftSubTab = true },
                label = { Text("Craft & Skill AI") },
                leadingIcon = { Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("teacher_subtab_craft_ai"),
            )
        }

        if (showCraftSubTab) {
            CraftStudioScreen(
                currentUser = teacher,
                projects = craftProjects,
                craftAiImageState = craftAiImageState,
                generatingPostId = generatingCraftPostId,
                onGeneratePreviewImage = onGenerateCraftAiImage,
                onClearPreviewImage = onClearCraftPreview,
                onPublishProject = onPublishCraftProject,
                onGenerateImageForPost = onGenerateCraftImageForPost,
                onApplaudPost = onApplaudCraftProject,
                onDeletePost = onDeleteCraftProject,
                onShowMessage = onShowMessage,
                onBack = { showCraftSubTab = false },
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f)) {
                DiaryMarksDoubtsTab(
                    teacher = teacher,
                    selectedClass = selectedClass,
                    selectedSection = selectedSection,
                    selectedSubject = selectedSubject,
                    subjectsForCls = subjectsForCls,
                    classStudents = classStudents,
                    homework = homework,
                    doubts = doubts,
                    onPostHomework = onPostHomework,
                    onPublishTermMarks = onPublishTermMarks,
                    onReplyDoubt = onReplyDoubt,
                    onDeleteHomework = onDeleteHomework,
                )
            }
        }
    }
}

@Composable
private fun DailyDptTab(
    teacher: SchoolUser,
    selectedDate: String,
    onDateChange: (String) -> Unit,
    selectedClass: String,
    selectedSection: String,
    selectedSubject: String,
    dptDaily: List<DptDaily>,
    onSaveDailyDpt: (DptDaily) -> Unit,
    onDeleteDpt: (DptDaily) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var assessmentType by remember { mutableStateOf("DPT") } // "DPT" | "EXAM"
    var heldToday by remember { mutableStateOf(true) }
    var topic by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("20") }
    var notes by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(SchoolCatalog.dptNotHeldReasons.first()) }
    var pendingAttachment by remember { mutableStateOf<PickedAttachment?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                readAttachmentFromUri(context, uri, forceImage = true)
                    .onSuccess { pendingAttachment = it }
                    .onFailure { onShowMessage(it.localizedMessage ?: "Unable to load selected photo.") }
            }
        },
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                readAttachmentFromUri(context, uri, forceImage = false)
                    .onSuccess { pendingAttachment = it }
                    .onFailure { onShowMessage(it.localizedMessage ?: "Unable to read selected document.") }
            }
        },
    )

    val matchingLogs = remember(dptDaily, selectedClass, selectedSection) {
        dptDaily.filter { it.className == selectedClass && it.section == selectedSection }
            .sortedByDescending { it.date }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (assessmentType == "EXAM") IndigoAccent else MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (assessmentType == "EXAM") "Exam Tracker ($selectedClass-$selectedSection • $selectedSubject)"
                        else "Daily DPT Tracker ($selectedClass-$selectedSection • $selectedSubject)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Switch between Daily DPT and Exam
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = assessmentType == "DPT",
                            onClick = {
                                assessmentType = "DPT"
                                if (maxMarks == "100") maxMarks = "20"
                                validationError = null
                            },
                            label = { Text("Daily DPT") },
                            leadingIcon = { Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).testTag("switch_mode_dpt"),
                        )
                        FilterChip(
                            selected = assessmentType == "EXAM",
                            onClick = {
                                assessmentType = "EXAM"
                                if (maxMarks == "20" || maxMarks == "10") maxMarks = "100"
                                validationError = null
                            },
                            label = { Text("Exam Mode") },
                            leadingIcon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).testTag("switch_mode_exam"),
                        )
                    }

                    OutlinedTextField(
                        value = selectedDate,
                        onValueChange = onDateChange,
                        label = { Text(if (assessmentType == "EXAM") "Exam Date (YYYY-MM-DD)" else "DPT Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (assessmentType == "EXAM") "Was Exam Held Today?" else "Was DPT Held Today?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = heldToday,
                                onClick = {
                                    heldToday = true
                                    validationError = null
                                },
                                label = { Text("YES (Held)") },
                                modifier = Modifier.testTag("dpt_held_yes"),
                            )
                            FilterChip(
                                selected = !heldToday,
                                onClick = {
                                    heldToday = false
                                    validationError = null
                                },
                                label = { Text("NO (Not Held)") },
                                modifier = Modifier.testTag("dpt_held_no"),
                            )
                        }
                    }

                    if (heldToday) {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = {
                                topic = it.take(200)
                                if (validationError != null) validationError = null
                            },
                            label = { Text(if (assessmentType == "EXAM") "Exam Title / Syllabus Topic" else "DPT Topic Name") },
                            isError = validationError != null && topic.isBlank(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("dpt_topic_input"),
                        )
                        OutlinedTextField(
                            value = maxMarks,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                val num = digits.toIntOrNull()
                                maxMarks = if (num != null && num > 100) "100" else digits
                                if (validationError != null) validationError = null
                            },
                            label = { Text("Maximum Marks (1 – 100)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("dpt_max_marks_input"),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            listOf("10", "20", "25", "50", "80", "100").forEach { preset ->
                                FilterChip(
                                    selected = maxMarks == preset,
                                    onClick = { maxMarks = preset },
                                    label = { Text(preset) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(1000) },
                            label = { Text("Optional Questions / Notes") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("dpt_notes_input"),
                        )
                        // Photo / Document attachment inside Optional Questions / Notes
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("dpt_attach_photo_btn"),
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Question Photo", style = MaterialTheme.typography.labelSmall)
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
                                modifier = Modifier.weight(1f).testTag("dpt_attach_doc_btn"),
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
                    } else {
                        Text("Select Reason:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            SchoolCatalog.dptNotHeldReasons.forEach { r ->
                                FilterChip(
                                    selected = reason == r,
                                    onClick = {
                                        reason = r
                                        validationError = null
                                    },
                                    label = { Text(r) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = {
                                reason = it.take(200)
                                if (validationError != null) validationError = null
                            },
                            label = { Text("Reason Details") },
                            isError = validationError != null && reason.isBlank(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("dpt_reason_input"),
                        )
                    }

                    if (validationError != null) {
                        Text(
                            validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = {
                            if (heldToday && topic.isBlank()) {
                                validationError = "Please enter the ${if (assessmentType == "EXAM") "Exam Title" else "DPT Topic Name"} before publishing."
                                return@Button
                            }
                            if (!heldToday && reason.isBlank()) {
                                validationError = "Please select or enter a reason why it was not held."
                                return@Button
                            }
                            val parsedMax = if (heldToday) {
                                val rawNum = maxMarks.toLongOrNull() ?: if (assessmentType == "EXAM") 100L else 20L
                                if (rawNum !in 1L..100L) {
                                    validationError = "Maximum Marks must be between 1 and 100."
                                    return@Button
                                }
                                rawNum
                            } else 0L

                            validationError = null
                            val att = if (heldToday) pendingAttachment else null
                            onSaveDailyDpt(
                                DptDaily(
                                    date = selectedDate.trim().ifBlank { SchoolCatalog.todayIso() }.take(20),
                                    className = selectedClass,
                                    section = selectedSection,
                                    subject = selectedSubject,
                                    assessmentType = assessmentType,
                                    held = heldToday,
                                    topic = if (heldToday) topic.trim().take(200) else "",
                                    maxMarks = parsedMax,
                                    notes = if (heldToday) notes.trim().take(1000) else "",
                                    reason = if (!heldToday) reason.trim().take(200) else "",
                                    attachmentName = att?.name ?: "",
                                    attachmentMimeType = att?.mimeType ?: "",
                                    attachmentData = att?.base64Data ?: "",
                                    teacherId = teacher.userId.ifBlank { "TCH" },
                                    teacherName = teacher.fullName.ifBlank { "Teacher" },
                                )
                            )
                            topic = ""
                            notes = ""
                            pendingAttachment = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dpt_publish_btn"),
                    ) {
                        Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (assessmentType == "EXAM") "Publish Exam Status" else "Publish Daily DPT Status")
                    }
                }
            }
        }

        item {
            Text("PUBLISHED DPT & EXAM LOGS ($selectedClass-$selectedSection)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(matchingLogs, key = { it.id }) { log ->
            val isExam = log.assessmentType == "EXAM"
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
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = if (isExam) Color(0xFFE0E7FF) else GoldSoft,
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        if (isExam) "EXAM" else "DPT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExam) NavyDeep else GoldAmberDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text("${log.subject} (${log.date})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Surface(color = if (log.held) EmeraldContainer else GoldSoft, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        if (log.held) "HELD" else "NOT HELD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.held) EmeraldSuccess else GoldAmberDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (log.held) {
                                Text("Topic: ${log.topic} • Max: ${log.maxMarks}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (log.notes.isNotBlank()) Text(log.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Reason: ${log.reason}", style = MaterialTheme.typography.bodyMedium, color = GoldAmberDark)
                            }
                        }
                        IconButton(onClick = { onDeleteDpt(log) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete DPT", tint = RoseError)
                        }
                    }
                    if (log.held && log.attachmentData.isNotBlank()) {
                        AttachmentPreviewBlock(
                            attachmentName = log.attachmentName,
                            attachmentMimeType = log.attachmentMimeType,
                            attachmentData = log.attachmentData,
                            onShowMessage = onShowMessage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyDptTab(
    teacher: SchoolUser,
    selectedClass: String,
    selectedSection: String,
    selectedSubject: String,
    classStudents: List<SchoolUser>,
    dptWeekly: List<DptWeekly>,
    onSaveWeeklyDptMarks: (List<DptWeekly>) -> Unit,
    onOpenProgressDashboard: () -> Unit,
) {
    var selectedWeek by remember { mutableStateOf(SchoolCatalog.dptWeeks[1]) }
    var maxMarksInput by remember { mutableStateOf("20") }
    val marksMap = remember { mutableStateMapOf<String, String>() }

    // Pre-populate existing marks
    LaunchedEffect(selectedWeek, selectedClass, selectedSection, selectedSubject, dptWeekly) {
        marksMap.clear()
        classStudents.forEach { st ->
            val existing = dptWeekly.firstOrNull {
                it.week == selectedWeek &&
                    it.className == selectedClass &&
                    it.section == selectedSection &&
                    it.subject == selectedSubject &&
                    it.studentDocId == st.id
            }
            if (existing != null) {
                marksMap[st.id] = existing.scored.toString()
            }
        }
    }

    // Compute Weekly Leaderboard across all subjects in this week/class/section
    val leaderboard = remember(classStudents, dptWeekly, selectedWeek, selectedClass, selectedSection) {
        classStudents.map { st ->
            val entries = dptWeekly.filter {
                it.week == selectedWeek && it.className == selectedClass &&
                    it.section == selectedSection && it.studentDocId == st.id
            }
            val totalMax = entries.sumOf { it.maxMarks }
            val pct = if (totalMax > 0L) (entries.sumOf { it.scored } * 100.0 / totalMax) else -1.0
            Triple(st, entries, pct)
        }.sortedByDescending { it.third }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                        "Weekly DPT Marks Entry ($selectedSubject)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        SchoolCatalog.dptWeeks.forEach { wk ->
                            FilterChip(selected = selectedWeek == wk, onClick = { selectedWeek = wk }, label = { Text(wk) })
                        }
                    }
                    OutlinedTextField(
                        value = maxMarksInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            val num = digits.toIntOrNull()
                            maxMarksInput = if (num != null && num > 100) "100" else digits
                        },
                        label = { Text("Weekly Max Marks ($selectedSubject • 1 to 100)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf("10", "20", "50", "100").forEach { preset ->
                            FilterChip(selected = maxMarksInput == preset, onClick = { maxMarksInput = preset }, label = { Text(preset) })
                        }
                    }
                    if (classStudents.isEmpty()) {
                        Text("No students enrolled in $selectedClass-$selectedSection.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        classStudents.forEach { st ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(st.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(st.userId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = marksMap[st.id] ?: "",
                                    onValueChange = { marksMap[st.id] = it.filter { ch -> ch.isDigit() }.take(3) },
                                    label = { Text("Marks") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(100.dp),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val maxVal = (maxMarksInput.toLongOrNull() ?: 20L).coerceIn(1L, 100L)
                                val entries = classStudents.mapNotNull { st ->
                                    val sc = marksMap[st.id]?.toLongOrNull() ?: return@mapNotNull null
                                    DptWeekly(
                                        week = selectedWeek,
                                        className = selectedClass,
                                        section = selectedSection,
                                        subject = selectedSubject,
                                        studentDocId = st.id,
                                        studentName = st.fullName,
                                        admissionNo = st.userId,
                                        scored = sc.coerceIn(0L, maxVal),
                                        maxMarks = maxVal,
                                        teacherId = teacher.userId,
                                    )
                                }
                                if (entries.isNotEmpty()) onSaveWeeklyDptMarks(entries)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("weekly_dpt_save_btn"),
                        ) {
                            Text("Save Weekly DPT Marks & Update Ranks")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenProgressDashboard,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_open_progress_chart_btn"),
                    ) {
                        Icon(Icons.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View Individual Student Progress Chart")
                    }
                }
            }
        }

        item {
            Text("EACH STUDENT'S DPT RESULT CARD ($selectedWeek)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(leaderboard.withIndex().toList(), key = { it.value.first.id }) { indexed ->
            val rank = indexed.index + 1
            val (student, entries, pct) = indexed.value
            StudentDptResultCard(
                student = student,
                weekLabel = selectedWeek,
                studentEntries = entries,
                rankLabel = if (pct >= 0.0) "#$rank" else "—",
            )
        }
    }
}

@Composable
private fun AttendanceAndLeaveTab(
    teacher: SchoolUser,
    selectedDate: String,
    selectedClass: String,
    selectedSection: String,
    classStudents: List<SchoolUser>,
    attendance: List<AttendanceRecord>,
    leaves: List<LeaveRequest>,
    onMarkAttendance: (AttendanceRecord) -> Unit,
    onMarkAllPresent: (List<SchoolUser>, String, String, String, String) -> Unit,
    onUpdateLeaveStatus: (String, String, String) -> Unit,
) {
    val remarkInputs = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Daily Attendance ($selectedClass-$selectedSection)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Date: $selectedDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { onMarkAllPresent(classStudents, selectedDate, selectedClass, selectedSection, teacher.userId) },
                    enabled = classStudents.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                    modifier = Modifier.testTag("mark_all_present_btn"),
                ) {
                    Text("Mark All Present")
                }
            }
        }

        items(classStudents, key = { it.id }) { st ->
            val current = attendance.firstOrNull { it.date == selectedDate && it.studentDocId == st.id }?.status ?: "Unmarked"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(st.fullName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(st.userId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(current, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Present" to EmeraldSuccess, "Absent" to RoseError, "Leave" to GoldAmberDark).forEach { (status, tint) ->
                            val selected = current == status
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onMarkAttendance(
                                        AttendanceRecord(
                                            date = selectedDate,
                                            className = selectedClass,
                                            section = selectedSection,
                                            studentDocId = st.id,
                                            status = status,
                                            markedBy = teacher.userId,
                                        )
                                    )
                                },
                                label = { Text(status, color = if (selected) Color.White else tint) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tint),
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("STUDENT LEAVE APPLICATIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }

        items(leaves, key = { it.id }) { lv ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${lv.studentName} (${lv.className}-${lv.section})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Surface(
                            color = when (lv.status) {
                                "Approved" -> EmeraldContainer
                                "Rejected" -> RoseContainer
                                else -> GoldSoft
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                lv.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyDeep,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text("${lv.fromDate} to ${lv.toDate} • ${lv.reason}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lv.status == "Pending") {
                        OutlinedTextField(
                            value = remarkInputs[lv.id] ?: "",
                            onValueChange = { remarkInputs[lv.id] = it },
                            label = { Text("Teacher Remark") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onUpdateLeaveStatus(lv.id, "Approved", remarkInputs[lv.id]?.ifBlank { "Approved" } ?: "Approved") },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                                modifier = Modifier.weight(1f),
                            ) { Text("Approve") }
                            OutlinedButton(
                                onClick = { onUpdateLeaveStatus(lv.id, "Rejected", remarkInputs[lv.id]?.ifBlank { "Unable to approve" } ?: "Unable to approve") },
                                modifier = Modifier.weight(1f),
                            ) { Text("Reject", color = RoseError) }
                        }
                    } else if (lv.teacherRemark.isNotBlank()) {
                        Text("Remark: ${lv.teacherRemark}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryMarksDoubtsTab(
    teacher: SchoolUser,
    selectedClass: String,
    selectedSection: String,
    selectedSubject: String,
    subjectsForCls: List<String>,
    classStudents: List<SchoolUser>,
    homework: List<HomeworkItem>,
    doubts: List<DoubtMessage>,
    onPostHomework: (HomeworkItem) -> Unit,
    onPublishTermMarks: (TermMarkCard) -> Unit,
    onReplyDoubt: (String, String, String) -> Unit,
    onDeleteHomework: (HomeworkItem) -> Unit,
) {
    var hwTitle by remember { mutableStateOf("") }
    var hwDueDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var hwDesc by remember { mutableStateOf("") }

    var termName by remember { mutableStateOf("Half-Yearly Exam 2026") }
    var selectedStudent by remember(classStudents) { mutableStateOf(classStudents.firstOrNull()) }
    var studentMenu by remember { mutableStateOf(false) }
    val termScores = remember(subjectsForCls) {
        mutableStateMapOf<String, String>().apply {
            subjectsForCls.forEach { put(it, "85") }
        }
    }
    var termRemarks by remember { mutableStateOf("Very Good Progress") }
    val doubtReplies = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Homework Diary Composer
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Post Homework ($selectedClass-$selectedSection • $selectedSubject)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(value = hwTitle, onValueChange = { hwTitle = it }, label = { Text("Homework Title / Chapter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = hwDueDate, onValueChange = { hwDueDate = it }, label = { Text("Due Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = hwDesc, onValueChange = { hwDesc = it }, label = { Text("Instructions") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (hwTitle.isBlank()) return@Button
                            onPostHomework(
                                HomeworkItem(
                                    className = selectedClass,
                                    section = selectedSection,
                                    subject = selectedSubject,
                                    title = hwTitle.trim(),
                                    description = hwDesc.trim(),
                                    dueDate = hwDueDate.trim(),
                                    teacherName = teacher.fullName,
                                )
                            )
                            hwTitle = ""
                            hwDesc = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Publish Homework") }
                }
            }
        }

        items(homework.filter { it.className == selectedClass && it.section == selectedSection }, key = { it.id }) { hw ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${hw.subject}: ${hw.title}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(hw.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Due: ${hw.dueDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDeleteHomework(hw) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete homework", tint = RoseError)
                    }
                }
            }
        }

        // 2. Term Report Card Publisher
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Publish Term Exam Report Card", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(value = termName, onValueChange = { termName = it }, label = { Text("Term Exam Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Box {
                        OutlinedButton(onClick = { studentMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedStudent?.let { "${it.fullName} (${it.userId})" } ?: "Select Student")
                        }
                        DropdownMenu(expanded = studentMenu, onDismissRequest = { studentMenu = false }) {
                            classStudents.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.fullName} (${st.userId})") },
                                    onClick = {
                                        selectedStudent = st
                                        studentMenu = false
                                    },
                                )
                            }
                        }
                    }
                    subjectsForCls.forEach { sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = termScores[sub] ?: "",
                                onValueChange = { termScores[sub] = it },
                                label = { Text("/ 100") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(100.dp),
                            )
                        }
                    }
                    OutlinedTextField(value = termRemarks, onValueChange = { termRemarks = it }, label = { Text("Teacher Remarks") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val st = selectedStudent ?: return@Button
                            val marksMap = subjectsForCls.associateWith { sub ->
                                val sc = (termScores[sub]?.toLongOrNull() ?: 0L).coerceIn(0L, 100L)
                                mapOf("scored" to sc, "max" to 100L)
                            }
                            onPublishTermMarks(
                                TermMarkCard(
                                    term = termName.trim(),
                                    className = selectedClass,
                                    section = selectedSection,
                                    studentDocId = st.id,
                                    studentName = st.fullName,
                                    admissionNo = st.userId,
                                    marks = marksMap,
                                    remarks = termRemarks.trim(),
                                    publishedBy = teacher.fullName,
                                )
                            )
                        },
                        enabled = selectedStudent != null,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Publish Term Report Card") }
                }
            }
        }

        // 3. Parent-Teacher Doubt Box
        item {
            Text("PARENT-TEACHER DOUBT BOX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(doubts, key = { it.id }) { dbt ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${dbt.studentName} (${dbt.className}) • ${dbt.subject}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(dbt.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (dbt.reply.isNotBlank()) {
                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Replied (${dbt.repliedBy}): ${dbt.reply}", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess, modifier = Modifier.padding(8.dp))
                        }
                    } else {
                        OutlinedTextField(
                            value = doubtReplies[dbt.id] ?: "",
                            onValueChange = { doubtReplies[dbt.id] = it },
                            label = { Text("Write Teacher Reply") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val rep = doubtReplies[dbt.id]?.trim().orEmpty()
                                if (rep.isNotEmpty()) {
                                    onReplyDoubt(dbt.id, rep, teacher.fullName)
                                    doubtReplies.remove(dbt.id)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyRoyal, contentColor = Color.White),
                        ) { Text("Send Reply") }
                    }
                }
            }
        }
    }
}
