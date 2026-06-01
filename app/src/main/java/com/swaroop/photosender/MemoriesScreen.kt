package com.swaroop.photosender

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

// ─── DATA ────────────────────────────────────────────────────────────────────

data class MemoryPhoto(
    val id: String,
    val base64: String,
    val sender: String,
    val receiver: String,
    val timestamp: Timestamp?
) {
    fun toBitmap(): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }

    fun formattedDate(): String {
        val ts = timestamp?.toDate() ?: return "Unknown date"
        val now = Date()
        val diff = now.time - ts.time
        val days = diff / (1000 * 60 * 60 * 24)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            days < 30L -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
            days < 365L -> "${days / 30} month${if (days / 30 > 1) "s" else ""} ago"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(ts)
        }
    }

    fun fullDate(): String {
        val ts = timestamp?.toDate() ?: return ""
        return SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()).format(ts)
    }
}

enum class TimelineFilter { ALL, THIS_WEEK, THIS_MONTH, BY_YEAR }

// ─── ICON COMPOSABLES ────────────────────────────────────────────────────────
// Lightweight inline SVG-style icon buttons using Unicode symbols styled as
// modern flat icons (no emoji, clean vector-feel)

@Composable
fun IconPill(
    icon: String,
    label: String,
    isSwaroop: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isSwaroop) Color(0xFFE0F0FF) else Color(0xFFF2E0FF)
    val fg = if (isSwaroop) Color(0xFF1A6FCC) else Color(0xFF7B4BAA)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 11.sp, color = fg)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun OutlineIconButton(
    symbol: String,
    contentDescription: String,
    active: Boolean = false,
    activeColor: Color = PinkPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (active) activeColor else Color.White.copy(alpha = 0.9f)
    val fg = if (active) Color.White else activeColor
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 14.sp, color = fg)
    }
}

// ─── MAIN SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun MemoriesScreen(myId: String, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var photos by remember { mutableStateOf<List<MemoryPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var savedIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val db = Firebase.firestore
            val partnerId = UserPrefs.getPartnerId(myId)
            val allSnap = db.collection("photos")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().await()

            val all = allSnap.documents.mapNotNull { doc ->
                val sender = doc.getString("sender") ?: ""
                val receiver = doc.getString("receiver") ?: ""
                val base64 = doc.getString("base64") ?: ""
                if ((sender == myId || sender == partnerId ||
                            receiver == myId || receiver == partnerId) &&
                    base64.isNotEmpty()
                ) {
                    MemoryPhoto(
                        id = doc.id,
                        base64 = base64,
                        sender = sender,
                        receiver = receiver,
                        timestamp = doc.getTimestamp("timestamp")
                    )
                } else null
            }.sortedByDescending { it.timestamp?.seconds ?: 0L }
            photos = all
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(PinkSoft)) {
        FloatingHeartsBackground()
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = PinkPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    "Our Memories",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PinkPrimary
                )

                // Count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PinkPrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "${photos.size} ◆",
                        fontSize = 12.sp,
                        color = PinkPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Tab bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.7f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("▤", "Timeline", 0),
                    Triple("♥", "Saved", 1),
                    Triple("↺", "Relive", 2)
                ).forEach { (icon, label, index) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == index) PinkPrimary else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                icon,
                                fontSize = 13.sp,
                                color = if (selectedTab == index) Color.White else Color(0xFFAAAAAA)
                            )
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTab == index) Color.White else Color(0xFFAAAAAA)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                LoadingHearts()
            } else if (photos.isEmpty()) {
                EmptyMemories()
            } else {
                AnimatedContent(targetState = selectedTab, label = "tab") { tab ->
                    when (tab) {
                        0 -> TimelineTab(
                            photos = photos,
                            myId = myId,
                            savedIds = savedIds,
                            onToggleSave = { id ->
                                savedIds = if (savedIds.contains(id))
                                    savedIds - id else savedIds + id
                            }
                        )
                        1 -> SavedTab(
                            photos = photos.filter { savedIds.contains(it.id) },
                            myId = myId,
                            onToggleSave = { id ->
                                savedIds = if (savedIds.contains(id))
                                    savedIds - id else savedIds + id
                            }
                        )
                        2 -> ReliveTab(
                            photos = photos,
                            myId = myId,
                            savedIds = savedIds,
                            onToggleSave = { id ->
                                savedIds = if (savedIds.contains(id))
                                    savedIds - id else savedIds + id
                            }
                        )
                        else -> TimelineTab(
                            photos = photos,
                            myId = myId,
                            savedIds = savedIds,
                            onToggleSave = { id ->
                                savedIds = if (savedIds.contains(id))
                                    savedIds - id else savedIds + id
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── LOADING / EMPTY ─────────────────────────────────────────────────────────

@Composable
fun LoadingHearts() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.8f, targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    tween(600, easing = EaseInOutSine), RepeatMode.Reverse
                ), label = "pulse"
            )
            Text("◆", fontSize = 48.sp, color = PinkPrimary, modifier = Modifier.scale(pulseScale))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Loading memories...", color = PinkPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun EmptyMemories() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◇", fontSize = 56.sp, color = PinkPrimary.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No memories yet!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PinkPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Start sending photos to build\nyour memory collection",
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── TIMELINE FILTER CHIPS ───────────────────────────────────────────────────

@Composable
fun TimelineFilterRow(selected: TimelineFilter, onSelect: (TimelineFilter) -> Unit) {
    val chips = listOf(
        TimelineFilter.ALL to "All",
        TimelineFilter.THIS_WEEK to "This Week",
        TimelineFilter.THIS_MONTH to "This Month",
        TimelineFilter.BY_YEAR to "By Year"
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(chips) { (filter, label) ->
            val isActive = selected == filter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isActive) PinkPrimary else Color.White)
                    .border(
                        width = 1.5.dp,
                        color = if (isActive) PinkPrimary else PinkMedium,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) Color.White else Color(0xFFC06080)
                )
            }
        }
    }
}

