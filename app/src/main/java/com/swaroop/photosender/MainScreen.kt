package com.swaroop.photosender

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.io.InputStream
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext


@Composable
fun MainScreen() { // Or whatever your main screen composable is named
    val context = LocalContext.current

    // 1. Create the launcher that will show the popup
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // User clicked "Allow"
            println("Notifications enabled!")
        } else {
            // User clicked "Don't allow"
            println("Notifications denied.")
        }
    }

    // 2. Trigger the popup as soon as the screen loads (if they are on Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isPermissionGranted) {
                // This line actually opens the popup!
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ... The rest of your existing Compose UI code goes here ...
}
@Composable
fun MainScreen(myId: String) {
    val context = LocalContext.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showEmoji by remember { mutableStateOf(false) }
    var showMemories by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(myId) {
        Firebase.firestore.collection("photos")
            .whereEqualTo("receiver", myId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                val base64 = snapshot?.documents
                    ?.firstOrNull()?.getString("base64") ?: return@addSnapshotListener
                scope.launch {
                    PhotoWidget.updateWidgetWithBase64(context, base64)
                }
            }
    }

    if (showMemories) {
        MemoriesScreen(myId = myId, onBack = { showMemories = false })
        return
    }

    if (capturedBitmap != null && showEmoji) {
        EmojiReactionDialog(
            myId = myId,
            bitmap = capturedBitmap!!,
            onDiscard = { capturedBitmap = null; showEmoji = false },
            onDone = { capturedBitmap = null; showEmoji = false }
        )
        return
    }

    HomeWithCamera(
        myId = myId,
        onPhotoCaptured = { bitmap -> capturedBitmap = bitmap; showEmoji = true },
        onMemoriesClick = { showMemories = true }
    )
}

