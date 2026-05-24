package com.fynnix.app.core.llm

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService

/** Gates the on-device LLM behind a RAM check — loading a multi-GB model on a
 *  device with too little memory would OOM-kill the app rather than fail gracefully. */
object LlmCapability {
    fun isSupported(context: Context, spec: LlmModelSpec = DEFAULT_LLM_MODEL): Boolean {
        val am = context.getSystemService<ActivityManager>() ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalRamGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        return totalRamGb >= spec.minDeviceRamGb
    }
}
