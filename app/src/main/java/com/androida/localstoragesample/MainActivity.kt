package com.androida.localstoragesample

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.androida.localstoragesample.storage.ExternalStorageManager
import com.androida.localstoragesample.storage.InternalStorageManager
import com.androida.localstoragesample.storage.InternalStoragePhoto
import com.androida.localstoragesample.storage.ShareStoragePhoto
import com.androida.localstoragesample.ui.theme.LocalStorageSampleTheme
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LocalStorageSampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainApp()
                }
            }
        }

    }
}

@Composable
fun MainApp() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var loadedInternalImages by remember { mutableStateOf<List<InternalStoragePhoto>>(emptyList()) }
    var loadedExternalImages by remember { mutableStateOf<List<ShareStoragePhoto>>(emptyList()) }
    var saveInternalStorage by remember { mutableStateOf(false) }

    val storagePermissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readPermissionGranted =
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: true
            val writePermissionGranted =
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: true

            if (readPermissionGranted && writePermissionGranted)
                coroutineScope.launch {
                    loadedExternalImages =
                        ExternalStorageManager.loadPhotosFromExternalStorage(context)
                }
        }

    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicturePreview()) { uri ->
            coroutineScope.launch {
                if (saveInternalStorage) {
                    val isSavedSuccess =
                        InternalStorageManager.savePhotoToInternalStorage(
                            context, UUID.randomUUID().toString(), uri
                        )
                    if (isSavedSuccess) {
                        loadedInternalImages =
                            InternalStorageManager.loadPhotoFromInternalStorage(context)
                    }
                } else {
                    val isSaveSuccess = ExternalStorageManager.savePhotoToExternalStorage(
                        context, UUID.randomUUID().toString(), uri
                    )
                    if (isSaveSuccess) {
                        if (ExternalStorageManager.permissionsToRequest(context).isNotEmpty()) {
                            storagePermissionsLauncher.launch(
                                ExternalStorageManager.permissionsToRequest(
                                    context
                                ).toTypedArray()
                            )
                        } else {
                            loadedExternalImages =
                                ExternalStorageManager.loadPhotosFromExternalStorage(context)
                        }
                    }
                }
            }
        }

    LaunchedEffect(Unit) {
        loadedInternalImages = InternalStorageManager.loadPhotoFromInternalStorage(context)
        if (ExternalStorageManager.permissionsToRequest(context).isNotEmpty()) {
            storagePermissionsLauncher.launch(
                ExternalStorageManager.permissionsToRequest(
                    context
                ).toTypedArray()
            )
        } else {
            loadedExternalImages =
                ExternalStorageManager.loadPhotosFromExternalStorage(context)
        }
    }

    DisplayLoadedImages(
        onTakePictureClicked = {
            saveInternalStorage = it
            launcher.launch()
        },
        loadedExternalImages,
        loadedInternalImages
    )
}

@Composable
fun DisplayLoadedImages(
    onTakePictureClicked: (fromInternal: Boolean) -> Unit,
    sharedPhotos: List<ShareStoragePhoto>,
    internalPhoto: List<InternalStoragePhoto>
) {
    Column(
        Modifier.fillMaxSize(),
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            onClick = { onTakePictureClicked.invoke(true) }) {
            Text(
                text = "Take Photo and save Internal Storage",
                textAlign = TextAlign.Center,
            )
        }
        LazyRow(modifier = Modifier) {

            items(internalPhoto.size) { item ->
                Image(
                    painter = rememberAsyncImagePainter(internalPhoto[item].bmp),
                    contentDescription = "Image",
                    modifier = Modifier
                        .height(200.dp)
                        .aspectRatio(1f)
                        .padding(10.dp)
                )
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            onClick = { onTakePictureClicked.invoke(false) }) {
            Text(
                text = "Take Photo and save Shared Storage",
                textAlign = TextAlign.Center,
            )
        }
        LazyRow(modifier = Modifier.aspectRatio(1f)) {
            items(sharedPhotos.size) { item ->
                Image(
                    painter = rememberAsyncImagePainter(sharedPhotos[item].contentUri),
                    contentDescription = "Image",
                    modifier = Modifier
                        .height(200.dp)
                        .aspectRatio(1f)
                        .padding(10.dp)
                )
            }
        }

    }
}