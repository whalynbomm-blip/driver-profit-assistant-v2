package za.co.driverprofit.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
        offer.record?.let { TripHistory.add(this, it) }
        showOverlay(offer)
    }

    private fun captureScreenForOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || captureInProgress) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureAt < 1400L) return
        lastCaptureAt = now
        captureInProgress = true
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
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
                    val capturedBitmap = bitmap
                    recognizer.process(InputImage.fromBitmap(capturedBitmap, 0))
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text.trim()
                            if (text.isNotBlank()) {
                                val offer = parseOffer(text)
                                if (offer != null && offer.decision != "CHECK DETAILS") {
                                    publishIfNew("OCR:$text", offer)
                                } else if (offer != null) {
                                    showOverlay(offer)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            capturedBitmap.recycle()
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
    data class Offer(
        val fare: String,
        val summary: String,
        val decision: String,
        val reason: String,
        val record: TripRecord? = null
    )

    private val distanceRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s?km", RegexOption.IGNORE_CASE)
    private val durationRegex = Regex("(\\d+)\\s?(?:min|mins|minutes)", RegexOption.IGNORE_CASE)

    fun parse(raw: String, costPerKm: Double, minimumHourlyNet: Double): Offer? {
        val normalized = raw.replace(',', '.')
        val moneyMatch = Regex("(R\\s?\\d+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?\\s?ZAR)", RegexOption.IGNORE_CASE).find(normalized)
            ?: return null
        val fareAmount = Regex("\\d+(?:\\.\\d{1,2})?").find(moneyMatch.value)?.value?.toDoubleOrNull() ?: return null
        val lines = normalized.lines()
        var pickupKm: Double? = null
        var tripKm: Double? = null
        val unlabeled = mutableListOf<Double>()

        lines.forEach { line ->
            val match = distanceRegex.find(line) ?: return@forEach
            val km = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val lower = line.lowercase(Locale.ROOT)
            when {
                listOf("pickup", "pick-up", "to passenger", "approach").any { lower.contains(it) } -> pickupKm = km
                listOf("trip", "destination", "drop-off", "dropoff", "ride distance").any { lower.contains(it) } -> tripKm = km
                else -> unlabeled.add(km)
            }
        }

        if (tripKm == null && unlabeled.size == 1) tripKm = unlabeled.first()
        if (tripKm == null && unlabeled.isEmpty()) {
            val all = distanceRegex.findAll(normalized).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
            if (all.size == 1) tripKm = all.first()
        }
        if (pickupKm == null && tripKm != null && unlabeled.size >= 2) {
            return Offer(moneyMatch.value, "Multiple distances detected", "CHECK DETAILS",
                "Could not reliably distinguish pickup distance from trip distance")
        }
        if (tripKm == null && unlabeled.size > 1) {
            return Offer(moneyMatch.value, unlabeled.joinToString(" + ") { "${it} km" }, "CHECK DETAILS",
                "Multiple unlabelled distances — verify pickup and trip distance")
        }

        val minutes = durationRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
        val totalKm = (pickupKm ?: 0.0) + (tripKm ?: 0.0)
        val summary = buildList {
            pickupKm?.let { add("Pickup ${String.format(Locale.US, "%.1f", it)} km") }
            tripKm?.let { add("Trip ${String.format(Locale.US, "%.1f", it)} km") }
            if (minutes != null) add("${minutes.toInt()} min")
        }.joinToString(" • ")

        if (tripKm == null || minutes == null || minutes <= 0 || totalKm <= 0) {
            return Offer(moneyMatch.value, summary.ifBlank { "Offer detected" }, "CHECK DETAILS",
                "Trip distance or duration is missing — no reliable profit estimate")
        }
        val estimatedCost = totalKm * costPerKm
        val estimatedNet = fareAmount - estimatedCost
        val hourlyNet = estimatedNet / (minutes / 60.0)
        val decision = when {
            estimatedNet <= 0 -> "DECLINE"
            hourlyNet >= minimumHourlyNet -> "ACCEPTABLE"
            else -> "NEGOTIATE"
        }
        val reason = String.format(Locale.US, "Est. net R%.2f • R%.0f net/hour • cost R%.2f", estimatedNet, hourlyNet, estimatedCost)
        val record = TripRecord(
            timestamp = System.currentTimeMillis(),
            fare = fareAmount,
            pickupKm = pickupKm,
            tripKm = tripKm,
            minutes = minutes,
            totalKm = totalKm,
            estimatedCost = estimatedCost,
            estimatedNet = estimatedNet,
            netPerHour = hourlyNet,
            decision = decision
        )
        return Offer(moneyMatch.value, summary, decision, reason, record)
    }
}
