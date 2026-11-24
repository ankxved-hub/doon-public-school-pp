package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DptDaily
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolUser
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class DptEntryMode(val label: String) {
    SCHEDULE("Schedule / Assign"),
    COMPLETED("Mark Completed"),
    NOT_HELD("Not Held"),
}

private enum class DptListFilter(val label: String) {
    ALL("All"),
    SCHEDULED("Scheduled"),
    COMPLETED("Completed"),
}

private data class DeadlineBadgeInfo(
    val label: String,
    val bgColor: Color,
    val textColor: Color,
    val isScheduled: Boolean,
)

private fun isScheduledPracticeTest(log: DptDaily): Boolean {
    return !log.held && (log.topic.isNotBlank() || log.reason.equals("Scheduled Practice Test", ignoreCase = true))
}

private fun deadlineBadgeFor(log: DptDaily, today: LocalDate): DeadlineBadgeInfo {
    if (log.held) {
        return DeadlineBadgeInfo("COMPLETED", EmeraldContainer, EmeraldSuccess, isScheduled = false)
    }
    if (!isScheduledPracticeTest(log)) {
        return DeadlineBadgeInfo("NOT HELD", GoldSoft, GoldAmberDark, isScheduled = false)
    }
    val parsed = runCatching { LocalDate.parse(log.date.trim()) }.getOrNull()
        ?: return DeadlineBadgeInfo("ASSIGNED", Color(0xFFE0E7FF), NavyDeep, isScheduled = true)
    val days = ChronoUnit.DAYS.between(today, parsed)
    return when {
        days < 0L -> DeadlineBadgeInfo("OVERDUE (${-days}d)", RoseContainer, RoseError, isScheduled = true)
        days == 0L -> DeadlineBadgeInfo("DUE TODAY", GoldSoft, GoldAmberDark, isScheduled = true)
        days == 1L -> DeadlineBadgeInfo("DUE TOMORROW", Color(0xFFE0E7FF), IndigoAccent, isScheduled = true)
        else -> DeadlineBadgeInfo("DUE IN ${days}d", Color(0xFFE0E7FF), NavyDeep, isScheduled = true)
    }
}

private fun isoDatePlusDays(days: Long): String {
    return runCatching { LocalDate.now().plusDays(days).toString() }.getOrDefault(SchoolCatalog.todayIso())
}

/**
 * Daily Practice Tests Dashboard component for Teachers.
 * Lets teachers schedule & assign upcoming practice tests with deadlines and test subjects,
 * mark scheduled tests as completed, or log not-held reasons, and browse a filterable list.
 */