// ─── TIMELINE TAB ─────────────────────────────────────────────────────────────

@Composable
fun TimelineTab(
    photos: List<MemoryPhoto>,
    myId: String,
    savedIds: Set<String>,
    onToggleSave: (String) -> Unit
) {
    var fullscreenPhoto by remember { mutableStateOf<MemoryPhoto?>(null) }
    var activeFilter by remember { mutableStateOf(TimelineFilter.ALL) }

    val filteredPhotos = remember(photos, activeFilter) {
        val now = Calendar.getInstance()
        photos.filter { photo ->
            val ts = photo.timestamp?.toDate() ?: return@filter activeFilter == TimelineFilter.ALL
            val cal = Calendar.getInstance().apply { time = ts }
            when (activeFilter) {
                TimelineFilter.ALL -> true
                TimelineFilter.THIS_WEEK -> {
                    val diff = now.timeInMillis - cal.timeInMillis
                    diff <= 7L * 24 * 60 * 60 * 1000
                }
                TimelineFilter.THIS_MONTH ->
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                TimelineFilter.BY_YEAR ->
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            }
        }
    }

    val grouped = remember(filteredPhotos, activeFilter) {
        filteredPhotos.groupBy { photo ->
            val ts = photo.timestamp?.toDate() ?: return@groupBy "Unknown"
            val cal = Calendar.getInstance().apply { time = ts }
            val now = Calendar.getInstance()
            when {
                activeFilter == TimelineFilter.BY_YEAR ->
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(ts)
                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
                else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(ts)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TimelineFilterRow(selected = activeFilter, onSelect = { activeFilter = it })
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            grouped.forEach { (dateLabel, dayPhotos) ->
                item {
                    // Date divider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PinkPrimary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                dateLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PinkPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Divider(color = PinkMedium, modifier = Modifier.weight(1f))
                    }
                }
                items(dayPhotos, key = { it.id }) { photo ->
                    TimelinePhotoCard(
                        photo = photo,
                        myId = myId,
                        isSaved = savedIds.contains(photo.id),
                        onToggleSave = { onToggleSave(photo.id) },
                        onClick = { fullscreenPhoto = photo }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    fullscreenPhoto?.let { photo ->
        FullscreenPhotoViewer(
            photo = photo,
            myId = myId,
            isSaved = savedIds.contains(photo.id),
            onToggleSave = { onToggleSave(photo.id) },
            onClose = { fullscreenPhoto = null }
        )
    }
}

// ─── TIMELINE PHOTO CARD ─────────────────────────────────────────────────────

@Composable
fun TimelinePhotoCard(
    photo: MemoryPhoto,
    myId: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(photo.id) { photo.toBitmap() }
    val isSentByMe = photo.sender == myId
    val partnerName = if (myId == "swaroop") "Vidhi" else "Swaroop"
    val cardScale = remember { Animatable(0.95f) }
    LaunchedEffect(Unit) { cardScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale.value)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Memory photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).background(PinkMedium),
                contentAlignment = Alignment.Center
            ) { Text("◆", fontSize = 40.sp, color = PinkPrimary.copy(alpha = 0.4f)) }
        }

        // Gradient overlay bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))
                )
        )

        // Top-right action buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Heart / bookmark
            OutlineIconButton(
                symbol = if (isSaved) "♥" else "♡",
                contentDescription = "Save",
                active = isSaved,
                onClick = onToggleSave
            )
            // Download
            OutlineIconButton(
                symbol = "↓",
                contentDescription = "Download",
                onClick = {
                    bitmap?.let { bmp ->
                        saveToGallery(context, bmp)
                        Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Bottom meta
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            // Direction pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                IconPill(
                    icon = if (isSentByMe) "▲" else "▼",
                    label = if (isSentByMe) "You" else partnerName,
                    isSwaroop = isSentByMe
                )
                Text("→", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                IconPill(
                    icon = if (!isSentByMe) "▲" else "▼",
                    label = if (!isSentByMe) "You" else partnerName,
                    isSwaroop = !isSentByMe
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(photo.fullDate(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        }

        // Search/expand icon top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) { Text("⤢", color = Color.White, fontSize = 12.sp) }
    }
}

// ─── SAVED TAB ────────────────────────────────────────────────────────────────

@Composable
fun SavedTab(
    photos: List<MemoryPhoto>,
    myId: String,
    onToggleSave: (String) -> Unit
) {
    var fullscreenPhoto by remember { mutableStateOf<MemoryPhoto?>(null) }

    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♡", fontSize = 52.sp, color = PinkPrimary.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No saved photos yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PinkPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Tap the heart icon on any photo\nto bookmark it here",
                    fontSize = 13.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.7f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("♥  Bookmarked moments", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PinkPrimary)
                Text("${photos.size} photos", fontSize = 12.sp, color = Color(0xFFAAAAAA))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(photos, key = { it.id }) { photo ->
            TimelinePhotoCard(
                photo = photo,
                myId = myId,
                isSaved = true,
                onToggleSave = { onToggleSave(photo.id) },
                onClick = { fullscreenPhoto = photo }
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    fullscreenPhoto?.let { photo ->
        FullscreenPhotoViewer(
            photo = photo,
            myId = myId,
            isSaved = true,
            onToggleSave = { onToggleSave(photo.id) },
            onClose = { fullscreenPhoto = null }
        )
    }
}

// ─── RELIVE TAB ───────────────────────────────────────────────────────────────

@Composable
fun ReliveTab(
    photos: List<MemoryPhoto>,
    myId: String,
    savedIds: Set<String>,
    onToggleSave: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partnerName = if (myId == "swaroop") "Vidhi" else "Swaroop"

    var currentPhoto by remember { mutableStateOf<MemoryPhoto?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRevealing by remember { mutableStateOf(false) }
    var hasRevealed by remember { mutableStateOf(false) }
    val revealScale = remember { Animatable(0f) }
    val buttonScale = remember { Animatable(1f) }

    fun pickRandom() {
        scope.launch {
            isRevealing = true
            hasRevealed = false
            revealScale.snapTo(0f)
            val picked = photos.random()
            val bmp = withContext(Dispatchers.IO) { picked.toBitmap() }
            currentPhoto = picked
            currentBitmap = bmp
            revealScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
            isRevealing = false
            hasRevealed = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Stats cards ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Total
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${photos.size}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = PinkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("◆", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                        Text("Total moments", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }
            // Saved
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${savedIds.size}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = PinkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("♥", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                        Text("Bookmarked", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Intro card ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(PinkPrimary.copy(alpha = 0.12f), PinkMedium.copy(alpha = 0.25f))
                    )
                )
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("↺", fontSize = 32.sp, color = PinkPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Relive a Memory", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PinkPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap below to rediscover a random shared moment",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Photo reveal ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .scale(revealScale.value)
                .shadow(14.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
        ) {
            if (currentBitmap != null && hasRevealed) {
                Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = "Relived memory",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                startY = 120f
                            )
                        )
                )
                currentPhoto?.let { photo ->
                    val isSentByMe = photo.sender == myId
                    val senderName = if (isSentByMe) "You" else "$partnerName"
                    val receiverName = if (!isSentByMe) "You" else "$partnerName"

                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconPill(
                                icon = if (isSentByMe) "▲" else "▼",
                                label = senderName,
                                isSwaroop = isSentByMe
                            )
                            Text("→", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            IconPill(
                                icon = if (!isSentByMe) "▲" else "▼",
                                label = receiverName,
                                isSwaroop = !isSentByMe
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PinkPrimary.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("◷ ${photo.formattedDate()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(PinkMedium, PinkSoft))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteTransition = rememberInfiniteTransition(label = "heart")
                        val heartScale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                tween(800, easing = EaseInOutSine), RepeatMode.Reverse
                            ), label = "hs"
                        )
                        Text("◆", fontSize = 56.sp, color = PinkPrimary.copy(alpha = 0.4f), modifier = Modifier.scale(heartScale))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Your memory is waiting...", color = PinkPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Actions row (save / download / share) ────────────────────────────
        if (hasRevealed && currentPhoto != null) {
            val photo = currentPhoto!!
            val isSaved = savedIds.contains(photo.id)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSaved) PinkPrimary else Color.White)
                        .clickable { onToggleSave(photo.id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isSaved) "♥" else "♡", fontSize = 14.sp, color = if (isSaved) Color.White else PinkPrimary)
                        Text(if (isSaved) "Saved" else "Save", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isSaved) Color.White else PinkPrimary)
                    }
                }
                // Download
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF0F0F8))
                        .clickable {
                            currentBitmap?.let { bmp ->
                                saveToGallery(context, bmp)
                                Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("↓", fontSize = 14.sp, color = Color(0xFF5050AA))
                        Text("Download", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5050AA))
                    }
                }
                // Share
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF0F8F0))
                        .clickable { /* share intent */ }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("↗", fontSize = 14.sp, color = Color(0xFF207040))
                        Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF207040))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── Relive button ────────────────────────────────────────────────────
        Button(
            onClick = {
                if (!isRevealing) {
                    scope.launch {
                        buttonScale.animateTo(0.93f, tween(80))
                        buttonScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                    pickRandom()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .scale(buttonScale.value),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
            elevation = ButtonDefaults.buttonElevation(6.dp)
        ) {
            if (isRevealing) {
                val infiniteTransition = rememberInfiniteTransition(label = "spin")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r"
                )
                Text("↺", fontSize = 18.sp, color = Color.White,
                    modifier = Modifier.graphicsLayer(rotationZ = rotation))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finding a memory...", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text(
                    if (hasRevealed) "↺  Show Another Memory" else "◆  Relive a Memory",
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                )
            }
        }

        if (hasRevealed) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("from ${photos.size} shared memories", color = Color(0xFFAAAAAA), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ─── FULLSCREEN VIEWER ────────────────────────────────────────────────────────

@Composable
fun FullscreenPhotoViewer(
    photo: MemoryPhoto,
    myId: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(photo.id) { photo.toBitmap() }
    val isSentByMe = photo.sender == myId
    val partnerName = if (myId == "swaroop") "Vidhi" else "Swaroop"
    val senderName = if (isSentByMe) "You" else partnerName
    val receiverName = if (!isSentByMe) "You" else partnerName

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full memory",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            )
            // Bottom fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )

            // Top bar actions
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

                // Right actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Heart/save
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSaved) PinkPrimary else Color.White.copy(alpha = 0.2f))
                            .clickable { onToggleSave() },
                        contentAlignment = Alignment.Center
                    ) { Text(if (isSaved) "♥" else "♡", color = if (isSaved) Color.White else PinkPrimary, fontSize = 16.sp) }

                    // Download
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable {
                                bitmap?.let {
                                    saveToGallery(context, it)
                                    Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("↓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }

            // Bottom meta
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconPill(icon = if (isSentByMe) "▲" else "▼", label = senderName, isSwaroop = isSentByMe)
                    Text("→", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    IconPill(icon = if (!isSentByMe) "▲" else "▼", label = receiverName, isSwaroop = !isSentByMe)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(photo.fullDate(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PinkPrimary.copy(alpha = 0.9f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("◷ ${photo.formattedDate()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── SAVE TO GALLERY ─────────────────────────────────────────────────────────

fun saveToGallery(context: Context, bitmap: Bitmap) {
    val filename = "OurMoments_${System.currentTimeMillis()}.jpg"
    val outputStream: OutputStream?

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OurMoments")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
    } else {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ourDir = File(imagesDir, "OurMoments").also { it.mkdirs() }
        outputStream = File(ourDir, filename).outputStream()
    }

    outputStream?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
}