@Composable
fun HomeWithCamera(
    myId: String,
    onPhotoCaptured: (Bitmap) -> Unit,
    onMemoriesClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val partnerName = if (myId == "swaroop") "Vidhi" else "Swaroop"
    var isFrontCamera by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Dialog States
    var showStoryDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val stream: InputStream? = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(stream)
            bitmap?.let { bmp -> onPhotoCaptured(bmp) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PinkSoft)) {
        FloatingHeartsBackground()
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart logo icon (Opens Our Story)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PinkPrimary.copy(alpha = 0.1f))
                        .clickable { showStoryDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("♡", fontSize = 18.sp, color = PinkPrimary)
                }

                Text(
                    "OurMoments",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PinkPrimary
                )

                // Settings icon (Opens Settings)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                        .clickable { showSettingsDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", fontSize = 16.sp, color = PinkPrimary)
                }
            }

            // ── Camera Box ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.70f)
                    .shadow(16.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            cameraProvider = provider
                            bindCamera(provider, lifecycleOwner, previewView, imageCapture, isFrontCamera)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    update = { previewView ->
                        cameraProvider?.let { provider ->
                            bindCamera(provider, lifecycleOwner, previewView, imageCapture, isFrontCamera)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ── Flip camera button (top-right) ───────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { isFrontCamera = !isFrontCamera },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⇄", fontSize = 18.sp, color = Color.White)
                }

                // ── Bottom label ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Capture a Moment", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Bottom controls ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        GalleryIcon()
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text("Gallery", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                }

                // Capture button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(10.dp, CircleShape)
                            .clip(CircleShape)
                            .background(PinkPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { capturePhoto(context, imageCapture) { onPhotoCaptured(it) } },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CameraShutterIcon()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text("Capture", fontSize = 11.sp, color = PinkPrimary, fontWeight = FontWeight.Bold)
                }

                // Memories button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(PinkPrimary.copy(alpha = 0.1f))
                            .clickable { onMemoriesClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        MemoriesIcon()
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text("Memories", fontSize = 11.sp, color = PinkPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Dialog Overlays ──────────────────────────────────────────────────
        if (showStoryDialog) {
            OurStoryDialog(myId = myId, onDismiss = { showStoryDialog = false })
        }

        if (showSettingsDialog) {
            SettingsDialog(myId = myId, onDismiss = { showSettingsDialog = false })
        }
    }
}

// ─── DIALOG COMPONENTS ───────────────────────────────────────────────────────

@Composable
fun OurStoryDialog(myId: String, onDismiss: () -> Unit) {
    val me = if (myId == "swaroop") "Swaroop" else "Vidhi"
    val partner = if (myId == "swaroop") "Vidhi" else "Swaroop"

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💕", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "$me & $partner",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PinkPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Milestone 1
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(PinkSoft),
                        contentAlignment = Alignment.Center
                    ) { Text("📅", fontSize = 18.sp) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("First Date", fontSize = 14.sp, color = Color.Gray)
                        Text("January 25, 2025", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                // Milestone 2
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(PinkSoft),
                        contentAlignment = Alignment.Center
                    ) { Text("💋", fontSize = 18.sp) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("First Kiss", fontSize = 14.sp, color = Color.Gray)
                        Text("February 14, 2025", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(myId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Check SharedPreferences when the dialog opens so it shows the correct state
    var notificationsEnabled by remember {
        mutableStateOf(UserPrefs.areNotificationsEnabled(context))
    }
    var saveToGallery by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column {
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                Spacer(modifier = Modifier.height(24.dp))

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Widget Notifications", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { isChecked ->
                            notificationsEnabled = isChecked
                            // Save the new state instantly to the device memory
                            UserPrefs.setNotificationsEnabled(context, isChecked)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PinkPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-save to Gallery", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = saveToGallery,
                        onCheckedChange = { saveToGallery = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PinkPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Unlink / Destructive Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Red.copy(alpha = 0.1f))
                        .clickable { /* Handle Unlink */ }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Unlink Partner", color = Color.Red, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── MODERN FLAT ICONS (Compose DrawScope API — no nativeCanvas) ─────────────

@Composable
fun GalleryIcon() {
    val iconGray = Color(0xFFAAAAAA)
    val iconGrayLight = Color(0x78AAAAAA)
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val r = 3.dp.toPx()

        drawRoundRect(
            color = iconGrayLight,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.05f),
            size = androidx.compose.ui.geometry.Size(w * 0.77f, h * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawRoundRect(
            color = iconGray,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.77f, h * 0.73f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        val mountainPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.10f, h * 0.82f)
            lineTo(w * 0.28f, h * 0.58f)
            lineTo(w * 0.46f, h * 0.70f)
            lineTo(w * 0.58f, h * 0.58f)
            lineTo(w * 0.75f, h * 0.82f)
            close()
        }
        drawPath(mountainPath, color = iconGray.copy(alpha = 0.35f))
        drawCircle(
            color = iconGray.copy(alpha = 0.55f),
            radius = w * 0.07f,
            center = androidx.compose.ui.geometry.Offset(w * 0.63f, h * 0.40f)
        )
    }
}

@Composable
fun CameraShutterIcon() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val stroke = 2.5.dp.toPx()
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color.White,
                radius = size.width / 2f - stroke / 2f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            drawCircle(color = Color.White, radius = size.width * 0.28f, center = center)
        }
    }
}

@Composable
fun MemoriesIcon() {
    val heartColor = Color(0xFFE8275A)
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f + h * 0.04f

        val heartPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy + h * 0.30f)
            cubicTo(
                cx - w * 0.05f, cy + h * 0.15f,
                cx - w * 0.42f, cy + h * 0.10f,
                cx - w * 0.42f, cy - h * 0.05f
            )
            cubicTo(
                cx - w * 0.42f, cy - h * 0.28f,
                cx - w * 0.18f, cy - h * 0.32f,
                cx, cy - h * 0.10f
            )
            cubicTo(
                cx + w * 0.18f, cy - h * 0.32f,
                cx + w * 0.42f, cy - h * 0.28f,
                cx + w * 0.42f, cy - h * 0.05f
            )
            cubicTo(
                cx + w * 0.42f, cy + h * 0.10f,
                cx + w * 0.05f, cy + h * 0.15f,
                cx, cy + h * 0.30f
            )
            close()
        }
        drawPath(heartPath, color = heartColor)

        drawCircle(
            color = heartColor.copy(alpha = 0.55f),
            radius = w * 0.055f,
            center = androidx.compose.ui.geometry.Offset(w * 0.80f, h * 0.17f)
        )
    }
}

fun bindCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture,
    isFront: Boolean
) {
    val selector = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA
    else CameraSelector.DEFAULT_BACK_CAMERA
    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }
    try {
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}