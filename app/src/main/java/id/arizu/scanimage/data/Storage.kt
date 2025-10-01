package id.arizu.scanimage.data

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object Storage {

    private const val FOLDER_NAME = "ScanImage"
    private val FOLDER_RELATIVE = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER_NAME
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun listScans(context: Context): List<Uri> {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MIME_TYPE,
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.FileColumns.RELATIVE_PATH else MediaStore.Files.FileColumns.DATA
        )

        val (selection, selectionArgs) =
            if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?" to
                        arrayOf("$FOLDER_RELATIVE%", "image/%")
            } else {
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val dir = File(base, "ScanImage").absolutePath
                "${MediaStore.Files.FileColumns.DATA} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?" to
                        arrayOf("$dir%", "image/%")
            }

        val uris = mutableListOf<Uri>()
        resolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = MediaStore.Files.getContentUri("external", id)
                uris.add(uri)
            }
        }
        return uris
    }

    fun saveScanBitmap(context: Context, bitmap: Bitmap): Uri? {
        val filename = "SCAN_${sdf.format(System.currentTimeMillis())}.jpg"
        val mime = "image/jpeg"

        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                // Tetap simpan di Documents/ScanImage
                put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER_RELATIVE) // e.g. "Documents/ScanImage"
                // Tandai sebagai image supaya bisa terindeks benar
                put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
            }

            // >>> PENTING: gunakan koleksi Files, bukan Images
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val uri = resolver.insert(collection, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
            uri
        } else {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docs, "ScanImage").apply { if (!exists()) mkdirs() }
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }


    // “Scan look” sederhana: grayscale + tingkatkan kontras
    fun enhanceToScan(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix().apply { setSaturation(0f) }

        val contrast = 1.3f
        val translate = (-0.3f * 255)
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(cm)

        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    fun bytesToBitmap(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
