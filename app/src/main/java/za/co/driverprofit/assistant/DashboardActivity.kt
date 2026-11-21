package za.co.driverprofit.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class DashboardActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val dashboardUrl = "https://whalynbomm-blip.github.io/driver-profit-assistant-v2/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(NativeDashboardBridge(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "https" && uri.host == "whalynbomm-blip.github.io") {
                    false
                } else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith(dashboardUrl)) {
                    view.evaluateJavascript("if(window.syncAndroidOffers){window.syncAndroidOffers();}", null)
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(dashboardUrl)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.evaluateJavascript("if(window.syncAndroidOffers){window.syncAndroidOffers();}", null)
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    inner class NativeDashboardBridge {
        @JavascriptInterface
        fun getDetectedOffers(): String {
            val rows = JSONArray()
            TripHistory.read(this@DashboardActivity).forEach { item ->
                rows.put(JSONObject().apply {
                    put("id", item.timestamp.toString())
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
            return rows.toString()
        }

        @JavascriptInterface
        fun getProfitSettings(): String {
            val prefs = getSharedPreferences("driver_profit", MODE_PRIVATE)
            return JSONObject().apply {
                put("costKm", prefs.getFloat("cost_per_km", 2.50f).toDouble())
                put("targetHour", prefs.getFloat("minimum_hourly_net", 280f).toDouble())
            }.toString()
        }

        @JavascriptInterface
        fun saveProfitSettings(costKm: Double, targetHour: Double) {
            if (costKm < 0 || targetHour < 0 || costKm.isNaN() || targetHour.isNaN()) return
            getSharedPreferences("driver_profit", MODE_PRIVATE).edit()
                .putFloat("cost_per_km", costKm.toFloat())
                .putFloat("minimum_hourly_net", targetHour.toFloat())
                .apply()
        }
    }
}
