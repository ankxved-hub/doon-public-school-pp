package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.RoseError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

data class PickedAttachment(
    val name: String,
    val mimeType: String,
    val base64Data: String,
)

private const val MAX_DOC_BYTES = 320 * 1024
private const val MAX_IMAGE_DIM_PX = 960

private val videoThumbnailCache = object : LruCache<String, ImageBitmap>(24) {}

private val urlRegex = Regex(
    """(https?://[^\s<>"]+|(?:www\.)?(?:youtube\.com|youtu\.be|instagram\.com|facebook\.com|fb\.watch)/[^\s<>"]+)""",
    RegexOption.IGNORE_CASE,
)

private val ytShortsRegex = Regex("""youtube\.com/shorts/([a-zA-Z0-9_-]{6,15})""", RegexOption.IGNORE_CASE)
private val ytShortLinkRegex = Regex("""youtu\.be/([a-zA-Z0-9_-]{6,15})""", RegexOption.IGNORE_CASE)
private val ytWatchRegex = Regex("""[?&]v=([a-zA-Z0-9_-]{6,15})""", RegexOption.IGNORE_CASE)
private val ytEmbedRegex = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{6,15})""", RegexOption.IGNORE_CASE)
private val ogImageRegex = Regex(
    """<meta[^>]+(?:property|name)=["'](?:og:image|twitter:image)["'][^>]+content=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE,
)
private val ogImageAltRegex = Regex(
    """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:image|twitter:image)["']""",
    RegexOption.IGNORE_CASE,
)

fun extractFirstMediaUrl(text: String): String? {
    val raw = urlRegex.find(text)?.value?.trimEnd('.', ',', ';', ')', ']') ?: return null
    return if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        raw
    } else {
        "https://$raw"
    }
}

