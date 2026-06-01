package com.swaroop.photosender

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ─── GRID CONFIG ─────────────────────────────────────────────────────────────
private const val COLS = 4
private const val ROWS = 6
private const val TOTAL_PIECES = COLS * ROWS

private fun buildRevealOrder(): List<Int> =
    (0 until TOTAL_PIECES).toMutableList().also { it.shuffle() }

// ─── MAIN SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun SendingScreen(
    progress: Float,
    myId: String,
    bitmap: Bitmap?,
    onDone: () -> Unit
) {
    val partnerName = if (myId == "swaroop") "Vidhi" else "Swaroop"

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = EaseInOutCubic)
        )
        onDone()
    }

    val revealOrder = remember { buildRevealOrder() }
    val visibleCount = (animatedProgress.value * TOTAL_PIECES).toInt()

    // Track the pop-in animation for each grid tile
    val pieceScales = remember { List(TOTAL_PIECES) { Animatable(0f) } }

    // Drop tiles in smoothly
    LaunchedEffect(visibleCount) {
        for (i in 0 until visibleCount) {
            val pieceIdx = revealOrder.getOrNull(i) ?: continue
            if (pieceScales[pieceIdx].value < 1f) {
                launch {
                    pieceScales[pieceIdx].animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
    }

    val heartScale by rememberInfiniteTransition(label = "heart").animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(550, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(PinkSoft),
        contentAlignment = Alignment.Center
    ) {
        FloatingHeartsBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Text(
                "Sending to $partnerName",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PinkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Watch your moment come together ✨",
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Photo Reveal Container ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFFE0EB)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val intDstSize = androidx.compose.ui.unit.IntSize(canvasW.toInt(), canvasH.toInt())

                        // 1. GAP FIX: When animation finishes, draw the raw, perfect image.
                        if (animatedProgress.value >= 1f) {
                            drawImage(image = imageBitmap, dstSize = intDstSize)
                            return@Canvas
                        }

                        // 2. TILE MATH: Calculate grid cleanly without bezier curves
                        val pieceW = canvasW / COLS
                        val pieceH = canvasH / ROWS

                        for (pieceIdx in 0 until TOTAL_PIECES) {
                            val sc = pieceScales[pieceIdx].value
                            if (sc <= 0f) continue

                            val col = pieceIdx % COLS
                            val row = pieceIdx / COLS
                            val left = col * pieceW
                            val top = row * pieceH
                            val cx = left + (pieceW / 2f)
                            val cy = top + (pieceH / 2f)

                            // Add a tiny overlap to kill transparent canvas bleeding
                            val seamlessScale = sc * 1.01f

                            // Create a temporary clip mask for this specific tile
                            val tilePath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = left,
                                        top = top,
                                        right = left + pieceW,
                                        bottom = top + pieceH,
                                        // Give the tiles rounded corners while they are popping in!
                                        cornerRadius = CornerRadius((1f - sc) * 20f)
                                    )
                                )
                            }

                            withTransform({
                                scale(seamlessScale, seamlessScale, pivot = Offset(cx, cy))
                            }) {
                                clipPath(tilePath) {
                                    // Draw the full image, but it's clipped to just this tile's square
                                    drawImage(image = imageBitmap, dstSize = intDstSize)
                                }

                                // Add a cool fading white border so it looks like physical tiles
                                val borderAlpha = (1f - sc).coerceIn(0f, 1f)
                                if (borderAlpha > 0.01f) {
                                    drawPath(
                                        path = tilePath,
                                        color = Color.White.copy(alpha = borderAlpha),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text("💌", fontSize = 64.sp, modifier = Modifier.scale(heartScale))
                }

                // Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$visibleCount / $TOTAL_PIECES",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Progress bar ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFFFD6E0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(PinkPrimary)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.value)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text("♥", fontSize = 14.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "${(animatedProgress.value * 100).toInt()}% complete",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            val label = when {
                animatedProgress.value < 0.3f -> "Preparing your moment..."
                animatedProgress.value < 0.6f -> "Sending to $partnerName..."
                animatedProgress.value < 0.9f -> "Almost there..."
                else -> "Delivered! 💌"
            }
            Text(
                label,
                fontSize = 13.sp,
                color = PinkPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}