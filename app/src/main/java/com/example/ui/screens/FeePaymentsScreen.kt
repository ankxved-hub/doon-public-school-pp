package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import com.example.data.model.FeeItem
import com.example.data.model.FeePayment
import com.example.data.model.SchoolUser
import com.example.ui.components.OfficialFeeReceiptDialog
import com.example.ui.components.StatMetricCard
import com.example.ui.components.formatInr
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

private enum class PaymentStatusFilter(val label: String) {
    ALL("All"),
    PAID("Paid"),
    PENDING("Pending"),
    REJECTED("Rejected"),
}

private fun FeePayment.displayStatusLabel(): String = when (status) {
    "VERIFIED" -> "Paid"
    "PENDING" -> "Pending"
    else -> "Rejected"
}

private fun FeePayment.displayDateLabel(): String {
    val createdDate = createdAt?.toDate()?.let {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(it)
    }
    return when {
        status == "VERIFIED" && verifiedAtStr.isNotBlank() -> verifiedAtStr
        !createdDate.isNullOrBlank() -> createdDate
        verifiedAtStr.isNotBlank() -> verifiedAtStr
        else -> "Submitted Today"
    }
}

/**
 * Clean, searchable Fee Payments screen displaying historical transaction records
 * with Paid / Pending / Rejected status indicators, transaction dates, payment modes,
 * UTR references, and official downloadable/printable receipts.
 */
@Composable
fun FeePaymentsScreen(
    payments: List<FeePayment>,
    feeItems: List<FeeItem> = emptyList(),
    students: List<SchoolUser> = emptyList(),
    isStudentView: Boolean = false,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    var filter by remember { mutableStateOf(PaymentStatusFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var receiptPayment by remember { mutableStateOf<FeePayment?>(null) }

    val sortedPayments = remember(payments) {
        payments.sortedWith(
            compareByDescending<FeePayment> { it.createdAt?.seconds ?: 0L }
                .thenByDescending { it.verifiedAtStr }
                .thenBy { if (it.status == "PENDING") 0 else if (it.status == "VERIFIED") 1 else 2 }
        )
    }

    val filteredPayments = remember(sortedPayments, filter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        sortedPayments.filter { p ->
            val statusOk = when (filter) {
                PaymentStatusFilter.ALL -> true
                PaymentStatusFilter.PAID -> p.status == "VERIFIED"
                PaymentStatusFilter.PENDING -> p.status == "PENDING"
                PaymentStatusFilter.REJECTED -> p.status == "REJECTED"
            }
            val queryOk = q.isEmpty() ||
                p.feeSummary.lowercase().contains(q) ||
                p.studentName.lowercase().contains(q) ||
                p.admissionNo.lowercase().contains(q) ||
                p.utrOrRef.lowercase().contains(q) ||
                p.receiptNo.lowercase().contains(q) ||
                p.displayDateLabel().lowercase().contains(q)
            statusOk && queryOk
        }
    }

    val paidList = remember(payments) { payments.filter { it.status == "VERIFIED" } }
    val pendingList = remember(payments) { payments.filter { it.status == "PENDING" } }
    val unpaidDueTotal = remember(feeItems) { feeItems.filter { it.status == "UNPAID" }.sumOf { it.amount } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("fee_payments_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Fee Payments", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Historical transaction records & receipts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
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
                            Text("FEE PAYMENTS LEDGER", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                            Text("Transaction History", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                "${payments.size} recorded transaction(s)" +
                                    if (unpaidDueTotal > 0L) " • Upcoming Dues: ${formatInr(unpaidDueTotal)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        Surface(color = GoldAmber.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp)) {
                            Icon(
                                Icons.Filled.ReceiptLong,
                                contentDescription = "Fee Payments",
                                tint = GoldAmber,
                                modifier = Modifier.padding(10.dp).size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        // KPI Summary Row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Total Paid",
                    value = formatInr(paidList.sumOf { it.amount }),
                    subtitle = "${paidList.size} Paid record(s)",
                    icon = Icons.Filled.CheckCircle,
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    title = "Pending",
                    value = formatInr(pendingList.sumOf { it.amount }),
                    subtitle = "${pendingList.size} Pending record(s)",
                    icon = Icons.Filled.HourglassTop,
                    accentColor = GoldAmberDark,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by fee head, receipt, UTR, date" + if (!isStudentView) ", or student" else "") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("fee_payments_search_input"),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    PaymentStatusFilter.entries.forEach { option ->
                        val count = when (option) {
                            PaymentStatusFilter.ALL -> sortedPayments.size
                            PaymentStatusFilter.PAID -> paidList.size
                            PaymentStatusFilter.PENDING -> pendingList.size
                            PaymentStatusFilter.REJECTED -> sortedPayments.count { it.status == "REJECTED" }
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text("${option.label} ($count)") },
                            modifier = Modifier.testTag("fee_payments_filter_${option.name}"),
                        )
                    }
                }
            }
        }

        // Clean Historical Transaction List
        if (filteredPayments.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            "No transaction records found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Try switching the Paid/Pending filter or submit a fee payment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(filteredPayments, key = { it.id }) { pay ->
                val statusLabel = pay.displayStatusLabel()
                val dateLabel = pay.displayDateLabel()
                val (statusBg, statusColor, statusIcon) = when (pay.status) {
                    "VERIFIED" -> Triple(EmeraldContainer, EmeraldSuccess, Icons.Filled.CheckCircle)
                    "PENDING" -> Triple(GoldSoft, GoldAmberDark, Icons.Filled.Schedule)
                    else -> Triple(RoseContainer, RoseError, Icons.Filled.Cancel)
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fee_payment_row_${pay.id}"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (!isStudentView) {
                                    Text(
                                        "${pay.studentName} • ${pay.className} (${pay.admissionNo})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    pay.feeSummary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    formatInr(pay.amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(statusIcon, contentDescription = statusLabel, tint = statusColor, modifier = Modifier.size(13.dp))
                                        Text(
                                            statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                    Text(
                                        "Date: $dateLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "Mode: ${pay.mode} (${pay.upiApp}) • UTR: ${pay.utrOrRef}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (pay.receiptNo.isNotBlank()) {
                                    Text(
                                        "Receipt: ${pay.receiptNo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldSuccess,
                                    )
                                }
                                if (pay.status == "REJECTED" && pay.rejectReason.isNotBlank()) {
                                    Text(
                                        "Reason: ${pay.rejectReason}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoseError,
                                    )
                                }
                            }

                            if (pay.status == "VERIFIED") {
                                FilledTonalButton(
                                    onClick = { receiptPayment = pay },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("fee_payment_receipt_btn_${pay.id}"),
                                ) {
                                    Icon(Icons.Filled.ReceiptLong, contentDescription = "Open Receipt", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Receipt", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
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
