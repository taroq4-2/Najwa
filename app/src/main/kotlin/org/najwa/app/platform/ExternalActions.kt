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

const val CONTACT_EMAIL="contact@os73.com"
fun contactEmailIntent(category: String,device: String,details: String): Intent {
    val subject="نجوى · "+category
    val body="نوع الطلب: $category\nالجهاز: $device\n\nالتفاصيل:\n$details"
    return Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:$CONTACT_EMAIL?subject="+Uri.encode(subject)+"&body="+Uri.encode(body)))
        .putExtra(Intent.EXTRA_SUBJECT,subject).putExtra(Intent.EXTRA_TEXT,body)
}
