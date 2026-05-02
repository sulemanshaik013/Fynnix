package com.fynnix.app.core.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Launches a fire-and-forget coroutine that catches and logs any exception instead of
 * letting it propagate as an unhandled exception. A transient Room/IO failure inside a
 * ViewModel action (delete, update, insert) must not crash the whole app — the user
 * loses nothing by the action silently not completing that they wouldn't also lose from
 * a crash, and a crash is strictly worse.
 */
fun CoroutineScope.launchCatching(tag: String, block: suspend () -> Unit) {
    launch {
        runCatching { block() }.onFailure { Log.e(tag, "Unhandled error", it) }
    }
}
