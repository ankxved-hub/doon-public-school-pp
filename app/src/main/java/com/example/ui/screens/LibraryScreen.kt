package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LeaveRequest
import com.example.data.model.LibraryIssue
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolUser
import com.example.ui.components.SchoolDatePickerField
import com.example.ui.components.StaffLeaveTabScreen
import com.example.ui.components.StatMetricCard
import com.example.ui.theme.*

/**
 * Dedicated Librarian Portal with 2 tabs:
 *  - Tab 0: Library Desk (Issue book to anyone, view Not Returned / Overdue, mark returned)
 *  - Tab 1: My Leave (Submit staff leave directly to Principal / Admin)
 */
@Composable
fun LibrarianScreen(
    activeTab: Int,
    librarian: SchoolUser,
    users: List<SchoolUser>,
    libraryIssues: List<LibraryIssue>,
    leaves: List<LeaveRequest>,
    onIssueBook: (LibraryIssue) -> Unit,
    onReturnBook: (LibraryIssue) -> Unit,
    onDeleteLibraryIssue: (LibraryIssue) -> Unit,
    onSubmitLeave: (LeaveRequest) -> Unit,
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (activeTab) {
        0 -> LibraryManagementContent(
            actor = librarian,
            allUsers = users,
            libraryIssues = libraryIssues,
            onIssueBook = onIssueBook,
            onReturnBook = onReturnBook,
            onDeleteLibraryIssue = onDeleteLibraryIssue,
            onOpenProfileEdit = onOpenProfileEdit,
            modifier = modifier,
        )
        else -> StaffLeaveTabScreen(
            staffUser = librarian,
            leaves = leaves,
            onSubmitLeave = onSubmitLeave,
            modifier = modifier,
        )
    }
}

private enum class LibraryFilter(val label: String) {
    NOT_RETURNED("Not Returned"),
    OVERDUE("Overdue"),
    RETURNED("Returned"),
    ALL("All Records"),
}

/**
 * Reusable Library Management Desk accessible by:
 *  - Librarian (full portal)
 *  - Teacher (special Library access inside Teacher portal)
 *  - Principal / Admin (oversight & issue/return)
 */
