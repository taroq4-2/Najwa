package org.najwa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.najwa.app.R
private val LocalInterfaceLabels=staticCompositionLocalOf<Map<String,String>> {emptyMap()}

@Composable
internal fun InterfaceLanguage(content: @Composable ()->Unit) {
    val context=LocalContext.current
    val language=LocalConfiguration.current.locales[0].language
    val resources=context.resources
    val labels=remember(resources,language) {
        if(language=="en")resources.getStringArray(R.array.interface_keys).zip(resources.getStringArray(R.array.interface_labels)).toMap()
        else emptyMap()
    }
    CompositionLocalProvider(LocalInterfaceLabels provides labels,content=content)
}

@Composable
internal fun uiText(text: String): String = LocalInterfaceLabels.current[text] ?: text
