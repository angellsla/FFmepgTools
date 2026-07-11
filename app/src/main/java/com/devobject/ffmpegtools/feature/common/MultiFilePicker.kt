package com.devobject.ffmpegtools.feature.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MultiFilePicker(
    label: String,
    uris: List<Uri>,
    onUrisChange: (List<Uri>) -> Unit,
    mimeType: String = "*/*"
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) {
            onUrisChange(uris + picked)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppBody(text = label, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        AppButton(onClick = { launcher.launch(arrayOf(mimeType)) }) { AppBody("选择多个文件") }

        uris.forEachIndexed { index, uri ->
            MultiFileItem(
                uri = uri,
                index = index,
                total = uris.size,
                onMoveUp = {
                    if (index > 0) {
                        val next = uris.toMutableList().apply { add(index - 1, removeAt(index)) }
                        onUrisChange(next)
                    }
                },
                onMoveDown = {
                    if (index < uris.size - 1) {
                        val next = uris.toMutableList().apply { add(index + 1, removeAt(index)) }
                        onUrisChange(next)
                    }
                },
                onDelete = { onUrisChange(uris.filterIndexed { i, _ -> i != index }) }
            )
        }
    }
}

@Composable
private fun MultiFileItem(
    uri: Uri,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val displayName = remember(uri) { queryDisplayName(context, uri) }

    AppClickableCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(Icons.Default.FileOpen, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceContainerVariant)
            AppBody(
                text = "${index + 1}. $displayName",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppIconButton(onClick = onMoveUp, enabled = index > 0) {
                AppIcon(Icons.Default.ArrowUpward, contentDescription = "上移")
            }
            AppIconButton(onClick = onMoveDown, enabled = index < total - 1) {
                AppIcon(Icons.Default.ArrowDownward, contentDescription = "下移")
            }
            AppIconButton(onClick = onDelete) {
                AppIcon(Icons.Default.Delete, contentDescription = "删除", tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "未知文件"
