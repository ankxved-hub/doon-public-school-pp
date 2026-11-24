package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.AttachmentPickerChip
import com.example.ui.components.AttachmentPreviewBlock
import com.example.ui.components.PickedAttachment
import com.example.ui.components.SchoolDatePickerField
import com.example.ui.components.readAttachmentFromUri
import com.example.ui.theme.*

@Composable
fun TeacherNoticesTab(
    teacher: SchoolUser,
    allUsers: List<SchoolUser>,
    selectedClass: String,
    selectedSection: String,
    circulars: List<Circular>,
    onPostNotice: (Circular) -> Unit,
    onDeleteNotice: (Circular) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var targetSpecificPerson by remember { mutableStateOf(false) }
    var recipientSearch by remember { mutableStateOf("") }
    var recipientMenuOpen by remember { mutableStateOf(false) }

    val selectableRecipients = remember(allUsers, teacher.id, recipientSearch) {
        allUsers
            .filter { it.id != teacher.id }
            .filter {
                val q = recipientSearch.trim()
                q.isBlank() ||
                    it.fullName.contains(q, ignoreCase = true) ||
                    it.userId.contains(q, ignoreCase = true) ||
                    it.className.contains(q, ignoreCase = true)
            }
            .sortedWith(compareBy({ it.role }, { it.fullName }))
    }

    var selectedRecipient by remember(allUsers) {
        mutableStateOf(
            allUsers.firstOrNull {
                it.role == "STUDENT" && it.className == selectedClass && it.section == selectedSection
            } ?: allUsers.firstOrNull { it.id != teacher.id }
        )
    }

    // Class / Wing / All Classes dropdown target
    var selectedTargetScope by remember(selectedClass) { mutableStateOf(selectedClass.ifBlank { "All Classes" }) }
    var selectedTargetSection by remember(selectedSection) { mutableStateOf(selectedSection.ifBlank { "ALL" }) }
    var classTargetMenuOpen by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }

    var category by remember { mutableStateOf("Notice") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
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

    var inboxFilter by remember { mutableStateOf("All") }

    val visibleNotices = remember(circulars, teacher.id, teacher.fullName, inboxFilter) {
        circulars.filter { c ->
            val canSee = c.recipientDocId.isBlank() ||
                c.recipientDocId == teacher.id ||
                c.postedBy == teacher.fullName
            if (!canSee) return@filter false
            when (inboxFilter) {
                "Direct to Me" -> c.recipientDocId == teacher.id
                "All Teachers" -> c.audience.equals("All Teachers", ignoreCase = true)
                "From Admin" -> c.senderRole.equals("ADMIN", ignoreCase = true)
                else -> true
            }
        }
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
                    Text(
                        "Send Class Notice / Holiday / Circular",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = !targetSpecificPerson,
                            onClick = {
                                targetSpecificPerson = false
                                validationError = null
                            },
                            label = { Text("Class / School") },
                            leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).testTag("notice_target_broadcast"),
                        )
                        FilterChip(
                            selected = targetSpecificPerson,
                            onClick = {
                                targetSpecificPerson = true
                                validationError = null
                            },
                            label = { Text("Specific Person") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).testTag("notice_target_person"),
                        )
                    }

                    if (targetSpecificPerson) {
                        OutlinedTextField(
                            value = recipientSearch,
                            onValueChange = { recipientSearch = it },
                            label = { Text("Filter Person (Name, Roll/Emp ID, Class)") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box {
                            OutlinedButton(
                                onClick = { recipientMenuOpen = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("notice_recipient_picker"),
                            ) {
                                Icon(Icons.Filled.PersonPin, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    selectedRecipient?.let {
                                        val cls = if (it.className.isNotBlank()) " • ${it.className}-${it.section}" else ""
                                        "${it.fullName} (${it.role}$cls)"
                                    } ?: "Select Recipient",
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = recipientMenuOpen, onDismissRequest = { recipientMenuOpen = false }) {
                                if (selectableRecipients.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No matching person found") },
                                        onClick = { recipientMenuOpen = false },
                                    )
                                } else {
                                    selectableRecipients.forEach { person ->
                                        val desc = if (person.className.isNotBlank()) {
                                            "${person.className}-${person.section}"
                                        } else person.role
                                        DropdownMenuItem(
                                            text = { Text("${person.fullName} (${person.userId} • $desc)") },
                                            onClick = {
                                                selectedRecipient = person
                                                recipientMenuOpen = false
                                                validationError = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Class / Wing / All Classes Dropdown Selector
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { classTargetMenuOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("teacher_notice_class_dropdown"),
                            ) {
                                Icon(Icons.Filled.Class, contentDescription = "Target Class", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Select Target Class", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (selectedTargetScope in SchoolCatalog.allClasses) {
                                            val secText = if (selectedTargetSection == "ALL") "All Sections" else "Section $selectedTargetSection"
                                            "$selectedTargetScope ($secText)"
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
                            DropdownMenu(expanded = classTargetMenuOpen, onDismissRequest = { classTargetMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("All Classes (Entire School)") },
                                    onClick = {
                                        selectedTargetScope = "All Classes"
                                        classTargetMenuOpen = false
                                    },
                                )
                                SchoolCatalog.wings.forEach { wing ->
                                    DropdownMenuItem(
                                        text = { Text("Wing: ${wing.name} (${wing.classesLabel})") },
                                        onClick = {
                                            selectedTargetScope = "Wing: ${wing.name}"
                                            classTargetMenuOpen = false
                                        },
                                    )
                                }
                                HorizontalDivider()
                                SchoolCatalog.allClasses.forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text("$cls (${SchoolCatalog.wingForClass(cls)})") },
                                        onClick = {
                                            selectedTargetScope = cls
                                            classTargetMenuOpen = false
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

                    SchoolDatePickerField(
                        label = "Notice / Holiday Date",
                        selectedDate = selectedDate,
                        onDateSelected = { selectedDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "teacher_notice_date_picker",
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf("Notice", "Holiday", "Assignment", "PTM Invite", "Exam Update", "Appreciation").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(150)
                            if (validationError != null) validationError = null
                        },
                        label = { Text("Notice Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_notice_title"),
                    )

                    OutlinedTextField(
                        value = content,
                        onValueChange = {
                            content = it.take(2000)
                            if (validationError != null) validationError = null
                        },
                        label = { Text("Notice / Holiday Details") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_notice_content"),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("notice_attach_photo_btn"),
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
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("notice_attach_doc_btn"),
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Attach Document", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    AttachmentPickerChip(
                        attachment = pendingAttachment,
                        onClear = { pendingAttachment = null },
                    )

                    if (validationError != null) {
                        Text(validationError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = {
                            if (targetSpecificPerson && selectedRecipient == null) {
                                validationError = "Please select a specific person to send this notice to."
                                return@Button
                            }
                            if (title.isBlank() || content.isBlank()) {
                                validationError = "Please enter both notice title and message."
                                return@Button
                            }
                            validationError = null
                            val rec = if (targetSpecificPerson) selectedRecipient else null
                            val recLabel = rec?.let { "${it.fullName} (${it.userId})" } ?: ""

                            val tClass = if (rec == null && selectedTargetScope in SchoolCatalog.allClasses) selectedTargetScope else ""
                            val tSection = if (tClass.isNotBlank() && selectedTargetSection != "ALL") selectedTargetSection else ""
                            val tWing = when {
                                rec != null -> ""
                                tClass.isNotBlank() -> SchoolCatalog.wingForClass(tClass)
                                selectedTargetScope.startsWith("Wing: ") -> selectedTargetScope.removePrefix("Wing: ").trim()
                                else -> ""
                            }
                            val audLabel = when {
                                rec != null -> "Direct: $recLabel"
                                tClass.isNotBlank() && tSection.isNotBlank() -> "$tClass-$tSection"
                                tClass.isNotBlank() -> "$tClass (All Sections)"
                                tWing.isNotBlank() -> tWing
                                else -> "All Classes"
                            }

                            onPostNotice(
                                Circular(
                                    title = title.trim(),
                                    category = category,
                                    audience = audLabel,
                                    targetWing = tWing,
                                    targetClass = tClass,
                                    targetSection = tSection,
                                    date = selectedDate.ifBlank { SchoolCatalog.todayIso() },
                                    content = content.trim(),
                                    postedBy = teacher.fullName,
                                    recipientDocId = rec?.id ?: "",
                                    recipientName = recLabel,
                                    senderRole = "TEACHER",
                                    attachmentName = pendingAttachment?.name ?: "",
                                    attachmentMimeType = pendingAttachment?.mimeType ?: "",
                                    attachmentData = pendingAttachment?.base64Data ?: "",
                                )
                            )
                            title = ""
                            content = ""
                            pendingAttachment = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("teacher_send_notice_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (targetSpecificPerson) "Send Direct Notice" else "Publish Targeted Notice")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "NOTICES, NEWSPAPER & ADMIN MESSAGES (${visibleNotices.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf("All", "From Admin", "Direct to Me", "All Teachers").forEach { f ->
                        FilterChip(
                            selected = inboxFilter == f,
                            onClick = { inboxFilter = f },
                            label = { Text(f) },
                            modifier = Modifier.testTag("teacher_notice_filter_${f.lowercase().replace(' ', '_')}"),
                        )
                    }
                }
            }
        }

        items(visibleNotices, key = { it.id }) { c ->
            val isDirectToMe = c.recipientDocId == teacher.id
            val isDirect = c.recipientDocId.isNotBlank()
            val isAllTeachers = c.audience.equals("All Teachers", ignoreCase = true)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    when {
                        isDirectToMe -> IndigoAccent
                        isAllTeachers -> EmeraldSuccess.copy(alpha = 0.65f)
                        isDirect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
                                Text(c.category, style = MaterialTheme.typography.labelSmall, color = NavyDeep, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            if (isDirectToMe) {
                                Surface(color = IndigoAccent.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "DIRECT TO YOU",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            } else if (isAllTeachers) {
                                Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "ALL TEACHERS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            } else if (isDirect) {
                                Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "To: ${c.recipientName.ifBlank { "Specific Person" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            } else {
                                Text(c.audience, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (c.postedBy == teacher.fullName) {
                            IconButton(onClick = { onDeleteNotice(c) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete notice", tint = RoseError)
                            }
                        }
                    }
                    Text(c.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(c.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("From: ${c.postedBy} (${c.senderRole}) • ${c.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AttachmentPreviewBlock(
                        attachmentName = c.attachmentName,
                        attachmentMimeType = c.attachmentMimeType,
                        attachmentData = c.attachmentData,
                        onShowMessage = onShowMessage,
                    )
                }
            }
        }
    }
}
