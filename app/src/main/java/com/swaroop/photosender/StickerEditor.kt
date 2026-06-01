package com.swaroop.photosender

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─── Data ─────────────────────────────────────────────────────────────────────

data class PlacedSticker(
    val emoji: String,
    val offset: Offset = Offset(0f, 0f),  // CENTER of sticker in box-px
    val scale: Float = 1f,
    val rotation: Float = 0f              // degrees
)

// ─── Bitmap merge ─────────────────────────────────────────────────────────────

fun mergeBitmapWithStickers(
    original: Bitmap,
    stickers: List<PlacedSticker>,
    boxSize: IntSize
): Bitmap {
    if (boxSize.width == 0 || boxSize.height == 0 || stickers.isEmpty()) return original

    val result = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(result)

    val ratioX = result.width.toFloat() / boxSize.width
    val ratioY = result.height.toFloat() / boxSize.height
    val bitmapFontPx = boxSize.width * 0.11f * ratioX

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = bitmapFontPx
    }

    stickers.forEach { sticker ->
        val cx = sticker.offset.x * ratioX
        val cy = sticker.offset.y * ratioY
        val bounds = android.graphics.Rect()
        paint.getTextBounds(sticker.emoji, 0, sticker.emoji.length, bounds)
        val tw = paint.measureText(sticker.emoji)
        val th = bounds.height().toFloat()

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(sticker.rotation)
        canvas.scale(sticker.scale, sticker.scale)
        canvas.drawText(sticker.emoji, -tw / 2f, th / 2f, paint)
        canvas.restore()
    }
    return result
}

// ─── Perfect Matrix-Aligned Crop ──────────────────────────────────────────────

fun applyCrop(
    original: Bitmap,
    boxSize: IntSize,
    cropOffsetX: Float,
    cropOffsetY: Float,
    cropScale: Float
): Bitmap {
    if (boxSize.width == 0 || boxSize.height == 0) return original

    val bW = original.width.toFloat()
    val bH = original.height.toFloat()

    // 1. Replicate ContentScale.Crop exactly
    val scaleX = boxSize.width.toFloat() / bW
    val scaleY = boxSize.height.toFloat() / bH
    val baseScale = maxOf(scaleX, scaleY)

    // 2. Setup an Android Matrix representing the exact display viewport transformations
    val matrix = android.graphics.Matrix()

    // Scale and translate to the center (matching ContentScale.Crop behavior)
    matrix.postScale(baseScale, baseScale)
    val initialTx = (boxSize.width - (bW * baseScale)) / 2f
    val initialTy = (boxSize.height - (bH * baseScale)) / 2f
    matrix.postTranslate(initialTx, initialTy)

    // Apply the user's manual crop adjustments (Pinch-to-zoom centered on the box)
    matrix.postScale(cropScale, cropScale, boxSize.width / 2f, boxSize.height / 2f)
    matrix.postTranslate(cropOffsetX, cropOffsetY)

    // 3. Invert the matrix to trace layout coordinates back onto the raw Bitmap pixels
    val inverse = android.graphics.Matrix()
    matrix.invert(inverse)

    // 4. Map the 4 viewport corners to see exactly which bitmap pixels are visible
    val srcPoints = floatArrayOf(
        0f, 0f,                                 // Top-Left corner of screen
        boxSize.width.toFloat(), boxSize.height.toFloat() // Bottom-Right corner of screen
    )
    val dstPoints = FloatArray(4)
    inverse.mapPoints(dstPoints, srcPoints)

    val srcX = dstPoints[0]
    val srcY = dstPoints[1]
    val srcW = dstPoints[2] - dstPoints[0]
    val srcH = dstPoints[3] - dstPoints[1]

    // 5. Coerce variables safely within the physical limits of the image file
    val finalX = srcX.roundToInt().coerceIn(0, original.width - 1)
    val finalY = srcY.roundToInt().coerceIn(0, original.height - 1)
    val finalW = srcW.roundToInt().coerceIn(1, original.width - finalX)
    val finalH = srcH.roundToInt().coerceIn(1, original.height - finalY)

    return Bitmap.createBitmap(original, finalX, finalY, finalW, finalH)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}

private fun angleDeg(x1: Float, y1: Float, x2: Float, y2: Float): Float =
    (atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()) * 180.0 / PI).toFloat()

