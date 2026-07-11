package com.devobject.ffmpegtools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.app.FfmpegToolsApp
import com.devobject.ffmpegtools.ui.theme.FfmpegToolsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        setContent {
            val uiState by viewModel.state.collectAsState()
            FfmpegToolsTheme(themeMode = uiState.themeMode) {
                FfmpegToolsApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action ?: return
        val uri = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        viewModel.handleExternalSharedUri(uri)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizuku()
    }
}
