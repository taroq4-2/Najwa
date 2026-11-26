package org.najwa.app.platform

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rest of a backup beyond the settings: the khatma and reading position, adhkar progress, tasbeeh, the worship
 * records, reading preferences, the prayer log and the mosque profiles. It travels only in the file the person saves
 * with "Back up"; nothing leaves the phone otherwise. Restoring replaces each part that the file contains.
 */
object WorshipBackup {
    /** Private preference files, by name; only these are written back on restore. */
    val PREFERENCES=listOf("quran","adhkar","tasbeeh","worship","worship_settings","reading","plan")
    /** Private JSON files, by name. */
    val FILES=listOf("prayer-log.json","mosques.json")

    fun export(context: Context): JSONObject {
        val prefs=JSONObject()
        PREFERENCES.forEach {name->
            val values=JSONObject()
            context.getSharedPreferences(name,Context.MODE_PRIVATE).all.forEach {(key,value)->
                val typed=when(value) {
                    is Boolean -> JSONObject().put("t","b").put("v",value)
                    is Int -> JSONObject().put("t","i").put("v",value)
                    is Long -> JSONObject().put("t","l").put("v",value)
                    is Float -> JSONObject().put("t","f").put("v",value.toDouble())
                    is String -> JSONObject().put("t","s").put("v",value)
                    is Set<*> -> JSONObject().put("t","ss").put("v",JSONArray(value.filterIsInstance<String>()))
                    else -> null
                }
                if(typed!=null)values.put(key,typed)
            }
            prefs.put(name,values)
        }
        val files=JSONObject()
        FILES.forEach {name->runCatching {JsonFile(context,name).read()}.getOrNull()?.let {files.put(name,it)}}
        return JSONObject().put("version",1).put("preferences",prefs).put("files",files)
    }

    /** Writes back what [data] holds; parts it does not contain are left as they are. Returns false on a malformed file. */
    fun restore(context: Context,data: JSONObject): Boolean = runCatching {
        require(data.optInt("version",0)==1)
        val prefs=data.optJSONObject("preferences") ?: JSONObject()
        // Validate everything first, so a bad file changes nothing.
        val parsed=PREFERENCES.filter {prefs.has(it)}.associateWith {name->
            val values=prefs.getJSONObject(name)
            values.keys().asSequence().associateWith {key->
                val typed=values.getJSONObject(key)
                when(typed.getString("t")) {
                    "b" -> typed.getBoolean("v")
                    "i" -> typed.getInt("v")
                    "l" -> typed.getLong("v")
                    "f" -> typed.getDouble("v").toFloat()
                    "s" -> typed.getString("v")
                    "ss" -> typed.getJSONArray("v").let {a->(0 until a.length()).map {a.getString(it)}.toSet()}
                    else -> error("type")
                }
            }
        }
        parsed.forEach {(name,values)->
            val editor=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
            values.forEach {(key,v)->
                when(v) {
                    is Boolean -> editor.putBoolean(key,v)
                    is Int -> editor.putInt(key,v)
                    is Long -> editor.putLong(key,v)
                    is Float -> editor.putFloat(key,v)
                    is String -> editor.putString(key,v)
                    is Set<*> -> editor.putStringSet(key,v.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        }
        val files=data.optJSONObject("files") ?: JSONObject()
        FILES.filter {files.has(it)}.forEach {name->JsonFile(context,name).write(files.getJSONObject(name))}
        DayWidget.update(context)
        true
    }.getOrDefault(false)
}
