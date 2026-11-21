package za.co.driverprofit.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        root.addView(TextView(this).apply {
            text = "Driver Profit Assistant"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "V2 Android companion\n\nOffer reading works only after you enable the accessibility service. The assistant reads offer text for analysis and does not accept or reject trips."
            textSize = 16f
            setPadding(0, 24, 0, 24)
        })
        root.addView(Button(this).apply {
            text = "1. Enable offer reading"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        root.addView(Button(this).apply {
            text = "2. Allow floating assistant"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
        })
        root.addView(Button(this).apply {
            text = if (prefs.getBoolean("reader_enabled", true)) "Turn offer reading OFF" else "Turn offer reading ON"
            setOnClickListener {
                val enabled = !prefs.getBoolean("reader_enabled", true)
                prefs.edit().putBoolean("reader_enabled", enabled).apply()
                text = if (enabled) "Turn offer reading OFF" else "Turn offer reading ON"
            }
        })
        root.addView(TextView(this).apply {
            text = "\nPrivacy and safety: reading is local on this phone. No trip is automatically accepted or rejected."
            textSize = 14f
        })
        setContentView(root)
    }
}
