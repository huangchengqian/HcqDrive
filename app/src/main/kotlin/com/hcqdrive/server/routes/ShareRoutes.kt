package com.hcqdrive.server.routes

import com.hcqdrive.fs.FileService
import com.hcqdrive.share.ShareService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.io.FileNotFoundException
import java.net.URLEncoder
import java.nio.channels.Channels
import java.nio.file.Files

@Serializable
data class ShareCreateRequest(
    val path: String,
    val ttlSeconds: Long? = null,
    val maxDownloads: Int? = null,
    val password: String? = null,
)

@Serializable
data class ShareRevokeRequest(val token: String)

@Serializable
data class ShareListResponse(val shares: List<com.hcqdrive.share.ShareDto>)

@Serializable
data class ShareOkResponse(val ok: Boolean)

fun Route.configureShareRoutes() {
    route("/api/share") {

        // Management endpoints (create / list / revoke) require pairing.
        // The read endpoint (/{token}) and the info endpoint are made public
        // by the auth plugin so unauthenticated consumers can resolve a link.

        post("/create") {
            val req = call.receive<ShareCreateRequest>()
            val safe = FileService.get().sanitizePath(req.path)
            val dto = ShareService.create(safe, req.ttlSeconds, req.maxDownloads, req.password)
            call.respond(HttpStatusCode.Created, dto)
        }

        get("/list") {
            call.respond(ShareListResponse(ShareService.list()))
        }

        post("/revoke") {
            val req = call.receive<ShareRevokeRequest>()
            val ok = ShareService.revoke(req.token)
            if (ok) {
                call.respond(ShareOkResponse(ok = true))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Share not found", "code" to "SHARE_NOT_FOUND"),
                )
            }
        }

        get("/info") {
            val token = call.request.queryParameters["token"]
                ?: throw IllegalArgumentException("token required")
            val share = ShareService.list().firstOrNull { it.token == token }
            if (share == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Share not found", "code" to "SHARE_NOT_FOUND"),
                )
            } else {
                call.respond(share)
            }
        }

        get("/{token}") {
            val token = call.parameters["token"]
                ?: throw IllegalArgumentException("token required")
            val password = call.request.queryParameters["password"]
            val result = ShareService.resolve(token, password)

            when (result) {
                is ShareService.ResolveResult.NotFound -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Share not found", "code" to "SHARE_NOT_FOUND"),
                    )
                }
                is ShareService.ResolveResult.Expired -> {
                    call.respond(
                        HttpStatusCode.Gone,
                        mapOf("error" to "Share link expired", "code" to "SHARE_EXPIRED"),
                    )
                }
                is ShareService.ResolveResult.MaxDownloadsReached -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Download limit reached", "code" to "SHARE_LIMIT_REACHED"),
                    )
                }
                is ShareService.ResolveResult.PasswordRequired -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Password required", "code" to "SHARE_PASSWORD_REQUIRED"),
                    )
                }
                is ShareService.ResolveResult.InvalidPassword -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid password", "code" to "SHARE_INVALID_PASSWORD"),
                    )
                }
                is ShareService.ResolveResult.Success -> streamSharedFile(call, result.token, result.path)
            }
        }
    }
}

private suspend fun streamSharedFile(
    call: io.ktor.server.application.ApplicationCall,
    token: String,
    path: String,
) {
    val entry = runCatching { FileService.get().stat(path) }.getOrNull()
    if (entry == null || entry.kind == "directory") {
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to "File not found", "code" to "NOT_FOUND"),
        )
        return
    }

    ShareService.recordDownload(token)

    val fileName = entry.name
    val mime = FileService.guessMime(fileName) ?: "application/octet-stream"

    val absoluteFile = FileService.get().absoluteFile(path)
    if (absoluteFile != null && absoluteFile.isFile) {
        streamFileRange(call, absoluteFile, mime, fileName, isDownload = true)
        return
    }

    // MediaStore path: stream the input stream in one shot. Range support is
    // best-effort here; large media downloads remain functional but cannot be
    // resumed, which matches the behaviour of the regular raw endpoint.
    try {
        val (input, totalSize) = FileService.get().openInputStream(path)
        call.response.header(HttpHeaders.AcceptRanges, "none")
        call.response.header(HttpHeaders.ContentDisposition, attachmentDisposition(fileName).toString())
        call.response.header(HttpHeaders.ContentLength, totalSize.toString())
        call.response.header("X-Content-Type-Options", "nosniff")
        call.respondOutputStream(ContentType.parse(mime)) {
            input.use { src ->
                val buf = ByteArray(64 * 1024)
                var remaining = totalSize
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = src.read(buf, 0, toRead)
                    if (read <= 0) break
                    write(buf, 0, read)
                    remaining -= read
                }
            }
        }
    } catch (_: FileNotFoundException) {
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to "File not found", "code" to "NOT_FOUND"),
        )
    }
}

private suspend fun streamFileRange(
    call: io.ktor.server.application.ApplicationCall,
    file: java.io.File,
    mime: String,
    fileName: String,
    isDownload: Boolean,
) {
    val totalSize = file.length()
    val rangeHeader = call.shareRangeHeader()
    val (start, end) = parseShareRange(rangeHeader, totalSize)
    val length = end - start + 1
    if (rangeHeader != null) {
        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalSize")
        call.response.status(HttpStatusCode.PartialContent)
    } else {
        call.response.status(HttpStatusCode.OK)
    }
    call.response.header(HttpHeaders.AcceptRanges, "bytes")
    call.response.header(HttpHeaders.ContentDisposition, attachmentDisposition(fileName).toString())
    call.response.header(HttpHeaders.ContentLength, length.toString())
    call.response.header("X-Content-Type-Options", "nosniff")

    call.respondOutputStream(ContentType.parse(mime)) {
        Files.newByteChannel(file.toPath()).use { channel ->
            channel.position(start)
            Channels.newInputStream(channel).use { input ->
                val buf = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = input.read(buf, 0, toRead)
                    if (read <= 0) break
                    write(buf, 0, read)
                    remaining -= read
                }
            }
        }
    }
}

private fun attachmentDisposition(fileName: String): ContentDisposition {
    val ascii = fileName.all { it.code in 0x20..0x7E && it != '"' }
    val encoded = URLEncoder.encode(fileName, Charsets.UTF_8.name()).replace("+", "%20")
    val value = if (ascii) fileName else "utf-8''$encoded"
    return ContentDisposition.Attachment.withParameter(
        ContentDisposition.Parameters.FileName, value,
    )
}
