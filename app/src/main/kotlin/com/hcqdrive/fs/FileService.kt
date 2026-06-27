package com.hcqdrive.fs

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.hcqdrive.HcqDriveApp
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

@Serializable
data class ListResponse(
    val path: String,
    val parent: String? = null,
    val entries: List<FileEntry>,
)

@Serializable
data class SearchHit(
    val path: String,
    val entry: FileEntry,
)

@Serializable
data class SearchResponse(
    val query: String,
    val total: Int,
    val results: List<SearchHit>,
)

@Serializable
data class MoveCopyRequest(
    val src: String,
    val dst: String,
)

@Serializable
data class MkdirRequest(
    val path: String,
)

@Serializable
data class RenameRequest(
    val path: String,
    val newName: String,
)

@Serializable
data class DeleteRequest(
    val path: String,
)

@Serializable
data class RestoreRequest(
    val trashPath: String,
    val originalPath: String? = null,
)

@Serializable
data class PathRequest(
    val path: String,
)

@Serializable
data class UploadInitRequest(
    val path: String,
    val filename: String,
    val totalSize: Long,
    val totalChunks: Int,
)

@Serializable
data class UploadInitResponse(
    val uploadId: String,
    val chunkSize: Long,
)

@Serializable
data class UploadChunkResponse(
    val received: Long,
    val chunk: Int,
    val total: Int,
)

@Serializable
data class UploadCompleteRequest(
    val uploadId: String,
)

@Serializable
data class UploadCancelRequest(
    val uploadId: String,
)

class FileService private constructor(private val appContext: Context) {

    private val resolver: ContentResolver get() = appContext.contentResolver

