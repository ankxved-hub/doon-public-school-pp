package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.AttachmentPreviewBlock
import com.example.ui.components.StatMetricCard
import com.example.ui.components.printHtmlDocument
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class AssessmentFilter(val label: String) {
    ALL("All Tests"),
    DPT_ONLY("Daily / Weekly DPT"),
    EXAM_ONLY("Major Exams"),
}

private data class ChartPoint(
    val id: String,
    val label: String,
    val shortLabel: String,
    val title: String,
    val scored: Long,
    val maxMarks: Long,
    val percentage: Float,
    val isMajorExam: Boolean,
    val sortOrder: Int,
)

private data class TimelineScoreItem(
    val key: String,
    val title: String,
    val periodLabel: String,
    val subjectBreakdown: List<Triple<String, Long, Long>>,
    val scored: Long,
    val maxMarks: Long,
    val percentage: Float,
    val isMajorExam: Boolean,
    val remarks: String,
)

/**
 * DPT Performance Dashboard Screen:
 * Aggregates individual student results across Weekly/Daily DPTs and Major Term Exams,
 * visualizing progress over time via an interactive multi-series line chart so parents,
 * students, teachers, and the principal can clearly distinguish daily tests vs major exams.
 */
@Composable
fun DptResultDashboardScreen(
    primaryStudent: SchoolUser,
    allStudents: List<SchoolUser>,
    dptDaily: List<DptDaily>,
    dptWeekly: List<DptWeekly>,
    termMarks: List<TermMarkCard>,
    allowStudentPicker: Boolean = false,
    onBack: (() -> Unit)? = null,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    var selectedStudent by remember(primaryStudent.id, allStudents) {
        mutableStateOf(
            allStudents.firstOrNull { it.id == primaryStudent.id } ?: primaryStudent
        )
    }
    var studentMenuOpen by remember { mutableStateOf(false) }
    var assessmentFilter by remember { mutableStateOf(AssessmentFilter.ALL) }
    var selectedSubject by remember(selectedStudent.className) { mutableStateOf("All Subjects") }

    val availableSubjects = remember(selectedStudent.className) {
        listOf("All Subjects") + SchoolCatalog.subjectsForClass(selectedStudent.className.ifBlank { "Class X" })
    }

    val studentWeekly = remember(dptWeekly, selectedStudent.id) {
        dptWeekly.filter { it.studentDocId == selectedStudent.id }
    }
    val studentTermMarks = remember(termMarks, selectedStudent.id) {
        termMarks.filter { it.studentDocId == selectedStudent.id }
    }
    val classDailyLogs = remember(dptDaily, selectedStudent.className, selectedStudent.section, selectedSubject) {
        dptDaily.filter { log ->
            log.className == selectedStudent.className &&
                log.section == selectedStudent.section &&
                (selectedSubject == "All Subjects" || log.subject == selectedSubject)
        }.sortedByDescending { it.date }
    }

    // Build chronological DPT points (by week) + Major Exam points
    val dptPoints = remember(studentWeekly, selectedSubject) {
        val groupedByWeek = studentWeekly
            .filter { selectedSubject == "All Subjects" || it.subject == selectedSubject }
            .groupBy { it.week }

        val weekOrder = SchoolCatalog.dptWeeks
        groupedByWeek.entries.sortedBy { (wk, _) ->
            val idx = weekOrder.indexOf(wk)
            if (idx >= 0) idx else 99
        }.mapIndexedNotNull { index, (week, entries) ->
            val scored = entries.sumOf { it.scored }
            val max = entries.sumOf { it.maxMarks }
            if (max <= 0L) null else {
                val pct = (scored * 100f) / max.toFloat()
                val wkShort = week.substringBefore(" (").replace("Week ", "Wk")
                ChartPoint(
                    id = "dpt_$week",
                    label = week,
                    shortLabel = wkShort,
                    title = if (selectedSubject == "All Subjects") "$week (${entries.size} subj)" else "$week • $selectedSubject",
                    scored = scored,
                    maxMarks = max,
                    percentage = pct,
                    isMajorExam = false,
                    sortOrder = index * 2,
                )
            }
        }
    }

    val examPoints = remember(studentTermMarks, selectedSubject) {
        studentTermMarks.mapIndexedNotNull { index, card ->
            val pairs = if (selectedSubject == "All Subjects") {
                card.marks.values.map { (it["scored"] ?: 0L) to (it["max"] ?: 100L) }
            } else {
                val subMap = card.marks[selectedSubject]
                if (subMap != null) listOf((subMap["scored"] ?: 0L) to (subMap["max"] ?: 100L)) else emptyList()
            }
            val scored = pairs.sumOf { it.first }
            val max = pairs.sumOf { it.second }
            if (max <= 0L) null else {
                val pct = (scored * 100f) / max.toFloat()
                val shortTerm = card.term.split(" ").take(2).joinToString(" ").take(10)
                ChartPoint(
                    id = "exam_${card.id}",
                    label = card.term,
                    shortLabel = shortTerm,
                    title = if (selectedSubject == "All Subjects") card.term else "${card.term} • $selectedSubject",
                    scored = scored,
                    maxMarks = max,
                    percentage = pct,
                    isMajorExam = true,
                    sortOrder = index * 3 + 1,
                )
            }
        }
    }

    val dptTimelineItems = remember(studentWeekly, selectedSubject) {
        studentWeekly
            .filter { selectedSubject == "All Subjects" || it.subject == selectedSubject }
            .groupBy { it.week }
            .entries
            .sortedByDescending { SchoolCatalog.dptWeeks.indexOf(it.key) }
            .mapNotNull { (week, entries) ->
                val scored = entries.sumOf { it.scored }
                val max = entries.sumOf { it.maxMarks }
                if (max <= 0L) null else TimelineScoreItem(
                    key = "wk_$week",
                    title = "Weekly DPT Compilation",
                    periodLabel = week,
                    subjectBreakdown = entries.map { Triple(it.subject, it.scored, it.maxMarks) },
                    scored = scored,
                    maxMarks = max,
                    percentage = (scored * 100f) / max.toFloat(),
                    isMajorExam = false,
                    remarks = "",
                )
            }
    }

    val examTimelineItems = remember(studentTermMarks, selectedSubject) {
        studentTermMarks.mapNotNull { card ->
            val filteredEntries = if (selectedSubject == "All Subjects") {
                card.marks.entries.map { Triple(it.key, it.value["scored"] ?: 0L, it.value["max"] ?: 100L) }
            } else {
                card.marks[selectedSubject]?.let {
                    listOf(Triple(selectedSubject, it["scored"] ?: 0L, it["max"] ?: 100L))
                } ?: emptyList()
            }
            val scored = filteredEntries.sumOf { it.second }
            val max = filteredEntries.sumOf { it.third }
            if (max <= 0L) null else TimelineScoreItem(
                key = "tm_${card.id}",
                title = card.term,
                periodLabel = "Published by ${card.publishedBy}",
                subjectBreakdown = filteredEntries,
                scored = scored,
                maxMarks = max,
                percentage = (scored * 100f) / max.toFloat(),
                isMajorExam = true,
                remarks = card.remarks,
            )
        }
    }

    val visibleItems = remember(assessmentFilter, dptTimelineItems, examTimelineItems) {
        when (assessmentFilter) {
            AssessmentFilter.ALL -> examTimelineItems + dptTimelineItems
            AssessmentFilter.DPT_ONLY -> dptTimelineItems
            AssessmentFilter.EXAM_ONLY -> examTimelineItems
        }
    }

    val dptAvg = remember(dptPoints) {
        if (dptPoints.isEmpty()) null else dptPoints.map { it.percentage }.average()
    }
    val examAvg = remember(examPoints) {
        if (examPoints.isEmpty()) null else examPoints.map { it.percentage }.average()
    }
    val overallAvg = remember(dptPoints, examPoints) {
        val all = dptPoints + examPoints
        if (all.isEmpty()) null else all.map { it.percentage }.average()
    }

    // Class Rank in latest populated week
    val classPeers = remember(allStudents, selectedStudent) {
        val peers = allStudents.filter {
            it.role == "STUDENT" && it.className == selectedStudent.className && it.section == selectedStudent.section
        }
        if (peers.any { it.id == selectedStudent.id }) peers else peers + selectedStudent
    }
    val latestWeek = remember(studentWeekly) {
        studentWeekly.maxByOrNull { SchoolCatalog.dptWeeks.indexOf(it.week) }?.week ?: SchoolCatalog.dptWeeks[1]
    }
    val rankLabel = remember(classPeers, dptWeekly, latestWeek, selectedStudent.id) {
        val ranked = classPeers.map { st ->
            val items = dptWeekly.filter { it.studentDocId == st.id && it.week == latestWeek }
            val max = items.sumOf { it.maxMarks }
            val pct = if (max > 0L) items.sumOf { it.scored } * 100.0 / max else -1.0
            st.id to pct
        }.sortedByDescending { it.second }
        val idx = ranked.indexOfFirst { it.first == selectedStudent.id }
        if (idx >= 0 && ranked[idx].second >= 0.0) "#${idx + 1} of ${ranked.size}" else "—"
    }

    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dpt_performance_dashboard"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            if (onBack != null) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("dpt_dashboard_back_btn"),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAmber)
                                }
                            }
                            Column {
                                Text("DPT & EXAM PERFORMANCE DASHBOARD", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                                Text(selectedStudent.fullName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Text(
                                    "${selectedStudent.userId} • ${selectedStudent.className}-${selectedStudent.section} • Parent: ${selectedStudent.parentName.ifBlank { "Guardian" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                val rows = visibleItems.joinToString("") { item ->
                                    val badge = if (item.isMajorExam) "MAJOR EXAM" else "DPT"
                                    "<tr><td style='padding:6px;border:1px solid #cbd5e1;'>$badge</td><td style='padding:6px;border:1px solid #cbd5e1;'>${item.title} (${item.periodLabel})</td><td style='padding:6px;border:1px solid #cbd5e1;'>${item.scored}/${item.maxMarks} (${"%.1f".format(item.percentage)}%)</td></tr>"
                                }
                                val html = """
                                    <html><body style="font-family:sans-serif;padding:24px;color:#0f172a;">
                                    <h2>${SchoolInfo.NAME}</h2>
                                    <p style="color:#475569;">${SchoolInfo.AFFILIATION} • Cumulative DPT & Exam Progress Report</p>
                                    <p><b>Student:</b> ${selectedStudent.fullName} (${selectedStudent.userId}) | <b>Class:</b> ${selectedStudent.className}-${selectedStudent.section}</p>
                                    <p><b>Overall Avg:</b> ${overallAvg?.let { "${"%.1f".format(it)}%" } ?: "—"} | <b>DPT Avg:</b> ${dptAvg?.let { "${"%.1f".format(it)}%" } ?: "—"} | <b>Major Exam Avg:</b> ${examAvg?.let { "${"%.1f".format(it)}%" } ?: "—"}</p>
                                    <table style="width:100%;border-collapse:collapse;margin-top:12px;">
                                      <tr style="background:#0f172a;color:#f59e0b;"><th style="padding:8px;">Type</th><th style="padding:8px;">Assessment</th><th style="padding:8px;">Score</th></tr>
                                      $rows
                                    </table></body></html>
                                """.trimIndent()
                                printHtmlDocument(context, "DPS_Progress_${selectedStudent.userId}", html)
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldAmber, contentColor = NavyDeep),
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Print Report", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (allowStudentPicker && allStudents.isNotEmpty()) {
                        Box {
                            OutlinedButton(
                                onClick = { studentMenuOpen = true },
                                border = BorderStroke(1.dp, GoldAmber),
                                modifier = Modifier.fillMaxWidth().testTag("dpt_dashboard_student_picker"),
                            ) {
                                Icon(Icons.Filled.PersonSearch, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Switch Student: ${selectedStudent.fullName} (${selectedStudent.className}-${selectedStudent.section})",
                                    color = GoldAmber,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GoldAmber)
                            }
                            DropdownMenu(expanded = studentMenuOpen, onDismissRequest = { studentMenuOpen = false }) {
                                allStudents.forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text("${st.fullName} (${st.userId} • ${st.className}-${st.section})") },
                                        onClick = {
                                            selectedStudent = st
                                            studentMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // KPI Summary Cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Daily/Wk DPT Avg",
                    value = dptAvg?.let { "${"%.1f".format(it)}%" } ?: "—",
                    subtitle = "${dptPoints.size} DPT cycle(s)",
                    icon = Icons.Filled.Quiz,
                    accentColor = GoldAmberDark,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Major Exam Avg",
                    value = examAvg?.let { "${"%.1f".format(it)}%" } ?: "—",
                    subtitle = "${examPoints.size} major exam(s)",
                    icon = Icons.Filled.WorkspacePremium,
                    accentColor = IndigoAccent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Overall Progress",
                    value = overallAvg?.let { "${"%.1f".format(it)}%" } ?: "—",
                    subtitle = selectedSubject,
                    icon = Icons.Filled.TrendingUp,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Class Rank",
                    value = rankLabel,
                    subtitle = latestWeek.substringBefore(" ("),
                    icon = Icons.Filled.Leaderboard,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Assessment Type & Subject Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    AssessmentFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = assessmentFilter == filter,
                            onClick = { assessmentFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("perf_filter_${filter.name}"),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    availableSubjects.forEach { sub ->
                        FilterChip(
                            selected = selectedSubject == sub,
                            onClick = { selectedSubject = sub },
                            label = { Text(sub) },
                        )
                    }
                }
            }
        }

        // Interactive Progress Line Chart (Daily/Weekly DPT vs Major Exams)
        item {
            val chartDpt = if (assessmentFilter == AssessmentFilter.EXAM_ONLY) emptyList() else dptPoints
            val chartExam = if (assessmentFilter == AssessmentFilter.DPT_ONLY) emptyList() else examPoints
            ProgressTrendLineChartCard(
                studentName = selectedStudent.fullName,
                dptPoints = chartDpt,
                examPoints = chartExam,
            )
        }

        // Individual Assessment Results (Distinguishing Major Exams vs Daily/Weekly DPT)
        item {
            Text(
                text = "INDIVIDUAL SCORES (DPT TESTS VS MAJOR EXAMS)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (visibleItems.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Text("No scores recorded yet for the selected filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(visibleItems, key = { it.key }) { item ->
                val accent = if (item.isMajorExam) IndigoAccent else GoldAmberDark
                val badgeBg = if (item.isMajorExam) Color(0xFFE0E7FF) else GoldSoft
                val badgeText = if (item.isMajorExam) "MAJOR EXAM" else "WEEKLY DPT"

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.isMajorExam) NavyDeep else GoldAmberDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                    Text(item.periodLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Surface(
                                color = accent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
                                    Text("${"%.1f".format(item.percentage)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                                    Text("${item.scored}/${item.maxMarks}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        item.subjectBreakdown.forEach { (sub, sc, mx) ->
                            val ratio = if (mx > 0L) (sc.toFloat() / mx.toFloat()).coerceIn(0f, 1f) else 0f
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    Text("$sc / $mx (${(ratio * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    color = accent,
                                    trackColor = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                        if (item.remarks.isNotBlank()) {
                            Text("Remarks: ${item.remarks}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                        }
                    }
                }
            }
        }

        // Daily DPT & Scheduled Exam Status Logs (including attached Question Papers / Notes)
        if (classDailyLogs.isNotEmpty() && assessmentFilter != AssessmentFilter.EXAM_ONLY) {
            item {
                Text(
                    text = "RECENT DAILY DPT & EXAM LOGS (${selectedStudent.className}-${selectedStudent.section})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(classDailyLogs, key = { "dash_daily_${it.id}" }) { log ->
                val isExam = log.assessmentType == "EXAM"
                if (assessmentFilter == AssessmentFilter.ALL || (!isExam && assessmentFilter == AssessmentFilter.DPT_ONLY)) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isExam) IndigoAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = if (isExam) Color(0xFFE0E7FF) else GoldSoft,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = if (isExam) "EXAM" else "DAILY DPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExam) NavyDeep else GoldAmberDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text("${log.subject} (${log.date})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Surface(
                                    color = if (log.held) EmeraldContainer else GoldSoft,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        if (log.held) "HELD • Max ${log.maxMarks}" else "NOT HELD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.held) EmeraldSuccess else GoldAmberDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            if (log.held) {
                                Text("Topic: ${log.topic}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                if (log.notes.isNotBlank()) {
                                    Text(log.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AttachmentPreviewBlock(
                                    attachmentName = log.attachmentName,
                                    attachmentMimeType = log.attachmentMimeType,
                                    attachmentData = log.attachmentData,
                                    onShowMessage = onShowMessage,
                                )
                            } else {
                                Text("Reason: ${log.reason}", style = MaterialTheme.typography.bodySmall, color = GoldAmberDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTrendLineChartCard(
    studentName: String,
    dptPoints: List<ChartPoint>,
    examPoints: List<ChartPoint>,
) {
    val allPoints = remember(dptPoints, examPoints) {
        (dptPoints + examPoints).sortedWith(compareBy({ it.sortOrder }, { it.label }))
    }
    var selectedPoint by remember(allPoints) { mutableStateOf(allPoints.lastOrNull()) }
    var plottedCoords by remember { mutableStateOf<List<Pair<ChartPoint, Offset>>>(emptyList()) }

    val dptLineColor = GoldAmber
    val examLineColor = IndigoAccent
    val gridColor = MaterialTheme.colorScheme.outline
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("progress_line_chart_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Progress Over Time (%)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Tap any point on the chart to inspect $studentName's score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Legend distinguishing Daily/Weekly DPTs vs Major Exams
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(12.dp).background(dptLineColor, CircleShape))
                    Text("Daily / Weekly DPT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(12.dp).background(examLineColor, RoundedCornerShape(3.dp)))
                    Text("Major Exams (Dashed)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (allPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No score points to plot yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val argbText = android.graphics.Color.argb(
                    (labelTextColor.alpha * 255).toInt(),
                    (labelTextColor.red * 255).toInt(),
                    (labelTextColor.green * 255).toInt(),
                    (labelTextColor.blue * 255).toInt(),
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .pointerInput(plottedCoords) {
                            detectTapGestures { tap ->
                                val nearest = plottedCoords.minByOrNull { (_, offset) ->
                                    abs(offset.x - tap.x) + abs(offset.y - tap.y)
                                }
                                if (nearest != null) {
                                    selectedPoint = nearest.first
                                }
                            }
                        }
                        .testTag("dpt_line_chart_canvas"),
                ) {
                    val leftPad = 42.dp.toPx()
                    val rightPad = 16.dp.toPx()
                    val topPad = 16.dp.toPx()
                    val bottomPad = 32.dp.toPx()

                    val chartWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    val textPaint = android.graphics.Paint().apply {
                        color = argbText
                        textSize = 10.dp.toPx()
                        isAntiAlias = true
                    }

                    // Horizontal Y grid lines (0%, 25%, 50%, 75%, 100%)
                    listOf(0, 25, 50, 75, 100).forEach { tick ->
                        val y = topPad + chartHeight * (1f - tick / 100f)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "$tick%",
                            4.dp.toPx(),
                            y + 4.dp.toPx(),
                            textPaint,
                        )
                    }

                    val totalCount = allPoints.size
                    fun xForIndex(idx: Int): Float = if (totalCount <= 1) {
                        leftPad + chartWidth / 2f
                    } else {
                        leftPad + (idx.toFloat() / (totalCount - 1).toFloat()) * chartWidth
                    }

                    fun yForPct(pct: Float): Float {
                        val clamped = pct.coerceIn(0f, 100f)
                        return topPad + chartHeight * (1f - clamped / 100f)
                    }

                    val newCoords = mutableListOf<Pair<ChartPoint, Offset>>()
                    val dptSeriesCoords = mutableListOf<Offset>()
                    val examSeriesCoords = mutableListOf<Offset>()

                    allPoints.forEachIndexed { idx, pt ->
                        val coord = Offset(xForIndex(idx), yForPct(pt.percentage))
                        newCoords.add(pt to coord)
                        if (pt.isMajorExam) examSeriesCoords.add(coord) else dptSeriesCoords.add(coord)

                        // X-axis label
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        drawContext.canvas.nativeCanvas.drawText(
                            pt.shortLabel,
                            coord.x,
                            size.height - 8.dp.toPx(),
                            textPaint,
                        )
                    }
                    plottedCoords = newCoords

                    // Draw Series 1: Daily / Weekly DPT (Solid Gold Line + soft fill)
                    if (dptSeriesCoords.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(dptSeriesCoords.first().x, dptSeriesCoords.first().y)
                            dptSeriesCoords.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        val areaPath = Path().apply {
                            addPath(linePath)
                            lineTo(dptSeriesCoords.last().x, topPad + chartHeight)
                            lineTo(dptSeriesCoords.first().x, topPad + chartHeight)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                listOf(dptLineColor.copy(alpha = 0.22f), Color.Transparent),
                                startY = topPad,
                                endY = topPad + chartHeight,
                            ),
                        )
                        drawPath(
                            path = linePath,
                            color = dptLineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }

                    // Draw Series 2: Major Exams (Dashed Indigo Line)
                    if (examSeriesCoords.size >= 2) {
                        val examPath = Path().apply {
                            moveTo(examSeriesCoords.first().x, examSeriesCoords.first().y)
                            examSeriesCoords.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = examPath,
                            color = examLineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
                            ),
                        )
                    }

                    // Highlight crosshair for selected point
                    val active = selectedPoint
                    val activeCoord = newCoords.firstOrNull { it.first.id == active?.id }?.second
                    if (activeCoord != null) {
                        drawLine(
                            color = NavyDeep.copy(alpha = 0.35f),
                            start = Offset(activeCoord.x, topPad),
                            end = Offset(activeCoord.x, topPad + chartHeight),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                        )
                    }

                    // Draw data markers (Circles for DPT, Diamonds/Squares for Major Exams)
                    newCoords.forEach { (pt, coord) ->
                        val isSelected = pt.id == active?.id
                        val markerColor = if (pt.isMajorExam) examLineColor else dptLineColor
                        val radius = if (isSelected) 7.5.dp.toPx() else 5.dp.toPx()

                        if (isSelected) {
                            drawCircle(
                                color = markerColor.copy(alpha = 0.25f),
                                radius = 12.dp.toPx(),
                                center = coord,
                            )
                        }
                        if (pt.isMajorExam) {
                            val d = Path().apply {
                                moveTo(coord.x, coord.y - radius)
                                lineTo(coord.x + radius, coord.y)
                                lineTo(coord.x, coord.y + radius)
                                lineTo(coord.x - radius, coord.y)
                                close()
                            }
                            drawPath(d, color = markerColor)
                            drawPath(d, color = Color.White, style = Stroke(width = 1.8.dp.toPx()))
                        } else {
                            drawCircle(color = markerColor, radius = radius, center = coord)
                            drawCircle(color = Color.White, radius = radius * 0.45f, center = coord)
                        }
                    }
                }

                // Selected Point Inspection Pill
                selectedPoint?.let { pt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, (if (pt.isMajorExam) examLineColor else dptLineColor).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (pt.isMajorExam) "MAJOR EXAM • ${pt.title}" else "DAILY / WEEKLY DPT • ${pt.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pt.isMajorExam) examLineColor else GoldAmberDark,
                                )
                                Text(
                                    "Score: ${pt.scored} / ${pt.maxMarks}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                "${"%.1f".format(pt.percentage)}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (pt.isMajorExam) examLineColor else GoldAmberDark,
                            )
                        }
                    }
                }
            }
        }
    }
}
