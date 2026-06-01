package com.swaroop.photosender

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun EmojiReactionDialog(
    myId: String,
    bitmap: Bitmap,
    onDiscard: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val photoViewModel: PhotoViewModel = viewModel()

    var isSending by remember { mutableStateOf(false) }
    var placedStickers by remember { mutableStateOf(listOf<PlacedSticker>()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showWidgetPreview by remember { mutableStateOf(false) }
    var showCropMode by remember { mutableStateOf(false) }

    // Track the rendered photo box size so merge is pixel-accurate
    var boxWidthPx by remember { mutableStateOf(0f) }
    var boxHeightPx by remember { mutableStateOf(0f) }

    // Non-destructive core storage references
    val originalBitmap = remember { bitmap }

    // Persistent crop state metrics shared cleanly across layout views
    var cropOffsetX by remember { mutableStateOf(0f) }
    var cropOffsetY by remember { mutableStateOf(0f) }
    var cropScale by remember { mutableStateOf(1f) }

    val stickers = remember {
        listOf(
            "❤️","😘","💕","🔥","✨","🌸",
            "💫","🥰","💋","🎉","⭐","🦋",
            "💖","😍","🌹","💝","🥺","😊",
            "🌈","🎀","💃","🫶","💞","🌺",
            "😇","🤩","💯","🎊","🫦","💜",
            "🩷","💙","🌙","☀️","🍓","🌷"
        )
    }

    val photoScale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        photoScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    // ── Puzzle Screen Overlay (Triggers when image sending starts) ──
    if (isSending) {
        val finalCropSize = IntSize(boxWidthPx.toInt(), boxHeightPx.toInt())
        val processedCropBase = remember(originalBitmap, finalCropSize, cropOffsetX, cropOffsetY, cropScale) {
            applyCrop(originalBitmap, finalCropSize, cropOffsetX, cropOffsetY, cropScale)
        }

        SendingScreen(
            progress = 0f,
            myId = myId,
            bitmap = processedCropBase,
            onDone = onDone
        )
        return
    }

    // ── Widget Preview Overlay Mode ─────────────────────────────────────────
    if (showWidgetPreview) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { showWidgetPreview = false },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Widget Preview", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This is what ${if (myId == "swaroop") "Vidhi" else "Swaroop"} will see",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(3.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                ) {
                    val previewBitmap = remember(originalBitmap, placedStickers, cropOffsetX, cropOffsetY, cropScale, boxWidthPx, boxHeightPx) {
                        val finalCropSize = IntSize(boxWidthPx.toInt(), boxHeightPx.toInt())
                        val croppedBase = applyCrop(originalBitmap, finalCropSize, cropOffsetX, cropOffsetY, cropScale)
                        mergeBitmapWithStickers(croppedBase, placedStickers, finalCropSize)
                    }

                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Widget preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OurMoments", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tap anywhere to close", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(PinkSoft)) {
        FloatingHeartsBackground()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Bar Control Panel ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                        .clickable { onDiscard() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = PinkPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Text("OurMoments", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PinkPrimary)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PinkPrimary.copy(alpha = 0.15f))
                        .clickable { showWidgetPreview = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("👁 Preview", color = PinkPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Photo / Canvas Display Area ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.55f)
                    .scale(photoScale.value)
                    .shadow(16.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black)
                    .onGloballyPositioned { coords ->
                        boxWidthPx  = coords.size.width.toFloat()
                        boxHeightPx = coords.size.height.toFloat()
                    }
            ) {
                if (showCropMode) {
                    // ── Active Crop Window Mode ──────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    cropOffsetX += pan.x
                                    cropOffsetY += pan.y
                                    cropScale = (cropScale * zoom).coerceIn(1f, 4f)
                                }
                            }
                    ) {
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = cropScale
                                    scaleY = cropScale
                                    translationX = cropOffsetX
                                    translationY = cropOffsetY
                                }
                        )
                        // Rule-of-thirds grid alignment guidelines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val sw = 1.dp.toPx()
                            val c = Color.White.copy(alpha = 0.45f)
                            drawLine(c, Offset(w / 3, 0f), Offset(w / 3, h), sw)
                            drawLine(c, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), sw)
                            drawLine(c, Offset(0f, h / 3), Offset(w, h / 3), sw)
                            drawLine(c, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), sw)
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(PinkPrimary)
                                .clickable { showCropMode = false }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("✓ Done Cropping", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // ── Active Sticker Canvas Layer Mode ─────────────────────
                    StickerCanvas(
                        stickers = placedStickers,
                        selectedIndex = selectedIndex,
                        onSelect = { i -> selectedIndex = i },
                        onUpdate = { i, updated ->
                            placedStickers = placedStickers.toMutableList().also { it[i] = updated }
                        },
                        onDelete = { i ->
                            placedStickers = placedStickers.toMutableList().also { it.removeAt(i) }
                            selectedIndex = null
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = cropScale
                                    scaleY = cropScale
                                    translationX = cropOffsetX
                                    translationY = cropOffsetY
                                }
                        )

                        Text(
                            "✦", fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Interactive Customization Controls ───────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showCropMode) PinkPrimary.copy(alpha = 0.2f) else Color.White)
                            .clickable { showCropMode = !showCropMode }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✂️ Crop", color = PinkPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (selectedIndex != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Red.copy(alpha = 0.1f))
                                .clickable {
                                    placedStickers = placedStickers.toMutableList()
                                        .also { it.removeAt(selectedIndex!!) }
                                    selectedIndex = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("🗑️ Remove", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("Pinch • Rotate • Drag", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = true
                    ) {
                        items(stickers, key = { it }) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        val cx = if (boxWidthPx > 0f) boxWidthPx / 2f else 300f
                                        val cy = if (boxHeightPx > 0f) boxHeightPx / 2f else 400f
                                        val spread = placedStickers.size * 30f
                                        placedStickers = placedStickers +
                                                PlacedSticker(emoji, Offset(cx + spread, cy + spread))
                                        selectedIndex = placedStickers.size - 1
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val finalCropSize = IntSize(boxWidthPx.toInt(), boxHeightPx.toInt())
                        val croppedBase = applyCrop(originalBitmap, finalCropSize, cropOffsetX, cropOffsetY, cropScale)
                        val finalBitmap = mergeBitmapWithStickers(croppedBase, placedStickers, finalCropSize)

                        photoViewModel.sendPhoto(context, myId, finalBitmap)
                        isSending = true
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text(
                        "💌 Send to ${if (myId == "swaroop") "Vidhi" else "Swaroop"}",
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Instantly appears on their home screen ✨",
                    color = Color(0xFFAAAAAA), fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}