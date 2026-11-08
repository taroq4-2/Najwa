package org.najwa.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import org.najwa.app.ui.NajwaApp

class MainActivity: ComponentActivity() {
    private val resumed=mutableIntStateOf(0)
    private val homeRequest=mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { NajwaApp((application as NajwaApplication).runtime,resumed.intValue,homeRequest.intValue) } }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_home",false))homeRequest.intValue++}
    override fun onResume() { super.onResume(); (application as NajwaApplication).runtime.reconcile(); org.najwa.app.platform.PrayerWidget.updateAll(this); resumed.intValue++ }
}
