package za.co.driverprofit.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var readerButton: Button
    private lateinit var costInput: EditText
    private lateinit var hourlyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Driver Profit Assistant"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "Android offer reader • V2 companion"
            textSize = 15f
            setPadding(0, 8, 0, 24)
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
            text = "Minimum target net earnings per hour (R)"
            setPadding(0, 12, 0, 0)
        })
        hourlyInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getFloat("minimum_hourly_net", 100f).toString())
            hint = "e.g. 100"
        }
        root.addView(hourlyInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(Button(this).apply {
            text = "Save profit settings"
            setOnClickListener { saveSettings() }
        })
        root.addView(TextView(this).apply {
            text = "\nThe reader analyses visible offer text only when enabled. It does not tap Accept or Reject. Estimates depend on the information visible in the offer."
            textSize = 13f
            setPadding(0, 20, 0, 0)
        })
        setContentView(root)
        refreshReaderButton()
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
}