    fun sanitizePath(input: String): String {
        if (input.isBlank()) return "/"
        var path = input.replace('\\', '/')
        while (path.contains("//")) path = path.replace("//", "/")
        if (!path.startsWith("/")) path = "/$path"
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." }) {
            throw SecurityException("Path traversal attempt rejected: $input")
        }
        return "/" + parts.joinToString("/")
    }

    fun resolvePath(path: String): ResolvedPath {
        val sanitized = sanitizePath(path)
        if (sanitized == "/") return ResolvedPath.Root
        val parts = sanitized.trimStart('/').split('/')
        val first = parts.first().lowercase(Locale.ROOT)
        val alias = ALIASES[first]
        if (alias != null) {
            val remaining = parts.drop(1).joinToString("/")
            val base = alias()
            val file = if (remaining.isEmpty()) base else File(base, remaining)
            return ResolvedPath.Explicit(file, sanitized)
        }
        if (first == "internal" || first == "app") {
            val remaining = parts.drop(1).joinToString("/")
            val base = appContext.getExternalFilesDir(null)
                ?: throw FileNotFoundException("External files dir unavailable")
            val file = if (remaining.isEmpty()) base else File(base, remaining)
            return ResolvedPath.Explicit(file, sanitized)
        }
        if (first == ".trash") {
            val remaining = parts.drop(1).joinToString("/")
            val trashDir = File(rootDir(), ".trash")
            val file = if (remaining.isEmpty()) trashDir else File(trashDir, remaining)
            return ResolvedPath.Explicit(file, sanitized)
        }
        return ResolvedPath.MediaVirtual(sanitized, first)
    }

    fun rootDir(): File = Environment.getExternalStorageDirectory()

    fun trashDir(): File = File(rootDir(), ".trash").apply { mkdirs() }

    suspend fun list(path: String): ListResponse = withContext(Dispatchers.IO) {
        val resolved = resolvePath(path)
        val canonicalPath = if (path.isBlank() || path == "/") "/" else sanitizePath(path)
        val parent = if (canonicalPath == "/") null else parentOf(canonicalPath)
        when (resolved) {
            is ResolvedPath.Root -> {
                val entries = buildList {
                    add(rootEntry("Pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)))
                    add(rootEntry("DCIM", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)))
                    add(rootEntry("Movies", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)))
                    add(rootEntry("Music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)))
                    add(rootEntry("Download", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)))
                    add(rootEntry("Internal", appContext.getExternalFilesDir(null)))
                }
                ListResponse(path = "/", parent = null, entries = entries)
            }
            is ResolvedPath.Explicit -> {
                val file = resolved.file
                require(file.isDirectory) { "Not a directory: $path" }
                val children = (file.listFiles() ?: emptyArray())
                    .filterNot { it.isHidden && it.name.startsWith(".") }
                    .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
                    .map { toFileEntry(it, resolved.displayPath) }
                ListResponse(path = resolved.displayPath, parent = parent, entries = children)
            }
            is ResolvedPath.MediaVirtual -> {
                val collection = MediaCollection.parse(resolved.firstSegment)
                val entries = listMedia(collection).map { uri ->
                    val displayName = uri.displayName
                    val rel = resolved.displayPath.trimStart('/')
                    val entryPath = "/$rel/$displayName"
                    val mime = uri.mime
                    val thumbAvailable = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
                    FileEntry(
                        id = entryPath,
                        name = displayName,
                        path = entryPath,
                        kind = "file",
                        size = uri.size,
                        modifiedAt = uri.dateModified,
                        mime = mime,
                        createdAt = uri.dateModified,
                        type = classifyType(displayName, mime, false),
                        hidden = displayName.startsWith("."),
                        thumbnailUrl = if (thumbAvailable) "/api/file/thumb?path=${java.net.URLEncoder.encode(entryPath, "UTF-8")}&size=256" else null,
                    )
                }
                ListResponse(path = resolved.displayPath, parent = parent, entries = entries)
            }
        }
    }

    suspend fun stat(path: String): FileEntry? = withContext(Dispatchers.IO) {
        val resolved = resolvePath(path)
        when (resolved) {
            is ResolvedPath.Root -> FileEntry(
                id = "/", name = "/", path = "/", kind = "directory", size = 0, modifiedAt = 0,
                mime = null, createdAt = 0, type = "folder", hidden = false, thumbnailUrl = null,
            )
            is ResolvedPath.Explicit -> {
                val file = resolved.file
                if (!file.exists()) return@withContext null
                toFileEntry(file, resolved.displayPath)
            }
            is ResolvedPath.MediaVirtual -> {
                val name = path.substringAfterLast('/')
                val found = findMediaUri(resolved.firstSegment, name)
                if (found != null) {
                    val thumbAvailable = found.mime?.startsWith("image/") == true || found.mime?.startsWith("video/") == true
                    FileEntry(
                        id = resolved.displayPath,
                        name = found.displayName,
                        path = resolved.displayPath,
                        kind = "file",
                        size = found.size,
                        modifiedAt = found.dateModified,
                        mime = found.mime,
                        createdAt = found.dateModified,
                        type = classifyType(found.displayName, found.mime, false),
                        hidden = false,
                        thumbnailUrl = if (thumbAvailable) "/api/file/thumb?path=${java.net.URLEncoder.encode(resolved.displayPath, "UTF-8")}&size=256" else null,
                    )
                } else null
            }
        }
    }

    suspend fun read(path: String, range: LongRange? = null): ByteArray = withContext(Dispatchers.IO) {
        val resolved = resolvePath(path)
        val file = when (resolved) {
            is ResolvedPath.Explicit -> resolved.file
            is ResolvedPath.MediaVirtual -> throw UnsupportedOperationException("Range read not supported for media virtual paths in M1")
            is ResolvedPath.Root -> throw IllegalArgumentException("Cannot read a directory")
        }
        require(file.isFile) { "Not a file: $path" }
        if (range == null) file.readBytes()
        else {
            val start = range.first.coerceAtLeast(0)
            val end = range.last.coerceAtMost(file.length() - 1).coerceAtLeast(start)
            val length = (end - start + 1).toInt()
            file.inputStream().use { input ->
                input.skip(start)
                input.readNBytes(length)
            }
        }
    }

    suspend fun write(path: String, data: ByteArray, append: Boolean) = withContext(Dispatchers.IO) {
        val resolved = resolvePath(path)
        require(resolved is ResolvedPath.Explicit) { "Write requires explicit (non-media-virtual) path: $path" }
        val file = resolved.file
        file.parentFile?.mkdirs()
        if (append) file.appendBytes(data) else file.writeBytes(data)
    }

    suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        val resolved = resolvePath(path)
        require(resolved is ResolvedPath.Explicit) { "Mkdir requires explicit path: $path" }
        val file = resolved.file
        require(!file.exists()) { "Path already exists: $path" }
        file.mkdirs()
    }

    suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        require(newName.isNotBlank() && !newName.contains('/') && newName != "." && newName != "..") {
            "Invalid name: $newName"
        }
        val resolved = resolvePath(path)
        require(resolved is ResolvedPath.Explicit) { "Rename requires explicit path: $path" }
        val src = resolved.file
        require(src.exists()) { "Source not found: $path" }
        val target = File(src.parentFile, newName)
        require(!target.exists()) { "Target already exists: ${target.absolutePath}" }
        src.renameTo(target)
    }

    suspend fun move(src: String, dst: String) = withContext(Dispatchers.IO) {
        val srcResolved = resolvePath(src)
        require(srcResolved is ResolvedPath.Explicit) { "Move requires explicit source: $src" }
        val dstResolved = resolvePath(dst)
        require(dstResolved is ResolvedPath.Explicit) { "Move requires explicit destination: $dst" }
        val srcFile = srcResolved.file
        require(srcFile.exists()) { "Source not found: $src" }
        val dstFile = dstResolved.file
        dstFile.parentFile?.mkdirs()
        srcFile.renameTo(dstFile)
    }

    suspend fun delete(path: String): String = withContext(Dispatchers.IO) {
        val resolved = resolvePath(path)
        require(resolved is ResolvedPath.Explicit) { "Delete requires explicit path: $path" }
        val src = resolved.file
        require(src.exists()) { "Source not found: $path" }
        val ts = System.currentTimeMillis()
        val trashDir = trashDir()
        val trashFile = File(trashDir, "${ts}_${src.name}")
        var target = trashFile
        var counter = 1
        while (target.exists()) {
            target = File(trashDir, "${ts}_${counter}_${src.name}")
            counter++
        }
        src.renameTo(target)
        target.absolutePath
    }

    suspend fun restore(trashPath: String, originalPath: String?): String = withContext(Dispatchers.IO) {
        val resolved = resolvePath(trashPath)
        require(resolved is ResolvedPath.Explicit) { "Restore requires explicit path: $trashPath" }
        val trashFile = resolved.file
        require(trashFile.exists()) { "Trash entry not found: $trashPath" }
        val dest = if (originalPath != null) {
            File(sanitizePath(originalPath))
        } else {
            val origName = trashFile.name.substringAfter('_')
            File(rootDir(), origName)
        }
        trashFile.renameTo(dest)
        dest.absolutePath
    }

    fun exists(path: String): Boolean {
        val resolved = resolvePath(path)
        return when (resolved) {
            is ResolvedPath.Root -> true
            is ResolvedPath.Explicit -> resolved.file.exists()
            is ResolvedPath.MediaVirtual -> {
                val name = path.substringAfterLast('/')
                val collection = MediaCollection.parse(resolved.firstSegment)
                listMedia(collection).any { it.displayName == name }
            }
        }
    }

    suspend fun search(
        query: String,
        type: String? = null,
        basePath: String = "/",
        limit: Int = 200,
    ): SearchResponse = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return@withContext SearchResponse(query, 0, emptyList())
        val hits = mutableListOf<SearchHit>()
        walk(rootDir(), basePath, depth = 0) { file, displayPath ->
            if (hits.size >= limit) return@walk
            if (file.name.lowercase(Locale.ROOT).contains(q)) {
                if (matchesType(file, type)) {
                    hits.add(SearchHit(displayPath, toFileEntry(file, displayPath)))
                }
            }
        }
        SearchResponse(query, hits.size, hits)
    }

    private fun walk(file: File, displayPath: String, depth: Int, visit: (File, String) -> Unit) {
        if (depth > 6) return
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                if (child.isHidden && child.name.startsWith(".")) continue
                val childPath = if (displayPath == "/") "/${child.name}" else "$displayPath/${child.name}"
                visit(child, childPath)
                if (child.isDirectory) walk(child, childPath, depth + 1, visit)
            }
        }
    }

    private fun matchesType(file: File, type: String?): Boolean {
        if (type == null) return true
        return when (type.lowercase(Locale.ROOT)) {
            "image" -> file.isFile && (guessMime(file.name)?.startsWith("image/") == true)
            "video" -> file.isFile && (guessMime(file.name)?.startsWith("video/") == true)
            "audio" -> file.isFile && (guessMime(file.name)?.startsWith("audio/") == true)
            "file" -> file.isFile
            "dir", "directory", "folder" -> file.isDirectory
            else -> true
        }
    }

    fun toFileEntry(file: File, displayPath: String): FileEntry {
        val mime = if (file.isFile) guessMime(file.name) else null
        val isDir = file.isDirectory
        val entryPath = if (displayPath.endsWith("/${file.name}")) displayPath
            else if (displayPath == "/") "/${file.name}" else "$displayPath/${file.name}"
        val thumbAvailable = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
        return FileEntry(
            id = entryPath,
            name = file.name,
            path = entryPath,
            kind = if (isDir) "directory" else "file",
            size = if (isDir) 0L else file.length(),
            modifiedAt = file.lastModified(),
            mime = mime,
            createdAt = file.lastModified(),
            type = classifyType(file.name, mime, isDir),
            hidden = file.isHidden || file.name.startsWith("."),
            thumbnailUrl = if (thumbAvailable) "/api/file/thumb?path=${java.net.URLEncoder.encode(entryPath, "UTF-8")}&size=256" else null,
            childCount = if (isDir) file.list()?.size else null,
        )
    }

    private fun classifyType(name: String, mime: String?, isDir: Boolean): String {
        if (isDir) return "folder"
        if (mime != null) {
            if (mime.startsWith("image/")) return "image"
            if (mime.startsWith("video/")) return "video"
            if (mime.startsWith("audio/")) return "audio"
            if (mime.contains("pdf") || mime.contains("document") || mime.contains("text")) return "document"
            if (mime.contains("zip") || mime.contains("rar") || mime.contains("tar") || mime.contains("7z")) return "archive"
        }
        val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> "image"
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> "video"
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" -> "audio"
            "pdf" -> "document"
            "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "document"
            "txt", "md", "csv", "json", "xml", "yaml", "yml" -> "document"
            "zip", "rar", "7z", "tar", "gz", "bz2" -> "archive"
            else -> "unknown"
        }
    }

    private fun rootEntry(name: String, file: File?): FileEntry {
        val exists = file?.exists() == true
        val childCount = if (exists) file?.list()?.size else null
        return FileEntry(
            id = "/$name",
            name = name,
            path = "/$name",
            kind = "directory",
            size = 0L,
            modifiedAt = file?.lastModified() ?: 0L,
            mime = null,
            createdAt = file?.lastModified(),
            type = "folder",
            hidden = false,
            thumbnailUrl = null,
            childCount = childCount,
        ).also { Log.d("HcqDrive", "Root entry $name: exists=$exists") }
    }

    private fun listMedia(collection: MediaCollection): List<MediaUri> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val cursor: Cursor? = resolver.query(
            collection.uri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT 2000"
        )
        val result = mutableListOf<MediaUri>()
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection.uri, c.getLong(idCol))
                result.add(MediaUri(
                    uri = uri,
                    displayName = c.getString(nameCol) ?: continue,
                    size = c.getLong(sizeCol),
                    dateModified = c.getLong(dateCol) * 1000L,
                    mime = c.getString(mimeCol),
                ))
            }
        }
        return result
    }

    private fun findMediaUri(collectionName: String, displayName: String): MediaUri? {
        val collection = MediaCollection.parse(collectionName)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val cursor: Cursor? = resolver.query(collection.uri, arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        ), selection, arrayOf(displayName), null)
        return cursor?.use { c ->
            if (c.moveToFirst()) {
                val uri = ContentUris.withAppendedId(collection.uri, c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
                MediaUri(
                    uri = uri,
                    displayName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: return@use null,
                    size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                    dateModified = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)) * 1000L,
                    mime = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)),
                )
            } else null
        }
    }

    private fun parentOf(path: String): String? {
        val sanitized = sanitizePath(path)
        if (sanitized == "/") return null
        val parent = sanitized.substringBeforeLast('/', missingDelimiterValue = "/")
        return if (parent.isEmpty()) "/" else parent
    }

    fun openInputStream(path: String): Pair<InputStream, Long> {
        val resolved = resolvePath(path)
        return when (resolved) {
            is ResolvedPath.Explicit -> {
                val file = resolved.file
                require(file.isFile) { "Not a file: $path" }
                file.inputStream().buffered() to file.length()
            }
            is ResolvedPath.MediaVirtual -> {
                val name = path.substringAfterLast('/')
                val found = findMediaUri(resolved.firstSegment, name)
                    ?: throw FileNotFoundException("Media not found: $path")
                resolver.openInputStream(found.uri)!!.buffered() to found.size
            }
            is ResolvedPath.Root -> throw IllegalArgumentException("Cannot open root as stream")
        }
    }

    fun openOutputStream(path: String, append: Boolean = false): OutputStream {
        val resolved = resolvePath(path)
        require(resolved is ResolvedPath.Explicit) { "Output requires explicit path: $path" }
        val file = resolved.file
        file.parentFile?.mkdirs()
        return if (append) java.io.FileOutputStream(file, true).buffered()
        else file.outputStream().buffered()
    }

    fun absoluteFile(path: String): File? {
        val resolved = resolvePath(path)
        return if (resolved is ResolvedPath.Explicit) resolved.file else null
    }

    private fun querySize(uri: Uri): Long {
        return try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) { 0L }
    }

    sealed class ResolvedPath {
        data object Root : ResolvedPath()
        data class Explicit(val file: File, val displayPath: String) : ResolvedPath()
        data class MediaVirtual(val displayPath: String, val firstSegment: String) : ResolvedPath()
    }

    data class MediaUri(
        val uri: Uri,
        val displayName: String,
        val size: Long,
        val dateModified: Long,
        val mime: String?,
    )

    enum class MediaCollection(val uri: Uri) {
        Images(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
        Video(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
        Audio(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);

        companion object {
            fun parse(name: String): MediaCollection = when (name.lowercase(Locale.ROOT)) {
                "images", "pictures" -> Images
                "videos", "movies" -> Video
                "audio", "music" -> Audio
                else -> Images
            }
        }
    }

    companion object {
        private val ALIASES: Map<String, () -> File> = mapOf(
            "pictures" to { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) },
            "dcim" to { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) },
            "movies" to { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) },
            "music" to { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) },
            "download" to { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) },
            "downloads" to { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) },
        )

        @Volatile
        private var instance: FileService? = null

        fun get(context: Context = HcqDriveApp.appContext): FileService {
            return instance ?: synchronized(this) {
                instance ?: FileService(context.applicationContext).also { instance = it }
            }
        }

        fun guessMime(name: String): String? {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext.isEmpty()) return null
            val systemMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (systemMime != null) return systemMime
            return EXTRA_MIMES[ext]
        }

        private val EXTRA_MIMES: Map<String, String> = mapOf(
            "mkv" to "video/x-matroska",
            "avi" to "video/x-msvideo",
            "mov" to "video/quicktime",
            "wmv" to "video/x-ms-wmv",
            "flv" to "video/x-flv",
            "m4v" to "video/x-m4v",
            "3gp" to "video/3gpp",
            "ts" to "video/mp2t",
            "flac" to "audio/flac",
            "aac" to "audio/aac",
            "wma" to "audio/x-ms-wma",
            "opus" to "audio/opus",
            "heic" to "image/heic",
            "heif" to "image/heif",
            "webp" to "image/webp",
            "bmp" to "image/bmp",
            "avif" to "image/avif",
        )
    }
}
