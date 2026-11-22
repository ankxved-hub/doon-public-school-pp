package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SchoolInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max

/**
 * Pure-Kotlin ISO/IEC 18004 Byte-mode QR Matrix Generator (Versions 1..8, ECC-L)
 * Renders scannable UPI payment QR codes and Student ID QR codes completely offline.
 */
object QrEncoder {
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
            }
            poly = next
        }
        return poly
    }

    private fun rsRemainder(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val res = IntArray(ecLen)
        for (b in data) {
            val factor = b xor res[0]
            System.arraycopy(res, 1, res, 0, ecLen - 1)
            res[ecLen - 1] = 0
            for (i in 0 until ecLen) {
                res[i] = res[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return res
    }

    // Version specs (ECC Level L): (version, totalDataCodewords, ecCodewordsPerBlock, numBlocksGroup1, dataPerBlockG1, numBlocksG2, dataPerBlockG2)
    private data class VersionSpec(
        val version: Int,
        val totalData: Int,
        val ecPerBlock: Int,
        val blocks: List<Int>,
        val alignmentCenters: IntArray,
    )

    private val VERSIONS = listOf(
        VersionSpec(1, 19, 7, listOf(19), intArrayOf()),
        VersionSpec(2, 34, 10, listOf(34), intArrayOf(6, 18)),
        VersionSpec(3, 55, 15, listOf(55), intArrayOf(6, 22)),
        VersionSpec(4, 80, 20, listOf(80), intArrayOf(6, 26)),
        VersionSpec(5, 108, 26, listOf(108), intArrayOf(6, 30)),
        VersionSpec(6, 136, 18, listOf(68, 68), intArrayOf(6, 34)),
        VersionSpec(7, 156, 20, listOf(78, 78), intArrayOf(6, 22, 38)),
        VersionSpec(8, 194, 24, listOf(97, 97), intArrayOf(6, 24, 42)),
        VersionSpec(9, 232, 30, listOf(116, 116), intArrayOf(6, 26, 46)),
        VersionSpec(10, 274, 18, listOf(68, 68, 69, 69), intArrayOf(6, 28, 50)),
    )

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val spec = VERSIONS.firstOrNull { bytes.size + 3 <= it.totalData } ?: VERSIONS.last()
        val maxBytes = spec.totalData - 2
        val payload = if (bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes

        val bits = ArrayList<Int>(spec.totalData * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode = 0100
        appendBits(0b0100, 4)
        val lenBits = if (spec.version <= 9) 8 else 16
        appendBits(payload.size, lenBits)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val capBits = spec.totalData * 8
        val termBits = minOf(4, capBits - bits.size)
        appendBits(0, termBits)
        while (bits.size % 8 != 0) bits.add(0)
        var padIndex = 0
        while (bits.size < capBits) {
            appendBits(if (padIndex % 2 == 0) 0xEC else 0x11, 8)
            padIndex++
        }

        val dataBytes = IntArray(spec.totalData) { idx ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[idx * 8 + b]
            v
        }

        val dataBlocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0
        for (blockLen in spec.blocks) {
            val slice = dataBytes.copyOfRange(offset, offset + blockLen)
            dataBlocks.add(slice)
            ecBlocks.add(rsRemainder(slice, spec.ecPerBlock))
            offset += blockLen
        }

        val finalCodewords = ArrayList<Int>()
        val maxDataBlock = spec.blocks.max()
        for (i in 0 until maxDataBlock) {
            for (blk in dataBlocks) {
                if (i < blk.size) finalCodewords.add(blk[i])
            }
        }
        for (i in 0 until spec.ecPerBlock) {
            for (ec in ecBlocks) {
                finalCodewords.add(ec[i])
            }
        }

        val size = spec.version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunctionModule(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                matrix[r][c] = dark
                isFunction[r][c] = true
            }
        }

        fun drawFinder(topR: Int, leftC: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = topR + dr
                    val c = leftC + dc
                    if (r !in 0 until size || c !in 0 until size) continue
                    val dark = dr in 0..6 && dc in 0..6 && (
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                    )
                    setFunctionModule(r, c, dark)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setFunctionModule(6, i, dark)
            setFunctionModule(i, 6, dark)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (rCenter in centers) {
            for (cCenter in centers) {
                if ((rCenter == 6 && cCenter == 6) ||
                    (rCenter == 6 && cCenter == size - 7) ||
                    (rCenter == size - 7 && cCenter == 6)
                ) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        setFunctionModule(rCenter + dr, cCenter + dc, dark)
                    }
                }
            }
        }

        // Reserve format info & dark module
        for (i in 0..8) {
            if (i != 6) {
                setFunctionModule(8, i, false)
                setFunctionModule(i, 8, false)
            }
        }
        for (i in size - 8 until size) {
            setFunctionModule(8, i, false)
            setFunctionModule(i, 8, false)
        }
        setFunctionModule(size - 8, 8, true)

        if (spec.version >= 7) {
            val rem = spec.version
            var bitsVer = rem shl 12
            var temp = bitsVer
            for (i in 17 downTo 12) {
                if ((temp ushr i) and 1 != 0) temp = temp xor (0x1F25 shl (i - 12))
            }
            bitsVer = bitsVer or temp
            for (i in 0 until 18) {
                val dark = ((bitsVer ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                setFunctionModule(a, b, dark)
                setFunctionModule(b, a, dark)
            }
        }

        // Place codewords with mask 0 ((r + c) % 2 == 0)
        var bitIdx = 0
        val totalBits = finalCodewords.size * 8
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val c = right - j
                    val upward = ((right + 1) and 2) == 0
                    val r = if (upward) size - 1 - vert else vert
                    if (!isFunction[r][c]) {
                        val bit = if (bitIdx < totalBits) {
                            (finalCodewords[bitIdx / 8] ushr (7 - (bitIdx % 8))) and 1
                        } else 0
                        bitIdx++
                        val masked = bit xor (if ((r + c) % 2 == 0) 1 else 0)
                        matrix[r][c] = masked == 1
                    }
                }
            }
            right -= 2
        }

        // Format info for ECC-L (01) and Mask 0 (000) -> data=01000 (8) -> BCH = 0x77C4
        val fmtBits = 0b111011111000100
        for (i in 0..5) setFunctionModule(8, i, ((fmtBits ushr i) and 1) != 0)
        setFunctionModule(8, 7, ((fmtBits ushr 6) and 1) != 0)
        setFunctionModule(8, 8, ((fmtBits ushr 7) and 1) != 0)
        setFunctionModule(7, 8, ((fmtBits ushr 8) and 1) != 0)
        for (i in 9 until 15) setFunctionModule(14 - i, 8, ((fmtBits ushr i) and 1) != 0)

        for (i in 0..7) setFunctionModule(size - 1 - i, 8, ((fmtBits ushr i) and 1) != 0)
        for (i in 8 until 15) setFunctionModule(8, size - 15 + i, ((fmtBits ushr i) and 1) != 0)

        return matrix
    }
}

