package org.najwa.app.platform

import android.content.Context
import org.json.JSONObject
import org.najwa.core.City

/** The city chosen before a trip, so coming home can switch back in one tap. */
object TravelHome {
    private fun prefs(context: Context)=context.getSharedPreferences("plan",Context.MODE_PRIVATE)
    fun save(context: Context,c: City)=prefs(context).edit().putString("homeCity",JSONObject().put("name",c.name).put("lat",c.latitude).put("lon",c.longitude)
        .put("zone",c.zone).put("country",c.countryCode ?: "").put("catalogId",c.catalogId ?: "").toString()).apply()
    fun read(context: Context): City?=prefs(context).getString("homeCity",null)?.let {s->runCatching {
        val j=JSONObject(s)
        City(j.getString("name"),j.getDouble("lat"),j.getDouble("lon"),j.getString("zone"),j.optString("country").takeIf {it.isNotBlank()},j.optString("catalogId").takeIf {it.isNotBlank()})
    }.getOrNull()}
    fun clear(context: Context)=prefs(context).edit().remove("homeCity").apply()
}
