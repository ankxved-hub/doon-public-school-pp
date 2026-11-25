package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.LeaveRequest
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolUser
import com.example.ui.theme.*

/**
 * Reusable Staff Leave Application Card + History for Teachers, Fee Managers, and Librarians.
 * All staff leave applications are routed directly to Principal / Admin (targetRole = "ADMIN").
 */
@Composable
fun StaffLeaveApplyCard(
    staffUser: SchoolUser,
    myLeaves: List<LeaveRequest>,
    onSubmitLeave: (LeaveRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fromDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var toDate by remember { mutableStateOf(SchoolCatalog.todayIso()) }
    var reason by remember { mutableStateOf("") }
    val roleLabel = staffUser.role.replace('_', ' ')

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.2.dp, NavyDeep.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("staff_leave_apply_card"),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "STAFF LEAVE APPLICATION (TO PRINCIPAL / ADMIN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Apply for Personal / Medical / Official Leave",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Surface(color = GoldSoft, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDeep,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    "Your leave request is sent directly to the Principal / Admin desk for approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchoolDatePickerField(
                        label = "From Date",
                        selectedDate = fromDate,
                        onDateSelected = { fromDate = it },
                        modifier = Modifier.weight(1f),
                    )
                    SchoolDatePickerField(
                        label = "To Date",
                        selectedDate = toDate,
                        onDateSelected = { toDate = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(450) },
                    label = { Text("Reason for Leave") },
                    placeholder = { Text("e.g. Medical appointment, family function, personal work...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("staff_leave_reason_input"),
                )
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            onSubmitLeave(
                                LeaveRequest(
                                    studentDocId = staffUser.id,
                                    studentName = staffUser.fullName,
                                    admissionNo = staffUser.userId,
                                    className = staffUser.className.ifBlank { roleLabel },
                                    section = staffUser.section,
                                    applicantRole = staffUser.role,
                                    targetRole = "ADMIN",
                                    fromDate = fromDate,
                                    toDate = toDate,
                                    reason = reason.trim(),
                                    status = "Pending",
                                    teacherRemark = "",
                                )
                            )
                            reason = ""
                        }
                    },
                    enabled = reason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("staff_leave_submit_btn"),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit Leave to Admin", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (myLeaves.isNotEmpty()) {
            Text(
                "MY SUBMITTED LEAVE REQUESTS (${myLeaves.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            myLeaves.forEach { lv ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${lv.fromDate} to ${lv.toDate}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
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
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDeep,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Text(lv.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (lv.teacherRemark.isNotBlank()) {
                            Text(
                                "Admin Remark: ${lv.teacherRemark}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Full-Screen Staff Leave Tab used by Fee Manager and Librarian portals.
 */
@Composable
fun StaffLeaveTabScreen(
    staffUser: SchoolUser,
    leaves: List<LeaveRequest>,
    onSubmitLeave: (LeaveRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val myLeaves = remember(leaves, staffUser.id) {
        leaves.filter { it.studentDocId == staffUser.id }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("STAFF LEAVE PORTAL", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                    Text(staffUser.fullName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "${staffUser.role.replace('_', ' ')} • ID: ${staffUser.userId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
        item {
            StaffLeaveApplyCard(
                staffUser = staffUser,
                myLeaves = myLeaves,
                onSubmitLeave = onSubmitLeave,
            )
        }
    }
}

/**
 * Admin / Principal Staff Leave Approvals Card showing leave requests submitted by
 * Teachers, Fee Managers, and Librarians.
 */
@Composable
fun AdminStaffLeaveApprovalsCard(
    leaves: List<LeaveRequest>,
    onUpdateLeaveStatus: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val staffLeaves = remember(leaves) {
        leaves.filter { it.targetRole == "ADMIN" || (it.applicantRole.isNotBlank() && it.applicantRole != "STUDENT") }
    }
    val pendingCount = remember(staffLeaves) { staffLeaves.count { it.status == "Pending" } }
    val remarkInputs = remember { mutableStateMapOf<String, String>() }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, if (pendingCount > 0) GoldAmber else MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().testTag("admin_staff_leave_approvals_card"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "STAFF LEAVE APPLICATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Teachers, Fee Manager & Librarian",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    color = if (pendingCount > 0) GoldSoft else EmeraldContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        if (pendingCount > 0) "$pendingCount Pending" else "All Clear",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDeep,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (staffLeaves.isEmpty()) {
                Text(
                    "No staff leave requests submitted yet. Leave applications from Teachers, Fee Managers, and Librarians will appear here for Principal approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                staffLeaves.take(15).forEach { lv ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        lv.studentName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "${lv.applicantRole.replace('_', ' ')} • ID: ${lv.admissionNo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDeep,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text(
                                "${lv.fromDate} to ${lv.toDate} • ${lv.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (lv.status == "Pending") {
                                OutlinedTextField(
                                    value = remarkInputs[lv.id] ?: "",
                                    onValueChange = { remarkInputs[lv.id] = it },
                                    label = { Text("Principal / Admin Remark") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            onUpdateLeaveStatus(
                                                lv.id,
                                                "Approved",
                                                remarkInputs[lv.id]?.ifBlank { "Approved by Principal" } ?: "Approved by Principal",
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    ) { Text("Approve") }
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateLeaveStatus(
                                                lv.id,
                                                "Rejected",
                                                remarkInputs[lv.id]?.ifBlank { "Unable to approve" } ?: "Unable to approve",
                                            )
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    ) { Text("Reject", color = RoseError) }
                                }
                            } else if (lv.teacherRemark.isNotBlank()) {
                                Text(
                                    "Remark: ${lv.teacherRemark}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
