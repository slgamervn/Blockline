package com.blockline.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences("blockline_settings", MODE_PRIVATE)
            .getString("app_language", "system")
        val localizedContext = if (language == "en" || language == "vi") {
            val config = Configuration(newBase.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BlocklineRoot() }
    }
}
