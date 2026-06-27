package com.hcqdrive.server.routes

import android.net.wifi.WifiManager
import android.content.Context
import com.hcqdrive.HcqDriveApp
import com.hcqdrive.auth.AuthService
import com.hcqdrive.server.HttpServer
import com.hcqdrive.service.HcqDriveService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val app: String,
    val version: String,
    val status: String,
    val uptime: Long,
    val connections: Int,
    val paired: Boolean,
    val deviceName: String? = null,
)

@Serializable
data class DiscoverResponse(
    val app: String,
    val version: String,
    val hostname: String,
    val port: Int,
    val address: String,
    val pairCode: String,
    val capabilities: List<String>,
)

@Serializable
data class HealthResponse(
    val ok: Boolean,
)

@Serializable
data class PairRequest(
    val code: String,
)

@Serializable
data class PairResponse(
    val token: String,
    val expiresIn: Long,
    val deviceName: String,
)

@Serializable
data class VerifyRequest(
    val token: String,
)

@Serializable
data class VerifyResponse(
    val valid: Boolean,
)

@Serializable
data class RevokeRequest(
    val token: String,
)

@Serializable
data class RevokeResponse(
    val revoked: Boolean,
)

@Serializable
data class PairCodeResponse(
    val code: String,
    val expiresAt: Long,
)

@Serializable
private data class ErrorResponse(val error: String, val code: String)

fun Route.configureAuthRoutes() {
    route("/api/auth") {
        get("/pair-code") {
            AuthService.generateCode()
            val active = AuthService.currentCodeAndExpiry()
            if (active == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("No active code", "NO_CODE"))
                return@get
            }
            val (code, expiresAt) = active
            call.respond(PairCodeResponse(code, expiresAt))
        }

        post("/pair") {
            val req = call.receive<PairRequest>()
            val ip = call.request.local.remoteHost
            val token = AuthService.verify(req.code, ip)
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired code", "INVALID_CODE"))
                return@post
            }
            call.respond(
                PairResponse(
                    token = token,
                    expiresIn = 30L * 24 * 60 * 60,
                    deviceName = android.os.Build.MODEL ?: "Android",
                )
            )
        }

        post("/verify") {
            val req = call.receive<VerifyRequest>()
            call.respond(VerifyResponse(valid = AuthService.validateToken(req.token)))
        }

        post("/revoke") {
            val req = call.receive<RevokeRequest>()
            call.respond(RevokeResponse(revoked = AuthService.revoke(req.token)))
        }
    }
}

fun Route.configureSystemRoutes() {
    get("/api/health") {
        call.respond(HealthResponse(ok = true))
    }
    get("/api/status") {
        call.respond(
            StatusResponse(
                app = HttpServer.appName(),
                version = HttpServer.version(),
                status = "running",
                uptime = HttpServer.uptimeSeconds(),
                connections = AuthService.pairedCount(),
                paired = AuthService.isPaired(),
                deviceName = android.os.Build.MODEL,
            )
        )
    }
    get("/api/discover") {
        val ctx = HcqDriveApp.appContext
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifi?.connectionInfo?.ipAddress ?: 0
        val ip = if (ipInt == 0) "0.0.0.0" else "%d.%d.%d.%d".format(ipInt and 0xFF, (ipInt shr 8) and 0xFF, (ipInt shr 16) and 0xFF, (ipInt shr 24) and 0xFF)
        val code = AuthService.currentCodeAndExpiry()?.first ?: AuthService.generateCode()
        call.respond(
            DiscoverResponse(
                app = HttpServer.appName(),
                version = HttpServer.version(),
                hostname = HcqDriveService.MDNS_HOSTNAME,
                port = HcqDriveService.DEFAULT_PORT,
                address = ip,
                pairCode = code,
                capabilities = listOf("webdav", "share", "thumbnails", "search", "zip"),
            )
        )
    }
}