@Composable
fun LibraryManagementContent(
    actor: SchoolUser,
    allUsers: List<SchoolUser>,
    libraryIssues: List<LibraryIssue>,
    onIssueBook: (LibraryIssue) -> Unit,
    onReturnBook: (LibraryIssue) -> Unit,
    onDeleteLibraryIssue: ((LibraryIssue) -> Unit)? = null,
    onOpenProfileEdit: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val today = remember { SchoolCatalog.todayIso() }
    var filter by remember { mutableStateOf(LibraryFilter.NOT_RETURNED) }
    var searchQuery by remember { mutableStateOf("") }

    // Issue Book Form state
    var showIssueForm by remember { mutableStateOf(false) }
    var borrowerSearch by remember { mutableStateOf("") }
    var selectedBorrower by remember { mutableStateOf<SchoolUser?>(null) }
    var manualBorrowerId by remember { mutableStateOf("") }
    var manualBorrowerName by remember { mutableStateOf("") }
    var manualBorrowerRole by remember { mutableStateOf("STUDENT") }
    var bookTitle by remember { mutableStateOf("") }
    var bookCode by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf(today) }
    var dueDate by remember { mutableStateOf(SchoolCatalog.isoDaysFromToday(14)) }
    var notes by remember { mutableStateOf("") }

    val activeNotReturned = remember(libraryIssues) { libraryIssues.filter { it.status == "ISSUED" } }
    val overdueList = remember(activeNotReturned, today) {
        activeNotReturned.filter { it.dueDate.isNotBlank() && it.dueDate < today }
    }
    val returnedList = remember(libraryIssues) { libraryIssues.filter { it.status == "RETURNED" } }

    val matchingBorrowers = remember(allUsers, borrowerSearch) {
        val q = borrowerSearch.trim().lowercase()
        val sorted = allUsers.sortedWith(compareBy<SchoolUser> { it.role != "STUDENT" }.thenBy { it.fullName })
        if (q.isBlank()) sorted.take(8)
        else sorted.filter {
            it.fullName.lowercase().contains(q) ||
                it.userId.lowercase().contains(q) ||
                it.className.lowercase().contains(q) ||
                it.role.lowercase().contains(q)
        }.take(10)
    }

    val filteredIssues = remember(libraryIssues, filter, searchQuery, today) {
        val base = when (filter) {
            LibraryFilter.NOT_RETURNED -> libraryIssues.filter { it.status == "ISSUED" }
            LibraryFilter.OVERDUE -> libraryIssues.filter { it.status == "ISSUED" && it.dueDate.isNotBlank() && it.dueDate < today }
            LibraryFilter.RETURNED -> libraryIssues.filter { it.status == "RETURNED" }
            LibraryFilter.ALL -> libraryIssues
        }
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) base
        else base.filter {
            it.bookTitle.lowercase().contains(q) ||
                it.bookCode.lowercase().contains(q) ||
                it.borrowerName.lowercase().contains(q) ||
                it.borrowerUserId.lowercase().contains(q) ||
                it.borrowerClassInfo.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("library_management_screen"),
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAmber)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SCHOOL LIBRARY MANAGEMENT", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                        Text(
                            "Book Issue & Return Register",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "${actor.fullName} (${actor.role.replace('_', ' ')}) • Issue to any student or staff",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }
                    if (onOpenProfileEdit != null) {
                        IconButton(onClick = onOpenProfileEdit, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = GoldAmber)
                        }
                    }
                }
            }
        }

        // Live KPI Counters
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Not Returned",
                    value = "${activeNotReturned.size}",
                    subtitle = "Books currently out",
                    icon = Icons.Filled.MenuBook,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Overdue",
                    value = "${overdueList.size}",
                    subtitle = "Past due date",
                    icon = Icons.Filled.WarningAmber,
                    accentColor = RoseError,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Returned",
                    value = "${returnedList.size}",
                    subtitle = "Completed",
                    icon = Icons.Filled.CheckCircle,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Issue Book Toggle & Composer Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, GoldAmber.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ISSUE A LIBRARY BOOK", style = MaterialTheme.typography.labelSmall, color = GoldAmberDark, fontWeight = FontWeight.Bold)
                            Text("Issue to any Student, Teacher, or Staff", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        FilledTonalButton(
                            onClick = { showIssueForm = !showIssueForm },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            modifier = Modifier.testTag("library_toggle_issue_form_btn"),
                        ) {
                            Icon(if (showIssueForm) Icons.Filled.ExpandLess else Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showIssueForm) "Hide" else "Issue Book")
                        }
                    }

                    if (showIssueForm) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Step 1: Select Borrower from Directory OR enter manually
                        Text("1. Select Borrower (Student / Teacher / Staff)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                        if (allUsers.isNotEmpty()) {
                            OutlinedTextField(
                                value = borrowerSearch,
                                onValueChange = { borrowerSearch = it },
                                label = { Text("Search Borrower by Name, Roll/ID, or Class") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("library_borrower_search_input"),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                matchingBorrowers.forEach { u ->
                                    val isChosen = selectedBorrower?.id == u.id
                                    FilterChip(
                                        selected = isChosen,
                                        onClick = {
                                            selectedBorrower = if (isChosen) null else u
                                            if (!isChosen) {
                                                manualBorrowerId = u.userId
                                                manualBorrowerName = u.fullName
                                                manualBorrowerRole = u.role
                                            }
                                        },
                                        label = {
                                            Text(
                                                "${u.fullName} (${u.userId}${if (u.className.isNotBlank()) " • ${u.className}-${u.section}" else " • ${u.role.replace('_', ' ')}"})",
                                                maxLines = 1,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = manualBorrowerId,
                                onValueChange = {
                                    manualBorrowerId = it
                                    if (selectedBorrower != null && !selectedBorrower!!.userId.equals(it.trim(), ignoreCase = true)) {
                                        selectedBorrower = null
                                    }
                                },
                                label = { Text("Borrower Roll / ID") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("library_borrower_id_input"),
                            )
                            OutlinedTextField(
                                value = manualBorrowerName,
                                onValueChange = {
                                    manualBorrowerName = it
                                    if (selectedBorrower != null && selectedBorrower!!.fullName != it) {
                                        selectedBorrower = null
                                    }
                                },
                                label = { Text("Borrower Name *") },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f).testTag("library_borrower_name_input"),
                            )
                        }

                        if (selectedBorrower == null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                listOf("STUDENT" to "Student", "TEACHER" to "Teacher", "FEE_MANAGER" to "Fee Mgr", "LIBRARIAN" to "Librarian", "ADMIN" to "Admin").forEach { (rKey, rLbl) ->
                                    FilterChip(
                                        selected = manualBorrowerRole == rKey,
                                        onClick = { manualBorrowerRole = rKey },
                                        label = { Text(rLbl) },
                                    )
                                }
                            }
                        }

                        // Step 2: Book Details
                        Text("2. Book Details & Return Due Date", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = bookTitle,
                            onValueChange = { bookTitle = it.take(150) },
                            label = { Text("Book Title *") },
                            placeholder = { Text("e.g. NCERT Physics Part 1, Wings of Fire") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("library_book_title_input"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = bookCode,
                                onValueChange = { bookCode = it.take(60) },
                                label = { Text("Book Code / ISBN") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("library_book_code_input"),
                            )
                            OutlinedTextField(
                                value = author,
                                onValueChange = { author = it.take(90) },
                                label = { Text("Author (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            SchoolDatePickerField(
                                label = "Issue Date",
                                selectedDate = issueDate,
                                onDateSelected = { issueDate = it },
                                modifier = Modifier.weight(1f),
                            )
                            SchoolDatePickerField(
                                label = "Return Due Date",
                                selectedDate = dueDate,
                                onDateSelected = { dueDate = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(280) },
                            label = { Text("Notes / Shelf / Condition (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            onClick = {
                                val chosen = selectedBorrower
                                val resolvedName = chosen?.fullName ?: manualBorrowerName.trim()
                                val resolvedUserId = chosen?.userId ?: manualBorrowerId.trim()
                                val resolvedRole = chosen?.role ?: manualBorrowerRole
                                val resolvedClassInfo = when {
                                    chosen != null && chosen.className.isNotBlank() -> "${chosen.className}-${chosen.section}"
                                    chosen != null -> chosen.role.replace('_', ' ')
                                    else -> resolvedRole.replace('_', ' ')
                                }
                                if (bookTitle.isNotBlank() && (resolvedName.isNotBlank() || resolvedUserId.isNotBlank())) {
                                    onIssueBook(
                                        LibraryIssue(
                                            bookTitle = bookTitle.trim(),
                                            bookCode = bookCode.trim(),
                                            author = author.trim(),
                                            borrowerDocId = chosen?.id.orEmpty(),
                                            borrowerUserId = resolvedUserId,
                                            borrowerName = resolvedName.ifBlank { resolvedUserId },
                                            borrowerRole = resolvedRole,
                                            borrowerClassInfo = resolvedClassInfo,
                                            issueDate = issueDate,
                                            dueDate = dueDate,
                                            issuedBy = actor.fullName,
                                            notes = notes.trim(),
                                        )
                                    )
                                    bookTitle = ""
                                    bookCode = ""
                                    author = ""
                                    notes = ""
                                    showIssueForm = false
                                }
                            },
                            enabled = bookTitle.isNotBlank() && (manualBorrowerName.isNotBlank() || manualBorrowerId.isNotBlank() || selectedBorrower != null),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("library_issue_submit_btn"),
                        ) {
                            Icon(Icons.Filled.LibraryAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm & Issue Book", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Filter & Search Bar for Checking Who Has Issued & Not Returned
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    LibraryFilter.entries.forEach { tab ->
                        val count = when (tab) {
                            LibraryFilter.NOT_RETURNED -> activeNotReturned.size
                            LibraryFilter.OVERDUE -> overdueList.size
                            LibraryFilter.RETURNED -> returnedList.size
                            LibraryFilter.ALL -> libraryIssues.size
                        }
                        FilterChip(
                            selected = filter == tab,
                            onClick = { filter = tab },
                            label = { Text("${tab.label} ($count)") },
                            modifier = Modifier.testTag("library_filter_${tab.name.lowercase()}"),
                        )
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Borrower, Roll/ID, Book Title, or Code") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("library_search_input"),
                )
            }
        }

        if (filteredIssues.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.LocalLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text(
                            when (filter) {
                                LibraryFilter.NOT_RETURNED -> "All issued books have been returned!"
                                LibraryFilter.OVERDUE -> "No overdue books right now."
                                LibraryFilter.RETURNED -> "No returned book history yet."
                                LibraryFilter.ALL -> "No books issued yet. Tap 'Issue Book' above to record the first issue."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(filteredIssues, key = { it.id }) { item ->
                val isOverdue = item.status == "ISSUED" && item.dueDate.isNotBlank() && item.dueDate < today
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = if (isOverdue) 1.5.dp else 1.dp,
                        color = if (isOverdue) RoseError else MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("library_issue_card_${item.id}"),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.bookTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subLine = buildString {
                                    if (item.bookCode.isNotBlank()) append("Code: ${item.bookCode}")
                                    if (item.author.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append("By ${item.author}")
                                    }
                                }
                                if (subLine.isNotBlank()) {
                                    Text(subLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(
                                color = when {
                                    item.status == "RETURNED" -> EmeraldContainer
                                    isOverdue -> RoseContainer
                                    else -> GoldSoft
                                },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = when {
                                        item.status == "RETURNED" -> "RETURNED"
                                        isOverdue -> "OVERDUE"
                                        else -> "NOT RETURNED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverdue) RoseError else NavyDeep,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Borrower: ${item.borrowerName} (${item.borrowerRole.replace('_', ' ')})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    buildString {
                                        if (item.borrowerUserId.isNotBlank()) append("ID: ${item.borrowerUserId}")
                                        if (item.borrowerClassInfo.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(item.borrowerClassInfo)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (item.status == "RETURNED") {
                                        "Issued: ${item.issueDate} • Returned: ${item.returnedDate.ifBlank { today }} • By ${item.issuedBy}"
                                    } else {
                                        "Issued: ${item.issueDate} • Due: ${item.dueDate} • By ${item.issuedBy}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) RoseError else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (item.notes.isNotBlank()) {
                            Text("Note: ${item.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (item.status == "ISSUED") {
                                Button(
                                    onClick = { onReturnBook(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("library_return_btn_${item.id}"),
                                ) {
                                    Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mark Returned", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (onDeleteLibraryIssue != null && (actor.role == "LIBRARIAN" || actor.role == "ADMIN")) {
                                OutlinedButton(
                                    onClick = { onDeleteLibraryIssue(item) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 44.dp),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete record", tint = RoseError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
