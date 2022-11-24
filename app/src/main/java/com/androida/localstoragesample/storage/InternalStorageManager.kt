package com.androida.localstoragesample.storage

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.IOException

data class InternalStoragePhoto(val name: String, val bmp: Bitmap)

object InternalStorageManager {

    private suspend fun deletePhotoFromInternalStorage(
        context: Context,
        filename: String
    ): Boolean {
        return withContext(IO) {
            try {
                context.deleteFile(filename)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun loadPhotoFromInternalStorage(context: Context): List<InternalStoragePhoto> {
        return withContext(IO) {
            val files = context.filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(name = it.name, bmp = bmp)
            } ?: listOf()
        }
    }

    suspend fun savePhotoToInternalStorage(
        context: Context,
        filename: String,
        bmp: Bitmap
    ): Boolean {
        return withContext(IO) {
            try {
                context.openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                        throw IOException("Couldn't save bitmap")
                    }
                }
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }
}