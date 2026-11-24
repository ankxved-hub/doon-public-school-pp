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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.model.SchoolCatalog.toSlotMap
import com.example.ui.components.AttachmentPickerChip
import com.example.ui.components.AttachmentPreviewBlock
import com.example.ui.components.PickedAttachment
import com.example.ui.components.SchoolDatePickerField
import com.example.ui.components.StudentDptResultCard
import com.example.ui.components.TeacherDailyPracticeTestsDashboard
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
    timetables: List<ClassTimetable> = emptyList(),
    craftProjects: List<CraftProject> = emptyList(),
    likedProjectIds: Set<String> = emptySet(),
    likingProjectIds: Set<String> = emptySet(),
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
    onSaveStudent: (SchoolUser) -> Unit = {},
    onDeleteStudent: (SchoolUser) -> Unit = {},
    onSaveTimetable: (String, String, List<SchoolCatalog.TimetableSlot>, String) -> Unit = { _, _, _, _ -> },
    onGenerateCraftAiImage: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onGenerateCustomAiImage: (String) -> Unit = {},
    onEditCraftAiImage: (String, String) -> Unit = { _, _ -> },
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
        val teacherSubs = teacher.subject.split(",").map { it.trim() }.filter { it.isNotBlank() }
        mutableStateOf(
            teacherSubs.firstOrNull { it in subjectsForCls }
                ?: subjectsForCls.firstOrNull()
                ?: "Mathematics"
        )
    }
    var selectedDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var showStudentProgressDashboard by remember { mutableStateOf(false) }
    var showCraftStudioOverlay by remember { mutableStateOf(false) }
    var showClassStudentsOverlay by remember { mutableStateOf(false) }
    var showTimetableOverlay by remember { mutableStateOf(false) }

    val allStudents = remember(users) {
        users.filter { it.role == "STUDENT" }.sortedBy { it.userId }
    }
    val classStudents = remember(allStudents, selectedClass, selectedSection) {
        allStudents.filter {
            it.className == selectedClass && it.section == selectedSection
        }
    }

    if (showClassStudentsOverlay) {
        BackHandler { showClassStudentsOverlay = false }
        TeacherClassStudentsView(
            teacher = teacher,
            allUsers = users,
            onSaveStudent = onSaveStudent,
            onDeleteStudent = onDeleteStudent,
            onShowMessage = onShowMessage,
            onBack = { showClassStudentsOverlay = false },
            modifier = modifier,
        )
        return
    }

    if (showTimetableOverlay) {
        BackHandler { showTimetableOverlay = false }
        TeacherTimetableEditorView(
            teacher = teacher,
            initialClass = selectedClass,
            initialSection = selectedSection,
            timetables = timetables,
            onSaveTimetable = onSaveTimetable,
            onShowMessage = onShowMessage,
            onBack = { showTimetableOverlay = false },
            modifier = modifier,
        )
        return
    }

    if (showCraftStudioOverlay) {
        BackHandler { showCraftStudioOverlay = false }
        CraftStudioScreen(
            currentUser = teacher,
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
                            "${teacher.userId} • ${teacher.subject.ifBlank { "Faculty" }} • Class: ${teacher.className}-${teacher.section}",
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
                    AssistChip(
                        onClick = { showClassStudentsOverlay = true },
                        label = { Text("My Students (${teacher.className}-${teacher.section})") },
                        leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = "Add Class Students", modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("teacher_bar_students_chip"),
                    )
                    AssistChip(
                        onClick = { showTimetableOverlay = true },
                        label = { Text("Timetable ($selectedClass-$selectedSection)") },
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = "Edit Timetable", modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("teacher_bar_timetable_chip"),
                    )
                }
            }
        }

        when (activeTab) {
            0 -> TeacherDailyPracticeTestsDashboard(
                teacher = teacher,
                selectedDate = selectedDate,
                onDateChange = { selectedDate = it },
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                selectedSubject = selectedSubject,
                subjectsForClass = subjectsForCls,
                dptDaily = dptDaily,
                onSaveDailyDpt = onSaveDailyDpt,
                onDeleteDpt = { onDeleteRecord("dptDaily", it.id, "Practice Test") },
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
                onOpenManageClassStudents = { showClassStudentsOverlay = true },
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
                allUsers = users,
                selectedClass = selectedClass,
                selectedSection = selectedSection,
                selectedSubject = selectedSubject,
                subjectsForCls = subjectsForCls,
                classStudents = classStudents,
                homework = homework,
                doubts = doubts,
                timetables = timetables,
                craftProjects = craftProjects,
                likedProjectIds = likedProjectIds,
                likingProjectIds = likingProjectIds,
                craftAiImageState = craftAiImageState,
                generatingCraftPostId = generatingCraftPostId,
                onPostHomework = onPostHomework,
                onPublishTermMarks = onPublishTermMarks,
                onReplyDoubt = onReplyDoubt,
                onDeleteHomework = { onDeleteRecord("homework", it.id, it.title) },
                onSaveStudent = onSaveStudent,
                onDeleteStudent = onDeleteStudent,
                onSaveTimetable = onSaveTimetable,
                onGenerateCraftAiImage = onGenerateCraftAiImage,
                onGenerateCustomAiImage = onGenerateCustomAiImage,
                onEditCraftAiImage = onEditCraftAiImage,
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

private enum class TeacherAcademicSubTab(val label: String) {
    DIARY_MARKS("Diary & Marks"),
    TIMETABLE("Timetable"),
    CLASS_STUDENTS("My Students"),
    CRAFT_STUDIO("Projects"),
}

@Composable
private fun TeacherDiaryOrCraftTab(
    teacher: SchoolUser,
    allUsers: List<SchoolUser>,
    selectedClass: String,
    selectedSection: String,
    selectedSubject: String,
    subjectsForCls: List<String>,
    classStudents: List<SchoolUser>,
    homework: List<HomeworkItem>,
    doubts: List<DoubtMessage>,
    timetables: List<ClassTimetable>,
    craftProjects: List<CraftProject>,
    likedProjectIds: Set<String>,
    likingProjectIds: Set<String>,
    craftAiImageState: CraftAiImageState,
    generatingCraftPostId: String?,
    onPostHomework: (HomeworkItem) -> Unit,
    onPublishTermMarks: (TermMarkCard) -> Unit,
    onReplyDoubt: (String, String, String) -> Unit,
    onDeleteHomework: (HomeworkItem) -> Unit,
    onSaveStudent: (SchoolUser) -> Unit,
    onDeleteStudent: (SchoolUser) -> Unit,
    onSaveTimetable: (String, String, List<SchoolCatalog.TimetableSlot>, String) -> Unit,
    onGenerateCraftAiImage: (String, String, String, String) -> Unit,
    onGenerateCustomAiImage: (String) -> Unit,
    onEditCraftAiImage: (String, String) -> Unit,
    onClearCraftPreview: () -> Unit,
    onPublishCraftProject: (CraftProject) -> Unit,
    onGenerateCraftImageForPost: (CraftProject) -> Unit,
    onApplaudCraftProject: (CraftProject) -> Unit,
    onDeleteCraftProject: (CraftProject) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    var activeSubTab by remember { mutableStateOf(TeacherAcademicSubTab.DIARY_MARKS) }
    BackHandler(enabled = activeSubTab != TeacherAcademicSubTab.DIARY_MARKS) {
        activeSubTab = TeacherAcademicSubTab.DIARY_MARKS
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TeacherAcademicSubTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    label = { Text(tab.label) },
                    leadingIcon = {
                        Icon(
                            when (tab) {
                                TeacherAcademicSubTab.DIARY_MARKS -> Icons.Filled.MenuBook
                                TeacherAcademicSubTab.TIMETABLE -> Icons.Filled.Schedule
                                TeacherAcademicSubTab.CLASS_STUDENTS -> Icons.Filled.PersonAdd
                                TeacherAcademicSubTab.CRAFT_STUDIO -> Icons.Filled.Brush
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("teacher_subtab_${tab.name.lowercase()}"),
                )
            }
        }

        when (activeSubTab) {
            TeacherAcademicSubTab.CRAFT_STUDIO -> CraftStudioScreen(
                currentUser = teacher,
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
                onDeletePost = onDeleteCraftProject,
                onShowMessage = onShowMessage,
                onBack = { activeSubTab = TeacherAcademicSubTab.DIARY_MARKS },
                modifier = Modifier.weight(1f),
            )
            TeacherAcademicSubTab.TIMETABLE -> TeacherTimetableEditorView(
                teacher = teacher,
                initialClass = selectedClass,
                initialSection = selectedSection,
                timetables = timetables,
                onSaveTimetable = onSaveTimetable,
                onShowMessage = onShowMessage,
                modifier = Modifier.weight(1f),
            )
            TeacherAcademicSubTab.CLASS_STUDENTS -> TeacherClassStudentsView(
                teacher = teacher,
                allUsers = allUsers,
                onSaveStudent = onSaveStudent,
                onDeleteStudent = onDeleteStudent,
                onShowMessage = onShowMessage,
                modifier = Modifier.weight(1f),
            )
            TeacherAcademicSubTab.DIARY_MARKS -> Box(modifier = Modifier.weight(1f)) {
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
    onOpenManageClassStudents: () -> Unit,
) {
    val remarkInputs = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GoldSoft),
                border = BorderStroke(1.dp, GoldAmber),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "My Class Roster: ${teacher.className.ifBlank { "Class" }}-${teacher.section}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDeep,
                        )
                        Text(
                            "Add or manage students of your assigned class with auto roll numbers (001, 002...).",
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyRoyal,
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenManageClassStudents,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        modifier = Modifier.testTag("teacher_manage_class_students_btn"),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Students")
                    }
                }
            }
        }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Enter marks out of 100 (leave blank to skip electives):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                subjectsForCls.forEach { termScores[it] = "" }
                            }
                        ) {
                            Text("Clear All")
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
                                onValueChange = { ch -> termScores[sub] = ch.filter { it.isDigit() }.take(3) },
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
                            val marksMap = subjectsForCls.mapNotNull { sub ->
                                val raw = termScores[sub]?.trim().orEmpty()
                                val sc = raw.toLongOrNull() ?: return@mapNotNull null
                                sub to mapOf("scored" to sc.coerceIn(0L, 100L), "max" to 100L)
                            }.toMap()
                            if (marksMap.isEmpty()) return@Button
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

@Composable
private fun TeacherClassStudentsView(
    teacher: SchoolUser,
    allUsers: List<SchoolUser>,
    onSaveStudent: (SchoolUser) -> Unit,
    onDeleteStudent: (SchoolUser) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val assignedClass = teacher.className.trim()
    val assignedSection = teacher.section.trim()
    val hasAssignedClass = assignedClass.isNotBlank() && assignedSection.isNotBlank()
    val assignedWing = remember(assignedClass) {
        if (assignedClass.isNotBlank()) SchoolCatalog.wingForClass(assignedClass) else teacher.wing
    }

    val myStudents = remember(allUsers, assignedClass, assignedSection) {
        allUsers
            .filter { it.role == "STUDENT" && it.className == assignedClass && it.section == assignedSection }
            .sortedBy { it.userId }
    }

    var editingDocId by remember { mutableStateOf("") }
    var editingCreatedAt by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var fullName by remember { mutableStateOf("") }
    var rollNo by remember(allUsers) { mutableStateOf(SchoolCatalog.nextStudentRollNumber(allUsers)) }
    var password by remember { mutableStateOf("stud123") }
    var parentName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var monthlyTuition by remember { mutableStateOf("2500") }
    var transportFee by remember { mutableStateOf("0") }

    LaunchedEffect(allUsers, editingDocId) {
        if (editingDocId.isBlank()) {
            rollNo = SchoolCatalog.nextStudentRollNumber(allUsers)
        }
    }

    fun resetStudentForm() {
        editingDocId = ""
        editingCreatedAt = null
        fullName = ""
        rollNo = SchoolCatalog.nextStudentRollNumber(allUsers)
        password = "stud123"
        parentName = ""
        phone = ""
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("teacher_class_students_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAmber)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MY ASSIGNED CLASS STUDENTS", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                        Text(
                            if (hasAssignedClass) "$assignedClass - Section $assignedSection ($assignedWing)"
                            else "No Assigned Class",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "Teachers can add and manage students strictly within their assigned class-section.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        if (!hasAssignedClass) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, RoseError),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Your teacher account does not have an assigned Class & Section yet. Ask Principal/Admin to assign your class.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoseError,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            return@LazyColumn
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
                        Text(
                            if (editingDocId.isBlank()) "Add Student to $assignedClass-$assignedSection"
                            else "Edit Student ($assignedClass-$assignedSection)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (editingDocId.isNotBlank()) {
                            TextButton(onClick = { resetStudentForm() }) { Text("Cancel") }
                        }
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Student Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_student_name_input"),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = rollNo,
                            onValueChange = { rollNo = it },
                            label = { Text("Roll / Adm No (Auto)") },
                            trailingIcon = if (editingDocId.isBlank()) {
                                {
                                    IconButton(onClick = { rollNo = SchoolCatalog.nextStudentRollNumber(allUsers) }) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Auto Next Roll No")
                                    }
                                }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("teacher_student_roll_input"),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Parent Login Password") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("teacher_student_pw_input"),
                        )
                    }

                    OutlinedTextField(
                        value = parentName,
                        onValueChange = { parentName = it },
                        label = { Text("Father / Mother Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Parent Mobile Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = monthlyTuition,
                            onValueChange = { monthlyTuition = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Monthly Tuition (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = transportFee,
                            onValueChange = { transportFee = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Bus Fee (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Button(
                        onClick = {
                            if (fullName.isBlank() || password.isBlank()) {
                                onShowMessage("Please enter student full name and password.")
                                return@Button
                            }
                            onSaveStudent(
                                SchoolUser(
                                    id = editingDocId,
                                    role = "STUDENT",
                                    userId = rollNo.trim().ifBlank { SchoolCatalog.nextStudentRollNumber(allUsers) },
                                    password = password.trim(),
                                    fullName = fullName.trim(),
                                    phone = phone.trim(),
                                    wing = assignedWing,
                                    className = assignedClass,
                                    section = assignedSection,
                                    parentName = parentName.trim(),
                                    monthlyTuition = monthlyTuition.toLongOrNull() ?: 0L,
                                    transportFee = transportFee.toLongOrNull() ?: 0L,
                                    createdAt = editingCreatedAt,
                                )
                            )
                            resetStudentForm()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("teacher_save_student_btn"),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (editingDocId.isBlank()) "Add Student to $assignedClass-$assignedSection" else "Save Student Changes")
                    }
                }
            }
        }

        item {
            Text(
                "$assignedClass-$assignedSection ROSTER (${myStudents.size} Students)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (myStudents.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No students in $assignedClass-$assignedSection yet. Add the first student (Roll 001) above!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(myStudents, key = { it.id }) { st ->
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
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(st.fullName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Roll / ID: ${st.userId} • PW: ${st.password}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Parent: ${st.parentName.ifBlank { "—" }} • Ph: ${st.phone.ifBlank { "—" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    editingDocId = st.id
                                    editingCreatedAt = st.createdAt
                                    fullName = st.fullName
                                    rollNo = st.userId
                                    password = st.password
                                    parentName = st.parentName
                                    phone = st.phone
                                    monthlyTuition = st.monthlyTuition.toString()
                                    transportFee = st.transportFee.toString()
                                },
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${st.fullName}", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteStudent(st) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${st.fullName}", tint = RoseError)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherTimetableEditorView(
    teacher: SchoolUser,
    initialClass: String,
    initialSection: String,
    timetables: List<ClassTimetable>,
    onSaveTimetable: (String, String, List<SchoolCatalog.TimetableSlot>, String) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var ttClass by remember(initialClass) { mutableStateOf(initialClass.ifBlank { "Class VII" }) }
    var ttSection by remember(initialSection) { mutableStateOf(initialSection.ifBlank { "A" }) }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val savedDoc = remember(timetables, ttClass, ttSection) {
        timetables.firstOrNull { it.className == ttClass && it.section == ttSection }
    }

    val editableSlots = remember(ttClass, ttSection, savedDoc) {
        val loaded = savedDoc?.slots?.map { SchoolCatalog.slotFromMap(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: SchoolCatalog.defaultTimetableSlotsFor(ttClass, ttSection)
        mutableStateListOf<SchoolCatalog.TimetableSlot>().apply { addAll(loaded) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("teacher_timetable_editor"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAmber)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CLASS-WISE TIMETABLE EDITOR", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                        Text("$ttClass - Section $ttSection", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            if (savedDoc != null) "Custom Timetable Saved • Last edited by ${savedDoc.updatedBy}"
                            else "Using Default Template • Edit & Save separately per Class-Section",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Class & Section to Edit Independently", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { classDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("tt_class_dropdown"),
                        ) {
                            Icon(Icons.Filled.Class, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("$ttClass (${SchoolCatalog.wingForClass(ttClass)})", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = classDropdownExpanded, onDismissRequest = { classDropdownExpanded = false }) {
                            SchoolCatalog.allClasses.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text("$cls (${SchoolCatalog.wingForClass(cls)})") },
                                    onClick = {
                                        ttClass = cls
                                        classDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SchoolCatalog.sections.forEach { sec ->
                            FilterChip(
                                selected = ttSection == sec,
                                onClick = { ttSection = sec },
                                label = { Text("Section $sec") },
                                modifier = Modifier.testTag("tt_section_chip_$sec"),
                            )
                        }
                    }
                }
            }
        }

        items(editableSlots.indices.toList(), key = { idx -> "${ttClass}_${ttSection}_$idx" }) { idx ->
            val slot = editableSlots[idx]
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Slot #${idx + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (editableSlots.size > 1) {
                            IconButton(onClick = { editableSlots.removeAt(idx) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove slot", tint = RoseError)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = slot.period,
                            onValueChange = { editableSlots[idx] = slot.copy(period = it.take(30)) },
                            label = { Text("Period") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = slot.time,
                            onValueChange = { editableSlots[idx] = slot.copy(time = it.take(30)) },
                            label = { Text("Time") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = slot.mon,
                            onValueChange = { editableSlots[idx] = slot.copy(mon = it.take(40)) },
                            label = { Text("Mon") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = slot.tue,
                            onValueChange = { editableSlots[idx] = slot.copy(tue = it.take(40)) },
                            label = { Text("Tue") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = slot.wed,
                            onValueChange = { editableSlots[idx] = slot.copy(wed = it.take(40)) },
                            label = { Text("Wed") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = slot.thu,
                            onValueChange = { editableSlots[idx] = slot.copy(thu = it.take(40)) },
                            label = { Text("Thu") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = slot.fri,
                            onValueChange = { editableSlots[idx] = slot.copy(fri = it.take(40)) },
                            label = { Text("Fri") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = slot.sat,
                            onValueChange = { editableSlots[idx] = slot.copy(sat = it.take(40)) },
                            label = { Text("Sat") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            if (editableSlots.size < 16) {
                                editableSlots.add(
                                    SchoolCatalog.TimetableSlot(
                                        period = "Period ${editableSlots.size + 1}",
                                        time = "01:10 - 01:50",
                                        mon = "Self Study",
                                        tue = "Self Study",
                                        wed = "Self Study",
                                        thu = "Self Study",
                                        fri = "Self Study",
                                        sat = "Club",
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Period")
                    }
                    OutlinedButton(
                        onClick = {
                            editableSlots.clear()
                            editableSlots.addAll(SchoolCatalog.defaultTimetableSlotsFor(ttClass, ttSection))
                            onShowMessage("Reset draft to $ttClass-$ttSection default schedule.")
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset Default")
                    }
                }
                Button(
                    onClick = {
                        onSaveTimetable(ttClass, ttSection, editableSlots.toList(), teacher.fullName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("tt_save_btn"),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Timetable for $ttClass-$ttSection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
