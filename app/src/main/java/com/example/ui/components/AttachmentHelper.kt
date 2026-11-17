package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.NavyDeep
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

data class PickedAttachment(
    val name: String,
    val mimeType: String,
    val base64Data: String,
)

private const val MAX_DOC_BYTES = 320 * 1024
private const val MAX_IMAGE_DIM_PX = 960

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
