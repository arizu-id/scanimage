package id.arizu.scanimage

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import id.arizu.scanimage.data.Storage
import id.arizu.scanimage.ui.camera.CameraActivity
import id.arizu.scanimage.ui.crop.CropActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share


class MainActivity : ComponentActivity() {

    private val requestStorageLegacy = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen(
                onRequestLegacyPerms = {
                    if (Build.VERSION.SDK_INT <= 28) {
                        requestStorageLegacy.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun AppScreen(onRequestLegacyPerms: () -> Unit) {
    val ctx = LocalContext.current
    var uris by remember { mutableStateOf(emptyList<Uri>()) }

    fun reload() { uris = Storage.listScans(ctx) }

    LaunchedEffect(Unit) {
        onRequestLegacyPerms()
        reload()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Launcher pilih gambar → arahkan ke CropActivity
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val intent = Intent(ctx, CropActivity::class.java)
                .putExtra("imageUri", uri)
            ctx.startActivity(intent)
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Gallery
                SmallFloatingActionButton(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) { Text("\uD83D\uDDBC") } // 🖼️

                // Kamera
                FloatingActionButton(onClick = {
                    ctx.startActivity(Intent(ctx, CameraActivity::class.java))
                }) { Text("+") }
            }
        }
    ) { pad ->
        if (uris.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) { Text(text = ctx.getString(R.string.no_scans)) }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(uris, key = { it.toString() }) { uri ->
                    var showConfirmDelete by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    // buka viewer bawaan
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(intent)
                                },
                            contentScale = ContentScale.Crop
                        )

                        Spacer(Modifier.width(12.dp))

                        // Nama file
                        Text(
                            text = uri.lastPathSegment ?: "ScannedDocument",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        // Tombol Share
                        IconButton(onClick = { shareScan(ctx, uri) }) {
                            androidx.compose.material.icons.Icons.Outlined.Share.let { icon ->
                                androidx.compose.material3.Icon(imageVector = icon, contentDescription = "Share")
                            }
                        }

                        // Tombol Delete (pakai dialog konfirmasi)
                        IconButton(onClick = { showConfirmDelete = true }) {
                            androidx.compose.material.icons.Icons.Outlined.Delete.let { icon ->
                                androidx.compose.material3.Icon(imageVector = icon, contentDescription = "Delete")
                            }
                        }
                    }

                    Divider()

                    if (showConfirmDelete) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showConfirmDelete = false },
                            title = { Text("Hapus dokumen?") },
                            text = { Text("File akan dihapus dari penyimpanan.") },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    showConfirmDelete = false
                                    deleteScan(ctx, uri)
                                    // refresh daftar
                                    uris = Storage.listScans(ctx)
                                }) { Text("Hapus") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showConfirmDelete = false }) {
                                    Text("Batal")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
private fun shareScan(ctx: android.content.Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Beri permission sementara ke semua target penerima
    ctx.grantUriPermission(
        ctx.packageName, uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
    ctx.startActivity(Intent.createChooser(send, "Bagikan dokumen"))
}

private fun deleteScan(ctx: android.content.Context, uri: Uri) {
    try {
        // Hapus via MediaStore
        ctx.contentResolver.delete(uri, null, null)
    } catch (t: Throwable) {
        android.util.Log.e("ScanImage", "Delete failed", t)
    }
}
