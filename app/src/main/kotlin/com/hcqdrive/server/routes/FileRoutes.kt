package com.hcqdrive.server.routes

import com.hcqdrive.fs.FileService
import com.hcqdrive.fs.UploadCancelRequest
import com.hcqdrive.fs.UploadChunkResponse
import com.hcqdrive.fs.UploadCompleteRequest
import com.hcqdrive.fs.UploadInitRequest
import com.hcqdrive.fs.UploadInitResponse
import com.hcqdrive.media.MediaService
import com.hcqdrive.transfer.UploadService
import com.hcqdrive.transfer.ZipService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder

fun Route.configureFileRoutes() {
    route("/api/file") {
        get("/raw") {
            val path = call.request.queryParameters["path"] ?: throw IllegalArgumentException("path required")
            val isDownload = call.request.queryParameters["download"] == "1"
            val fileName = path.substringAfterLast('/')
            val mime = FileService.guessMime(fileName) ?: "application/octet-stream"

            // Try direct file access first (internal storage / SD card)
            val file = FileService.get().absoluteFile(path)
            if (file != null && file.isFile) {
                val totalSize = file.length()
                val rangeHeader = call.request.headers[HttpHeaders.Range]
                val (start, end) = parseRange(rangeHeader, totalSize)
                val length = end - start + 1
                if (rangeHeader != null) {
                    call.response.header(HttpHeaders.ContentRange, "bytes ${start}-${end}/${totalSize}")
                    call.response.status(HttpStatusCode.PartialContent)
                } else {
                    call.response.status(HttpStatusCode.OK)
                }
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentDisposition,
                    dispositionHeader(fileName, isDownload).toString())
                call.response.header(HttpHeaders.ContentLength, length.toString())
                call.response.header("X-Content-Type-Options", "nosniff")
                call.respondOutputStream(ContentType.parse(mime)) {
                    RandomAccessFile(file, "r").use { raf ->
                        val channel = raf.channel
                        channel.position(start)
                        val buf = ByteArray(64 * 1024)
                        var remaining = length
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val read = channel.read(java.nio.ByteBuffer.wrap(buf, 0, toRead))
                            if (read <= 0) break
                            write(buf, 0, read)
                            remaining -= read
                        }
                    }
                }
                return@get
            }

            // Fallback: MediaStore path (DCIM/Pictures) via ContentResolver
            try {
                val (inputStream, totalSize) = FileService.get().openInputStream(path)
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentDisposition,
                    dispositionHeader(fileName, isDownload).toString())
                call.response.header(HttpHeaders.ContentLength, totalSize.toString())
                call.response.header("X-Content-Type-Options", "nosniff")
                call.respondOutputStream(ContentType.parse(mime)) {
                    inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var remaining = totalSize
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val read = input.read(buf, 0, toRead)
                            if (read <= 0) break
                            write(buf, 0, read)
                            remaining -= read
                        }
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.NotFound,
                    mapOf("error" to "File not found: ${e.message}", "code" to "NOT_FOUND"))
            }
        }

        get("/thumb") {
            val path = call.request.queryParameters["path"] ?: throw IllegalArgumentException("path required")
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 256
            try {
                val bytes = MediaService.get().generateThumbnail(path, size.coerceIn(32, 1024))
                if (bytes == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Thumbnail unavailable", "code" to "NOT_FOUND"))
                    return@get
                }
                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                call.respondBytes(bytes, ContentType.Image.JPEG)
            } catch (e: java.io.FileNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found", "code" to "NOT_FOUND"))
            }
        }

        get("/zip") {
            val pathsParam = call.request.queryParameters["paths"] ?: ""
            val entries = pathsParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (entries.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No paths provided", "code" to "BAD_REQUEST"))
                return@get
            }
            val zipName = "hcqdrive-${System.currentTimeMillis()}.zip"
            val disposition = ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, zipName
            )
            call.response.header(HttpHeaders.ContentDisposition, disposition.toString())
            call.response.header("X-Content-Type-Options", "nosniff")
            call.respondOutputStream(ContentType.Application.Zip) {
                ZipService.createZipStream(entries, this)
            }
        }

        post("/upload") {
            val path = call.request.queryParameters["path"] ?: "/"
            val sanitized = FileService.get().sanitizePath(path)
            val targetFile = FileService.get().absoluteFile(sanitized)
                ?: throw IllegalArgumentException("Upload target must be an explicit directory: $sanitized")
            val targetDir = if (targetFile.isDirectory) targetFile
            else targetFile.parentFile ?: throw IllegalArgumentException("Cannot resolve target directory")
            targetDir.mkdirs()
            val name = call.request.queryParameters["name"] ?: "upload-${System.currentTimeMillis()}"
            val safeName = name.replace('/', '_').replace('\\', '_')
            val data = call.receive<ByteArray>()
            val out = File(targetDir, safeName)
            out.writeBytes(data)
            call.respond(HttpStatusCode.Created, mapOf("ok" to true, "name" to safeName, "size" to out.length()))
        }

        post("/upload/init") {
            val req = call.receive<UploadInitRequest>()
            val targetDir = resolveUploadDir(req.path)
            val uploadId = UploadService.initSession(targetDir, req.filename, req.totalSize, req.totalChunks)
            call.respond(
                UploadInitResponse(
                    uploadId = uploadId,
                    chunkSize = UploadService.CHUNK_SIZE,
                )
            )
        }

        post("/upload/chunk") {
            val uploadId = call.request.queryParameters["uploadId"]
                ?: throw IllegalArgumentException("uploadId required")
            val index = call.request.queryParameters["index"]?.toIntOrNull()
                ?: throw IllegalArgumentException("index required")
            val total = call.request.queryParameters["total"]?.toIntOrNull() ?: 0
            val data = call.receive<ByteArray>()
            val received = withContext(Dispatchers.IO) {
                UploadService.writeChunk(uploadId, index, data)
            }
            call.respond(UploadChunkResponse(received = received, chunk = index, total = total))
        }

        post("/upload/complete") {
            val req = call.receive<UploadCompleteRequest>()
            val file = UploadService.complete(req.uploadId)
            call.respond(
                HttpStatusCode.OK,
                mapOf("ok" to true, "path" to file.absolutePath, "size" to file.length()),
            )
        }

        post("/upload/cancel") {
            val req = call.receive<UploadCancelRequest>()
            UploadService.cancel(req.uploadId)
            call.respond(mapOf("ok" to true))
        }

        get("/subtitle") {
            val path = call.request.queryParameters["path"] ?: throw IllegalArgumentException("path required")
            val base = path.replace(Regex("\\.[^./]+$"), "")
            val candidates = listOf("$base.srt", "$base.vtt", "$base.ass", "$base.ssa")
            for ((idx, candidate) in candidates.withIndex()) {
                try {
                    val (stream, _) = FileService.get().openInputStream(candidate)
                    val ext = candidate.substringAfterLast('.').lowercase()
                    val body = stream.bufferedReader().use { it.readText() }
                    val vtt = when (ext) {
                        "srt" -> srtToVtt(body)
                        "ass", "ssa" -> assToVtt(body)
                        else -> body
                    }
                    call.response.header(HttpHeaders.ContentType, "text/vtt; charset=utf-8")
                    call.response.header("X-Subtitle-Source", ext)
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=60")
                    call.respondText(vtt)
                    return@get
                } catch (_: java.io.FileNotFoundException) {
                    if (idx == candidates.lastIndex) {
                        call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "No subtitle found", "code" to "NOT_FOUND"))
                    }
                } catch (_: Exception) {
                    if (idx == candidates.lastIndex) {
                        call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "No subtitle found", "code" to "NOT_FOUND"))
                    }
                }
            }
        }
    }
}

