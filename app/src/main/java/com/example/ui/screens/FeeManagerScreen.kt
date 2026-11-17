package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.OfficialFeeReceiptDialog
import com.example.ui.components.StatMetricCard
import com.example.ui.components.formatInr
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FeeManagerScreen(
    activeTab: Int,
    feeManager: SchoolUser,
    users: List<SchoolUser>,
    gateway: GatewayConfig,
    feeItems: List<FeeItem>,
    payments: List<FeePayment>,
    onVerifyPayment: (FeePayment, String, String, Int) -> Unit,
    onRejectPayment: (FeePayment, String, String, String) -> Unit,
    onAssignFeeDemands: (List<SchoolUser>, String, String, Long, Boolean) -> Unit,
    onDeleteFeeItem: (FeeItem) -> Unit,
    onSaveGatewayConfig: (GatewayConfig) -> Unit,
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val students = remember(users) { users.filter { it.role == "STUDENT" }.sortedBy { it.className } }

    when (activeTab) {
        0 -> FeeVerifyQueueTab(
            feeManager = feeManager,
            students = students,
            payments = payments,
            onVerifyPayment = onVerifyPayment,
            onRejectPayment = onRejectPayment,
            onOpenProfileEdit = onOpenProfileEdit,
            modifier = modifier,
        )
        1 -> FeeLedgerBillingTab(
            students = students,
            feeItems = feeItems,
            onAssignFeeDemands = onAssignFeeDemands,
            onDeleteFeeItem = onDeleteFeeItem,
            modifier = modifier,
        )
        2 -> FeeGatewayConfigTab(
            gateway = gateway,
            onSaveGatewayConfig = onSaveGatewayConfig,
            modifier = modifier,
        )
        else -> FeePaymentsScreen(
            payments = payments,
            feeItems = feeItems,
            students = students,
            isStudentView = false,
            modifier = modifier,
        )
    }
}

@Composable
private fun FeeVerifyQueueTab(
    feeManager: SchoolUser,
    students: List<SchoolUser>,
    payments: List<FeePayment>,
    onVerifyPayment: (FeePayment, String, String, Int) -> Unit,
    onRejectPayment: (FeePayment, String, String, String) -> Unit,
    onOpenProfileEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingPayments = remember(payments) { payments.filter { it.status == "PENDING" } }
    val verifiedPayments = remember(payments) { payments.filter { it.status == "VERIFIED" } }
    val rejectReasons = remember { mutableStateMapOf<String, String>() }
    var receiptPayment by remember { mutableStateOf<FeePayment?>(null) }

    val totalVerifiedInr = verifiedPayments.sumOf { it.amount }
    val totalPendingInr = pendingPayments.sumOf { it.amount }

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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ACCOUNTS & FEE VERIFICATION DESK", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                        Text(feeManager.fullName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("ID: ${feeManager.userId} • Ph: ${feeManager.phone}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    FilledTonalButton(
                        onClick = onOpenProfileEdit,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldAmber, contentColor = NavyDeep),
                        modifier = Modifier.testTag("fee_edit_profile_btn"),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Profile")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Pending Queue",
                    value = "${pendingPayments.size}",
                    subtitle = formatInr(totalPendingInr),
                    icon = Icons.Filled.HourglassTop,
                    accentColor = GoldAmberDark,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Verified Receipts",
                    value = "${verifiedPayments.size}",
                    subtitle = formatInr(totalVerifiedInr),
                    icon = Icons.Filled.Verified,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Text("PENDING PAYMENT VERIFICATIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (pendingPayments.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "All submitted UTR / Razorpay proofs have been processed!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(pendingPayments, key = { it.id }) { pay ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, GoldAmber),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(pay.studentName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${pay.admissionNo} • ${pay.className}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatInr(pay.amount), style = MaterialTheme.typography.titleLarge, color = EmeraldSuccess)
                        }
                        Text("Fee Heads: ${pay.feeSummary}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Mode: ${pay.mode} (${pay.upiApp}) • UTR/Ref: ${pay.utrOrRef}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        OutlinedTextField(
                            value = rejectReasons[pay.id] ?: "",
                            onValueChange = { rejectReasons[pay.id] = it },
                            label = { Text("Rejection Remark (only if rejecting)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                    onVerifyPayment(pay, feeManager.fullName, stamp, verifiedPayments.size)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("verify_approve_btn_${pay.id}"),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify & Approve")
                            }
                            OutlinedButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                    onRejectPayment(pay, rejectReasons[pay.id] ?: "", feeManager.fullName, stamp)
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("verify_reject_btn_${pay.id}"),
                            ) {
                                Text("Reject", color = RoseError)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("OFFICIAL RECEIPTS ISSUED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }

        items(verifiedPayments, key = { it.id }) { pay ->
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
                        Text("${pay.receiptNo} • ${pay.studentName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("${formatInr(pay.amount)} • UTR: ${pay.utrOrRef}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Verified: ${pay.verifiedAtStr}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                    }
                    FilledTonalButton(onClick = { receiptPayment = pay }) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = "Open Receipt", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Receipt")
                    }
                }
            }
        }
    }

    receiptPayment?.let { pay ->
        OfficialFeeReceiptDialog(
            payment = pay,
            student = students.firstOrNull { it.id == pay.studentDocId },
            onDismiss = { receiptPayment = null },
        )
    }
}

