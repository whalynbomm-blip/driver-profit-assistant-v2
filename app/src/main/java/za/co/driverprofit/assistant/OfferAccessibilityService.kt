package za.co.driverprofit.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class OfferAccessibilityService : AccessibilityService() {
    private var overlay: TextView? = null
    private var lastOffer = ""
    private var lastCaptureAt = 0L
    private var captureInProgress = false
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
        if (!prefs.getBoolean("reader_enabled", true)) {
            hideOverlay()
            return
        }
        val root = rootInActiveWindow
        val collected = StringBuilder()
        if (root != null) {
            collectText(root, collected)
            root.recycle()
        }
        val visibleText = collected.toString().trim()
        val parsed = if (visibleText.isNotBlank()) parseOffer(visibleText) else null
        if (parsed != null && parsed.decision != "CHECK DETAILS") {
            publishIfNew(visibleText, parsed)
        } else {
            captureScreenForOcr()
        }
    }

    private fun parseOffer(text: String): OfferParser.Offer? {
        val prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
        return OfferParser.parse(
            text,
            prefs.getFloat("cost_per_km", 2.50f).toDouble(),
            prefs.getFloat("minimum_hourly_net", 100f).toDouble()
        )
    }

    private fun publishIfNew(sourceText: String, offer: OfferParser.Offer) {
        val key = sourceText.trim()
        if (key.isBlank() || key == lastOffer) return
        lastOffer = key
        showOverlay(offer)
    }

    private fun captureScreenForOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || captureInProgress) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureAt < 1400L) return
        lastCaptureAt = now
        captureInProgress = true
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        var bitmap: Bitmap? = null
                        try {
                            val hardware = result.hardwareBuffer
                            val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                            hardware.close()
                            bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                            wrapped?.recycle()
                        } catch (_: Exception) {
                            bitmap = null
                        }
                        if (bitmap == null) {
                            captureInProgress = false
                            return
                        }
                        val image = InputImage.fromBitmap(bitmap, 0)
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                val text = visionText.text.trim()
                                if (text.isNotBlank()) {
                                    val offer = parseOffer(text)
                                    if (offer != null) publishIfNew("OCR:$text", offer)
                                }
                            }
                            .addOnCompleteListener {
                                bitmap.recycle()
                                captureInProgress = false
                            }
                    }

                    override fun onFailure(errorCode: Int) {
                        captureInProgress = false
                    }
                })
        } catch (_: Exception) {
            captureInProgress = false
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out)
                child.recycle()
            }
        }
    }

    private fun showOverlay(offer: OfferParser.Offer) {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val label = TextView(this).apply {
            text = "DPA • ${offer.decision}\nFare: ${offer.fare}\n${offer.summary}\n${offer.reason}\nReview before accepting"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xEE17324D.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 120
        }
        try {
            hideOverlay()
            wm.addView(label, params)
            overlay = label
        } catch (_: Exception) { }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) } catch (_: Exception) { }
        overlay = null
    }

    override fun onInterrupt() { hideOverlay() }
    override fun onDestroy() {
        hideOverlay()
        recognizer.close()
        super.onDestroy()
    }
}

private object OfferParser {
    data class Offer(val fare: String, val summary: String, val decision: String, val reason: String)

    fun parse(raw: String, costPerKm: Double, minimumHourlyNet: Double): Offer? {
        val s = raw.replace(',', '.')
        val moneyMatch = Regex("(R\\s?\\d+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?\\s?ZAR)", RegexOption.IGNORE_CASE).find(s)
            ?: return null
        val amount = Regex("\\d+(?:\\.\\d{1,2})?").find(moneyMatch.value)?.value?.toDoubleOrNull() ?: return null
        val distances = Regex("(\\d+(?:\\.\\d+)?)\\s?km", RegexOption.IGNORE_CASE)
            .findAll(s).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val minutes = Regex("(\\d+)\\s?(?:min|mins|minutes)", RegexOption.IGNORE_CASE)
            .find(s)?.groupValues?.get(1)?.toDoubleOrNull()
        val summary = buildList {
            if (distances.isNotEmpty()) add(distances.joinToString(" + ") { String.format(Locale.US, "%.1f km", it) })
            if (minutes != null) add("${minutes.toInt()} min")
        }.joinToString(" • ")
        if (distances.isEmpty() || minutes == null || minutes <= 0) {
            return Offer(moneyMatch.value, summary.ifBlank { "Offer detected" }, "CHECK DETAILS", "Distance or duration not detected — no reliable profit estimate")
        }
        val totalKm = distances.sum()
        val estimatedCost = totalKm * costPerKm
        val estimatedNet = amount - estimatedCost
        val hourlyNet = estimatedNet / (minutes / 60.0)
        val decision = when {
            estimatedNet <= 0 -> "DECLINE"
            hourlyNet >= minimumHourlyNet -> "ACCEPTABLE"
            else -> "NEGOTIATE"
        }
        val reason = String.format(Locale.US, "Est. net R%.2f • R%.0f net/hour • cost R%.2f", estimatedNet, hourlyNet, estimatedCost)
        return Offer(moneyMatch.value, summary, decision, reason)
    }
}
