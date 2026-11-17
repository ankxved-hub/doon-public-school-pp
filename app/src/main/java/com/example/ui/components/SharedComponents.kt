package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

fun formatInr(amount: Long): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return "₹${nf.format(amount)}"
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = NavyRoyal,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Reusable Individual Student Weekly DPT Result Card showing subject-by-subject marks,
 * percentage progress bars, class rank, and performance badge.
 */
@Composable
fun StudentDptResultCard(
    student: SchoolUser,
    weekLabel: String,
    studentEntries: List<DptWeekly>,
    rankLabel: String,
    modifier: Modifier = Modifier,
) {
    val totalScored = studentEntries.sumOf { it.scored }
    val totalMax = studentEntries.sumOf { it.maxMarks }
    val pct = if (totalMax > 0L) (totalScored * 100.0 / totalMax) else 0.0
    val (badgeText, badgeColor, badgeBg) = when {
        totalMax == 0L -> Triple("Not Entered", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
        pct >= 85.0 -> Triple("Excellent", EmeraldSuccess, EmeraldContainer)
        pct >= 60.0 -> Triple("Good", NavyRoyal, Color(0xFFDBEAFE))
        else -> Triple("Needs Revision", GoldAmberDark, GoldSoft)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(color = GoldSoft, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            text = rankLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyDeep,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Column {
                        Text(student.fullName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${student.userId} • ${student.className}-${student.section} • $weekLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (studentEntries.isEmpty()) {
                Text(
                    "Weekly DPT marks not entered yet for $weekLabel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                studentEntries.forEach { entry ->
                    val ratio = if (entry.maxMarks > 0L) {
                        (entry.scored.toFloat() / entry.maxMarks.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.subject, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("${entry.scored} / ${entry.maxMarks}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = if (ratio >= 0.85f) EmeraldSuccess else if (ratio >= 0.60f) MaterialTheme.colorScheme.primary else GoldAmberDark,
                            trackColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total Score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$totalScored / $totalMax (${"%.1f".format(pct)}%)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Profile & Settings Dialog for ALL roles (Admin, Teacher, Fee Manager, Student).
 * Includes Dark Mode preference toggle (persisted locally via DataStore) AND
 * Phone / Password editing (synced to the Firestore 'users' collection).
 */
@Composable
fun ProfileEditDialog(
    user: SchoolUser,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSaveProfile: (newFullName: String, newPhone: String, newPassword: String) -> Unit,
) {
    var fullName by remember(user.id, user.fullName) { mutableStateOf(user.fullName) }
    var phone by remember(user.id, user.phone) { mutableStateOf(user.phone) }
    var password by remember(user.id, user.password) { mutableStateOf(user.password) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("Profile & App Settings", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(fullName.ifBlank { user.fullName }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${user.role.replace('_', ' ')} • ID: ${user.userId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (user.className.isNotBlank()) {
                            Text(
                                text = "${user.className}-${user.section} (${user.wing})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Dark Mode Toggle (persisted locally via DataStore Preferences)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = "Theme Mode",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text("Dark Mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    text = if (isDarkMode) "Dark Navy palette active" else "Light classic palette active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onToggleDarkMode,
                            modifier = Modifier.testTag("profile_dark_mode_switch"),
                        )
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text(
                            if (user.role == "ADMIN") "Principal / Admin Name"
                            else "Full Name"
                        )
                    },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_fullname_input"),
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input"),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Login Password") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_password_input"),
                )

                Text(
                    text = "Name, phone & password sync live to Firestore.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveProfile(fullName, phone, password)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                modifier = Modifier.testTag("save_profile_button"),
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun OfficialFeeReceiptDialog(
    payment: FeePayment,
    student: SchoolUser?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Official Fee Receipt", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = NavyDeep,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.dps_real_logo),
                                contentDescription = "Doon Public School Official Logo",
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                            )
                        }
                        Column {
                            Text(SchoolInfo.NAME.uppercase(), style = MaterialTheme.typography.titleMedium, color = GoldAmber)
                            Text("${SchoolInfo.TRUST} • ${SchoolInfo.AFFILIATION}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                            Text(SchoolInfo.ADDRESS, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
                HorizontalDivider()
                ReceiptRow("Receipt No", payment.receiptNo)
                ReceiptRow("Student", payment.studentName)
                ReceiptRow("Admission No", payment.admissionNo)
                ReceiptRow("Class / Wing", "${payment.className} (${student?.wing ?: "DPS Etah"})")
                ReceiptRow("Particulars", payment.feeSummary)
                ReceiptRow("Amount Paid", formatInr(payment.amount))
                ReceiptRow("Mode", "${payment.mode} (${payment.upiApp})")
                ReceiptRow("UTR / Ref No", payment.utrOrRef)
                ReceiptRow("Verified By", payment.verifiedBy)
                ReceiptRow("Verified On", payment.verifiedAtStr)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val html = """
                        <html><body style="font-family:sans-serif;padding:24px;color:#0f172a;">
                        <div style="border-bottom:2px solid #0f172a;padding-bottom:12px;margin-bottom:16px;">
                          <h2 style="margin:0;color:#0f172a;">${SchoolInfo.NAME}</h2>
                          <p style="margin:4px 0;font-size:12px;color:#b45309;"><b>${SchoolInfo.TRUST}</b> | ${SchoolInfo.AFFILIATION}</p>
                          <p style="margin:2px 0;font-size:11px;color:#475569;">${SchoolInfo.ADDRESS}</p>
                          <p style="margin:2px 0;font-size:11px;color:#475569;">Tel: ${SchoolInfo.PHONES} | ${SchoolInfo.EMAIL}</p>
                        </div>
                        <h3 style="background:#0f172a;color:#f59e0b;padding:8px 12px;border-radius:6px;">OFFICIAL FEE RECEIPT — ${payment.receiptNo}</h3>
                        <table style="width:100%;border-collapse:collapse;margin-top:12px;font-size:13px;">
                          <tr><td style="padding:6px;border:1px solid #cbd5e1;"><b>Student Name</b></td><td style="padding:6px;border:1px solid #cbd5e1;">${payment.studentName}</td></tr>
                          <tr><td style="padding:6px;border:1px solid #cbd5e1;"><b>Admission No</b></td><td style="padding:6px;border:1px solid #cbd5e1;">${payment.admissionNo}</td></tr>
                          <tr><td style="padding:6px;border:1px solid #cbd5e1;"><b>Class & Wing</b></td><td style="padding:6px;border:1px solid #cbd5e1;">${payment.className} (${student?.wing ?: "DPS Etah"})</td></tr>
                          <tr><td style="padding:6px;border:1px solid #cbd5e1;"><b>Particulars</b></td><td style="padding:6px;border:1px solid #cbd5e1;">${payment.feeSummary}</td></tr>
                          <tr><td style="padding:6px;border:1px solid #cbd5e1;"><b>Payment Mode & UTR</b></td><td style="padding:6px;border:1px solid #cbd5e1;">${payment.mode} (${payment.upiApp}) • ${payment.utrOrRef}</td></tr>
                          <tr><td style="padding:6px;border:1px solid #cbd5e1;"><b>Total Amount Paid</b></td><td style="padding:6px;border:1px solid #cbd5e1;color:#059669;"><b>${formatInr(payment.amount)} (VERIFIED)</b></td></tr>
                        </table>
                        <p style="margin-top:24px;font-size:12px;">Verified By: <b>${payment.verifiedBy}</b> on ${payment.verifiedAtStr}</p>
                        </body></html>
                    """.trimIndent()
                    printHtmlDocument(context, "DPS_Receipt_${payment.receiptNo}", html)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Print / PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun StudentIdCardDialog(
    student: SchoolUser,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val qrPayload = "DPS-ETAH|${student.userId}|${student.fullName}|${student.className}-${student.section}|${student.wing}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Digital Student ID Card", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, NavyDeep),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyDeep)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.dps_real_logo),
                                    contentDescription = "Doon Public School Official Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Column {
                                Text(SchoolInfo.NAME, style = MaterialTheme.typography.labelLarge, color = GoldAmber)
                                Text(SchoolInfo.AFFILIATION, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(student.fullName, style = MaterialTheme.typography.titleLarge, color = NavyDeep)
                            Surface(color = GoldSoft, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "${student.className}-${student.section} • ${student.wing}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NavyDeep,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            Text("Admission No: ${student.userId}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NavyDeep)
                            Text("Parent: ${student.parentName}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Mobile: ${student.phone}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            QrCodeCanvas(data = qrPayload, size = 128.dp)
                            Text(SchoolInfo.ADDRESS, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val html = """
                        <html><body style="font-family:sans-serif;padding:24px;">
                        <div style="border:2px solid #0f172a;border-radius:14px;max-width:380px;overflow:hidden;">
                          <div style="background:#0f172a;color:#f59e0b;padding:14px;">
                            <h3 style="margin:0;">${SchoolInfo.NAME}</h3>
                            <div style="color:#fff;font-size:11px;">${SchoolInfo.AFFILIATION}</div>
                          </div>
                          <div style="padding:16px;">
                            <h2 style="margin:0 0 6px 0;">${student.fullName}</h2>
                            <p style="margin:4px 0;"><b>Admission No:</b> ${student.userId}</p>
                            <p style="margin:4px 0;"><b>Class & Wing:</b> ${student.className}-${student.section} (${student.wing})</p>
                            <p style="margin:4px 0;"><b>Guardian:</b> ${student.parentName}</p>
                            <p style="margin:4px 0;"><b>Emergency Phone:</b> ${student.phone}</p>
                            <p style="margin-top:12px;font-size:11px;color:#475569;">${SchoolInfo.ADDRESS} | ${SchoolInfo.PHONES}</p>
                          </div>
                        </div></body></html>
                    """.trimIndent()
                    printHtmlDocument(context, "DPS_ID_${student.userId}", html)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Print ID Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
