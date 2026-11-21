package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolInfo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SchoolRoleLoginScreen(
    isSeeding: Boolean,
    isSyncError: Boolean = false,
    googleEmail: String?,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onRetrySync: () -> Unit = {},
    onLoginRole: suspend (role: String, userId: String, password: String) -> String?,
    onGoogleSignOut: () -> Unit,
    isCloudflareMode: Boolean = false,
    needsCloudflareAdminSetup: Boolean = false,
    onClaimCloudflareAdmin: suspend (fullName: String, userId: String, phone: String, password: String) -> String? = { _, _, _, _ -> null },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var selectedRole by remember { mutableStateOf("STUDENT") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var adminName by remember { mutableStateOf("Dr. Principal (Admin)") }
    var adminId by remember { mutableStateOf("ADMIN01") }
    var adminPhone by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }

    LaunchedEffect(isSyncError) {
        if (!isSyncError && errorMsg?.startsWith("Refreshing") == true) {
            errorMsg = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDeep, NavyRoyal, NavyDeep)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 620.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // School Branding Banner Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.dps_etah_real_building_alt),
                        contentDescription = "Doon Public School Etah Real Campus Building",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp),
                    )
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(54.dp),
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.dps_real_logo),
                                contentDescription = "Doon Public School Official Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(SchoolInfo.NAME, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("“${SchoolInfo.MOTTO}”", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = GoldAmber)
                            Text(SchoolInfo.TRUST, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Login Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("School ERP Role Login", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = if (isCloudflareMode) {
                                    "Cloudflare Edge Synced (Website + App Live)"
                                } else {
                                    "Cloud Synced: ${googleEmail ?: "Google Account"}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleDarkMode(!isDarkMode) },
                                modifier = Modifier.testTag("login_dark_mode_btn"),
                            ) {
                                Icon(
                                    if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (!isCloudflareMode) {
                                TextButton(onClick = onGoogleSignOut) {
                                    Text("Switch Google", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (isCloudflareMode && needsCloudflareAdminSetup) {
                        Surface(
                            color = GoldSoft,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "FIRST-RUN CLOUDFLARE SETUP (PRINCIPAL / ADMIN)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDeep,
                                )
                                Text(
                                    text = "Create the first Principal Admin account once to unlock student & teacher creation across App and Website.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavyDeep,
                                )
                                OutlinedTextField(
                                    value = adminName,
                                    onValueChange = { adminName = it },
                                    label = { Text("Principal / Admin Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = adminId,
                                    onValueChange = { adminId = it },
                                    label = { Text("Admin ID (e.g. ADMIN01)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = adminPhone,
                                    onValueChange = { adminPhone = it },
                                    label = { Text("Phone Number") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = adminPassword,
                                    onValueChange = { adminPassword = it },
                                    label = { Text("Strong Admin Password (min 6 chars)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = {
                                        if (!isSubmitting) {
                                            scope.launch {
                                                isSubmitting = true
                                                errorMsg = onClaimCloudflareAdmin(adminName, adminId, adminPhone, adminPassword)
                                                isSubmitting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Activate Principal Admin Account", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (isSeeding) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = GoldAmber,
                            )
                            Text(
                                text = "Connecting to Doon Public School cloud server...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (isSyncError) {
                        Surface(
                            color = GoldSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Weak connection. Tap Reconnect to refresh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavyDeep,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onRetrySync) {
                                    Text("Reconnect", color = NavyRoyal, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Text("STEP 1: SELECT YOUR ROLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Triple("STUDENT", "Student", Icons.Filled.School),
                            Triple("TEACHER", "Teacher", Icons.Filled.Person),
                            Triple("FEE_MANAGER", "Accounts", Icons.Filled.AccountBalanceWallet),
                            Triple("ADMIN", "Principal", Icons.Filled.AdminPanelSettings),
                        ).forEach { (roleKey, label, icon) ->
                            val active = selectedRole == roleKey
                            Surface(
                                color = if (active) NavyDeep else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (active) GoldAmber else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable {
                                        selectedRole = roleKey
                                        errorMsg = null
                                    }
                                    .testTag("role_chip_$roleKey"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(icon, contentDescription = label, tint = if (active) GoldAmber else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) GoldAmber else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = userId,
                        onValueChange = {
                            userId = it
                            errorMsg = null
                        },
                        label = {
                            Text(
                                when (selectedRole) {
                                    "STUDENT" -> "Roll / Admission No"
                                    "TEACHER" -> "Employee ID"
                                    "FEE_MANAGER" -> "Staff ID"
                                    else -> "Admin ID"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                when (selectedRole) {
                                    "STUDENT" -> "Enter Roll / Admission No (e.g. 001 or DPS2026-001)"
                                    "TEACHER" -> "Enter Teacher ID (e.g. TCH101)"
                                    "FEE_MANAGER" -> "Enter Accounts ID (e.g. FEE201)"
                                    else -> "Enter Principal Admin ID (e.g. ADMIN01)"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("role_userid_input"),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
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
                            .testTag("role_password_input"),
                    )

                    if (errorMsg != null) {
                        Surface(
                            color = RoseContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = errorMsg.orEmpty(),
                                color = RoseError,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                scope.launch {
                                    isSubmitting = true
                                    errorMsg = onLoginRole(selectedRole, userId, password)
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSeeding && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("role_login_submit"),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = GoldAmber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Signing In...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In to Doon Public School Portal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4 Official Academic Wings Reference
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OFFICIAL ACADEMIC WINGS", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                    SchoolCatalog.wings.forEach { w ->
                        Text("• ${w.name} (${w.subtitle}): ${w.classesLabel}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("${SchoolInfo.ADDRESS} • ${SchoolInfo.PHONES}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}