@Composable
private fun FeeLedgerBillingTab(
    students: List<SchoolUser>,
    feeItems: List<FeeItem>,
    onAssignFeeDemands: (List<SchoolUser>, String, String, Long, Boolean) -> Unit,
    onDeleteFeeItem: (FeeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bulkClassMode by remember { mutableStateOf(false) }
    var selectedStudent by remember(students) { mutableStateOf(students.firstOrNull()) }
    var selectedClass by remember { mutableStateOf("Class X") }
    var studentMenu by remember { mutableStateOf(false) }
    var classMenu by remember { mutableStateOf(false) }
    var feeHead by remember { mutableStateOf("Monthly Tuition Fee") }
    var monthLabel by remember { mutableStateOf("December 2026") }
    var customAmount by remember { mutableStateOf("2800") }

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
                    Text("Assign Fee Demand / Billing", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !bulkClassMode, onClick = { bulkClassMode = false }, label = { Text("Single Student") })
                        FilterChip(selected = bulkClassMode, onClick = { bulkClassMode = true }, label = { Text("Entire Class") })
                    }

                    if (!bulkClassMode) {
                        Box {
                            OutlinedButton(onClick = { studentMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedStudent?.let { "${it.fullName} (${it.userId} • ${it.className}-${it.section})" } ?: "Select Student")
                            }
                            DropdownMenu(expanded = studentMenu, onDismissRequest = { studentMenu = false }) {
                                students.forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text("${st.fullName} • ${st.className}-${st.section}") },
                                        onClick = {
                                            selectedStudent = st
                                            studentMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Box {
                            OutlinedButton(onClick = { classMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Target Class: $selectedClass")
                            }
                            DropdownMenu(expanded = classMenu, onDismissRequest = { classMenu = false }) {
                                SchoolCatalog.allClasses.forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text(cls) },
                                        onClick = {
                                            selectedClass = cls
                                            classMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("Monthly Tuition Fee", "Transport / Bus Fee", "Exam & DPT Fee", "Annual Charges").forEach { head ->
                            FilterChip(selected = feeHead == head, onClick = { feeHead = head }, label = { Text(head) })
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = monthLabel,
                            onValueChange = { monthLabel = it },
                            label = { Text("Month / Term") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customAmount,
                            onValueChange = { customAmount = it },
                            label = { Text("Amount (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Button(
                        onClick = {
                            val targets = if (bulkClassMode) {
                                students.filter { it.className == selectedClass }
                            } else {
                                listOfNotNull(selectedStudent)
                            }
                            val amt = customAmount.toLongOrNull() ?: 0L
                            if (targets.isNotEmpty()) {
                                onAssignFeeDemands(targets, feeHead, monthLabel.trim(), amt, bulkClassMode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("assign_fee_btn"),
                    ) { Text(if (bulkClassMode) "Bill Entire Class ($selectedClass)" else "Add Fee Head to Student") }
                }
            }
        }

        item {
            Text("STUDENT FEE LEDGERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(students, key = { it.id }) { st ->
            val stFees = feeItems.filter { it.studentDocId == st.id }
            val due = stFees.filter { it.status != "PAID" }.sumOf { it.amount }
            val paid = stFees.filter { it.status == "PAID" }.sumOf { it.amount }

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
                            Text("${st.fullName} (${st.userId})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("${st.className}-${st.section} • Parent: ${st.parentName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Due: ${formatInr(due)}", style = MaterialTheme.typography.labelLarge, color = if (due > 0) RoseError else EmeraldSuccess)
                            Text("Paid: ${formatInr(paid)}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                        }
                    }
                    stFees.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${item.title} (${formatInr(item.amount)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    color = when (item.status) {
                                        "PAID" -> EmeraldContainer
                                        "PENDING_VERIFICATION" -> GoldSoft
                                        else -> RoseContainer
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        item.status.replace('_', ' '),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyDeep,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                if (item.status == "UNPAID") {
                                    IconButton(onClick = { onDeleteFeeItem(item) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove fee item", tint = RoseError, modifier = Modifier.size(16.dp))
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

@Composable
private fun FeeGatewayConfigTab(
    gateway: GatewayConfig,
    onSaveGatewayConfig: (GatewayConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var merchantUpi by remember(gateway.id) { mutableStateOf(gateway.merchantUpi) }
    var payeeName by remember(gateway.id) { mutableStateOf(gateway.payeeName) }
    var bankName by remember(gateway.id) { mutableStateOf(gateway.bankName) }
    var accountNo by remember(gateway.id) { mutableStateOf(gateway.accountNo) }
    var ifsc by remember(gateway.id) { mutableStateOf(gateway.ifsc) }
    var razorpayKeyId by remember(gateway.id) { mutableStateOf(gateway.razorpayKeyId) }

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
                    Text("Official Merchant UPI & Bank Config", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Configured UPI VPA & Payee Name immediately power the Student QR Code & UPI Deep-Link Engine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = merchantUpi,
                        onValueChange = { merchantUpi = it },
                        label = { Text("Merchant UPI ID (VPA) e.g. doonpublicetah@sbi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("gateway_upi_input"),
                    )
                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Payee Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name & Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = accountNo,
                            onValueChange = { accountNo = it },
                            label = { Text("Account No") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = ifsc,
                            onValueChange = { ifsc = it },
                            label = { Text("IFSC Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = razorpayKeyId,
                        onValueChange = { razorpayKeyId = it },
                        label = { Text("Optional Razorpay Key ID (rzp_live_... or rzp_test_...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onSaveGatewayConfig(
                                gateway.copy(
                                    merchantUpi = merchantUpi.trim(),
                                    payeeName = payeeName.trim(),
                                    bankName = bankName.trim(),
                                    accountNo = accountNo.trim(),
                                    ifsc = ifsc.trim(),
                                    razorpayKeyId = razorpayKeyId.trim(),
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gateway_save_btn"),
                    ) { Text("Save Payment Gateway Configuration") }
                }
            }
        }
    }
}
