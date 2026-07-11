package com.devobject.ffmpegtools.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuManager {
    fun currentState(): ShizukuState = runCatching {
        if (!Shizuku.pingBinder()) return ShizukuState.SERVICE_NOT_RUNNING
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuState.AUTHORIZED
        } else {
            ShizukuState.UNAUTHORIZED
        }
    }.getOrElse { ShizukuState.NOT_INSTALLED }

    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        if (currentState() == ShizukuState.UNAUTHORIZED) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun versionName(): String = runCatching {
        if (Shizuku.pingBinder()) "API ${Shizuku.getVersion()}" else "服务未运行"
    }.getOrDefault("未安装或不可用")

    companion object {
        const val REQUEST_CODE = 60028
    }
}

enum class ShizukuState(val label: String) {
    NOT_INSTALLED("未安装或不可用"),
    SERVICE_NOT_RUNNING("服务未运行"),
    UNAUTHORIZED("未授权"),
    AUTHORIZED("已授权")
}
