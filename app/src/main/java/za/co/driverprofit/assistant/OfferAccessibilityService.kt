package za.co.driverprofit.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

class OfferAccessibilityService : AccessibilityService() {
    private var overlay: TextView? = null
    private var lastOffer = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
        if (!prefs.getBoolean("reader_enabled", true)) {
            hideOverlay()
            return
        }
        val root = rootInActiveWindow ?: return
        val collected = StringBuilder()
        collectText(root, collected)
        val text = collected.toString().trim()
        if (text.isBlank() || text == lastOffer) return
        val parsed = OfferParser.parse(text) ?: return
        lastOffer = text
        showOverlay(parsed)
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
            text = "DPA • ${offer.fare}\n${offer.summary}\nReview trip before accepting"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xEE17324D.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
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
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) { }
        overlay = null
    }

    override fun onInterrupt() { hideOverlay() }
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}

private object OfferParser {
    data class Offer(val fare: String, val summary: String)

    fun parse(raw: String): Offer? {
        val s = raw.replace(',', '.')
        val money = Regex("(R\\s?\\d+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?\\s?ZAR)", RegexOption.IGNORE_CASE)
            .find(s)?.value ?: return null
        val distance = Regex("(\\d+(?:\\.\\d+)?)\\s?km", RegexOption.IGNORE_CASE)
            .findAll(s).map { it.groupValues[1] + " km" }.toList()
        val duration = Regex("(\\d+)\\s?(min|mins|minutes)", RegexOption.IGNORE_CASE)
            .find(s)?.value
        val destination = s.lineSequence().map { it.trim() }
            .firstOrNull { it.length in 4..48 && it.any(Char::isLetter) && !it.contains(money, true) }
        val details = mutableListOf<String>()
        if (distance.isNotEmpty()) details.add(distance.distinct().joinToString(" • "))
        if (duration != null) details.add(duration)
        if (destination != null) details.add(destination)
        return Offer(money, if (details.isEmpty()) "Offer detected — check trip details" else details.joinToString(" • "))
    }
}
