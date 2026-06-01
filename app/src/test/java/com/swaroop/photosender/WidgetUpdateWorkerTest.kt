package com.swaroop.photosender



import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WidgetUpdateWorkerTest {

    private lateinit var context: Context
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockCollection: CollectionReference
    private lateinit var mockQuery: Query
    private lateinit var mockSnapshot: QuerySnapshot
    private lateinit var mockDocument: DocumentSnapshot

    // Valid 1x1 PNG in Base64 — decodes to a real bitmap
    private val VALID_BASE64_PNG =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Mock Firestore chain
        mockFirestore = mockk(relaxed = true)
        mockCollection = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)
        mockSnapshot = mockk(relaxed = true)
        mockDocument = mockk(relaxed = true)

        mockkStatic(Firebase::class)
        every { Firebase.firestore } returns mockFirestore
        every { mockFirestore.collection("photos") } returns mockCollection
        every { mockCollection.whereEqualTo("receiver", any<String>()) } returns mockQuery
        every { mockQuery.orderBy("timestamp", Query.Direction.DESCENDING) } returns mockQuery
        every { mockQuery.limit(1) } returns mockQuery

        // Mock UserPrefs (it's an object so mockkObject works)
        mockkObject(UserPrefs)

        // Mock PhotoWidget companion object
        mockkConstructor(PhotoWidget::class)
        mockkStatic("com.swaroop.photosender.PhotoWidget")
        coEvery { PhotoWidget.updateWidget(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── Happy Path ────────────────────────────────────────────────────────────

    @Test
    fun `doWork returns success when valid photo exists for receiver`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.getString("base64") } returns VALID_BASE64_PNG

        val result = buildWorker().doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun `doWork writes widget_photo png file to filesDir on success`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.getString("base64") } returns VALID_BASE64_PNG

        buildWorker().doWork()

        val file = File(context.filesDir, "widget_photo.png")
        assert(file.exists()) { "widget_photo.png should exist in filesDir" }
        assert(file.length() > 0) { "widget_photo.png should not be empty" }
    }

    @Test
    fun `doWork queries Firestore using the correct receiver id`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "vidhi"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.getString("base64") } returns VALID_BASE64_PNG

        buildWorker().doWork()

        verify { mockCollection.whereEqualTo("receiver", "vidhi") }
    }

    // ── Edge Cases ────────────────────────────────────────────────────────────

    @Test
    fun `doWork returns failure when myId is null`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns null

        val result = buildWorker().doWork()

        assertEquals(Result.failure(), result)
        // Firestore should never be touched
        verify(exactly = 0) { mockFirestore.collection(any()) }
    }

    @Test
    fun `doWork returns failure when Firestore snapshot is empty`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns emptyList()

        val result = buildWorker().doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns failure when base64 field is null in document`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.getString("base64") } returns null

        val result = buildWorker().doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns failure when base64 string is corrupted`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.getString("base64") } returns "not_valid_base64!!!"

        val result = buildWorker().doWork()

        // BitmapFactory.decodeByteArray returns null → Result.failure()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns retry when Firestore throws network exception`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } throws Exception("Network timeout")

        val result = buildWorker().doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `doWork returns retry when PhotoWidget updateWidget throws`() = runBlocking {
        every { UserPrefs.getMyId(context) } returns "swaroop"
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.getString("base64") } returns VALID_BASE64_PNG
        coEvery { PhotoWidget.updateWidget(any(), any()) } throws Exception("Widget failed")

        val result = buildWorker().doWork()

        assertEquals(Result.retry(), result)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildWorker(): WidgetUpdateWorker =
        TestListenableWorkerBuilder<WidgetUpdateWorker>(context).build()
}