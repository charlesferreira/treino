package dev.charlesferreira.treino.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Steps
import dev.charlesferreira.treino.ui.workouts.EquipmentDraft
import dev.charlesferreira.treino.ui.workouts.ItemDraft
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Treinos tab against Room: the rotation's order drives Hoje, duplicating, the equipment. */
@RunWith(RobolectricTestRunner::class)
class WorkoutsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: TreinoDatabase
    private lateinit var repo: TreinoRepository
    private lateinit var scope: CoroutineScope
    private var today = "2026-11-26"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TreinoDatabase.inMemory(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        val timer = object : TimerSwitches {
            override fun setSound(enabled: Boolean) = Unit
            override fun setVibration(enabled: Boolean) = Unit
        }
        var clock = 1_000_000L
        repo = TreinoRepository(db, SettingsStore(store, timer), today = { today }, now = { clock++ })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `reordering the rotation changes what Hoje suggests and the upcoming mark`() = runBlocking<Unit> {
        val a = repo.createWorkout("A")
        val b = repo.createWorkout("B")
        val c = repo.createWorkout("C")
        val ex = repo.createExercise("Supino")
        repo.logSet("2026-11-24", ex.id, 40.0, 10)
        repo.chooseWorkout("2026-11-24", a.id)

        assertEquals(b.id, repo.observeWorkoutFor(today).first()?.id)
        assertEquals(b.id, repo.observeUpcomingWorkout().first()?.id)

        repo.reorderWorkouts(listOf(a.id, c.id, b.id))
        assertEquals(listOf("A", "C", "B"), repo.observeWorkouts().first().map { it.name })
        assertEquals(c.id, repo.observeWorkoutFor(today).first()?.id)
        assertEquals(c.id, repo.observeUpcomingWorkout().first()?.id)

        // Once today has a set, "próximo" moves past today's workout; Hoje keeps today's.
        repo.logSet(today, ex.id, 40.0, 10)
        assertEquals(c.id, repo.observeWorkoutFor(today).first()?.id)
        assertEquals(b.id, repo.observeUpcomingWorkout().first()?.id)
    }

    @Test
    fun `items reorder and duplicate copies the workout right after the original`() = runBlocking<Unit> {
        val a = repo.createWorkout("Superior")
        val b = repo.createWorkout("Pernas")
        val e1 = repo.createExercise("Supino")
        val e2 = repo.createExercise("Remada")
        val e3 = repo.createExercise("Rosca")
        val i1 = repo.addItem(a.id, e1.id, sets = 3, repsMin = 8, repsMax = 12, restSec = 120)
        val i2 = repo.addItem(a.id, e2.id)
        val i3 = repo.addItem(a.id, e3.id)
        repo.reorderItems(a.id, listOf(i3.id, i1.id, i2.id))
        assertEquals(listOf(e3.id, e1.id, e2.id), repo.observeItems(a.id).first().map { it.exerciseId })

        val copy = assertNotNull(repo.duplicateWorkout(a.id))
        assertEquals("Superior (cópia)", copy.name)
        assertEquals(listOf("Superior", "Superior (cópia)", "Pernas"), repo.observeWorkouts().first().map { it.name })
        assertEquals(listOf(0, 1, 2), repo.observeWorkouts().first().map { it.position })
        val copied = repo.observeItems(copy.id).first()
        assertEquals(listOf(e3.id, e1.id, e2.id), copied.map { it.exerciseId })
        assertTrue(copied.none { it.id in setOf(i1.id, i2.id, i3.id) })
        assertEquals(120, copied[1].restSec)

        // The copy is independent: deleting the original keeps it.
        repo.deleteWorkout(a.id)
        assertEquals(3, repo.observeItems(copy.id).first().size)
        assertEquals(listOf(copy.id, b.id), repo.observeWorkouts().first().map { it.id })
    }

    @Test
    fun `equipment saved from the sheet changes the exercise and the item keeps its draft`() = runBlocking<Unit> {
        val w = repo.createWorkout("Superior")
        val ex = repo.createExercise("Rosca martelo", equipment = Equipment.DUMBBELL)
        assertEquals(Equipment.DUMBBELL, ex.equipment)
        val item = repo.addItem(w.id, ex.id, sets = 2, repsMin = 10, repsMax = 12, restSec = 90)

        // Machine with a 7,5 kg plate, step "1 placa".
        val machine = EquipmentDraft.of(ex).select(Equipment.MACHINE).platePlus().platePlus().platePlus().platePlus().platePlus()
        repo.updateExercise(machine.applyTo(ex))
        val stored = assertNotNull(repo.exercise(ex.id))
        assertEquals(Equipment.MACHINE, stored.equipment)
        assertFalse(stored.countsTwoDumbbells)
        assertEquals(7.5, Steps.of(stored).kg)
        assertEquals(1, Steps.of(stored).plates)

        // Back to dumbbells with a 2,5 kg step, and the item's draft saved with it.
        val dumbbell = EquipmentDraft.of(stored).select(Equipment.DUMBBELL).chooseStep(2.5).applyTo(stored)
        repo.updateExercise(dumbbell)
        val draft = ItemDraft.of(item, dumbbell).setsPlus().copy(repsMin = "6", repsMax = "8")
        repo.updateItem(assertNotNull(draft.applyTo(item)))
        val back = assertNotNull(repo.exercise(ex.id))
        assertTrue(back.countsTwoDumbbells)
        assertEquals(2.5, Steps.of(back).kg)
        assertEquals(17.5, Steps.up(15.0, back))
        val savedItem = repo.observeItems(w.id).first().single()
        assertEquals(Triple(3, 6, 8), Triple(savedItem.sets, savedItem.repsMin, savedItem.repsMax))
        assertEquals(0, savedItem.position)
    }
}
