package com.devobject.ffmpegtools.core.shizuku

import rikka.shizuku.Shizuku

object ShizukuProcessHelper {
    fun newProcess(command: Array<String>, environment: Array<String>?, workingDirectory: String?): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, command, environment, workingDirectory) as Process
    }
}
