package com.devobject.ffmpegtools

import android.content.Intent
import android.net.Uri
import android.os.Build
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
        setContent {
            val uiState by viewModel.state.collectAsState()
            FfmpegToolsTheme(themeMode = uiState.themeMode) {
                FfmpegToolsApp(viewModel = viewModel)
            }
        }
        // 在 Compose 初始化完成后再处理外部传入的 URI，避免状态丢失
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action ?: return
        val uri = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: return
        viewModel.handleExternalSharedUri(uri)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizuku()
    }
}
