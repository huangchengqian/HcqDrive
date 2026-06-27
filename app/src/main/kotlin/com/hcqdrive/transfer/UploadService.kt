package com.hcqdrive.transfer

import android.content.Context
import android.util.Log
import com.hcqdrive.HcqDriveApp
import com.hcqdrive.fs.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

object UploadService {

    private const val TAG = "HcqDrive"
    const val CHUNK_SIZE: Long = 5L * 1024 * 1024

    private data class UploadSession(
        val id: String,
        val targetDir: File,
        val targetName: String,
        val totalSize: Long,
        val totalChunks: Int,
        val receivedBytes: LongArray = LongArray(0),
        val receivedIndices: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf()),
        val mutex: Mutex = Mutex(),
    )

    private val sessions: MutableMap<String, UploadSession> = java.util.concurrent.ConcurrentHashMap()

    private fun tempRoot(): File {
        val root = File(HcqDriveApp.appContext.cacheDir, "hcqdrive-uploads")
        root.mkdirs()
        return root
    }

    fun initSession(targetDir: File, targetName: String, totalSize: Long, totalChunks: Int): String {
        val id = UUID.randomUUID().toString()
        val uploadDir = File(tempRoot(), id).apply { mkdirs() }
        val session = UploadSession(
            id = id,
            targetDir = targetDir,
            targetName = targetName,
            totalSize = totalSize,
            totalChunks = totalChunks.coerceAtLeast(1),
        )
        sessions[id] = session
        Log.i(TAG, "Upload init: id=$id target=${targetDir.name}/$targetName chunks=$totalChunks size=$totalSize")
        return id
    }

    suspend fun writeChunk(uploadId: String, index: Int, data: ByteArray, length: Int = data.size): Long = withContext(Dispatchers.IO) {
        val session = requireSession(uploadId)
        val chunkFile = File(File(tempRoot(), uploadId), "$index")
        session.mutex.withLock {
            FileOutputStream(chunkFile).use { it.write(data, 0, length) }
            session.receivedIndices.add(index)
            chunkFile.length()
        }
    }

    suspend fun writeChunkStream(uploadId: String, index: Int, input: java.io.InputStream): Long = withContext(Dispatchers.IO) {
        val session = requireSession(uploadId)
        val chunkFile = File(File(tempRoot(), uploadId), "$index")
        session.mutex.withLock {
            FileOutputStream(chunkFile).use { output ->
                input.copyTo(output, bufferSize = CHUNK_SIZE.toInt())
            }
            session.receivedIndices.add(index)
            chunkFile.length()
        }
    }

    suspend fun complete(uploadId: String): File = withContext(Dispatchers.IO) {
        val session = requireSession(uploadId)
        session.mutex.withLock {
            val expected = (0 until session.totalChunks).toSet()
            val missing = expected - session.receivedIndices
            if (missing.isNotEmpty()) {
                throw IOException("Missing chunks: $missing")
            }
            session.targetDir.mkdirs()
            val outFile = File(session.targetDir, session.targetName)
            FileOutputStream(outFile).use { out ->
                for (i in 0 until session.totalChunks) {
                    val chunkFile = File(File(tempRoot(), uploadId), "$i")
                    chunkFile.inputStream().use { input ->
                        input.copyTo(out, bufferSize = CHUNK_SIZE.toInt())
                    }
                }
            }
            cleanup(session)
            outFile
        }
    }

    fun cancel(uploadId: String) {
        val session = sessions.remove(uploadId) ?: return
        cleanup(session)
        Log.i(TAG, "Upload cancelled: id=$uploadId")
    }

    private fun cleanup(session: UploadSession) {
        val dir = File(tempRoot(), session.id)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
        sessions.remove(session.id)
    }

    fun disposeForContext(@Suppress("UNUSED_PARAMETER") context: Context) {
        sessions.keys.toList().forEach { cancel(it) }
    }

    private fun requireSession(uploadId: String): UploadSession {
        return sessions[uploadId] ?: throw IllegalArgumentException("Unknown uploadId: $uploadId")
    }
}
