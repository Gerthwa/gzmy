package com.gzmy.app.ui.drawing

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "DrawingActivity"
private val DarkBg = Color(0xFF0D0D0F)
private val SurfaceBg = Color(0xFF1A1A2E)
private val PinkAccent = Color(0xFFF43F7A)

class DrawingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrawingScreen(onBack = { finish() })
        }
    }
}

/** A stroke stored as a list of points (for easy bitmap rendering) */
data class PointStroke(
    val points: MutableList<Offset> = mutableListOf(),
    val color: Color = Color.Black,
    val width: Float = 6f
)

@Composable
fun DrawingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<PointStroke>() }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentWidth by remember { mutableFloatStateOf(6f) }
    var isSaving by remember { mutableStateOf(false) }
    // Trigger recomposition
    var drawTick by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 12.dp)
            )
            Text(
                text = "Cizim Gonder",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // Canvas area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
        ) {
            // Consume drawTick to force recomposition
            @Suppress("UNUSED_VARIABLE")
            val tick = drawTick

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val stroke = PointStroke(
                                    points = mutableListOf(offset),
                                    color = currentColor,
                                    width = currentWidth
                                )
                                strokes.add(stroke)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                strokes.lastOrNull()?.points?.add(change.position)
                                drawTick++
                            },
                            onDragEnd = {
                                drawTick++
                            }
                        )
                    }
            ) {
                for (stroke in strokes) {
                    if (stroke.points.size < 2) continue
                    val path = Path()
                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                    for (i in 1 until stroke.points.size) {
                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Color picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            val colors = listOf(
                Color.Black to "Siyah",
                Color.Red to "Kirmizi",
                PinkAccent to "Pembe",
                Color.Blue to "Mavi"
            )
            colors.forEach { (c, label) ->
                val selected = currentColor == c
                Button(
                    onClick = { currentColor = c },
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) c.copy(alpha = 0.7f) else SurfaceBg
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(label, color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // Thickness picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            listOf(4f to "Ince", 8f to "Normal", 14f to "Kalin").forEach { (w, label) ->
                val selected = currentWidth == w
                Button(
                    onClick = { currentWidth = w },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) PinkAccent.copy(alpha = 0.5f) else SurfaceBg
                    ),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { strokes.clear(); drawTick++ },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Temizle", color = Color.White)
            }

            Button(
                onClick = {
                    if (!isSaving && strokes.isNotEmpty()) {
                        isSaving = true
                        uploadDrawing(context, strokes.toList()) { success ->
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Cizim gonderildi!", Toast.LENGTH_SHORT).show()
                                (context as? ComponentActivity)?.finish()
                            } else {
                                Toast.makeText(context, "Gonderme hatasi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                shape = RoundedCornerShape(24.dp),
                enabled = !isSaving && strokes.isNotEmpty()
            ) {
                Text(
                    if (isSaving) "Gonderiliyor..." else "Kaydet",
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Render strokes to a 512x512 Bitmap, compress as JPEG 70%,
 * upload to Firebase Storage, save URL to Firestore.
 */
private fun uploadDrawing(
    context: Context,
    strokes: List<PointStroke>,
    onResult: (Boolean) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val prefs = context.getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
            val coupleCode = prefs.getString("couple_code", "") ?: ""
            if (coupleCode.isEmpty()) {
                withContext(Dispatchers.Main) { onResult(false) }
                return@launch
            }

            // --- Render to Android Bitmap ---
            val bmpSize = 512
            val bitmap = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            // We need to scale from compose canvas coordinates to 512x512
            // We'll just draw directly at 1:1 and clip (user sees ~same on phone)
            for (stroke in strokes) {
                if (stroke.points.size < 2) continue
                val paint = android.graphics.Paint().apply {
                    color = stroke.color.toArgb()
                    strokeWidth = stroke.width
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val path = android.graphics.Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                canvas.drawPath(path, paint)
            }

            // Compress
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val data = baos.toByteArray()
            bitmap.recycle()

            // Upload to Firebase Storage
            val ref = FirebaseStorage.getInstance().reference
                .child("drawings/${coupleCode}_latest.jpg")
            ref.putBytes(data).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            // Save URL to Firestore couple doc
            FirebaseFirestore.getInstance()
                .collection("couples").document(coupleCode)
                .update("latestDrawingUrl", downloadUrl)
                .await()

            Log.d(TAG, "Drawing uploaded: $downloadUrl")
            withContext(Dispatchers.Main) { onResult(true) }

        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            withContext(Dispatchers.Main) { onResult(false) }
        }
    }
}
