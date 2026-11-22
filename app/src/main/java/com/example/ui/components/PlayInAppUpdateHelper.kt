package com.example.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

@Stable
class PlayInAppUpdateCoordinator internal constructor(
    private val context: Context,
    private val appUpdateManager: AppUpdateManager,
    private val launchUpdateFlow: (AppUpdateInfo) -> Unit,
    private val onStatusMessage: (String) -> Unit,
) {
    var playUpdateAvailable by mutableStateOf(false)
        internal set

    var playAvailableVersionCode by mutableStateOf<Long?>(null)
        internal set

    internal var cachedAppUpdateInfo: AppUpdateInfo? = null

    fun refreshPlayUpdateAvailability() {
        runCatching {
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { info ->
                    cachedAppUpdateInfo = info
                    val availability = info.updateAvailability()
                    val availableCode = info.availableVersionCode().toLong()
                    if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        playUpdateAvailable = true
                        playAvailableVersionCode = availableCode
                        launchUpdateFlow(info)
                    } else if (availability == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        playUpdateAvailable = true
                        playAvailableVersionCode = availableCode
                    } else {
                        playUpdateAvailable = false
                    }
                }
                .addOnFailureListener {
                    // Device/install is outside Google Play Store (e.g. direct APK install)
                    playUpdateAvailable = false
                }
        }
    }

    fun startRealInAppOrFallbackUpdate(fallbackUrl: String) {
        val cached = cachedAppUpdateInfo
        if (cached != null &&
            cached.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            cached.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        ) {
            launchUpdateFlow(cached)
            return
        }

        runCatching {
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { freshInfo ->
                    cachedAppUpdateInfo = freshInfo
                    if ((freshInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                            freshInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
                        freshInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        launchUpdateFlow(freshInfo)
                    } else {
                        openFallbackDestination(fallbackUrl)
                    }
                }
                .addOnFailureListener {
                    openFallbackDestination(fallbackUrl)
                }
        }.onFailure {
            openFallbackDestination(fallbackUrl)
        }
    }

    private fun openFallbackDestination(rawUrl: String) {
        val clean = rawUrl.trim()
        if (clean.isBlank()) {
            onStatusMessage("Install from Google Play Store for automatic in-app updates, or ask Admin to set a direct APK/Play link.")
            return
        }
        val uri = Uri.parse(clean)
        val isPlayLink = uri.host?.contains("play.google.com", ignoreCase = true) == true ||
            clean.startsWith("market://", ignoreCase = true)
        if (isPlayLink) {
            try {
                val marketUri = Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}")
                context.startActivity(Intent(Intent.ACTION_VIEW, marketUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Fall back to HTTPS Play URL below
            }
        }
        openExternalLink(context, clean, onStatusMessage)
    }
}

@Composable
fun rememberPlayInAppUpdateCoordinator(
    onStatusMessage: (String) -> Unit,
): PlayInAppUpdateCoordinator {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appUpdateManager = remember(context) { AppUpdateManagerFactory.create(context.applicationContext) }

    var coordinatorRef by remember { mutableStateOf<PlayInAppUpdateCoordinator?>(null) }

    val updateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            onStatusMessage("In-app update paused. You can install anytime from the update prompt.")
        } else if (result.resultCode != Activity.RESULT_OK) {
            onStatusMessage("Play in-app update could not finish (code ${result.resultCode}). Please try again.")
        }
        coordinatorRef?.refreshPlayUpdateAvailability()
    }

    val coordinator = remember(context, appUpdateManager) {
        PlayInAppUpdateCoordinator(
            context = context,
            appUpdateManager = appUpdateManager,
            launchUpdateFlow = { info ->
                runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    )
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Unable to start Play in-app update.")
                }
            },
            onStatusMessage = onStatusMessage,
        ).also { coordinatorRef = it }
    }

    DisposableEffect(lifecycleOwner, coordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                coordinator.refreshPlayUpdateAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return coordinator
}
