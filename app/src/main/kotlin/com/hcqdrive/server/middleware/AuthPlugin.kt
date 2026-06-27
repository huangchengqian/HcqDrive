package com.hcqdrive.server.middleware

import com.hcqdrive.auth.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey

val AuthTokenKey: AttributeKey<String> = AttributeKey("HcqAuthToken")

val HcqAuthPlugin = createApplicationPlugin(name = "HcqAuthPlugin") {
    onCall { call ->
        val path = call.request.path()
        if (!path.startsWith("/api/")) return@onCall
        if (isPublic(path, call.request.httpMethod.value)) return@onCall
        val header = call.request.headers["Authorization"]
        val token = if (header != null && header.startsWith("Bearer")) {
            header.substringAfter("Bearer ").trim()
        } else null
        if (token == null || !AuthService.validateToken(token)) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("code" to "UNAUTHORIZED", "message" to "Invalid or missing token"),
            )
            return@onCall
        }
        call.attributes.put(AuthTokenKey, token)
    }
}

private val PUBLIC_PATHS: Set<String> = setOf(
    "/api/auth/pair",
    "/api/health",
    "/api/discover",
    "/api/status",
    "/api/file/thumb",
    "/api/file/raw",
)

private val PUBLIC_SHARE_PATHS: Set<String> = setOf(
    "/api/share/info",
)

private val PUBLIC_SHARE_TOKEN_PATTERN = Regex("^/api/share/[^/]+$")

private fun isPublic(path: String, method: String): Boolean {
    if (path in PUBLIC_PATHS) return true
    if (method == "GET" && path in PUBLIC_SHARE_PATHS) return true
    if (method == "GET" && PUBLIC_SHARE_TOKEN_PATTERN.matches(path)) return true
    return false
}
