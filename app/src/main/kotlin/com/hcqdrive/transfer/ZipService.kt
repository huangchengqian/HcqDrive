package com.hcqdrive.transfer

import android.util.Log
import com.hcqdrive.fs.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipService {

    private const val BUFFER_SIZE = 16 * 1024
    private const val TAG = "HcqDrive"

    suspend fun createZipStream(entries: List<String>, output: OutputStream) = withContext(Dispatchers.IO) {
        ZipOutputStream(output.buffered()).use { zos ->
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED)
            val seen = HashSet<String>()
            for (raw in entries) {
                val path = raw.trim()
                if (path.isEmpty()) continue
                val entry = buildEntry(path, seen) ?: continue
                try {
                    addEntry(zos, entry)
                } catch (e: Exception) {
                    Log.w(TAG, "Skip zip entry $path: ${e.message}")
                }
            }
        }
    }

    private fun buildEntry(path: String, seen: HashSet<String>): ResolvedZipEntry? {
        val file = FileService.get().absoluteFile(path) ?: return null
        if (!file.exists()) return null
        val name = file.name.ifEmpty { "entry" }
        val unique = if (seen.add(name)) name else uniqueName(seen, name)
        return ResolvedZipEntry(file, unique)
    }

    private fun uniqueName(seen: HashSet<String>, base: String): String {
        val dot = base.lastIndexOf('.')
        val stem = if (dot <= 0) base else base.substring(0, dot)
        val ext = if (dot <= 0) "" else base.substring(dot)
        var i = 1
        while (true) {
            val candidate = "$stem ($i)$ext"
            if (seen.add(candidate)) return candidate
            i++
        }
    }

    private fun addEntry(zos: ZipOutputStream, entry: ResolvedZipEntry) {
        if (entry.file.isDirectory) {
            val dirEntry = ZipEntry(entry.entryName + "/").apply { time = entry.file.lastModified() }
            zos.putNextEntry(dirEntry)
            zos.closeEntry()
            return
        }
        val zipEntry = ZipEntry(entry.entryName).apply { time = entry.file.lastModified() }
        zos.putNextEntry(zipEntry)
        BufferedInputStream(FileInputStream(entry.file), BUFFER_SIZE).use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                zos.write(buf, 0, read)
            }
        }
        zos.closeEntry()
    }

    private data class ResolvedZipEntry(val file: java.io.File, val entryName: String)
}
