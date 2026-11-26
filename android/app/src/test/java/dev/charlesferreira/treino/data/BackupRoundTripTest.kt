package dev.charlesferreira.treino.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.charlesferreira.treino.domain.backup.BackupExporter
import dev.charlesferreira.treino.domain.backup.BackupImporter
import dev.charlesferreira.treino.domain.backup.BackupSource
import dev.charlesferreira.treino.domain.backup.TreinoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

/** The Backup screen's path: import a PWA file, export format 3, import that again: same data. */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: TreinoDatabase
    private lateinit var repo: TreinoRepository
    private lateinit var scope: CoroutineScope

    private object NoTimer : TimerSwitches {
        override fun setSound(enabled: Boolean) = Unit
        override fun setVibration(enabled: Boolean) = Unit
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TreinoDatabase.inMemory(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        repo = TreinoRepository(db, SettingsStore(store, NoTimer), today = { "2026-11-26" })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun assertSameTables(expected: TreinoData, actual: TreinoData) {
        assertEquals(expected.exercises.sortedBy { it.id }, actual.exercises.sortedBy { it.id })
        assertEquals(expected.workouts.sortedBy { it.id }, actual.workouts.sortedBy { it.id })
        assertEquals(expected.workoutItems.sortedBy { it.id }, actual.workoutItems.sortedBy { it.id })
        assertEquals(expected.sessions.sortedBy { it.id }, actual.sessions.sortedBy { it.id })
        assertEquals(expected.setEntries.sortedBy { it.id }, actual.setEntries.sortedBy { it.id })
        assertEquals(expected.timeEntries.sortedBy { it.id }, actual.timeEntries.sortedBy { it.id })
    }

    @Test
    fun `import, export, import gives the same data`() = runBlocking<Unit> {
        val first = BackupImporter.parse(File("../domain/src/test/resources/backup-format2.json").readText())
        repo.replaceAll(first.data)
        // Something only the app has: a set with a time, a session start, an edit.
        repo.startSession("2026-11-26", at = 1_000L)
        val ex = first.data.exercises.first { it.legacyId == "supino-inclinado-halteres" }
        repo.logSet("2026-11-26", ex.id, 20.0, 10, loggedAt = 2_000L)
        val before = repo.snapshot()

        val exported = BackupExporter.export(before, "2026-11-26")
        val again = BackupImporter.parse(exported)
        assertEquals(BackupSource.FORMAT_3, again.source)
        assertEquals("2026-11-26", again.exportedAt)
        assertSameTables(before, again.data)
        assertEquals(before.settings, again.data.settings)

        repo.replaceAll(again.data)
        val after = repo.snapshot()
        assertSameTables(before, after)
        assertEquals(before.settings, after.settings)
    }

    @Test
    fun `the review's correction halves the session and remembers it`() = runBlocking<Unit> {
        val data = BackupImporter.parse(File("../domain/src/test/resources/backup-format2.json").readText()).data
        repo.replaceAll(data)
        val sets = repo.snapshot().setEntries.take(2)
        repo.updateSets(sets.map { it.copy(weightKg = it.weightKg / 2) })
        val byId = repo.snapshot().setEntries.associateBy { it.id }
        sets.forEach { assertEquals(it.weightKg / 2, byId.getValue(it.id).weightKg) }

        repo.settings.addReviewedLoads(listOf("a:b"))
        repo.settings.addReviewedLoads(listOf("c:d"))
        repo.settings.setLastExport("2026-11-26")
        val s = repo.settings.current()
        assertEquals(setOf("a:b", "c:d"), s.reviewedLoads)
        assertEquals("2026-11-26", s.lastExport)
    }
}
