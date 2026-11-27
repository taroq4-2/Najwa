package org.najwa.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Some vendor ROMs omit individual settings activities. Try a safe app-details fallback. */
fun openSystemSettings(context: Context,intent: Intent): Boolean =
    launchExternal(context,intent) || launchExternal(context,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))

fun launchExternal(context: Context,intent: Intent): Boolean = runCatching {
    context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true
}.getOrDefault(false)

/**
 * A random secret made on first use and kept on the device. Najwa's own shortcut and quick-settings tile carry it;
 * an intent without it (from another app) asks before silencing the phone.
 */
object LaunchToken {
    const val EXTRA="najwa_token"
    fun get(context: Context): String {
        val prefs=context.getSharedPreferences("security",Context.MODE_PRIVATE)
        prefs.getString("launch",null)?.let {return it}
        val token=java.util.UUID.randomUUID().toString()
        prefs.edit().putString("launch",token).apply()
        return token
    }
    fun matches(context: Context,intent: Intent?): Boolean=intent?.getStringExtra(EXTRA)?.let {it==get(context)}==true
}

const val CONTACT_EMAIL="contact@os73.com"
fun contactEmailIntent(category: String,device: String,details: String): Intent {
    val subject="نجوى · "+category
    val body="نوع الطلب: $category\nالجهاز: $device\n\nالتفاصيل:\n$details"
    return Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:$CONTACT_EMAIL?subject="+Uri.encode(subject)+"&body="+Uri.encode(body)))
        .putExtra(Intent.EXTRA_SUBJECT,subject).putExtra(Intent.EXTRA_TEXT,body)
}