private fun srtToVtt(srt: String): String {
    val normalized = srt
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    val sb = StringBuilder("WEBVTT\n\n")
    sb.append(
        normalized
            .replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2")
            .replace(Regex("(?m)^(\\d+)\\s*\\n"), "")
    )
    return sb.toString()
}

private fun assToVtt(ass: String): String {
    val sb = StringBuilder("WEBVTT\n\n")
    val events = ass.substringAfter("[Events]", "")
    val formatLine = Regex("^Format:\\s*(.+)$", RegexOption.MULTILINE).find(events)?.groupValues?.get(1)
    if (formatLine == null) {
        sb.append(ass)
        return sb.toString()
    }
    val fields = formatLine.split(",").map { it.trim().lowercase() }
    val startIdx = fields.indexOf("start")
    val endIdx = fields.indexOf("end")
    val textIdx = fields.indexOf("text")
    if (startIdx < 0 || endIdx < 0 || textIdx < 0) {
        sb.append(ass)
        return sb.toString()
    }
    val dialogRegex = Regex("^Dialogue:\\s*(.+)$", RegexOption.MULTILINE)
    for (match in dialogRegex.findAll(events)) {
        val parts = match.groupValues[1].split(",")
        if (parts.size < fields.size) continue
        val start = assTimeToVtt(parts[startIdx].trim())
        val end = assTimeToVtt(parts[endIdx].trim())
        val rawText = parts.drop(textIdx).joinToString(",")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace(Regex("\\{[^}]*\\}"), "")
            .trim()
        if (rawText.isEmpty()) continue
        sb.append(start).append(" --> ").append(end).append('\n')
        sb.append(rawText).append("\n\n")
    }
    return sb.toString()
}

private fun assTimeToVtt(t: String): String {
    val match = Regex("(\\d+):(\\d{2}):(\\d{2})[.,](\\d{2})").matchEntire(t) ?: return t
    val (h, m, s, cs) = match.destructured
    val ms = (cs.toInt() * 10).toString().padStart(3, '0')
    return "${h.padStart(2, '0')}:${m}:${s}.$ms"
}

private fun resolveUploadDir(path: String): File {
    val sanitized = FileService.get().sanitizePath(path)
    val target = FileService.get().absoluteFile(sanitized)
        ?: throw IllegalArgumentException("Upload path must be explicit: $path")
    if (target.exists() && !target.isDirectory) {
        throw IllegalArgumentException("Upload target must be a directory: $sanitized")
    }
    target.mkdirs()
    return target
}

private fun parseRange(rangeHeader: String?, totalSize: Long): Pair<Long, Long> {
    if (rangeHeader == null || totalSize <= 0) return 0L to (totalSize - 1).coerceAtLeast(0)
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(rangeHeader.trim()) ?: return 0L to (totalSize - 1)
    val (s, e) = match.destructured
    val start = s.toLongOrNull() ?: 0L
    val end = e.toLongOrNull() ?: (totalSize - 1)
    if (start > end || start >= totalSize) return 0L to (totalSize - 1)
    return start to end.coerceAtMost(totalSize - 1)
}

private fun dispositionHeader(fileName: String, isDownload: Boolean): ContentDisposition {
    return if (isDownload) {
        ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName, encodeFilename(fileName)
        )
    } else {
        ContentDisposition.Inline.withParameter(
            ContentDisposition.Parameters.FileName, encodeFilename(fileName)
        )
    }
}

private fun encodeFilename(name: String): String {
    val ascii = name.all { it.code in 0x20..0x7E && it != '"' }
    val encoded = URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")
    return if (ascii) name else "utf-8''$encoded"
}
