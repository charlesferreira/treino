package dev.charlesferreira.treino.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.backup.BackupImporter
import dev.charlesferreira.treino.domain.backup.Catalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Room in memory under Robolectric (SDK 36 image; the app compiles against 37). */
@RunWith(RobolectricTestRunner::class)
class TreinoRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: TreinoDatabase
    private lateinit var repo: TreinoRepository
    private lateinit var scope: CoroutineScope
    private val timer = FakeTimer()
    private var today = "2026-11-26"
    private var clock = 1_000_000L

    private class FakeTimer : TimerSwitches {
        var sound: Boolean? = null
        var vibration: Boolean? = null
        override fun setSound(enabled: Boolean) { sound = enabled }
        override fun setVibration(enabled: Boolean) { vibration = enabled }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TreinoDatabase.inMemory(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        repo = TreinoRepository(db, SettingsStore(store, timer), today = { today }, now = { clock++ })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun exercise(name: String) = repo.createExercise(name)

    @Test
    fun `first use - empty until the catalog is seeded, and seeding again keeps edits`() = runBlocking<Unit> {
        assertFalse(repo.observeHasData().first())
        repo.seedCatalog()
        assertTrue(repo.observeHasData().first())
        val all = repo.observeExercises().first()
        assertEquals(Catalog.exercises.size, all.size)

        val first = Catalog.exercises.first()
        repo.updateExercise(first.copy(name = "Renomeado", stepKg = 2.5))
        repo.seedCatalog()
        assertEquals("Renomeado", repo.exercise(first.id)!!.name)
        assertEquals(2.5, repo.exercise(first.id)!!.stepKg)
        assertEquals(Catalog.exercises.size, repo.observeExercises().first().size)
    }

    @Test
    fun `enums and aliases round trip through the converters`() = runBlocking<Unit> {
        val e = Catalog.exercises.first { it.aliases.isNotEmpty() }
        repo.updateExercise(e.copy(kind = ExerciseKind.TIME, equipment = Equipment.CABLE))
        val back = repo.exercise(e.id)!!
        assertEquals(e.aliases, back.aliases)
        assertEquals(ExerciseKind.TIME, back.kind)
        assertEquals(Equipment.CABLE, back.equipment)
    }

    @Test
    fun `sets - one session per day, positions per exercise, edit and delete renumber`() = runBlocking<Unit> {
        val supino = exercise("Supino com halteres")
        val remada = exercise("Remada baixa")
        val a = repo.logSet(today, supino.id, 18.0, 10)
        val b = repo.logSet(today, supino.id, 18.0, 9)
        val c = repo.logSet(today, remada.id, 50.0, 12)
        val d = repo.logSet(today, supino.id, 16.0, 10)
        assertEquals(listOf(0, 1, 0, 2), listOf(a, b, c, d).map { it.position })
        assertEquals(setOf(a.sessionId), setOf(b.sessionId, c.sessionId, d.sessionId))
        assertTrue(supino.countsTwoDumbbells)

        repo.updateSet(b.id, 20.0, 8)
        repo.deleteSet(a.id)
        val sets = repo.observeSets(a.sessionId).first().filter { it.exerciseId == supino.id }.sortedBy { it.position }
        assertEquals(listOf(b.id to 0, d.id to 1), sets.map { it.id to it.position })
        assertEquals(20.0 to 8, sets[0].weightKg to sets[0].reps)
        assertNotNull(sets[0].loggedAt)
    }

    @Test
    fun `times, note, start and finish`() = runBlocking<Unit> {
        val corrida = repo.createExercise("Corrida", kind = ExerciseKind.TIME)
        val t = repo.logTime(today, corrida.id, 35.0, km = 5.2, place = "  parque ")
        assertEquals("parque", t.place)
        repo.updateTime(t.id, 40.0, null, "")
        val times = repo.observeTimes(t.sessionId).first()
        assertEquals(40.0, times.single().minutes)
        assertNull(times.single().place)

        repo.setNote(today, "  Ombro incomodou. ")
        val started = repo.startSession(today, at = 10L)
        assertEquals(10L, repo.startSession(today, at = 99L).startedAt) // only once
        repo.finishSession(today, at = 20L)
        val s = repo.observeSession(today).first()!!
        assertEquals(started.id, s.id)
        assertEquals("Ombro incomodou.", s.note)
        assertEquals(10L to 20L, s.startedAt to s.endedAt)

        repo.setNote(today, "   ")
        assertNull(repo.observeSession(today).first()!!.note)
        repo.deleteTime(t.id)
        assertTrue(repo.observeTimes(t.sessionId).first().isEmpty())
    }

    @Test
    fun `delete the day - session, sets, times and note go, other days stay`() = runBlocking<Unit> {
        val supino = exercise("Supino reto")
        val corrida = repo.createExercise("Corrida", kind = ExerciseKind.TIME)
        val yesterday = repo.logSet("2026-11-25", supino.id, 20.0, 10)
        repo.startSession(today)
        val set = repo.logSet(today, supino.id, 22.0, 10)
        repo.logTime(today, corrida.id, 20.0)
        repo.setNote(today, "Nota")
        repo.finishSession(today)

        repo.deleteDay(today)
        assertNull(repo.observeSession(today).first())
        assertTrue(repo.observeSets(set.sessionId).first().isEmpty())
        assertTrue(repo.observeTimes(set.sessionId).first().isEmpty())
        assertEquals(listOf("2026-11-25"), repo.observeDays().first().map { it.session.date })
        assertEquals(listOf(yesterday.id), repo.observeAllSets().first().map { it.id })
        assertTrue(repo.observeAllTimes().first().isEmpty())

        // The day starts over: a new session, nothing started.
        val again = repo.logSet(today, supino.id, 22.0, 8)
        assertEquals(0, again.position)
        assertNull(repo.observeSession(today).first()!!.startedAt)
    }

    @Test
    fun `start another workout - the day goes on with it, sets kept, end cleared`() = runBlocking<Unit> {
        val a = repo.createWorkout("Superior A")
        val b = repo.createWorkout("Inferior A")
        val ex = exercise("Rosca martelo")
        repo.startSession(today, at = 10L)
        repo.logSet(today, ex.id, 14.0, 12)
        repo.finishSession(today, at = 20L)
        assertEquals(a.id, repo.observeSession(today).first()!!.workoutId)

        val s = repo.reopenSession(today, b.id, at = 30L)
        assertEquals(b.id, s.workoutId)
        assertEquals(10L, s.startedAt)
        assertNull(s.endedAt)
        assertEquals(1, repo.observeSets(s.id).first().size)
        assertEquals(b.id, repo.observeWorkoutFor(today).first()!!.id)
    }

    @Test
    fun `today's workout follows the rotation and the manual choice`() = runBlocking<Unit> {
        val a = repo.createWorkout("Superior A")
        val b = repo.createWorkout("Inferior A")
        val c = repo.createWorkout("Superior B")
        val ex = exercise("Rosca martelo")
        assertEquals(a.id, repo.observeWorkoutFor(today).first()!!.id)

        // A session is created with the day's suggestion.
        today = "2026-11-24"
        repo.logSet(today, ex.id, 14.0, 12)
        assertEquals(a.id, repo.observeSession(today).first()!!.workoutId)

        today = "2026-11-26"
        assertEquals(b.id, repo.observeWorkoutFor(today).first()!!.id)
        repo.chooseWorkout(today, c.id)
        assertEquals(c.id, repo.observeWorkoutFor(today).first()!!.id)
        assertEquals(b.id, repo.observeNextInSequence(today).first()!!.id)
        // An empty session does not move the rotation.
        assertEquals(b.id, repo.observeWorkoutFor("2026-11-27").first()!!.id)
    }

    @Test
    fun `workouts and items - create, reorder, delete repacks, cascades`() = runBlocking<Unit> {
        val a = repo.createWorkout(" A ")
        val b = repo.createWorkout("B")
        val c = repo.createWorkout("C")
        assertEquals("A", a.name)
        repo.reorderWorkouts(listOf(c.id, a.id))
        assertEquals(listOf("C", "A", "B"), repo.observeWorkouts().first().map { it.name })
        assertEquals(listOf(0, 1, 2), repo.observeWorkouts().first().map { it.position })
        repo.renameWorkout(b.id, "B2")

        val e1 = exercise("Supino reto")
        val e2 = exercise("Crucifixo")
        val e3 = exercise("Tríceps corda")
        val i1 = repo.addItem(a.id, e1.id, sets = 3, repsMin = 8, repsMax = 12)
        val i2 = repo.addItem(a.id, e2.id)
        val i3 = repo.addItem(a.id, e3.id)
        repo.reorderItems(a.id, listOf(i3.id, i1.id, i2.id))
        assertEquals(listOf(i3.id, i1.id, i2.id), repo.observeItems(a.id).first().map { it.id })
        repo.updateItem(i1.copy(sets = 4, restSec = 120, position = 99, workoutId = c.id))
        val stored = repo.observeItems(a.id).first().first { it.id == i1.id }
        assertEquals(4 to 120, stored.sets to stored.restSec)
        assertEquals(1, stored.position)
        repo.deleteItem(i3.id)
        assertEquals(listOf(i1.id to 0, i2.id to 1), repo.observeItems(a.id).first().map { it.id to it.position })

        // Deleting a workout: items go, days stay without a workout, the rotation is repacked.
        repo.chooseWorkout(today, a.id)
        repo.deleteWorkout(a.id)
        assertTrue(repo.observeItems(a.id).first().isEmpty())
        assertNull(repo.observeSession(today).first()!!.workoutId)
        assertEquals(listOf("C" to 0, "B2" to 1), repo.observeWorkouts().first().map { it.name to it.position })
    }

    @Test
    fun `an exercise with history cannot be deleted, one without leaves the workouts`() = runBlocking<Unit> {
        val w = repo.createWorkout("A")
        val used = exercise("Supino")
        val free = exercise("Crucifixo")
        val other = exercise("Remada")
        repo.addItem(w.id, free.id)
        repo.addItem(w.id, other.id)
        repo.logSet(today, used.id, 40.0, 10)
        assertFalse(repo.deleteExercise(used.id))
        assertNotNull(repo.exercise(used.id))
        assertTrue(repo.deleteExercise(free.id))
        assertNull(repo.exercise(free.id))
        assertEquals(listOf(other.id to 0), repo.observeItems(w.id).first().map { it.exerciseId to it.position })
    }

    @Test
    fun `days list and exercise history`() = runBlocking<Unit> {
        val w = repo.createWorkout("Superior A")
        val ex = exercise("Rosca martelo")
        repo.logSet("2026-11-20", ex.id, 12.0, 12)
        repo.logSet("2026-11-20", ex.id, 12.0, 10)
        repo.logSet("2026-11-24", ex.id, 14.0, 12)
        repo.setNote("2026-11-25", "Descanso")
        repo.ensureSession("2026-11-26") // empty: not a day in the list

        val days = repo.observeDays().first()
        assertEquals(listOf("2026-11-25", "2026-11-24", "2026-11-20"), days.map { it.session.date })
        assertEquals(listOf(0, 1, 2), days.map { it.setCount })
        assertEquals("Superior A", days.last().workoutName)
        assertEquals(w.id, days.last().session.workoutId)

        val history = repo.observeExerciseHistory(ex.id).first()
        assertEquals(listOf("2026-11-20", "2026-11-24"), history.map { it.date })
        assertEquals(listOf(12, 10), history.first().sets.map { it.reps })

        val current = repo.ensureSession("2026-11-26")
        assertEquals(14.0 to 12, repo.prefill(ex.id, 0, current.id)!!.let { it.weightKg to it.reps })
        assertEquals(14.0 to 12, repo.prefill(ex.id, 1, current.id)!!.let { it.weightKg to it.reps })
    }

    @Test
    fun `replaceAll imports a PWA backup and snapshot gives it back`() = runBlocking<Unit> {
        // Something that must disappear.
        repo.logSet(today, exercise("Velho").id, 10.0, 10)

        val text = File("../domain/src/test/resources/backup-format2.json").readText()
        val data = BackupImporter.parse(text).data
        repo.replaceAll(data)

        val snap = repo.snapshot()
        assertEquals(data.exercises.sortedBy { it.id }, snap.exercises.sortedBy { it.id })
        assertEquals(data.workouts.sortedBy { it.id }, snap.workouts.sortedBy { it.id })
        assertEquals(data.workoutItems.sortedBy { it.id }, snap.workoutItems.sortedBy { it.id })
        assertEquals(data.sessions.sortedBy { it.id }, snap.sessions.sortedBy { it.id })
        assertEquals(data.setEntries.sortedBy { it.id }, snap.setEntries.sortedBy { it.id })
        assertEquals(data.timeEntries.sortedBy { it.id }, snap.timeEntries.sortedBy { it.id })
        assertTrue(snap.exercises.none { it.name == "Velho" })

        // The backup said sound on and the name; the rest keeps the defaults.
        assertEquals("Charles", snap.settings.profileName)
        assertEquals(true, snap.settings.sound)
        assertEquals(true, timer.sound)
        assertEquals(true, timer.vibration)

        // Importing twice gives the same data (deterministic UUIDs).
        repo.replaceAll(data)
        assertEquals(data.setEntries.size, repo.snapshot().setEntries.size)
    }

    @Test
    fun `a broken backup rolls back and keeps what was there`() = runBlocking<Unit> {
        val ex = exercise("Supino")
        repo.logSet(today, ex.id, 40.0, 10)
        val data = BackupImporter.parse(File("../domain/src/test/resources/backup-format2.json").readText()).data
        val broken = data.copy(setEntries = data.setEntries + data.setEntries.first().copy(id = "x", exerciseId = "missing"))
        assertFailsWith<Exception> { repo.replaceAll(broken) }
        assertEquals(listOf("Supino"), repo.snapshot().exercises.map { it.name })
    }

    @Test
    fun `settings mirror sound and vibration into the timer`() = runBlocking<Unit> {
        val settings = repo.settings
        assertEquals(Settings(), settings.current())
        settings.setSound(false)
        settings.setVibration(false)
        settings.setKeepScreenOn(true)
        settings.setProfileName("  Ana Clara ")
        assertEquals(Settings(sound = false, vibration = false, keepScreenOn = true, profileName = "Ana Clara"), settings.current())
        assertEquals(false, timer.sound)
        assertEquals(false, timer.vibration)
    }
}
