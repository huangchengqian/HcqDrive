package com.hcqdrive.server.routes

import com.hcqdrive.auth.AuthService
import com.hcqdrive.fs.FileEntry
import com.hcqdrive.fs.FileService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * WebDAV (RFC 4918) entry point. HTTP Basic with user `hcqdrive` and password =
 * the current pairing code; `/dav/<rest>` maps to local `<rest>` via
 * [FileService.sanitizePath].
 */
private val PROPFIND = HttpMethod("PROPFIND")
private val MKCOL = HttpMethod("MKCOL")
private val M_COPY = HttpMethod("COPY")
private val M_MOVE = HttpMethod("MOVE")
private val M_LOCK = HttpMethod("LOCK")
private val M_UNLOCK = HttpMethod("UNLOCK")

private const val ROOT_DISPLAY_NAME = "HcqDrive"
private const val REALM = "HcqDrive"

fun Route.configureDavRoutes() {
    route("/dav") {
        handle {
            val auth = call.request.header(HttpHeaders.Authorization)
            if (auth == null || !validateDavAuth(auth)) {
                call.response.header(HttpHeaders.WWWAuthenticate, """Basic realm="$REALM"""")
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                return@handle
            }
            val rawPath = call.request.path().removePrefix("/dav").let { if (it.isEmpty()) "/" else it }
            val davPath = URLDecoder.decode(rawPath, "UTF-8")
            val method = call.request.httpMethod
            when (method) {
                HttpMethod.Options -> call.handleDavOptions()
                PROPFIND -> call.handleDavPropfind(davPath)
                HttpMethod.Get, HttpMethod.Head -> call.handleDavGet(davPath)
                HttpMethod.Put -> call.handleDavPut(davPath)
                MKCOL -> call.handleDavMkcol(davPath)
                HttpMethod.Delete -> call.handleDavDelete(davPath)
                M_COPY -> call.handleDavCopy(davPath)
                M_MOVE -> call.handleDavMove(davPath)
                M_LOCK, M_UNLOCK -> call.respondDavStatus(HttpStatusCode.OK)
                else -> call.respondText("Method not allowed", status = HttpStatusCode.MethodNotAllowed)
            }
        }
    }
}

private fun validateDavAuth(header: String): Boolean {
    if (!header.startsWith("Basic ")) return false
    val decoded = try {
        String(java.util.Base64.getDecoder().decode(header.removePrefix("Basic ")), Charsets.UTF_8)
    } catch (_: Exception) { return false }
    val parts = decoded.split(":", limit = 2)
    val user = parts[0]
    val pass = parts.getOrNull(1) ?: ""
    val pairCode = AuthService.currentCodeAndExpiry()?.first ?: return false
    return user == "hcqdrive" && pass == pairCode
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavOptions() {
    response.header("DAV", "1, 2")
    response.header(
        "Allow",
        "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL, COPY, MOVE, LOCK, UNLOCK",
    )
    response.header(HttpHeaders.ContentLength, "0")
    respondText("")
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavPropfind(davPath: String) {
    val depth = request.header("Depth") ?: "1"
    val effectiveDepth = if (depth.equals("0", ignoreCase = true)) 0 else 1
    val fs = FileService.get()
    val sanitized = fs.sanitizePath(davPath.ifEmpty { "/" })
    val entry = fs.stat(sanitized) ?: run {
        respondText("Not found", status = HttpStatusCode.NotFound)
        return@handleDavPropfind
    }
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="utf-8"?>""")
    sb.append("""<multistatus xmlns="DAV:">""")
    appendPropResponse(sb, davHrefFor(sanitized, entry), entry)
    if (effectiveDepth >= 1 && entry.kind == "directory") {
        for (e in fs.list(sanitized).entries) {
            appendPropResponse(sb, davHrefFor(e.path, e), e)
        }
    }
    sb.append("</multistatus>")
    response.header(HttpHeaders.ContentType, "application/xml; charset=\"utf-8\"")
    response.status(HttpStatusCode.MultiStatus)
    respondText(sb.toString())
}

private fun davHrefFor(displayPath: String, e: FileEntry): String {
    val normalized = if (displayPath == "/") "" else displayPath
    val withSlash = if (e.kind == "directory" && !normalized.endsWith("/")) "$normalized/" else normalized
    return "/dav$withSlash"
}

private fun appendPropResponse(sb: StringBuilder, href: String, e: FileEntry) {
    val h = xmlEscape(href)
    val n = xmlEscape(if (href == "/dav/" || href == "/dav") ROOT_DISPLAY_NAME else e.name)
    val isDir = e.kind == "directory"
    val mime = if (isDir) "httpd/unix-directory" else (e.mime ?: "application/octet-stream")
    val size = if (isDir) 0L else e.size
    val ts = if (e.modifiedAt > 0) e.modifiedAt else System.currentTimeMillis()
    val created = if ((e.createdAt ?: 0L) > 0) (e.createdAt ?: 0L) else ts
    val modified = rfc1123(ts)
    val createdIso = iso8601(created)
    sb.append("<response>")
    sb.append("<href>").append(h).append("</href>")
    sb.append("<propstat>")
    sb.append("<prop>")
    sb.append("<displayname>").append(n).append("</displayname>")
    sb.append("<resourcetype>")
    if (isDir) sb.append("<collection/>")
    sb.append("</resourcetype>")
    sb.append("<getcontenttype>").append(xmlEscape(mime)).append("</getcontenttype>")
    sb.append("<getcontentlength>").append(size).append("</getcontentlength>")
    sb.append("<getlastmodified>").append(modified).append("</getlastmodified>")
    sb.append("<creationdate>").append(createdIso).append("</creationdate>")
    sb.append("<getetag>\"")
        .append(ts.toString(16))
        .append("-").append(size)
        .append("\"</getetag>")
    sb.append("</prop>")
    sb.append("<status>HTTP/1.1 200 OK</status>")
    sb.append("</propstat>")
    sb.append("</response>")
}

private fun xmlEscape(input: String): String {
    val sb = StringBuilder(input.length + 8)
    for (ch in input) {
        when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&apos;")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun rfc1123(millis: Long): String {
    val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }
    return f.format(Date(millis))
}

private fun iso8601(millis: Long): String {
    val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return f.format(Date(millis))
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavGet(davPath: String) {
    val fs = FileService.get()
    val sanitized = fs.sanitizePath(davPath.ifEmpty { "/" })
    val entry = fs.stat(sanitized)
    if (entry == null || entry.kind == "directory") {
        respondText("Not found", status = HttpStatusCode.NotFound)
        return
    }
    val absolute = fs.absoluteFile(sanitized)
    if (absolute == null || !absolute.isFile) {
        respondText("Not found", status = HttpStatusCode.NotFound)
        return
    }
    val totalSize = absolute.length()
    val mime = FileService.guessMime(entry.name) ?: "application/octet-stream"
    response.header("X-Content-Type-Options", "nosniff")
    response.header(HttpHeaders.AcceptRanges, "bytes")
    val rangeHeader = request.header(HttpHeaders.Range)
    val (start, end) = parseRange(rangeHeader, totalSize)
    if (start > 0 || end < totalSize - 1) {
        val length = (end - start + 1).toInt()
        response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalSize")
        response.header(HttpHeaders.ContentLength, length.toString())
        response.status(HttpStatusCode.PartialContent)
        val bytes = absolute.inputStream().use { input ->
            input.skip(start)
            input.readNBytes(length)
        }
        respondBytes(bytes, ContentType.parse(mime))
    } else {
        response.header(HttpHeaders.ContentLength, totalSize.toString())
        respondBytes(absolute.readBytes(), ContentType.parse(mime))
    }
}

private fun parseRange(rangeHeader: String?, totalSize: Long): Pair<Long, Long> {
    if (rangeHeader.isNullOrBlank() || totalSize <= 0) {
        return 0L to (totalSize - 1).coerceAtLeast(0L)
    }
    val match = Regex("""bytes=(\d*)-(\d*)""").matchEntire(rangeHeader.trim())
        ?: return 0L to (totalSize - 1)
    val (s, e) = match.destructured
    val start = s.toLongOrNull() ?: 0L
    val end = e.toLongOrNull() ?: (totalSize - 1)
    if (start > end || start >= totalSize) return 0L to (totalSize - 1)
    return start to end.coerceAtMost(totalSize - 1)
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavPut(davPath: String) {
    val fs = FileService.get()
    val sanitized = fs.sanitizePath(davPath.ifEmpty { "/" })
    if (sanitized == "/") {
        respondDavStatus(HttpStatusCode.MethodNotAllowed, "Cannot PUT to root")
        return
    }
    val existed = try { fs.stat(sanitized) != null } catch (_: Exception) { false }
    val data = receive<ByteArray>()
    try {
        fs.write(sanitized, data, append = false)
    } catch (_: IllegalArgumentException) {
        respondDavStatus(HttpStatusCode.Forbidden, "PUT not supported on virtual media path")
        return
    } catch (e: Exception) {
        respondDavStatus(HttpStatusCode.InternalServerError, e.message)
        return
    }
    if (existed) respondDavStatus(HttpStatusCode.NoContent) else respondDavStatus(HttpStatusCode.Created)
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavMkcol(davPath: String) {
    val fs = FileService.get()
    val sanitized = fs.sanitizePath(davPath.ifEmpty { "/" })
    try {
        fs.mkdir(sanitized)
        respondDavStatus(HttpStatusCode.Created)
    } catch (e: Exception) {
        respondDavStatus(HttpStatusCode.Conflict, e.message)
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavDelete(davPath: String) {
    val fs = FileService.get()
    val sanitized = fs.sanitizePath(davPath.ifEmpty { "/" })
    try {
        fs.delete(sanitized)
        respondDavStatus(HttpStatusCode.NoContent)
    } catch (e: Exception) {
        respondDavStatus(HttpStatusCode.NotFound, e.message)
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavCopy(davPath: String) {
    val destHeader = request.header("Destination")
        ?: run { respondDavStatus(HttpStatusCode.BadRequest, "Destination header required"); return }
    val overwrite = (request.header("Overwrite") ?: "T").equals("T", ignoreCase = true)
    val destPath = extractDavDestination(destHeader)
    if (destPath == null) {
        respondDavStatus(HttpStatusCode.BadRequest, "Invalid Destination header")
        return
    }
    val fs = FileService.get()
    val src = fs.sanitizePath(davPath.ifEmpty { "/" })
    val dst = fs.sanitizePath(destPath)
    if (src == dst) {
        respondDavStatus(HttpStatusCode.Forbidden, "Source and destination are identical")
        return
    }
    try {
        if (!fs.exists(src)) {
            respondDavStatus(HttpStatusCode.NotFound, "Source not found")
            return
        }
        if (fs.exists(dst)) {
            if (!overwrite) {
                respondDavStatus(HttpStatusCode.PreconditionFailed, "Destination exists and Overwrite is F")
                return
            }
            fs.delete(dst)
        }
        copyRecursive(fs, src, dst)
        respondDavStatus(HttpStatusCode.Created)
    } catch (_: IllegalArgumentException) {
        respondDavStatus(HttpStatusCode.Forbidden, "COPY not supported on virtual media path")
    } catch (e: Exception) {
        respondDavStatus(HttpStatusCode.InternalServerError, e.message)
    }
}

private fun copyRecursive(fs: FileService, src: String, dst: String) {
    val srcResolved = fs.resolvePath(src)
    val dstResolved = fs.resolvePath(dst)
    require(srcResolved is FileService.ResolvedPath.Explicit) { "COPY source must be explicit: $src" }
    require(dstResolved is FileService.ResolvedPath.Explicit) { "COPY destination must be explicit: $dst" }
    val srcFile = srcResolved.file
    dstResolved.file.parentFile?.mkdirs()
    if (srcFile.isDirectory) {
        dstResolved.file.mkdirs()
        val children = srcFile.listFiles() ?: emptyArray()
        for (child in children) {
            val childSrc = "${src.trimEnd('/')}/${child.name}"
            val childDst = "${dst.trimEnd('/')}/${child.name}"
            copyRecursive(fs, childSrc, childDst)
        }
    } else {
        srcFile.copyTo(dstResolved.file, overwrite = true)
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.handleDavMove(davPath: String) {
    val destHeader = request.header("Destination")
        ?: run { respondDavStatus(HttpStatusCode.BadRequest, "Destination header required"); return }
    val overwrite = (request.header("Overwrite") ?: "T").equals("T", ignoreCase = true)
    val destPath = extractDavDestination(destHeader)
    if (destPath == null) {
        respondDavStatus(HttpStatusCode.BadRequest, "Invalid Destination header")
        return
    }
    val fs = FileService.get()
    val src = fs.sanitizePath(davPath.ifEmpty { "/" })
    val dst = fs.sanitizePath(destPath)
    if (src == dst) {
        respondDavStatus(HttpStatusCode.Forbidden, "Source and destination are identical")
        return
    }
    try {
        if (!fs.exists(src)) {
            respondDavStatus(HttpStatusCode.NotFound, "Source not found")
            return
        }
        if (fs.exists(dst)) {
            if (!overwrite) {
                respondDavStatus(HttpStatusCode.PreconditionFailed, "Destination exists and Overwrite is F")
                return
            }
            fs.delete(dst)
        }
        fs.move(src, dst)
        respondDavStatus(HttpStatusCode.Created)
    } catch (_: IllegalArgumentException) {
        respondDavStatus(HttpStatusCode.Forbidden, "MOVE not supported on virtual media path")
    } catch (e: Exception) {
        respondDavStatus(HttpStatusCode.InternalServerError, e.message)
    }
}

private fun extractDavDestination(header: String): String? {
    return try {
        val uri = URI(header)
        val raw = uri.rawPath ?: uri.path ?: return null
        val decoded = URLDecoder.decode(raw, "UTF-8")
        decoded.removePrefix("/dav").let { if (it.isEmpty()) "/" else it }
    } catch (_: Exception) {
        null
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondDavStatus(
    status: HttpStatusCode,
    message: String? = null,
) {
    if (message == null) {
        respondText("", status = status)
    } else {
        respondText(message, status = status)
    }
}
