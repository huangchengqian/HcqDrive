package com.hcqdrive.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.hcqdrive.HcqDriveApp
import com.hcqdrive.fs.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

class MediaService private constructor(private val appContext: Context) {

    private val cacheDir: File = File(appContext.cacheDir, "thumbs").apply { mkdirs() }

    private val inflightMutexes: MutableMap<String, Mutex> = java.util.concurrent.ConcurrentHashMap()

    suspend fun generateThumbnail(path: String, maxSize: Int = 256): ByteArray? = withContext(Dispatchers.IO) {
        val mime = FileService.guessMime(path.substringAfterLast('/'))
            ?: return@withContext null

        val cacheKey = cacheKey(path, maxSize)
        val cacheFile = File(cacheDir, "$cacheKey.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return@withContext cacheFile.readBytes()
        }

        val mutex = inflightMutexes.getOrPut(cacheKey) { Mutex() }
        return@withContext mutex.withLock {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return@withLock cacheFile.readBytes()
            }
            val bytes = when {
                mime.startsWith("image/") -> generateImageThumbnail(path, maxSize)
                mime.startsWith("video/") -> generateVideoThumbnail(path, maxSize)
                else -> null
            }
            if (bytes != null) {
                runCatching { FileOutputStream(cacheFile).use { it.write(bytes) } }
            }
            bytes
        }
    }

    private fun generateImageThumbnail(path: String, maxSize: Int): ByteArray? {
        return try {
            val (stream, _) = FileService.get().openInputStream(path)
            stream.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val (stream2, _) = FileService.get().openInputStream(path)
                stream2.use { input2 ->
                    val bitmap = BitmapFactory.decodeStream(input2, null, opts) ?: return@use null
                    return@use compressJpeg(bitmap, maxSize)
                }
            }
        } catch (e: Exception) {
            Log.w("HcqDrive", "Image thumbnail failed for $path: ${e.message}")
            null
        }
    }

    private fun generateVideoThumbnail(path: String, maxSize: Int): ByteArray? {
        return try {
            val (stream, _) = FileService.get().openInputStream(path)
            val tmpFile = java.io.File(appContext.cacheDir, "vthumb_${path.hashCode()}.mp4")
            stream.use { input -> tmpFile.outputStream().use { out -> input.copyTo(out) } }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tmpFile.absolutePath)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0)
                frame?.let { compressJpeg(it, maxSize) }
            } finally {
                runCatching { retriever.release() }
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.w("HcqDrive", "Video thumbnail failed for $path: ${e.message}")
            null
        }
    }

    private fun compressJpeg(bitmap: Bitmap, maxSize: Int): ByteArray {
        val scaled = scaleToMax(bitmap, maxSize)
        if (scaled !== bitmap) bitmap.recycle()
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
        scaled.recycle()
        return baos.toByteArray()
    }

    private fun scaleToMax(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val ratio = if (w >= h) maxSize.toFloat() / w else maxSize.toFloat() / h
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxSize && h / 2 >= maxSize) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    suspend fun getExif(path: String): ExifInfo? = withContext(Dispatchers.IO) {
        val file = FileService.get().absoluteFile(path) ?: return@withContext null
        if (!file.exists()) return@withContext null
        try {
            val exif = ExifInterface(file.absolutePath)
            val latLng = FloatArray(2)
            val hasGps = exif.latLong?.let { true } ?: false
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            val isoStr = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            val iso = isoStr?.toIntOrNull()
            val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
            val shutterRaw = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                ?: exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE)
            val shutter = if (shutterRaw != null) formatShutter(shutterRaw) else null
            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            ExifInfo(
                make = make?.takeIf { it.isNotBlank() },
                model = model?.takeIf { it.isNotBlank() },
                dateTime = dateTime?.takeIf { it.isNotBlank() },
                latitude = if (hasGps) exif.latLong?.get(0) else null,
                longitude = if (hasGps) exif.latLong?.get(1) else null,
                iso = iso,
                aperture = aperture?.takeIf { it.isNotBlank() },
                shutterSpeed = shutter,
                focalLength = focal?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            Log.w("HcqDrive", "EXIF read failed for $path: ${e.message}")
            null
        }
    }

    private fun formatShutter(raw: String): String {
        return try {
            val v = raw.toDouble()
            if (v >= 1.0) "1/${(1.0 / v).toInt().coerceAtLeast(1)}s"
            else "${(1.0 / v).toInt().coerceAtLeast(1)}s"
        } catch (e: Exception) {
            raw
        }
    }

    suspend fun getDuration(path: String): Long? = withContext(Dispatchers.IO) {
        val file = FileService.get().absoluteFile(path) ?: return@withContext null
        if (!file.exists()) return@withContext null
        val mime = FileService.guessMime(file.name) ?: return@withContext null
        if (!mime.startsWith("video/") && !mime.startsWith("audio/")) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull()
        } catch (e: Exception) {
            Log.w("HcqDrive", "Duration read failed for $path: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun cacheKey(path: String, maxSize: Int): String {
        val md = MessageDigest.getInstance("SHA-1")
        val input = "${path.lowercase(Locale.ROOT)}|${maxSize}"
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile
        private var instance: MediaService? = null

        fun get(context: Context = HcqDriveApp.appContext): MediaService {
            return instance ?: synchronized(this) {
                instance ?: MediaService(context.applicationContext).also { instance = it }
            }
        }
    }
}