// ─── Sticker Canvas (Using pointerInteropFilter for multi-touch accuracy) ───

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StickerCanvas(
    stickers: List<PlacedSticker>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onDelete: (Int) -> Unit,
    onUpdate: (Int, PlacedSticker) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    var activeIdx   by remember { mutableStateOf(-1) }
    var prevX       by remember { mutableStateOf(0f) }
    var prevY       by remember { mutableStateOf(0f) }
    var prevSpan    by remember { mutableStateOf(0f) }
    var prevAngle   by remember { mutableStateOf(0f) }
    var prevMidX    by remember { mutableStateOf(0f) }
    var prevMidY    by remember { mutableStateOf(0f) }

    fun hitTest(x: Float, y: Float): Int {
        for (i in stickers.indices.reversed()) {
            val s = stickers[i]
            val r = 70f * s.scale
            if (abs(x - s.offset.x) < r && abs(y - s.offset.y) < r) return i
        }
        return -1
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInteropFilter { event ->
                val pointerCount = event.pointerCount

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val x = event.getX(0)
                        val y = event.getY(0)
                        val hit = hitTest(x, y)
                        if (hit == -1) {
                            onSelect(null)
                            activeIdx = -1
                        } else {
                            onSelect(hit)
                            activeIdx = hit
                            prevX = x
                            prevY = y
                            prevSpan = 0f
                        }
                        true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (activeIdx != -1 && pointerCount >= 2) {
                            val x0 = event.getX(0)
                            val y0 = event.getY(0)
                            val x1 = event.getX(1)
                            val y1 = event.getY(1)
                            prevSpan  = dist(x0, y0, x1, y1)
                            prevAngle = angleDeg(x0, y0, x1, y1)
                            prevMidX  = (x0 + x1) / 2f
                            prevMidY  = (y0 + y1) / 2f
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (activeIdx == -1) return@pointerInteropFilter false
                        val s = stickers.getOrNull(activeIdx) ?: return@pointerInteropFilter false

                        if (pointerCount == 1) {
                            // ── Single Finger Drag (Pan) ───────────────────────
                            val x = event.getX(0)
                            val y = event.getY(0)
                            val dx = x - prevX
                            val dy = y - prevY
                            prevX = x
                            prevY = y

                            onUpdate(activeIdx, s.copy(
                                offset = Offset(
                                    (s.offset.x + dx).coerceIn(0f, boxSize.width.toFloat()),
                                    (s.offset.y + dy).coerceIn(0f, boxSize.height.toFloat())
                                )
                            ))
                        } else {
                            // ── Two Finger Pan + Zoom + Rotate ─────────────────
                            val x0 = event.getX(0)
                            val y0 = event.getY(0)
                            val x1 = event.getX(1)
                            val y1 = event.getY(1)

                            val span  = dist(x0, y0, x1, y1)
                            val angle = angleDeg(x0, y0, x1, y1)
                            val midX  = (x0 + x1) / 2f
                            val midY  = (y0 + y1) / 2f

                            val zoom = if (prevSpan == 0f || span == 0f) 1f else span / prevSpan
                            var rotDelta = if (prevSpan == 0f) 0f else angle - prevAngle
                            while (rotDelta >  180f) rotDelta -= 360f
                            while (rotDelta < -180f) rotDelta += 360f
                            val dx = if (prevSpan == 0f) 0f else midX - prevMidX
                            val dy = if (prevSpan == 0f) 0f else midY - prevMidY

                            prevSpan  = span
                            prevAngle = angle
                            prevMidX  = midX
                            prevMidY  = midY

                            onUpdate(activeIdx, s.copy(
                                offset = Offset(
                                    (s.offset.x + dx).coerceIn(0f, boxSize.width.toFloat()),
                                    (s.offset.y + dy).coerceIn(0f, boxSize.height.toFloat())
                                ),
                                scale    = (s.scale * zoom).coerceIn(0.2f, 6f),
                                rotation = s.rotation + rotDelta
                            ))
                        }
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        activeIdx = -1
                        prevSpan  = 0f
                        true
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        if (pointerCount > 1) {
                            val remainingIdx = if (event.actionIndex == 0) 1 else 0
                            prevX    = event.getX(remainingIdx)
                            prevY    = event.getY(remainingIdx)
                            prevSpan = 0f
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        content()

        stickers.forEachIndexed { index, sticker ->
            StickerView(
                sticker    = sticker,
                isSelected = index == selectedIndex,
                onDelete   = { onDelete(index) }
            )
        }
    }
}

// ─── Purely Visual Sticker Render ─────────────────────────────────────────────

@Composable
fun StickerView(
    sticker: PlacedSticker,
    isSelected: Boolean,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(sticker.offset.x.roundToInt(), sticker.offset.y.roundToInt())
            }
            .wrapContentSize(unbounded = true)
            .graphicsLayer(
                scaleX          = sticker.scale,
                scaleY          = sticker.scale,
                rotationZ       = sticker.rotation,
                transformOrigin = TransformOrigin(0f, 0f)
            )
    ) {
        Text(
            text     = sticker.emoji,
            fontSize = 40.sp,
            modifier = Modifier
                .offset(x = (-20).dp, y = (-20).dp)
                .then(
                    if (isSelected) Modifier.border(
                        1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)
                    ) else Modifier
                )
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .offset(x = 2.dp, y = (-46).dp)
                    .size(22.dp)
                    .background(Color.Red, CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}
