package id.arizu.scanimage.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.arizu.scanimage.R
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        // Overlay tombol Compose
        val overlay = ComposeView(this).apply {
            setContent {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Button(
                        onClick = { takePhoto() },
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        Text(getString(R.string.scan_button))
                    }
                }
            }
        }
        root.addView(overlay)

        setContentView(root)

        // Izin kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                // format default = JPEG (aman)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraActivity", "bind failed", e)
                Toast.makeText(this, getString(R.string.camera_fail), Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val ic = if (::imageCapture.isInitialized) imageCapture else return

        // File JPEG sementara di cache
        val photoFile = File(cacheDir, "scan_src_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        ic.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Kirim ke CropActivity dengan PATH (bukan Uri SAF)
                    val intent = Intent(this@CameraActivity, id.arizu.scanimage.ui.crop.CropActivity::class.java)
                        .putExtra("imagePath", photoFile.absolutePath)
                    startActivity(intent)
                    runOnUiThread { finish() } // kembali; setelah crop, user balik ke Main
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Log.e("CameraActivity", "capture failed", exception)
                        Toast.makeText(
                            this@CameraActivity,
                            "Gagal memotret: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}
