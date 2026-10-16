package com.example.evinspector.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Shrinks a freshly captured photo in place: caps the longest edge, bakes the EXIF rotation into
 * the pixels (so the PDF writer never has to think about orientation), and re-encodes as JPEG.
 * This keeps a 52-charger session with many failure photos at a sane size on disk and in the PDF.
 */
object PhotoProcessor {
    private const val MAX_EDGE = 1280
    private const val JPEG_QUALITY = 75

    fun shrinkInPlace(file: File): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2

            val decoded = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return false

            val matrix = Matrix()
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
            }
            val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
            if (scale < 1f) matrix.postScale(scale, scale)

            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            val temp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temp).use { out ->
                result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (result !== decoded) decoded.recycle()
            result.recycle()

            file.delete()
            temp.renameTo(file)
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }
    }

    /** Small decode for list thumbnails. */
    fun decodeThumbnail(file: File, sampleSize: Int = 4): Bitmap? =
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}
