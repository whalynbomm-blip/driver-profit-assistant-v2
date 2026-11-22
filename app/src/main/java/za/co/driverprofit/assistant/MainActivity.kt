package za.co.driverprofit.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("driver_profit", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var readerButton: Button
    private lateinit var overlayButton: Button
    private lateinit var costInput: EditText
    private lateinit var preferredList: LinearLayout
    private lateinit var restrictedList: LinearLayout
    private lateinit var historyText: TextView
    private val bg = Color.rgb(12, 16, 22)
    private val card = Color.rgb(25, 32, 42)
    private val green = Color.rgb(160, 255, 35)
    private val muted = Color.rgb(164, 177, 192)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(bg)
        }
        root.addView(label("IndAssistant OCR", 27, Color.WHITE, true))
        root.addView(label("DRIVER PROFIT ASSISTANT  •  INDEPENDENT OCR", 11, green, true).apply { setPadding(0, dp(4), 0, dp(18)) })
        val statusCard = panel()
        status = label("READY TO CONFIGURE", 13, green, true)
        statusCard.addView(status)
        statusCard.addView(label("Reads visible inDrive offers and estimates your net after vehicle costs. You always make the final decision.", 13, muted, false).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(statusCard)
        root.addView(section("ASSISTANT CONTROLS"))
        readerButton = actionButton("")
        readerButton.setOnClickListener { prefs.edit().putBoolean("reader_enabled", !prefs.getBoolean("reader_enabled", true)).apply(); refreshControls() }
        root.addView(readerButton)
        overlayButton = actionButton("")
        overlayButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            } else {
                prefs.edit().putBoolean("overlay_enabled", !prefs.getBoolean("overlay_enabled", true)).apply()
                refreshControls()
            }
        }
        root.addView(overlayButton)
        root.addView(actionButton("OPEN ACCESSIBILITY SETTINGS").apply { setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(actionButton("ALLOW NOTIFICATIONS").apply { setOnClickListener { requestNotifications() } })
        root.addView(section("YOUR VEHICLE • FORD FIGO 2014"))
        val costCard = panel()
        costCard.addView(label("Estimated total running cost per kilometre (R)", 13, muted, false))
        costInput = edit(prefs.getFloat("cost_per_km", 2.50f).toString(), "e.g. 2.50")
        costCard.addView(costInput)
        costCard.addView(label("Enter your fuel, servicing, tyres and other running costs combined into one per-km estimate. You can change this any time.", 12, muted, false))
        costCard.addView(actionButton("SAVE VEHICLE COST").apply { setOnClickListener { saveCost() } })
        root.addView(costCard)
        root.addView(section("FIXED NET EARNINGS TARGETS"))
        val targets = panel()
        targets.addView(label("10–15 min     R60–R70 net", 16, green, true))
        targets.addView(label("Around 20 min     R80–R90 net", 15, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        targets.addView(label("25–30 min     R110–R140 net", 15, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        targets.addView(label("Recommendations use the lower end of each target band as the minimum. Estimated net is fare minus estimated vehicle cost.", 12, muted, false).apply { setPadding(0, dp(12), 0, 0) })
        root.addView(targets)
        root.addView(section("AREA PREFERENCES"))
        val prefCard = panel()
        prefCard.addView(label("PREFERRED AREAS", 13, green, true))
        preferredList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        prefCard.addView(preferredList)
        prefCard.addView(actionButton("+ ADD PREFERRED AREA").apply { setOnClickListener { addArea(true) } })
        root.addView(prefCard)
        val restrictedCard = panel()
        restrictedCard.addView(label("RESTRICTED / NO-GO AREAS", 13, Color.rgb(255, 120, 105), true))
        restrictedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        restrictedCard.addView(restrictedList)
        restrictedCard.addView(actionButton("+ ADD RESTRICTED AREA").apply { setOnClickListener { addArea(false) } })
        root.addView(restrictedCard)
        root.addView(section("RECENT OFFER HISTORY"))
        val historyCard = panel()
        historyText = label("No offers recorded yet.", 13, muted, false)
        historyCard.addView(historyText)
        historyCard.addView(actionButton("CLEAR HISTORY").apply { setOnClickListener { TripHistory.clear(this@MainActivity); refreshHistory() } })
        root.addView(historyCard)
        root.addView(label("Privacy and safety: offer text is processed on your device. OCR estimates can be wrong if details are hidden or unclear. Check every offer yourself. This app never taps Accept or Reject.", 12, muted, false).apply { setPadding(dp(4), dp(20), dp(4), 0) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        refreshControls()
        refreshAreas()
        refreshHistory()
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refreshControls(); if (::historyText.isInitialized) refreshHistory() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(15), dp(16), dp(15)); setBackgroundColor(card)
        val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); layoutParams = lp
    }
    private fun section(value: String) = label(value, 12, muted, true).apply { setPadding(dp(3), dp(20), 0, dp(9)) }
    private fun actionButton(value: String) = Button(this).apply {
        text = value; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(42, 55, 70)))
        val lp = LinearLayout.LayoutParams(-1, dp(48)); lp.topMargin = dp(8); layoutParams = lp
    }
    private fun edit(value: String, hintText: String) = EditText(this).apply {
        setText(value); hint = hintText; textSize = 16f; setTextColor(Color.WHITE); setHintTextColor(muted)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine(true)
    }
    private fun refreshControls() {
        val read = prefs.getBoolean("reader_enabled", true)
        val over = prefs.getBoolean("overlay_enabled", true)
        readerButton.text = if (read) "OFFER READER: ON  •  TAP TO TURN OFF" else "OFFER READER: OFF  •  TAP TO TURN ON"
        overlayButton.text = if (!Settings.canDrawOverlays(this)) "FLOATING ASSISTANT: PERMISSION NEEDED" else if (over) "FLOATING ASSISTANT: ON  •  TAP TO TURN OFF" else "FLOATING ASSISTANT: OFF  •  TAP TO TURN ON"
        status.text = if (read && Settings.canDrawOverlays(this) && over) "ASSISTANT CONFIGURED • READY TO READ" else "SETUP REQUIRED • CHECK CONTROLS BELOW"
    }
    private fun saveCost() {
        val value = costInput.text.toString().toFloatOrNull()
        if (value == null || value < 0f) { costInput.error = "Enter a valid amount"; return }
        prefs.edit().putFloat("cost_per_km", value).apply()
        Toast.makeText(this, "Vehicle cost saved", Toast.LENGTH_SHORT).show()
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
        else Toast.makeText(this, "Notification permission is already available or not required on this Android version.", Toast.LENGTH_SHORT).show()
    }
    private fun readAreas(key: String): MutableList<String> {
        val arr = try { org.json.JSONArray(prefs.getString(key, "[]") ?: "[]") } catch (_: Exception) { org.json.JSONArray() }
        return (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }.toMutableList()
    }
    private fun saveAreas(key: String, values: List<String>) {
        val arr = org.json.JSONArray(); values.forEach { arr.put(it) }; prefs.edit().putString(key, arr.toString()).apply()
    }
    private fun refreshAreas() {
        if (!::preferredList.isInitialized) return
        renderAreas(preferredList, "preferred_areas", true)
        renderAreas(restrictedList, "restricted_areas", false)
    }
    private fun renderAreas(container: LinearLayout, key: String, preferred: Boolean) {
        container.removeAllViews()
        val values = readAreas(key)
        if (values.isEmpty()) container.addView(label("None added yet", 12, muted, false).apply { setPadding(0, dp(8), 0, dp(4)) })
        values.forEach { value ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val name = label(value, 14, Color.WHITE, false); line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(Button(this).apply {
                text = "REMOVE"; textSize = 10f; setOnClickListener { val next = readAreas(key).apply { remove(value) }; saveAreas(key, next); refreshAreas() }
            })
            container.addView(line)
        }
    }
    private fun addArea(preferred: Boolean) {
        val input = EditText(this).apply { hint = "Area or suburb name" }
        AlertDialog.Builder(this).setTitle(if (preferred) "Add preferred area" else "Add restricted / no-go area")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotBlank()) {
                    val key = if (preferred) "preferred_areas" else "restricted_areas"
                    val values = readAreas(key)
                    if (values.none { it.equals(value, true) }) values.add(value)
                    saveAreas(key, values); refreshAreas()
                }
            }.show()
    }
    private fun refreshHistory() {
        val rows = TripHistory.read(this)
        if (rows.isEmpty()) { historyText.text = "No offers recorded yet. Detected offers with enough details will appear here."; return }
        val fmt = SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault())
        historyText.text = rows.take(12).joinToString("\n\n") { r ->
            fmt.format(Date(r.timestamp)) + "\nFare R%.2f • net R%.2f • %.0f min\n%s".format(Locale.US, r.fare, r.estimatedNet, r.minutes, r.decision)
        }
    }
}
