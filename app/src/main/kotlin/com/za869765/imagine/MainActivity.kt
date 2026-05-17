package com.za869765.imagine

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.nav.ImagineRoot
import com.za869765.imagine.ui.theme.ImagineTheme

fun applyScreenshotFlag(activity: Activity, enabled: Boolean) {
    if (enabled) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = SecurePrefs.get(this)
        applyScreenshotFlag(this, prefs.preventScreenshots)

        enableEdgeToEdge()
        setContent {
            ImagineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ImagineRoot()
                }
            }
        }
    }
}
