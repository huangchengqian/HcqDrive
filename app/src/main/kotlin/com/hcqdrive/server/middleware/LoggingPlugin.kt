package com.hcqdrive.server.middleware

import android.util.Log
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

private val StartTimeKey: AttributeKey<Long> = AttributeKey("HcqLogStart")

val HcqLoggingPlugin = createApplicationPlugin(name = "HcqLoggingPlugin") {
    onCall { call ->
        call.attributes.put(StartTimeKey, System.nanoTime())
    }
    onCallRespond { call, _ ->
        val start = call.attributes.getOrNull(StartTimeKey) ?: return@onCallRespond
        val durationMs = (System.nanoTime() - start) / 1_000_000
        Log.i(
            "HcqDrive",
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value} (${durationMs}ms)",
        )
    }
}