@Composable
fun TeacherDailyPracticeTestsDashboard(
    teacher: SchoolUser,
    selectedDate: String,
    onDateChange: (String) -> Unit,
    selectedClass: String,
    selectedSection: String,
    selectedSubject: String,
    subjectsForClass: List<String>,
    dptDaily: List<DptDaily>,
    onSaveDailyDpt: (DptDaily) -> Unit,
    onDeleteDpt: (DptDaily) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = remember { runCatching { LocalDate.now() }.getOrNull() ?: LocalDate.of(2026, 1, 1) }

    var showComposer by remember { mutableStateOf(true) }
    var entryMode by remember { mutableStateOf(DptEntryMode.SCHEDULE) }
    var assessmentType by remember { mutableStateOf("DPT") } // "DPT" | "EXAM"
    var formSubject by remember(selectedSubject) { mutableStateOf(selectedSubject) }
    var deadlineDate by remember(selectedDate) { mutableStateOf(selectedDate.ifBlank { SchoolCatalog.todayIso() }) }
    var topic by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("20") }
    var notes by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(SchoolCatalog.dptNotHeldReasons.first()) }
    var pendingAttachment by remember { mutableStateOf<PickedAttachment?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var listFilter by remember { mutableStateOf(DptListFilter.ALL) }
    var subjectFilter by remember { mutableStateOf("ALL") }

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

    val classLogs = remember(dptDaily, selectedClass, selectedSection) {
        dptDaily
            .filter { it.className == selectedClass && it.section == selectedSection }
            .sortedWith(
                compareByDescending<DptDaily> { isScheduledPracticeTest(it) }
                    .thenBy { if (isScheduledPracticeTest(it)) it.date else "9999-99-99" }
                    .thenByDescending { it.date }
            )
    }

    val scheduledCount = remember(classLogs) { classLogs.count { isScheduledPracticeTest(it) } }
    val dueTodayCount = remember(classLogs, today) {
        classLogs.count { isScheduledPracticeTest(it) && it.date.trim() == today.toString() }
    }
    val completedCount = remember(classLogs) { classLogs.count { it.held } }

    val visibleLogs = remember(classLogs, listFilter, subjectFilter) {
        classLogs.filter { log ->
            val statusOk = when (listFilter) {
                DptListFilter.ALL -> true
                DptListFilter.SCHEDULED -> isScheduledPracticeTest(log)
                DptListFilter.COMPLETED -> log.held
            }
            val subjectOk = subjectFilter == "ALL" || log.subject.equals(subjectFilter, ignoreCase = true)
            statusOk && subjectOk
        }
    }

    val availableSubjectsInLogs = remember(classLogs, subjectsForClass) {
        (subjectsForClass + classLogs.map { it.subject }.filter { it.isNotBlank() }).distinct()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("teacher_dpt_dashboard"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Dashboard Hero Summary Header
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "DAILY PRACTICE TESTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAmber,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "$selectedClass-$selectedSection Practice Test Hub",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                        }
                        FilledTonalButton(
                            onClick = { showComposer = !showComposer },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = GoldAmber,
                                contentColor = NavyDeep,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dpt_toggle_schedule_btn"),
                        ) {
                            Icon(
                                if (showComposer) Icons.Filled.ExpandLess else Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showComposer) "Hide" else "Schedule")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DptDashboardStatPill(
                            label = "Scheduled",
                            value = scheduledCount.toString(),
                            accent = GoldAmber,
                            modifier = Modifier.weight(1f),
                        )
                        DptDashboardStatPill(
                            label = "Due Today",
                            value = dueTodayCount.toString(),
                            accent = Color(0xFF93C5FD),
                            modifier = Modifier.weight(1f),
                        )
                        DptDashboardStatPill(
                            label = "Completed",
                            value = completedCount.toString(),
                            accent = Color(0xFF6EE7B7),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Schedule & Assign Practice Test Composer
        item {
            AnimatedVisibility(visible = showComposer) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (assessmentType == "EXAM") IndigoAccent else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Schedule & Assign Practice Test ($selectedClass-$selectedSection)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Action Mode Selector
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            DptEntryMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = entryMode == mode,
                                    onClick = {
                                        entryMode = mode
                                        validationError = null
                                    },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("dpt_mode_${mode.name.lowercase()}"),
                                )
                            }
                        }

                        // Assessment Type Selector
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
                                label = { Text("Practice Test (DPT)") },
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

                        // Test Subject Chips
                        Text("Test Subject", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            subjectsForClass.forEach { sub ->
                                FilterChip(
                                    selected = formSubject == sub,
                                    onClick = { formSubject = sub },
                                    label = { Text(sub) },
                                )
                            }
                        }

                        // Deadline / Test Date Picker + Quick Presets
                        SchoolDatePickerField(
                            label = if (entryMode == DptEntryMode.SCHEDULE) "Submission / Test Deadline" else "Test Date",
                            selectedDate = deadlineDate,
                            onDateSelected = {
                                deadlineDate = it
                                onDateChange(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTagName = "dpt_deadline_picker",
                        )
                        if (entryMode == DptEntryMode.SCHEDULE) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                listOf(
                                    "Today" to 0L,
                                    "Tomorrow" to 1L,
                                    "+3 Days" to 3L,
                                    "+7 Days" to 7L,
                                ).forEach { (lbl, offset) ->
                                    val iso = isoDatePlusDays(offset)
                                    AssistChip(
                                        onClick = {
                                            deadlineDate = iso
                                            onDateChange(iso)
                                        },
                                        label = { Text(lbl) },
                                    )
                                }
                            }
                        }

                        if (entryMode != DptEntryMode.NOT_HELD) {
                            OutlinedTextField(
                                value = topic,
                                onValueChange = {
                                    topic = it.take(200)
                                    if (validationError != null) validationError = null
                                },
                                label = { Text(if (assessmentType == "EXAM") "Exam Title / Syllabus Topic" else "Practice Test Title / Topic") },
                                placeholder = { Text("e.g. Ch. 4 Quadratic Equations — 15 MCQs") },
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
                                label = { Text("Instructions, Syllabus & Practice Questions (Optional)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("dpt_notes_input"),
                            )

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
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dpt_attach_photo_btn"),
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
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dpt_attach_doc_btn"),
                                ) {
                                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Attach PDF/Doc", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            AttachmentPickerChip(
                                attachment = pendingAttachment,
                                onClear = { pendingAttachment = null },
                            )
                        } else {
                            Text("Select Not-Held Reason:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                val isNotHeld = entryMode == DptEntryMode.NOT_HELD
                                if (!isNotHeld && topic.isBlank()) {
                                    validationError = "Please enter a practice test title or topic."
                                    return@Button
                                }
                                if (isNotHeld && reason.isBlank()) {
                                    validationError = "Please enter why the practice test was not held."
                                    return@Button
                                }
                                val parsedMax = if (!isNotHeld) {
                                    val rawNum = maxMarks.toLongOrNull() ?: if (assessmentType == "EXAM") 100L else 20L
                                    if (rawNum !in 1L..100L) {
                                        validationError = "Maximum Marks must be between 1 and 100."
                                        return@Button
                                    }
                                    rawNum
                                } else 0L

                                validationError = null
                                val att = if (!isNotHeld) pendingAttachment else null
                                onSaveDailyDpt(
                                    DptDaily(
                                        date = deadlineDate.trim().ifBlank { SchoolCatalog.todayIso() }.take(20),
                                        className = selectedClass,
                                        section = selectedSection,
                                        subject = formSubject.ifBlank { selectedSubject },
                                        assessmentType = assessmentType,
                                        held = entryMode == DptEntryMode.COMPLETED,
                                        topic = if (!isNotHeld) topic.trim().take(200) else "",
                                        maxMarks = parsedMax,
                                        notes = if (!isNotHeld) notes.trim().take(1000) else "",
                                        reason = when (entryMode) {
                                            DptEntryMode.SCHEDULE -> "Scheduled Practice Test"
                                            DptEntryMode.COMPLETED -> ""
                                            DptEntryMode.NOT_HELD -> reason.trim().take(200)
                                        },
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
                            Icon(
                                when (entryMode) {
                                    DptEntryMode.SCHEDULE -> Icons.Filled.EventAvailable
                                    DptEntryMode.COMPLETED -> Icons.Filled.CheckCircle
                                    DptEntryMode.NOT_HELD -> Icons.Filled.EventBusy
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (entryMode) {
                                    DptEntryMode.SCHEDULE -> "Schedule & Assign Practice Test"
                                    DptEntryMode.COMPLETED -> "Publish Completed Test"
                                    DptEntryMode.NOT_HELD -> "Log Test Not Held"
                                }
                            )
                        }
                    }
                }
            }
        }

        // Filter Bar for Practice Tests List
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ASSIGNED & SCHEDULED PRACTICE TESTS (${visibleLogs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    DptListFilter.entries.forEach { f ->
                        FilterChip(
                            selected = listFilter == f,
                            onClick = { listFilter = f },
                            label = { Text(f.label) },
                            modifier = Modifier.testTag("dpt_filter_${f.name.lowercase()}"),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = subjectFilter == "ALL",
                        onClick = { subjectFilter = "ALL" },
                        label = { Text("All Subjects") },
                    )
                    availableSubjectsInLogs.forEach { sub ->
                        FilterChip(
                            selected = subjectFilter == sub,
                            onClick = { subjectFilter = sub },
                            label = { Text(sub) },
                        )
                    }
                }
            }
        }

        if (visibleLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = null, tint = GoldAmberDark, modifier = Modifier.size(34.dp))
                        Text("No practice tests in this view yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Schedule a practice test above with a subject and deadline to assign it to $selectedClass-$selectedSection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(visibleLogs, key = { it.id }) { log ->
                val isExam = log.assessmentType == "EXAM"
                val badge = deadlineBadgeFor(log, today)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        when {
                            badge.isScheduled -> IndigoAccent.copy(alpha = 0.55f)
                            isExam -> GoldAmber
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("dpt_item_card_${log.id}"),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Subject + Type + Deadline Status Pills
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    Surface(
                                        color = if (isExam) Color(0xFFE0E7FF) else GoldSoft,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            if (isExam) "EXAM" else "DPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExam) NavyDeep else GoldAmberDark,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                    Surface(color = badge.bgColor, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            badge.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badge.textColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "${log.className}-${log.section}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                // Subject & Topic Title
                                Text(
                                    text = buildString {
                                        append(log.subject)
                                        if (log.topic.isNotBlank()) append(" • ${log.topic}")
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Deadline & Marks Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            Icons.Filled.CalendarToday,
                                            contentDescription = "Deadline",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = if (badge.isScheduled) "Deadline: ${log.date}" else "Date: ${log.date}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (log.maxMarks > 0L) {
                                        Text(
                                            "Max Marks: ${log.maxMarks}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (log.notes.isNotBlank()) {
                                    Text(
                                        log.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else if (!log.held && !badge.isScheduled && log.reason.isNotBlank()) {
                                    Text(
                                        "Reason: ${log.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoldAmberDark,
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteDpt(log) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete practice test", tint = RoseError)
                            }
                        }

                        if (badge.isScheduled) {
                            FilledTonalButton(
                                onClick = {
                                    onSaveDailyDpt(
                                        log.copy(
                                            held = true,
                                            reason = "",
                                        )
                                    )
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = EmeraldContainer,
                                    contentColor = EmeraldSuccess,
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("dpt_mark_completed_${log.id}"),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mark Practice Test Completed")
                            }
                        }

                        if (log.attachmentData.isNotBlank()) {
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
}

@Composable
private fun DptDashboardStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
