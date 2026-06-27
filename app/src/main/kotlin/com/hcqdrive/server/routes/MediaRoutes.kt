package com.hcqdrive.server.routes

import com.hcqdrive.fs.FileService
import com.hcqdrive.media.ExifInfo
import com.hcqdrive.media.MediaService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class PhotoEntry(
    val path: String,
    val takenAt: Long,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class PhotosResponse(
    val photos: List<PhotoEntry>,
    val total: Int,
)

fun Route.configureMediaRoutes() {
    route("/api/media") {
        get("/exif") {
            val path = call.request.queryParameters["path"] ?: throw IllegalArgumentException("path required")
            val exif: ExifInfo? = MediaService.get().getExif(path)
            if (exif == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "EXIF not available", "code" to "NO_EXIF"))
            } else {
                call.respond(exif)
            }
        }
        get("/photos") {
            val year = call.request.queryParameters["year"]?.toIntOrNull()
            val month = call.request.queryParameters["month"]?.toIntOrNull()
            val album = call.request.queryParameters["album"] ?: "DCIM"
            val dirPath = "/$album"
            val listing = runBlocking { FileService.get().list(dirPath) }
            val photos = listing.entries
                .filter { it.kind != "directory" && (it.mime?.startsWith("image/") == true) }
                .map { entry ->
                    val date = entry.modifiedAt
                    val include = if (year == null) true else {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = date }
                        val matchesYear = cal.get(java.util.Calendar.YEAR) == year
                        val matchesMonth = month == null || cal.get(java.util.Calendar.MONTH) + 1 == month
                        matchesYear && matchesMonth
                    }
                    if (include) PhotoEntry(
                        path = entry.path,
                        takenAt = date,
                        size = entry.size,
                    ) else null
                }
                .filterNotNull()
            call.respond(PhotosResponse(photos = photos, total = photos.size))
        }
    }
}