@Composable
fun QrCodeCanvas(
    data: String,
    size: Dp = 176.dp,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(data) { QrEncoder.encode(data) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Canvas(modifier = Modifier.size(size)) {
                val n = matrix.size
                val cell = this.size.width / n
                drawRect(color = Color.White, size = this.size)
                for (r in 0 until n) {
                    for (c in 0 until n) {
                        if (matrix[r][c]) {
                            drawRect(
                                color = Color(0xFF0F172A),
                                topLeft = Offset(c * cell, r * cell),
                                size = Size(cell + 0.5f, cell + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun printHtmlDocument(context: Context, jobName: String, htmlBody: String) {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            val adapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, htmlBody, "text/HTML", "UTF-8", null)
}

private fun encodeDrawableToDataUri(
    context: Context,
    @DrawableRes resId: Int,
    maxDimPx: Int = 720,
    usePng: Boolean = false,
): String {
    val decoded = BitmapFactory.decodeResource(context.resources, resId) ?: return ""
    val largest = max(decoded.width, decoded.height)
    val scaled = if (largest > maxDimPx && largest > 0) {
        val ratio = maxDimPx.toFloat() / largest.toFloat()
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }
    val out = ByteArrayOutputStream()
    val mime = if (usePng) {
        scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
        "image/png"
    } else {
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        "image/jpeg"
    }
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return "data:$mime;base64,$encoded"
}

suspend fun printTeacherLoginGuidePdf(
    context: Context,
    schoolWebsite: String = "https://doonpublicschooletah.in",
) {
    val htmlBody = withContext(Dispatchers.Default) {
        val logoUri = encodeDrawableToDataUri(context, R.drawable.dps_real_logo, maxDimPx = 240, usePng = true)
        val campusUri = encodeDrawableToDataUri(context, R.drawable.dps_etah_real_building_alt, maxDimPx = 840)
        val step1Uri = encodeDrawableToDataUri(context, R.drawable.teacher_guide_step1_1795342841943, maxDimPx = 680)
        val step2Uri = encodeDrawableToDataUri(context, R.drawable.teacher_guide_step2_1795342863239, maxDimPx = 680)
        val step3Uri = encodeDrawableToDataUri(context, R.drawable.teacher_guide_step3_1795342843136, maxDimPx = 760)
        val safeUrl = TextUtils.htmlEncode(schoolWebsite)
        val safeName = TextUtils.htmlEncode(SchoolInfo.NAME)
        val safeAddress = TextUtils.htmlEncode(SchoolInfo.ADDRESS)
        val safePhones = TextUtils.htmlEncode(SchoolInfo.PHONES)
        val safeEmail = TextUtils.htmlEncode(SchoolInfo.EMAIL)

        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <style>
            @page { size: A4; margin: 12mm; }
            body { font-family: Arial, Helvetica, sans-serif; color: #0f172a; margin: 0; padding: 0; line-height: 1.42; }
            .header { background: #0c1f3d; color: #ffffff; border-radius: 14px; overflow: hidden; margin-bottom: 14px; border: 2px solid #f59e0b; }
            .campus-img { width: 100%; height: 130px; object-fit: cover; display: block; }
            .brand-row { display: flex; align-items: center; gap: 12px; padding: 12px 16px; }
            .logo-box { width: 56px; height: 56px; background: #ffffff; border-radius: 10px; padding: 4px; flex-shrink: 0; }
            .logo-box img { width: 100%; height: 100%; object-fit: contain; }
            .title { font-size: 19px; font-weight: bold; margin: 0; color: #ffffff; }
            .subtitle { font-size: 12px; color: #fcd34d; margin: 3px 0 0 0; font-weight: bold; }
            .meta { font-size: 11px; color: #dbeafe; margin: 2px 0 0 0; }
            .card { border: 1px solid #cbd5e1; border-radius: 12px; padding: 12px 14px; margin-bottom: 12px; page-break-inside: avoid; background: #ffffff; }
            .step-badge { display: inline-block; background: #0c1f3d; color: #fcd34d; font-size: 11px; font-weight: bold; padding: 3px 9px; border-radius: 999px; margin-bottom: 6px; }
            .step-grid { display: flex; gap: 14px; align-items: flex-start; }
            .step-text { flex: 1.25; font-size: 12px; }
            .step-img { flex: 1; text-align: center; }
            .step-img img { width: 100%; max-width: 245px; border-radius: 10px; border: 1px solid #94a3b8; }
            ul { margin: 6px 0; padding-left: 18px; }
            li { margin-bottom: 5px; }
            .tip { background: #fef3c7; border-left: 4px solid #d97706; padding: 8px 10px; border-radius: 6px; font-size: 11.5px; margin-top: 8px; }
            .footer { background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 10px; padding: 10px 14px; font-size: 11px; color: #334155; }
          </style>
        </head>
        <body>
          <div class="header">
            <img class="campus-img" src="$campusUri" alt="Doon Public School Etah Campus" />
            <div class="brand-row">
              <div class="logo-box"><img src="$logoUri" alt="School Crest" /></div>
              <div>
                <h1 class="title">$safeName — Teacher Login Guide</h1>
                <p class="subtitle">Step-by-Step Illustrated Guide for Android App &amp; Web Portal</p>
                <p class="meta">Portal: $safeUrl &bull; Official Teacher ERP Guide</p>
              </div>
            </div>
          </div>

          <div class="card">
            <span class="step-badge">PART 1 &bull; ANDROID APP TEACHER LOGIN</span>
            <div class="step-grid">
              <div class="step-text">
                <strong>How to Sign In on Your Android Phone:</strong>
                <ul>
                  <li><strong>Step 1 — Open App:</strong> Launch the <em>Doon Public School, Etah</em> Android app. Check the bottom footer for <em>Cloudflare Edge Connected</em>.</li>
                  <li><strong>Step 2 — Tap Teacher Role:</strong> Under <em>STEP 1: SELECT YOUR ROLE</em>, tap <strong>Teacher</strong> (chip highlights in Navy &amp; Gold).</li>
                  <li><strong>Step 3 — Enter Employee ID:</strong> Type your Teacher Employee ID given by the Admin Office (example: <strong>TCH101</strong>).</li>
                  <li><strong>Step 4 — Enter Password &amp; Sign In:</strong> Type your password (use the eye icon to verify spelling) and tap <strong>Sign In to Doon Public School Portal</strong>.</li>
                </ul>
                <div class="tip"><strong>Before you start:</strong> Ask Principal / Admin for your Employee ID &amp; initial password created under <em>Admin &rarr; Accounts</em>.</div>
              </div>
              <div class="step-img">
                <img src="$step1Uri" alt="Android Teacher Login Screen" />
              </div>
            </div>
          </div>

          <div class="card">
            <span class="step-badge">PART 2 &bull; INSIDE THE TEACHER PORTAL &amp; MULTI-ROLE SWITCHING</span>
            <div class="step-grid">
              <div class="step-text">
                <strong>Daily Tasks &amp; Role Switching:</strong>
                <ul>
                  <li><strong>Daily &amp; Weekly DPT:</strong> Mark topic taught, homework details, class attendance, marks, and parent notices right from the bottom tabs.</li>
                  <li><strong>Multi-Role Accounts:</strong> If Admin granted you extra access (such as <em>Fee Manager</em> or <em>Principal / Admin</em>), tap the <strong>Swap icon (&hArr;)</strong> in the top-right bar to switch portals without logging out.</li>
                  <li><strong>Profile &amp; Password:</strong> Tap your profile icon anytime to view your assigned wing, classes, and subjects.</li>
                </ul>
              </div>
              <div class="step-img">
                <img src="$step2Uri" alt="Teacher Portal & Role Swap Guide" />
              </div>
            </div>
          </div>

          <div class="card">
            <span class="step-badge">PART 3 &bull; WEBSITE LOGIN ($safeUrl)</span>
            <div class="step-grid">
              <div class="step-text">
                <strong>Using the School Website on Mobile Browser or Laptop:</strong>
                <ul>
                  <li><strong>Open Portal:</strong> Visit <strong>$safeUrl</strong> (or your Cloudflare Workers portal link) in Chrome / Edge.</li>
                  <li><strong>Select Teacher Role:</strong> Click the <strong>Teacher</strong> tab in the Role Login box.</li>
                  <li><strong>Enter Credentials:</strong> Enter the exact same <strong>Employee ID</strong> (e.g., <em>TCH101</em>) and <strong>Password</strong> used in the Android app.</li>
                  <li><strong>Live Cloud Sync:</strong> Attendance, Daily/Weekly DPT, and Marks saved on the website appear instantly in the Android app and vice-versa.</li>
                </ul>
              </div>
              <div class="step-img">
                <img src="$step3Uri" alt="Web Portal Teacher Login Guide" />
              </div>
            </div>
          </div>

          <div class="footer">
            <strong>Need Login Help or Password Reset?</strong> Contact the School Admin Office ($safePhones &bull; $safeEmail).<br/>
            Campus: $safeAddress &bull; Do not share your Staff Employee ID or password with students or outside parties.
          </div>
        </body>
        </html>
        """.trimIndent()
    }
    withContext(Dispatchers.Main) {
        printHtmlDocument(context, "DPS_Etah_Teacher_Login_Guide", htmlBody)
    }
}
