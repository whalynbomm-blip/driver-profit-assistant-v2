package za.co.driverprofit.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TripRecord(
    val timestamp: Long,
    val fare: Double,
    val pickupKm: Double?,
    val tripKm: Double,
    val minutes: Double,
    val totalKm: Double,
    val estimatedCost: Double,
    val estimatedNet: Double,
    val netPerHour: Double,
    val decision: String
)

object TripHistory {
    private const val PREFS = "driver_profit"
    private const val KEY = "trip_history"
    private const val MAX_RECORDS = 100

    fun add(context: Context, record: TripRecord) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val records = read(context).toMutableList()
        val duplicate = records.any {
            kotlin.math.abs(it.timestamp - record.timestamp) < 10_000L &&
                it.fare == record.fare && it.totalKm == record.totalKm && it.minutes == record.minutes
        }
        if (duplicate) return
        records.add(0, record)
        val json = JSONArray()
        records.take(MAX_RECORDS).forEach { item ->
            json.put(JSONObject().apply {
                put("timestamp", item.timestamp)
                put("fare", item.fare)
                put("pickupKm", item.pickupKm ?: JSONObject.NULL)
                put("tripKm", item.tripKm)
                put("minutes", item.minutes)
                put("totalKm", item.totalKm)
                put("estimatedCost", item.estimatedCost)
                put("estimatedNet", item.estimatedNet)
                put("netPerHour", item.netPerHour)
                put("decision", item.decision)
            })
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun read(context: Context): List<TripRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                TripRecord(
                    timestamp = item.optLong("timestamp"),
                    fare = item.optDouble("fare"),
                    pickupKm = if (item.isNull("pickupKm")) null else item.optDouble("pickupKm"),
                    tripKm = item.optDouble("tripKm"),
                    minutes = item.optDouble("minutes"),
                    totalKm = item.optDouble("totalKm"),
                    estimatedCost = item.optDouble("estimatedCost"),
                    estimatedNet = item.optDouble("estimatedNet"),
                    netPerHour = item.optDouble("netPerHour"),
                    decision = item.optString("decision", "CHECK DETAILS")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
