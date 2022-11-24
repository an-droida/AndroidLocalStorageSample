package com.androida.localstoragesample.storage

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.androida.localstoragesample.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class ShareStoragePhoto(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val contentUri: Uri
)

object ExternalStorageManager {

    fun permissionsToRequest(context: Context): MutableList<String> {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val hasWritePermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if (!hasWritePermission) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (!hasReadPermission) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissionsToRequest
    }

    suspend fun savePhotoToExternalStorage(
        context: Context,
        displayName: String,
        bmp: Bitmap
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val imageCollection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, bmp.width)
                put(MediaStore.Images.Media.HEIGHT, bmp.height)
            }
            try {
                context.contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                    context.contentResolver.openOutputStream(uri).use { outputStream ->
                        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                            throw IOException("Couldn't save bitmap")
                        }
                    }
                } ?: throw IOException("Couldn't create MediaStore entry")
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun loadPhotosFromExternalStorage(context: Context): List<ShareStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val collection =
                sdk29AndUp {  // we specify collection we want to query images from external storage that is different for sdk29 and up
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                // We have a projection, which columns of the database are actually interested in
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )

            val photos =
                mutableListOf<ShareStoragePhoto>() // We have a list of photos we will populate here in that query call
            context.contentResolver.query(  // Pass Query parameters here
                collection,
                projection,
                null, // a selection for query specific images; null = all images
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC" // sorting of our images - by name ascending
            )
                ?.use { cursor -> // with cursor we just loop over all that result set and get corresponding column values and add those to our list
                    val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val displayNameColumn =
                        cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val displayName = cursor.getString(displayNameColumn)
                        val width = cursor.getInt(widthColumn)
                        val height = cursor.getInt(heightColumn)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        photos.add(
                            ShareStoragePhoto(
                                id = id,
                                name = displayName,
                                width = width,
                                height = height,
                                contentUri = contentUri
                            )
                        )
                    }
                    photos.toList()
                } ?: emptyList()
        }
    }

    private suspend fun MainActivity.deletePhotoFromExternalStorage(photoUri: Uri) {
        val intentSenderLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == RESULT_OK) {
                    Toast.makeText(this, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Photo couldn't deleted", Toast.LENGTH_SHORT).show()
                }
            }
        withContext(Dispatchers.IO) {
            try {
                contentResolver.delete(photoUri, null, null)
            } catch (e: SecurityException) {  // if the uri doesn't exist
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(
                            contentResolver,
                            listOf(photoUri)
                        ).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val recoverableSecurityException = e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }
                intentSender?.let { sender ->
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(sender).build()
                    )
                }
            }
        }
    }
}
