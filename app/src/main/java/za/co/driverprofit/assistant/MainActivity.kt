package za.co.driverprofit.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var readerButton: Button
    private lateinit var costInput: EditText
    private lateinit var hourlyInput: EditText
    private lateinit var historyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "IndAssistant"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "Android offer reader • V2 companion"
            textSize = 15f
            setPadding(0, 8, 0, 24)
        })
        root.addView(Button(this).apply {
            text = "Open earnings dashboard"
            setOnClickListener { startActivity(Intent(this@MainActivity, DashboardActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "1. Enable offer reading"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "2. Allow floating assistant"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        })
        root.addView(Button(this).apply {
            text = "3. Enable notifications"
            setOnClickListener { requestNotificationPermission() }
        })
        readerButton = Button(this).apply { setOnClickListener { toggleReader() } }
        root.addView(readerButton)
        root.addView(TextView(this).apply {
            text = "Profit settings"
            textSize = 19f
            setPadding(0, 28, 0, 8)
        })
        root.addView(TextView(this).apply { text = "Estimated vehicle cost per km (R)" })
        costInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getFloat("cost_per_km", 2.50f).toString())
            hint = "e.g. 2.50"
        }
        root.addView(costInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(TextView(this).apply {
            text = "Minimum target net per hour (R) — R280 = R70 per 15 minutes"
            setPadding(0, 12, 0, 0)
        })
        hourlyInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getFloat("minimum_hourly_net", 280f).toString())
            hint = "e.g. 100"
        }
        root.addView(hourlyInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(Button(this).apply {
            text = "Save profit settings"
            setOnClickListener { saveSettings() }
        })
        root.addView(TextView(this).apply {
            text = "Trip history"
            textSize = 19f
            setPadding(0, 28, 0, 8)
        })
        historyText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 4, 0, 8)
        }
        root.addView(historyText)
        root.addView(Button(this).apply {
            text = "Export detected offers for web dashboard"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "indassistant-android-offers.json")
                }
                startActivityForResult(intent, 4102)
            }
        })
        root.addView(Button(this).apply {
            text = "Clear trip history"
            setOnClickListener {
                TripHistory.clear(this@MainActivity)
                refreshHistory()
            }
        })
        root.addView(TextView(this).apply {
            text = "\nThe reader analyses visible offer text and uses on-device OCR when needed. Estimates depend on what the offer screen displays. Check every recommendation yourself. The app never taps Accept or Reject."
            textSize = 13f
            setPadding(0, 20, 0, 0)
        })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        refreshReaderButton()
        refreshHistory()
    }

    @Deprecated("Use Activity Result APIs for new integrations")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4102 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val exported = JSONArray()
            TripHistory.read(this).forEach { item ->
                exported.put(JSONObject().apply {
                    put("id", item.timestamp)
                    put("timestamp", item.timestamp)
                    put("date", Instant.ofEpochMilli(item.timestamp).toString())
                    put("fare", item.fare)
                    put("pickup", item.pickupKm ?: 0.0)
                    put("trip", item.tripKm)
                    put("km", item.totalKm)
                    put("minutes", item.minutes)
                    put("cost", item.estimatedCost)
                    put("net", item.estimatedNet)
                    put("hour", item.netPerHour)
                    put("destination", "")
                    put("decision", item.decision)
                    put("source", "android-reader")
                })
            }
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(exported.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open export file")
            android.widget.Toast.makeText(this, "Android offers exported. Import this JSON in the web app History tab.", android.widget.Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            android.widget.Toast.makeText(this, "Export failed: " + error.message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::historyText.isInitialized) refreshHistory()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
            } else {
                android.widget.Toast.makeText(this, "Notifications are already enabled", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(this, "This Android version does not require notification permission", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleReader() {
        val enabled = !prefs.getBoolean("reader_enabled", true)
        prefs.edit().putBoolean("reader_enabled", enabled).apply()
        refreshReaderButton()
    }

    private fun refreshReaderButton() {
        readerButton.text = if (prefs.getBoolean("reader_enabled", true))
            "Offer reading: ON — tap to turn OFF" else "Offer reading: OFF — tap to turn ON"
    }

    private fun saveSettings() {
        val cost = costInput.text.toString().toFloatOrNull()
        val hourly = hourlyInput.text.toString().toFloatOrNull()
        if (cost == null || hourly == null || cost < 0 || hourly < 0) {
            costInput.error = "Enter valid non-negative amounts"
            return
        }
        prefs.edit()
            .putFloat("cost_per_km", cost)
            .putFloat("minimum_hourly_net", hourly)
            .apply()
        android.widget.Toast.makeText(this, "Profit settings saved", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun refreshHistory() {
        val records = TripHistory.read(this)
        if (records.isEmpty()) {
            historyText.text = "No offers recorded yet. Detected offers with enough distance and time details will appear here."
            return
        }
        val totalNet = records.sumOf { it.estimatedNet }
        val totalFare = records.sumOf { it.fare }
        val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        val lines = records.take(15).map { item ->
            val date = formatter.format(Date(item.timestamp))
            val pickup = item.pickupKm?.let { "Pickup %.1f km • ".format(Locale.US, it) } ?: ""
            "$date\nR%.2f fare • %sTrip %.1f km • %.0f min\nEst. net R%.2f • %s".format(
                Locale.US, item.fare, pickup, item.tripKm, item.minutes,
                item.estimatedNet, item.decision
            )
        }
        historyText.text = "Recorded offers: ${records.size} (latest 15 shown)\nTotal recorded fares: R%.2f\nTotal estimated net: R%.2f\n\n%s".format(
            Locale.US, totalFare, totalNet, lines.joinToString("\n\n")
        )
    }
}