fun extractYoutubeVideoId(url: String): String? {
    ytShortsRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
    ytShortLinkRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
    ytWatchRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
    ytEmbedRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

fun detectVideoPlatformLabel(url: String): String {
    val lower = url.lowercase()
    return when {
        lower.contains("youtube.com/shorts") -> "YOUTUBE SHORT"
        lower.contains("youtu.be") || lower.contains("youtube.com") -> "YOUTUBE VIDEO"
        lower.contains("instagram.com/reel") || lower.contains("instagram.com") -> "INSTAGRAM REEL"
        lower.contains("facebook.com/reel") || lower.contains("fb.watch") || lower.contains("facebook.com") -> "FACEBOOK REEL"
        else -> "SCHOOL REEL / SHORT"
    }
}

private fun downloadImageBitmapFromUrl(imageUrl: String): ImageBitmap? {
    return try {
        val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
        }
        conn.inputStream.use { stream ->
            val bytes = stream.readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

private fun fetchVideoThumbnailBlocking(videoUrl: String): ImageBitmap? {
    val ytId = extractYoutubeVideoId(videoUrl)
    if (ytId != null) {
        val hqUrl = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
        downloadImageBitmapFromUrl(hqUrl)?.let { return it }
        val mqUrl = "https://i.ytimg.com/vi/$ytId/mqdefault.jpg"
        downloadImageBitmapFromUrl(mqUrl)?.let { return it }
    }
    return try {
        val conn = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
        }
        val contentType = conn.contentType.orEmpty().lowercase()
        if (contentType.startsWith("image/")) {
            return conn.inputStream.use { stream ->
                val bytes = stream.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
        val htmlSnippet = conn.inputStream.bufferedReader().use { reader ->
            val buf = CharArray(32 * 1024)
            val count = reader.read(buf)
            if (count > 0) String(buf, 0, count) else ""
        }
        val ogImg = (ogImageRegex.find(htmlSnippet)?.groupValues?.getOrNull(1)
            ?: ogImageAltRegex.find(htmlSnippet)?.groupValues?.getOrNull(1))
            ?.replace("&amp;", "&")
        if (!ogImg.isNullOrBlank()) {
            downloadImageBitmapFromUrl(ogImg)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun rememberVideoThumbnailBitmap(videoUrl: String): ImageBitmap? {
    val cleanUrl = videoUrl.trim()
    var bitmap by remember(cleanUrl) {
        mutableStateOf(if (cleanUrl.isNotBlank()) videoThumbnailCache.get(cleanUrl) else null)
    }
    LaunchedEffect(cleanUrl) {
        if (cleanUrl.isBlank() || bitmap != null) return@LaunchedEffect
        val fetched = withContext(Dispatchers.IO) {
            fetchVideoThumbnailBlocking(cleanUrl)
        }
        if (fetched != null) {
            videoThumbnailCache.put(cleanUrl, fetched)
            bitmap = fetched
        }
    }
    return bitmap
}

fun openExternalLink(context: Context, rawUrl: String, onError: (String) -> Unit = {}) {
    val normalized = if (rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true)) {
        rawUrl.trim()
    } else {
        "https://${rawUrl.trim()}"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onError("No app or browser found to open this link.")
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Unable to open link.")
    }
}

/**
 * Rich Reel / Short / Video Thumbnail Card:
 * Shows a real video thumbnail photo (from attached custom photo, auto-fetched YouTube/Reel thumbnail,
 * or DPS Etah campus photo fallback) with a Play Button overlay and platform badge so Reels & Shorts
 * never appear as plain text links.
 */
@Composable
fun ReelOrShortThumbnailCard(
    videoUrl: String,
    title: String,
    customThumbnailBitmap: ImageBitmap? = null,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remoteThumbnail = rememberVideoThumbnailBitmap(videoUrl)
    val displayBitmap = customThumbnailBitmap ?: remoteThumbnail
    val platformLabel = remember(videoUrl) { detectVideoPlatformLabel(videoUrl) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, GoldAmber.copy(alpha = 0.85f)),
        color = NavyDeep,
        modifier = modifier
            .fillMaxWidth()
            .clickable { openExternalLink(context, videoUrl, onShowMessage) }
            .testTag("reel_short_thumbnail_card"),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap,
                        contentDescription = title.ifBlank { "Reel / Short Thumbnail" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.dps_etah_real_building_alt),
                        contentDescription = title.ifBlank { "Doon Public School Reel / Short Thumbnail" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Dark cinematic gradient scrim so the Play button and badges stand out clearly
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.72f),
                                )
                            )
                        )
                )

                // Top-left Platform Badge (YOUTUBE SHORT / INSTAGRAM REEL / FACEBOOK REEL)
                Surface(
                    color = RoseError,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = platformLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Top-right DPS Etah badge
                Surface(
                    color = NavyDeep.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    Text(
                        text = "Tap to Watch",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Center Play Button Overlay
                Surface(
                    shape = CircleShape,
                    color = NavyDeep.copy(alpha = 0.86f),
                    border = BorderStroke(2.dp, GoldAmber),
                    modifier = Modifier.size(62.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play Reel or Short",
                            tint = GoldAmber,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Bottom caption inside thumbnail
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = title.ifBlank { "Doon Public School, Etah — Official Reel / Short" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = videoUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDeep)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.SmartDisplay,
                        contentDescription = null,
                        tint = GoldAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Video Thumbnail Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                FilledTonalButton(
                    onClick = { openExternalLink(context, videoUrl, onShowMessage) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = GoldAmber,
                        contentColor = NavyDeep,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watch Reel / Short", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Renders a Circular / Notice's text, automatically detecting any Reel / Short / Video link
 * and showing a rich Photo Thumbnail Card with a Play button (or standard AttachmentPreviewBlock
 * for regular photo/PDF notices).
 */
@Composable
fun CircularMediaAndAttachmentSection(
    title: String,
    content: String,
    attachmentName: String,
    attachmentMimeType: String,
    attachmentData: String,
    onShowMessage: (String) -> Unit = {},
) {
    val detectedUrl = remember(content) { extractFirstMediaUrl(content) }
    val displayText = remember(content, detectedUrl) {
        if (detectedUrl != null) {
            content.replace(detectedUrl, "").trim().ifBlank { content }
        } else {
            content
        }
    }
    val attachedBitmap = remember(attachmentData, attachmentMimeType) {
        if (attachmentMimeType.startsWith("image/") && attachmentData.isNotBlank()) {
            decodeAttachmentBitmap(attachmentData)
        } else {
            null
        }
    }

    if (displayText.isNotBlank()) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (detectedUrl != null) {
        Spacer(Modifier.height(4.dp))
        ReelOrShortThumbnailCard(
            videoUrl = detectedUrl,
            title = title,
            customThumbnailBitmap = attachedBitmap,
            onShowMessage = onShowMessage,
        )
        // If a non-image document was also attached alongside the Reel/Short link, show it too
        if (attachedBitmap == null && attachmentData.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            AttachmentPreviewBlock(
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType,
                attachmentData = attachmentData,
                onShowMessage = onShowMessage,
            )
        }
    } else if (attachmentData.isNotBlank() || attachmentName.isNotBlank()) {
        AttachmentPreviewBlock(
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            attachmentData = attachmentData,
            onShowMessage = onShowMessage,
        )
    }
}

fun readAttachmentFromUri(
    context: Context,
    uri: Uri,
    forceImage: Boolean = false,
): Result<PickedAttachment> = runCatching {
    val resolver = context.contentResolver
    val detectedMime = resolver.getType(uri)?.take(120).orEmpty()
    val rawName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else null
    }?.takeIf { it.isNotBlank() } ?: if (forceImage) "photo.jpg" else "document"

    val safeName = rawName.take(150)
    val isImage = forceImage || detectedMime.startsWith("image/")

    if (isImage) {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > MAX_IMAGE_DIM_PX * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Unable to read selected image.")

        val scaled = scaleBitmapDown(decoded, MAX_IMAGE_DIM_PX)
        if (scaled !== decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        var quality = 72
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > MAX_DOC_BYTES && quality > 30) {
            out.reset()
            quality -= 15
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        scaled.recycle()

        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        require(encoded.length <= 440_000) { "Image is too large even after compression." }
        PickedAttachment(
            name = if (safeName.contains('.')) safeName else "$safeName.jpg",
            mimeType = "image/jpeg",
            base64Data = encoded,
        )
    } else {
        val rawBytes = resolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(16 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buf)
                if (read == -1) break
                total += read
                if (total > MAX_DOC_BYTES) {
                    throw IllegalArgumentException("Document exceeds 320 KB cloud limit. Please choose a smaller file.")
                }
                out.write(buf, 0, read)
            }
            out.toByteArray()
        } ?: throw IllegalStateException("Unable to open selected document.")

        val encoded = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        PickedAttachment(
            name = safeName,
            mimeType = detectedMime.ifBlank { "application/octet-stream" },
            base64Data = encoded,
        )
    }
}

private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDim && h <= maxDim) return bitmap
    val ratio = maxDim.toFloat() / max(w, h).toFloat()
    val nw = (w * ratio).toInt().coerceAtLeast(1)
    val nh = (h * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
}

fun decodeAttachmentBitmap(base64Data: String): ImageBitmap? {
    if (base64Data.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

fun encodeBitmapToBase64(bitmap: Bitmap, maxDim: Int = 800, maxBytes: Int = 310 * 1024): String {
    val scaled = scaleBitmapDown(bitmap, maxDim)
    val out = ByteArrayOutputStream()
    var quality = 78
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    while (out.size() > maxBytes && quality > 30) {
        out.reset()
        quality -= 12
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    if (scaled !== bitmap) {
        scaled.recycle()
    }
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun openDocumentAttachment(
    context: Context,
    fileName: String,
    mimeType: String,
    base64Data: String,
    onError: (String) -> Unit,
) {
    try {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val cleanFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "document.bin" }
        val target = File(dir, cleanFileName)
        target.writeBytes(bytes)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val resolvedMime = mimeType.ifBlank {
            when {
                cleanFileName.endsWith(".pdf", true) -> "application/pdf"
                cleanFileName.endsWith(".txt", true) -> "text/plain"
                cleanFileName.endsWith(".jpg", true) || cleanFileName.endsWith(".jpeg", true) -> "image/jpeg"
                cleanFileName.endsWith(".png", true) -> "image/png"
                else -> "*/*"
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open $cleanFileName"))
    } catch (_: ActivityNotFoundException) {
        onError("No installed app found to open $fileName.")
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Failed to open $fileName.")
    }
}

@Composable
fun AttachmentPreviewBlock(
    attachmentName: String,
    attachmentMimeType: String,
    attachmentData: String,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (attachmentData.isBlank() && attachmentName.isBlank()) return

    val context = LocalContext.current
    val isImage = attachmentMimeType.startsWith("image/")
    val bitmap = remember(attachmentData, isImage) {
        if (isImage) decodeAttachmentBitmap(attachmentData) else null
    }
    var showFullImage by remember { mutableStateOf(false) }

    if (bitmap != null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .clickable { showFullImage = true },
        ) {
            Column {
                Image(
                    bitmap = bitmap,
                    contentDescription = attachmentName.ifBlank { "Attached Photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            attachmentName.ifBlank { "Attached Photo" },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text("Tap to zoom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    } else if (attachmentData.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.fillMaxWidth(),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Description, contentDescription = "Document", tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            attachmentName.ifBlank { "Attached Document" },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            attachmentMimeType.ifBlank { "Document File" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        openDocumentAttachment(
                            context = context,
                            fileName = attachmentName,
                            mimeType = attachmentMimeType,
                            base64Data = attachmentData,
                            onError = onShowMessage,
                        )
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showFullImage && bitmap != null) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            attachmentName.ifBlank { "Photo Preview" },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showFullImage = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close preview", tint = Color.White)
                        }
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachmentName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 460.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentPickerChip(
    attachment: PickedAttachment?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachment == null) return
    val isImage = attachment.mimeType.startsWith("image/")
    val previewBmp = remember(attachment) {
        if (isImage) decodeAttachmentBitmap(attachment.base64Data) else null
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(
                        attachment.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (isImage) "Photo ready to attach" else "Document ready to attach",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Remove attachment")
            }
        }
    }
}
