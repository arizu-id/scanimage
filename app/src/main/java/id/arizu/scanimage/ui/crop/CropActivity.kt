package id.arizu.scanimage.ui.crop

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import id.arizu.scanimage.data.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max

class CropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri: Uri? = intent.getParcelableExtra("imageUri")
        val imagePath: String? = intent.getStringExtra("imagePath")

        setContent {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()

            // Load bitmap dari Uri atau dari path file
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(Unit) {
                bitmap = withContext(Dispatchers.IO) {
                    when {
                        imageUri != null -> ctx.contentResolver.openInputStream(imageUri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                        imagePath != null -> BitmapFactory.decodeFile(imagePath)
                        else -> null
                    }
                }
            }

            if (bitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Memuat gambar…")
                }
                return@setContent
            }

            val bmp = bitmap!!
            CropScreen(
                src = bmp,
                onCancel = { finish() },
                onConfirm = { pts ->
                    scope.launch {
                        // 1) transform perspektif → 2) enhance → 3) simpan
                        val warped = withContext(Dispatchers.Default) { warpPerspective(bmp, pts) }
                        val scanned = withContext(Dispatchers.Default) { Storage.enhanceToScan(warped) }
                        withContext(Dispatchers.IO) {
                            Storage.saveScanBitmap(ctx, scanned)
                        }
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            )
        }
    }
}

@Composable
private fun CropScreen(
    src: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (List<Offset>) -> Unit
) {
    // Tampilkan gambar scaled, tapi titik disimpan dalam koordinat BITMAP (bukan view)
    val srcW = src.width.toFloat()
    val srcH = src.height.toFloat()

    // Default 4 titik: mendekati keempat sudut gambar
    var points by remember {
        mutableStateOf(
            listOf(
                Offset(srcW * 0.1f, srcH * 0.1f),          // TL
                Offset(srcW * 0.9f, srcH * 0.1f),          // TR
                Offset(srcW * 0.9f, srcH * 0.9f),          // BR
                Offset(srcW * 0.1f, srcH * 0.9f)           // BL
            )
        )
    }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("Batal") }
                Button(
                    onClick = { onConfirm(points) },
                    modifier = Modifier.weight(1f)
                ) { Text("Scan") }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Skala = min(availableW/srcW, availableH/srcH)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val availW = constraints.maxWidth.toFloat()
                val availH = constraints.maxHeight.toFloat()
                val scale = kotlin.math.min(availW / srcW, availH / srcH)
                val drawW = srcW * scale
                val drawH = srcH * scale

                // Indeks titik yang sedang di-drag (bitmap-space)
                var draggingIndex by remember { mutableStateOf<Int?>(null) }

                Box(
                    modifier = Modifier
                        .size(width = drawW.pxToDp(), height = drawH.pxToDp())
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    // Gambar bitmap (akan otomatis diperkecil/ditarik ke ukuran Box)
                    Image(
                        bitmap = src.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize()
                    )

                    // Overlay titik & garis (digambar dalam VIEW-space)
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val viewPts = points.map { it * scale }
                                        val idx = viewPts.indices.minByOrNull { i ->
                                            dist(viewPts[i], pos)
                                        }
                                        if (idx != null && dist(viewPts[idx], pos) <= 48f) {
                                            draggingIndex = idx
                                        }
                                    },
                                    onDragEnd = { draggingIndex = null },
                                    onDragCancel = { draggingIndex = null },
                                    onDrag = { change, dragAmount ->
                                        val idx = draggingIndex ?: return@detectDragGestures
                                        val currView = points[idx] * scale
                                        val newView = currView + dragAmount
                                        val newBitmap = newView / scale
                                        // Clamp ke dalam gambar
                                        val clamped = Offset(
                                            newBitmap.x.coerceIn(0f, srcW),
                                            newBitmap.y.coerceIn(0f, srcH)
                                        )
                                        points = points.toMutableList().also { it[idx] = clamped }
                                        change.consume()
                                    }
                                )
                            }
                    ) {
                        val viewPts = points.map { it * scale }
                        // Polygon
                        for (i in 0..3) {
                            val a = viewPts[i]
                            val b = viewPts[(i + 1) % 4]
                            drawLine(
                                color = Color(0xFF00FF88),
                                start = a,
                                end = b,
                                strokeWidth = 4f
                            )
                        }
                        // Handles
                        viewPts.forEach {
                            drawCircle(Color(0xFFFF4081), radius = 12f, center = it)
                        }
                    }
                }
            }
        }
    }
}

/** Perspective transform tanpa OpenCV: pakai Matrix.setPolyToPoly */
private fun warpPerspective(src: Bitmap, pts: List<Offset>): Bitmap {
    // Tentukan ukuran target berdasar panjang sisi terpanjang
    val wTop = distance(pts[0], pts[1])
    val wBottom = distance(pts[3], pts[2])
    val hLeft = distance(pts[0], pts[3])
    val hRight = distance(pts[1], pts[2])
    val targetW = max(wTop, wBottom).toInt().coerceAtLeast(100)
    val targetH = max(hLeft, hRight).toInt().coerceAtLeast(100)

    val srcPts = floatArrayOf(
        pts[0].x, pts[0].y,  // TL
        pts[1].x, pts[1].y,  // TR
        pts[2].x, pts[2].y,  // BR
        pts[3].x, pts[3].y   // BL
    )
    val dstPts = floatArrayOf(
        0f, 0f,
        targetW.toFloat(), 0f,
        targetW.toFloat(), targetH.toFloat(),
        0f, targetH.toFloat()
    )

    val matrix = Matrix().apply {
        setPolyToPoly(srcPts, 0, dstPts, 0, 4)
    }

    val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(src, matrix, paint)
    return out
}

private fun distance(a: Offset, b: Offset): Float =
    hypot(a.x - b.x, a.y - b.y)

private fun dist(a: Offset, b: Offset): Float = distance(a, b)

private operator fun Offset.times(scale: Float) = Offset(this.x * scale, this.y * scale)
private operator fun Offset.div(scale: Float) = Offset(this.x / scale, this.y / scale)
private operator fun Offset.plus(other: Offset) = Offset(this.x + other.x, this.y + other.y)

/** Helper konversi pixel → Dp (perlu @Composable karena akses LocalDensity) */
@Composable
private fun Float.pxToDp(): Dp {
    val density = LocalDensity.current
    return with(density) { this@pxToDp.toDp() }
}
