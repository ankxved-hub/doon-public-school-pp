package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolInfo
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyRoyal
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "Auth"

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Activity context required for Credential Manager")
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOutGoogle(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun GoogleAuthGateScreen(
    onAuthSuccess: () -> Unit,
    onConnectCloudflare: (suspend (String) -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cfUrlInput by remember { mutableStateOf("") }
    var cfConnecting by remember { mutableStateOf(false) }
    var cfError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(onConnectCloudflare == null) {
        if (onConnectCloudflare == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(NavyDeep, NavyRoyal, NavyDeep))
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Campus Hero Banner
            Card(
                shape = RoundedCornerShape(24.dp),
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
                            .height(175.dp),
                    )
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(56.dp),
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
                            Column {
                                Text(
                                    text = SchoolInfo.NAME,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                                Text(
                                    text = SchoolInfo.AFFILIATION,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldAmber,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "“${SchoolInfo.MOTTO}”",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFFFDE68A),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = SchoolInfo.TRUST,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }

            // Wings Preview
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onConnectCloudflare != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Connect Cloudflare Edge Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "Paste your school's Cloudflare workers.dev URL to sync live with the website and D1 database (no Google Sign-In required).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = cfUrlInput,
                            onValueChange = {
                                cfUrlInput = it
                                cfError = null
                            },
                            label = { Text("Cloudflare Worker URL") },
                            placeholder = { Text("https://doon-public-school-etah.yourname.workers.dev") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("cloudflare_gate_url_input"),
                        )
                        if (cfError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = cfError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (!cfConnecting) {
                                    scope.launch {
                                        cfConnecting = true
                                        cfError = onConnectCloudflare(cfUrlInput)
                                        cfConnecting = false
                                    }
                                }
                            },
                            enabled = !cfConnecting,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("cloudflare_gate_connect_btn"),
                        ) {
                            if (cfConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = GoldAmber,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Verifying Cloudflare Server...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Verify & Connect Cloudflare Edge", fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        Text(
                            text = "Optional Firebase Fallback (Google Account)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Official Cloud School ERP Portal",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "Sign in with your Google Account to establish encrypted Firestore cloud sync, then choose your School Role (Principal, Teacher, Fee Manager, or Student/Parent).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SchoolCatalog.wings.take(2).forEach { wing ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(wing.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Text(wing.classesLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SchoolCatalog.wings.drop(2).forEach { wing ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(wing.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Text(wing.classesLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = { isLoading = false },
                            )
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("google_sign_in_button"),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google Instead", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // School Address Footer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(14.dp))
                    Text(SchoolInfo.ADDRESS, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(14.dp))
                    Text("${SchoolInfo.PHONES} • ${SchoolInfo.EMAIL}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f))
                }
            }
        }
    }
}
