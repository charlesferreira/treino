package dev.charlesferreira.treino.ui.evolution

import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvolutionStateTest {
    private val curl = Exercise("curl", name = "Rosca martelo", group = "biceps", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val pull = Exercise("pull", name = "Puxada supinada", group = "costas", equipment = Equipment.CABLE)
    private val press = Exercise("press", name = "Leg press", group = "quadriceps", equipment = Equipment.MACHINE)
    private val run = Exercise("run", name = "Corrida", group = "cardio", kind = ExerciseKind.TIME)
    private val upper = Workout("w1", name = "Superior B", position = 0)
    private val cardio = Workout("w2", name = "Corrida Z2", position = 1)

    private val min = 60_000L

    /** Superior B on 12, 19 and 26 nov (the first imported), a run on 25 nov, legs on 1 out. */
    private val sessions = listOf(
        Session("s1", "2026-11-12", "w1"),
        Session("s2", "2026-11-19", "w1", startedAt = 0L, endedAt = 52 * min),
        Session("s3", "2026-11-26", "w1", startedAt = 100 * min, endedAt = 158 * min),
        Session("r1", "2026-11-25", "w2", startedAt = 0L),
        Session("l1", "2026-10-01", null, startedAt = 0L, endedAt = 60 * min),
        Session("n1", "2026-11-20", null, note = "Folga"),
        Session("e1", "2026-11-21", null),
    )
    private val sets = listOf(
        SetEntry("a", "s1", "curl", 0, 12.0, 12),
        SetEntry("b", "s1", "pull", 0, 59.0, 10),
        SetEntry("c", "s2", "curl", 0, 14.0, 12),
        SetEntry("d", "s2", "pull", 0, 64.0, 10, loggedAt = 10 * min),
        SetEntry("e", "s3", "pull", 0, 64.0, 10, loggedAt = 101 * min),
        SetEntry("f", "s3", "curl", 0, 16.0, 11, loggedAt = 120 * min),
        SetEntry("g", "s3", "curl", 1, 16.0, 10, loggedAt = 123 * min),
        SetEntry("h", "l1", "press", 0, 180.0, 10),
    )
    private val times = listOf(TimeEntry("t", "r1", "run", 0, 35.0, km = 5.2, place = "esteira", loggedAt = 35 * min))
    private val data = EvolutionData(sessions, listOf(upper, cardio), listOf(curl, pull, press, run), sets, times)

    @Test
    fun `calendario tem tres semanas terminando na de hoje`() {
        val month = buildMonth("2026-11-26", dayMarks(data))
        assertEquals("Novembro", month.title)
        // 12, 19, 25 e 26 nov; a nota sozinha (20) e o dia vazio (21) não contam.
        assertEquals(4, month.count)
        assertEquals(3, month.weeks.size)
        assertEquals("2026-11-09", month.weeks[0][0].date)
        assertEquals("2026-11-29", month.weeks[2][6].date)
        val byDate = month.weeks.flatten().associateBy { it.date }
        assertEquals(DayMark.LOAD, byDate.getValue("2026-11-26").mark)
        assertTrue(byDate.getValue("2026-11-26").today)
        assertEquals(DayMark.TIME, byDate.getValue("2026-11-25").mark)
        assertEquals(DayMark.NONE, byDate.getValue("2026-11-20").mark)
        assertFalse(byDate.getValue("2026-11-27").today)
    }

    @Test
    fun `dias com fatos e duracao so quando ha horario`() {
        val days = buildDays(data, "2026-11-26")
        assertEquals(listOf("2026-11-26", "2026-11-25", "2026-11-20", "2026-11-19", "2026-11-12", "2026-10-01"), days.map { it.date })

        val latest = days[0]
        assertEquals("26", latest.day)
        assertEquals("qui", latest.weekday)
        assertEquals("Superior B", latest.title)
        assertEquals(
            listOf(Fact(value = "3", after = " séries"), Fact(value = "58", after = " min"), Fact(before = "volume ", value = "1.312 kg")),
            latest.facts,
        )
        // Rosca subiu (14 → 16), puxada ficou; na ordem em que foram feitos.
        assertEquals(listOf(DayLine("Puxada supinada", 64.0, false), DayLine("Rosca martelo", 16.0, true)), latest.lines)

        assertEquals(listOf(Fact(value = "35", after = " min"), Fact(value = "5,2", after = " km"), Fact(before = "esteira")), days[1].facts)
        assertEquals("Corrida Z2", days[1].title)
        assertTrue(days[1].lines.isEmpty())

        assertTrue(days[2].facts.isEmpty())
        // Importado: sem horário, sem duração.
        assertEquals(listOf(Fact(value = "2", after = " séries"), Fact(before = "volume ", value = "878 kg")), days[4].facts)
        // Sem treino: o nome do primeiro exercício.
        assertEquals("Leg press", days[5].title)
        assertEquals(listOf("2026-11", "2026-10"), days.map { it.monthKey }.distinct())
        assertEquals("Outubro", days[5].monthLabel)
    }

    @Test
    fun `exercicios com subida recente primeiro e o resto igual desde`() {
        val ui = buildExercises(data, "2026-11-26")
        assertEquals(listOf("Rosca martelo", "Puxada supinada", "Leg press"), ui.rows.map { it.name })
        val curlRow = ui.rows[0]
        assertTrue(curlRow.up)
        assertEquals("+2 kg em 26 nov", curlRow.fact)
        assertEquals(listOf(12.0, 14.0, 16.0), curlRow.spark)
        assertEquals(16.0, curlRow.currentKg)
        assertTrue(curlRow.perDumbbell)
        // A puxada subiu em 19 nov e ficou: continua sendo a subida recente.
        assertTrue(ui.rows[1].up)
        assertEquals("+5 kg em 19 nov", ui.rows[1].fact)
        assertEquals("1ª vez em 1 out", ui.rows[2].fact)
        assertFalse(ui.rows[2].up)

        assertEquals(2, ui.overview.risen)
        // 12, 19, 25 e 26 nov dentro de 30 dias.
        assertEquals(4, ui.overview.workouts)
        // 1 out (180 × 10 = 1.800) é o período anterior; o atual tem 878 + 976 + 1.312 = 3.166.
        assertEquals(76, ui.overview.volumeChangePercent)
        assertEquals(listOf(BodyFilter.ALL, BodyFilter.UPPER, BodyFilter.LEGS, BodyFilter.ARMS), ui.filters)
    }

    @Test
    fun `carga que voltou ao normal nao conta como subida`() {
        val s = listOf(Session("x1", "2026-11-01"), Session("x2", "2026-11-10"), Session("x3", "2026-11-20"))
        val st = listOf(
            SetEntry("1", "x1", "pull", 0, 60.0, 10),
            SetEntry("2", "x2", "pull", 0, 65.0, 10),
            SetEntry("3", "x3", "pull", 0, 60.0, 10),
        )
        val row = buildExercises(EvolutionData(s, emptyList(), listOf(pull), st, emptyList()), "2026-11-26").rows.single()
        assertFalse(row.up)
        assertEquals("igual desde 20 nov", row.fact)
    }

    @Test
    fun `filtro por grupo`() {
        assertTrue(BodyFilter.ARMS.matches("biceps"))
        assertFalse(BodyFilter.UPPER.matches("biceps"))
        assertTrue(BodyFilter.LEGS.matches("gluteos"))
        assertTrue(BodyFilter.ALL.matches("meus"))
        assertEquals("+6%", percentText(6))
        assertEquals("−4%", percentText(-4))
    }

    @Test
    fun `dia do historico com unidade e ordem do treino`() {
        val items = listOf(WorkoutItem("i1", "w1", "curl", 0, 2), WorkoutItem("i2", "w1", "pull", 1, 1))
        val ui = buildDayDetail(
            "2026-11-26", sessions[2], upper, items, data.exercisesById,
            sets.filter { it.sessionId == "s3" }, emptyList(),
        )
        assertEquals("26 nov, quinta", ui.title)
        assertEquals("Superior B", ui.workoutName)
        assertEquals(58, ui.minutes)
        assertEquals(3, ui.seriesCount)
        assertEquals(1312.0, ui.volumeKg)
        assertEquals(listOf("Rosca martelo", "Puxada supinada"), ui.exercises.map { it.exercise.name })
        assertEquals(listOf("kg por halter", "kg"), ui.exercises.map { it.unit })
        assertEquals("16 × 11", setText(ui.exercises[0].sets[0]))

        val runDay = buildDayDetail("2026-11-25", sessions[3], cardio, emptyList(), data.exercisesById, emptyList(), times)
        assertNull(runDay.volumeKg)
        assertNull(runDay.exercises.single().unit)
        assertEquals("35 min, 5,2 km, esteira", timeText(runDay.exercises.single().times.single()))
    }

    @Test
    fun `progressao com subida, melhor serie e mais reps na carga anterior`() {
        val days = sessions.filter { it.workoutId == "w1" }.sortedBy { it.date }.map { s ->
            ExerciseDay(s.id, s.date, sets.filter { it.sessionId == s.id && it.exerciseId == "curl" }, emptyList())
        }
        val ui = buildProgress(curl, days)
        assertEquals("Halteres 2×", ui.equipmentTag)
        assertEquals("Carga máxima por halter", ui.maxLabel)
        assertEquals(16.0, ui.maxKg)
        assertEquals("+4 kg em 2 sem", ui.change)
        assertTrue(ui.changeUp)
        assertEquals(listOf(false, true, true), ui.sessions.map { it.up })
        assertEquals(DatedSets("16 × 11", "2026-11-26"), ui.best)
        assertEquals(14.0, ui.mostRepsKg)
        assertEquals(DatedSets("14 × 12", "2026-11-19"), ui.mostReps)
        assertEquals("16 × 11, 16 × 10", sessionSetsText(ui.sessions.last().sets))
        // Volume conta 2 halteres; melhor série = carga × reps da série.
        assertEquals((16.0 * 11 + 16.0 * 10) * 2, ui.sessions.last().value(Metric.VOLUME))
        assertEquals(176.0, ui.sessions.last().value(Metric.BEST))
    }
}
