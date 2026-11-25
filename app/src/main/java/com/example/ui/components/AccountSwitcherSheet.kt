package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.SchoolUser
import com.example.data.preferences.SavedAccountEntry
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Multi-Account Instant Switcher Dialog:
 * Lets a user switch between multiple logged-in accounts on the same device with 1 tap
 * (no re-entering ID or password) and add additional accounts at any time.
 */
@Composable
fun AccountSwitcherDialog(
    activeUser: SchoolUser,
    savedAccounts: List<SavedAccountEntry>,
    isSwitching: Boolean,
    onSwitchAccount: (SavedAccountEntry) -> Unit,
    onRemoveAccount: (SavedAccountEntry) -> Unit,
    onAddAccount: suspend (role: String, userId: String, password: String) -> String?,
    onSignOutCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAddForm by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("STUDENT") }
    var newUserId by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    val displayAccounts = remember(savedAccounts, activeUser) {
        val hasActive = savedAccounts.any {
            (activeUser.id.isNotBlank() && it.docId == activeUser.id) ||
                (it.userId.equals(activeUser.userId, ignoreCase = true) && it.role == activeUser.role)
        }
        if (hasActive) {
            savedAccounts
        } else {
            val classMeta = when {
                activeUser.className.isNotBlank() -> "${activeUser.className}-${activeUser.section}"
                activeUser.subject.isNotBlank() -> activeUser.subject
                else -> activeUser.wing
            }
            listOf(
                SavedAccountEntry(
                    ownerUid = "",
                    docId = activeUser.id,
                    userId = activeUser.userId,
                    fullName = activeUser.fullName,
                    role = activeUser.role,
                    classInfo = classMeta,
                )
            ) + savedAccounts
        }
    }

    Dialog(
        onDismissRequest = { if (!isSwitching && !isAdding) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .testTag("account_switcher_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Navy Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDeep)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (showAddForm) "Add Another Account" else "Switch Account",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = if (showAddForm) {
                                    "Sign in once to save this account for instant switching"
                                } else {
                                    "Tap any saved account to switch instantly without password"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAmber,
                            )
                        }
                        IconButton(
                            onClick = { if (!isSwitching && !isAdding) onDismiss() },
                            modifier = Modifier.size(48.dp).testTag("account_switcher_close_btn"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Account Switcher", tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!showAddForm) {
                        if (isSwitching) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NavyDeep)
                                Spacer(Modifier.width(10.dp))
                                Text("Switching account...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        displayAccounts.forEach { acc ->
                            val isActive = (activeUser.id.isNotBlank() && acc.docId == activeUser.id) ||
                                (acc.userId.equals(activeUser.userId, ignoreCase = true) && acc.role == activeUser.role)
                            SavedAccountRowCard(
                                account = acc,
                                isActive = isActive,
                                enabled = !isSwitching,
                                onSelect = {
                                    if (isActive) {
                                        onDismiss()
                                    } else {
                                        onSwitchAccount(acc)
                                    }
                                },
                                onRemove = if (savedAccounts.any { it.docId == acc.docId && it.userId == acc.userId }) {
                                    { onRemoveAccount(acc) }
                                } else null,
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Button(
                            onClick = {
                                showAddForm = true
                                addError = null
                            },
                            enabled = !isSwitching,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("account_switcher_add_btn"),
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Account", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onSignOutCurrent()
                            },
                            enabled = !isSwitching,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("account_switcher_signout_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp), tint = RoseError)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out of Current Account", color = RoseError)
                        }
                    } else {
                        // Inline Add Another Account Form
                        Text(
                            "Select Account Role",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            listOf(
                                "STUDENT" to "Student",
                                "TEACHER" to "Teacher",
                                "LIBRARIAN" to "Librarian",
                                "FEE_MANAGER" to "Fee Mgr",
                                "ADMIN" to "Principal",
                            ).forEach { (roleKey, label) ->
                                FilterChip(
                                    selected = selectedRole == roleKey,
                                    onClick = {
                                        selectedRole = roleKey
                                        addError = null
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("add_account_role_${roleKey.lowercase()}"),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = newUserId,
                            onValueChange = {
                                newUserId = it
                                addError = null
                            },
                            label = {
                                Text(
                                    when (selectedRole) {
                                        "STUDENT" -> "Roll / Admission Number"
                                        "TEACHER" -> "Teacher Employee ID"
                                        "LIBRARIAN" -> "Librarian ID"
                                        "FEE_MANAGER" -> "Fee Manager ID"
                                        else -> "Admin ID"
                                    }
                                )
                            },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("add_account_id_input"),
                        )

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                addError = null
                            },
                            label = { Text("Password") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showPassword) "Hide Password" else "Show Password",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("add_account_password_input"),
                        )

                        if (addError != null) {
                            Surface(
                                color = RoseContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = addError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoseError,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showAddForm = false
                                    addError = null
                                },
                                enabled = !isAdding,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text("Back")
                            }
                            Button(
                                onClick = {
                                    if (!isAdding) {
                                        scope.launch {
                                            isAdding = true
                                            val err = onAddAccount(selectedRole, newUserId, newPassword)
                                            isAdding = false
                                            if (err == null) {
                                                newUserId = ""
                                                newPassword = ""
                                                showAddForm = false
                                                onDismiss()
                                            } else {
                                                addError = err
                                            }
                                        }
                                    }
                                },
                                enabled = !isAdding && newUserId.isNotBlank() && newPassword.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.4f).heightIn(min = 48.dp).testTag("add_account_submit_btn"),
                            ) {
                                if (isAdding) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = GoldAmber,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Signing In...")
                                } else {
                                    Text("Sign In & Add", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable Quick Sign-In card displayed on the Login screen whenever 1 or more accounts
 * are saved on this device, allowing 1-tap instant login without retyping ID/password.
 */
@Composable
fun SavedAccountsQuickLoginCard(
    savedAccounts: List<SavedAccountEntry>,
    isSwitching: Boolean,
    onQuickSwitch: (SavedAccountEntry) -> Unit,
    onRemoveAccount: (SavedAccountEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (savedAccounts.isEmpty()) return

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, GoldAmber),
        modifier = modifier
            .fillMaxWidth()
            .testTag("saved_accounts_quick_login_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.SwitchAccount, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved Accounts on This Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Tap any account below to open instantly — or sign in below to add another.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isSwitching) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NavyDeep)
                    Text("Opening saved account...", style = MaterialTheme.typography.bodySmall)
                }
            }

            savedAccounts.forEach { acc ->
                SavedAccountRowCard(
                    account = acc,
                    isActive = false,
                    enabled = !isSwitching,
                    onSelect = { onQuickSwitch(acc) },
                    onRemove = { onRemoveAccount(acc) },
                )
            }
        }
    }
}

@Composable
private fun SavedAccountRowCard(
    account: SavedAccountEntry,
    isActive: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val initials = remember(account.fullName, account.userId) {
        account.fullName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { account.userId.take(2).uppercase() }
    }
    val roleLabel = account.role.replace('_', ' ')

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) GoldSoft.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) GoldAmber else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onSelect)
            .testTag("saved_account_row_${account.userId}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isActive) NavyDeep else IndigoAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) GoldAmber else Color.White,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = account.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(color = NavyDeep.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NavyDeep,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = buildString {
                            append(account.userId)
                            if (account.classInfo.isNotBlank()) append(" • ${account.classInfo}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isActive) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Active Account",
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(22.dp),
                )
            } else if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("remove_saved_account_${account.userId}"),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${account.fullName} from device",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
