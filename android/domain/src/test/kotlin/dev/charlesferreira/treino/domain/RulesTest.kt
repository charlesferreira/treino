package dev.charlesferreira.treino.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    private val dumbbell = Exercise("ex-h", name = "Rosca martelo", group = "biceps", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val machine = Exercise("ex-m", name = "Leg press", group = "quadriceps", equipment = Equipment.MACHINE)

    private fun session(id: String, date: String, workoutId: String? = null, startedAt: Long? = null, endedAt: Long? = null) =
        Session(id, date, workoutId, startedAt = startedAt, endedAt = endedAt)

    private fun set(session: String, ex: String, pos: Int, w: Double, r: Int, at: Long? = null) =
        SetEntry("$session-$ex-$pos", session, ex, pos, w, r, at)

    /* ---------------------------------- volume ---------------------------------- */

    @Test
    fun `volume conta x2 com halteres`() {
        assertEquals(320.0, Volume.ofSet(set("s", dumbbell.id, 0, 16.0, 10), dumbbell))
        assertEquals(1200.0, Volume.ofSet(set("s", machine.id, 0, 120.0, 10), machine))
        val sets = listOf(
            set("s1", dumbbell.id, 0, 16.0, 10),
            set("s1", dumbbell.id, 1, 16.0, 8),
            set("s1", machine.id, 0, 100.0, 10),
            set("s2", machine.id, 0, 999.0, 1),
        )
        assertEquals(576.0, Volume.ofExercise(sets.filter { it.sessionId == "s1" }, dumbbell))
        val byId = listOf(dumbbell, machine).associateBy { it.id }
        assertEquals(576.0 + 1000.0, Volume.ofSession("s1", sets, byId))
    }

    /* --------------------------- carga máxima e subida --------------------------- */

    @Test
    fun `carga maxima, subidas, igual desde e melhor serie`() {
        val sessions = listOf(
            session("a", "2026-10-08"), session("b", "2026-10-22"),
            session("c", "2026-11-05"), session("d", "2026-11-19"), session("e", "2026-11-26"),
        )
        val sets = listOf(
            set("a", dumbbell.id, 0, 12.0, 12),
            set("b", dumbbell.id, 0, 14.0, 12), set("b", dumbbell.id, 1, 12.0, 12),
            set("c", dumbbell.id, 0, 14.0, 11),
            set("d", dumbbell.id, 0, 14.0, 12),
            set("e", dumbbell.id, 0, 16.0, 11), set("e", dumbbell.id, 1, 16.0, 10),
            set("e", machine.id, 0, 200.0, 5),
        )
        val h = LoadHistory.of(dumbbell.id, sessions.shuffled(java.util.Random(1)), sets)
        assertEquals(listOf("2026-10-08", "2026-10-22", "2026-11-05", "2026-11-19", "2026-11-26"), h.loads.map { it.date })
        assertEquals(16.0, h.maxKg)
        assertEquals(
            listOf(LoadIncrease("b", "2026-10-22", 12.0, 14.0), LoadIncrease("e", "2026-11-26", 14.0, 16.0)),
            h.increases(),
        )
        val last = h.lastIncrease()!!
        assertEquals(2.0, last.deltaKg)
        assertEquals("+2 kg em 26 nov", PtBr.increase(last, dumbbell))
        assertNull(h.increaseIn("c"))
        assertEquals("2026-11-26", h.stableSince())
        assertEquals(4.0, h.changeSince("2026-10-08"))

        val flat = LoadHistory.of(dumbbell.id, sessions.take(4), sets)
        assertEquals("2026-10-22", flat.stableSince())

        assertEquals(set("e", dumbbell.id, 0, 16.0, 11), bestSet(sets.filter { it.exerciseId == dumbbell.id }))
        assertNull(LoadHistory.of("nada", sessions, sets).lastIncrease())
    }

    @Test
    fun `formatacao pt-BR`() {
        assertEquals("26 nov", PtBr.shortDate("2026-11-26"))
        assertEquals("1 out", PtBr.shortDate("2026-10-01"))
        assertEquals("01 out", PtBr.shortDate("2026-10-01", padDay = true))
        assertEquals("19 nov, quinta", PtBr.dayTitle("2026-11-19"))
        assertEquals("Novembro", PtBr.monthName("2026-11-26"))
        assertEquals("qui", PtBr.weekdayShort("2026-11-26"))
        assertEquals("sáb", PtBr.weekdayShort("2026-11-28"))
        assertEquals("6.840", PtBr.number(6840.0))
        assertEquals("1.234.567", PtBr.number(1234567.0))
        assertEquals("2,5 kg", PtBr.kg(2.5))
        assertEquals("+5 kg", PtBr.loadDelta(5.0))
        assertEquals("−2,5 kg", PtBr.loadDelta(-2.5))
        assertEquals("+1 placa", PtBr.loadDelta(5.0, machine))
        assertEquals("+2 placas", PtBr.loadDelta(10.0, machine))
        assertEquals("+7 kg", PtBr.loadDelta(7.0, machine))
        assertEquals("kg por halter", PtBr.loadUnit(dumbbell))
        assertEquals("kg", PtBr.loadUnit(machine))
    }

    /* -------------------------------- próxima série ------------------------------- */

    @Test
    fun `proxima serie vem da mesma serie da ultima sessao`() {
        val sessions = listOf(session("old", "2026-11-12"), session("last", "2026-11-19"), session("now", "2026-11-26"))
        val sets = listOf(
            set("old", dumbbell.id, 0, 12.0, 12),
            set("last", dumbbell.id, 0, 14.0, 12),
            set("last", dumbbell.id, 1, 14.0, 11),
            set("now", dumbbell.id, 0, 16.0, 11),
        )
        assertEquals(SetPrefill(14.0, 12), NextSet.prefill(dumbbell.id, 0, sessions, sets, currentSessionId = "now"))
        assertEquals(SetPrefill(14.0, 11), NextSet.prefill(dumbbell.id, 1, sessions, sets, currentSessionId = "now"))
        // A última sessão teve 2 séries: a 3ª vem da última dela.
        assertEquals(SetPrefill(14.0, 11), NextSet.prefill(dumbbell.id, 2, sessions, sets, currentSessionId = "now"))
        // Sem excluir a sessão de hoje, ela é a última.
        assertEquals(SetPrefill(16.0, 11), NextSet.prefill(dumbbell.id, 0, sessions, sets))
        assertNull(NextSet.prefill(machine.id, 0, sessions, sets, currentSessionId = "now"))
    }

    /* ------------------------------ sequência circular ------------------------------ */

    private val wA = Workout("wa", "monday", "Superior A", 0)
    private val wB = Workout("wb", "tuesday", "Pernas A", 1)
    private val wC = Workout("wc", "wednesday", "Corrida", 2)
    private val workouts = listOf(wC, wA, wB) // fora de ordem de propósito

    @Test
    fun `sequencia sem historico comeca pelo primeiro`() {
        assertEquals(wA, WorkoutSequence.nextInSequence(workouts, emptyList(), emptySet(), "2026-11-26"))
        assertNull(WorkoutSequence.nextInSequence(emptyList(), emptyList(), emptySet(), "2026-11-26"))
    }

    @Test
    fun `sequencia segue o ultimo executado e da a volta`() {
        val sessions = listOf(session("s1", "2026-11-24", "wb"), session("s2", "2026-11-25", "wc"))
        val sets = listOf(set("s1", "x", 0, 10.0, 10))
        val times = listOf(TimeEntry("t", "s2", "corrida", 0, 30.0))
        // s2 (Corrida, último) → volta para o primeiro.
        assertEquals(wA, WorkoutSequence.suggestWorkout(workouts, sessions, sets, times, "2026-11-26"))
        // Sem o tempo, s2 não conta: a âncora é s1 (Pernas A) → Corrida.
        assertEquals(wC, WorkoutSequence.suggestWorkout(workouts, sessions, sets, emptyList(), "2026-11-26"))
        // Só olha para antes da data.
        assertEquals(wA, WorkoutSequence.nextInSequence(workouts, sessions, setOf("s1", "s2"), "2026-11-24"))
        // No dia 24 o treino registrado (Pernas A) ganha.
        assertEquals(wB, WorkoutSequence.suggestWorkout(workouts, sessions, sets, times, "2026-11-24"))
    }

    @Test
    fun `treino escolhido no dia ganha e ancora desconhecida volta ao primeiro`() {
        val sessions = listOf(session("s1", "2026-11-25", "wa"), session("today", "2026-11-26", "wc"))
        val sets = listOf(set("s1", "x", 0, 10.0, 10))
        assertEquals(wC, WorkoutSequence.suggestWorkout(workouts, sessions, sets, emptyList(), "2026-11-26"))
        assertEquals(wB, WorkoutSequence.nextInSequence(workouts, sessions, setOf("s1"), "2026-11-26"))

        val unknown = listOf(session("s1", "2026-11-25", "apagado"))
        assertEquals(wA, WorkoutSequence.nextInSequence(workouts, unknown, setOf("s1"), "2026-11-26"))
        // Escolha de hoje apontando para treino que não existe mais: cai na fila.
        val gone = listOf(session("s1", "2026-11-25", "wa"), session("today", "2026-11-26", "apagado"))
        assertEquals(wB, WorkoutSequence.suggestWorkout(workouts, gone, setOf("s1"), "2026-11-26"))
    }

    /* ----------------------------------- passo ----------------------------------- */

    @Test
    fun `passo por equipamento e passo customizado`() {
        assertEquals(LoadStep(2.0), Steps.defaultFor(Equipment.DUMBBELL))
        assertEquals(LoadStep(2.0), Steps.defaultFor(Equipment.BARBELL))
        assertEquals(LoadStep(5.0, plates = 1), Steps.defaultFor(Equipment.MACHINE))
        assertEquals(LoadStep(2.5), Steps.defaultFor(Equipment.CABLE))
        assertEquals(LoadStep(2.0), Steps.defaultFor(Equipment.BODYWEIGHT))
        assertEquals(LoadStep(1.0), Steps.defaultFor(Equipment.OTHER))
        assertEquals(LoadStep(7.5, plates = 1), Steps.of(machine.copy(plateKg = 7.5)))
        assertEquals(LoadStep(2.5), Steps.of(dumbbell.copy(stepKg = 2.5)))
        assertEquals(LoadStep(1.0), Steps.of(machine.copy(stepKg = 1.0)))
        assertEquals(18.0, Steps.up(16.0, dumbbell))
        assertEquals(0.0, Steps.down(1.0, dumbbell))
        val cable = Exercise("c", name = "Face pull", group = "ombros", equipment = Equipment.CABLE)
        assertEquals(7.5, Steps.up(Steps.up(Steps.up(0.0, cable), cable), cable))
    }

    @Test
    fun `equipamento deduzido do nome`() {
        assertEquals(Equipment.DUMBBELL, EquipmentGuess.fromName("Rosca alternada c/ HALTER", ExerciseKind.LOAD))
        assertEquals(Equipment.DUMBBELL, EquipmentGuess.fromName("Supino reto (barra ou halteres)", ExerciseKind.LOAD))
        assertEquals(Equipment.MACHINE, EquipmentGuess.fromName("Cadeira extensora", ExerciseKind.LOAD))
        assertEquals(Equipment.CABLE, EquipmentGuess.fromName("Tríceps na polia com corda", ExerciseKind.LOAD))
        assertEquals(Equipment.BODYWEIGHT, EquipmentGuess.fromName("Barra fixa (pegada pronada)", ExerciseKind.LOAD))
        assertEquals(Equipment.BARBELL, EquipmentGuess.fromName("Rosca direta com barra W", ExerciseKind.LOAD))
        assertEquals(Equipment.OTHER, EquipmentGuess.fromName("Corrida", ExerciseKind.TIME))
    }

    /* ------------------------------ cargas suspeitas ------------------------------ */

    @Test
    fun `detector sinaliza o par anotado como halter e nao altera nada`() {
        // O caso do design: 16, 36, 36, 18 → as duas de 36 são sinalizadas.
        val dates = listOf("2026-09-10", "2026-09-24", "2026-10-01", "2026-10-08", "2026-10-15")
        val loads = listOf(16.0, 36.0, 36.0, 18.0, 18.0)
        val sessions = dates.mapIndexed { i, d -> session("s$i", d) }
        val sets = loads.flatMapIndexed { i, w -> listOf(set("s$i", dumbbell.id, 0, w, 10), set("s$i", dumbbell.id, 1, w, 9)) } +
            listOf(set("s1", machine.id, 0, 400.0, 10))
        val before = sets.toList()

        val report = SuspiciousLoads.detect(listOf(dumbbell, machine), sessions, sets)
        assertEquals(1, report.size)
        val flagged = report.single()
        assertEquals(dumbbell, flagged.exercise)
        assertEquals(listOf("2026-09-24", "2026-10-01"), flagged.sessions.map { it.date })
        val first = flagged.sessions.first()
        assertEquals(36.0, first.maxKg)
        assertEquals(18.0, first.neighborMedianKg)
        assertEquals(2.0, first.ratio)
        assertEquals(18.0, first.suggestedMaxKg)
        assertEquals(listOf(18.0, 18.0), first.suggestedSets.map { it.weightKg })
        assertEquals(listOf(36.0, 36.0), first.sets.map { it.weightKg })
        assertEquals(before, sets)
    }

    @Test
    fun `detector ignora variacao normal, poucas vizinhas e exercicios sem halteres`() {
        val sessions = (0 until 5).map { session("s$it", "2026-10-0${it + 1}") }
        val steady = listOf(16.0, 18.0, 20.0, 18.0, 25.0) // 25 / mediana(18, 20, 18) = 1,39
        val sets = steady.mapIndexed { i, w -> set("s$i", dumbbell.id, 0, w, 10) }
        assertTrue(SuspiciousLoads.detect(listOf(dumbbell), sessions, sets).isEmpty())

        val two = listOf(set("s0", dumbbell.id, 0, 16.0, 10), set("s1", dumbbell.id, 0, 36.0, 10))
        assertTrue(SuspiciousLoads.detect(listOf(dumbbell), sessions, two).isEmpty())

        val doubled = listOf(16.0, 16.0, 36.0, 16.0, 16.0).mapIndexed { i, w -> set("s$i", machine.id, 0, w, 10) }
        assertTrue(SuspiciousLoads.detect(listOf(machine), sessions, doubled).isEmpty())

        // Limite: 1,6 × a mediana conta.
        val edge = listOf(10.0, 10.0, 16.0, 10.0).mapIndexed { i, w -> set("s$i", dumbbell.id, 0, w, 10) }
        assertEquals(listOf("2026-10-03"), SuspiciousLoads.detect(listOf(dumbbell), sessions, edge).single().sessions.map { it.date })
    }

    /* --------------------------------- duração --------------------------------- */

    @Test
    fun `duracao pelo inicio e fim, pelas series, ou nula`() {
        val min = 60_000L
        val full = session("a", "2026-11-26", startedAt = 0, endedAt = 58 * min)
        assertEquals(58 * min, SessionDuration.of(full, emptyList(), emptyList()))
        assertEquals(58, SessionDuration.minutes(full, emptyList(), emptyList()))

        val bySets = session("b", "2026-11-26")
        val sets = listOf(set("b", "x", 0, 10.0, 10, at = 10 * min), set("b", "x", 1, 10.0, 10, at = 40 * min))
        val times = listOf(TimeEntry("t", "b", "corrida", 0, 10.0, loggedAt = 62 * min))
        assertEquals(52 * min, SessionDuration.of(bySets, sets, times))

        val inProgress = session("c", "2026-11-26", startedAt = 5 * min)
        assertEquals(25 * min, SessionDuration.of(inProgress, listOf(set("c", "x", 0, 1.0, 1, at = 30 * min)), emptyList()))

        val imported = session("d", "2026-08-01")
        assertNull(SessionDuration.of(imported, listOf(set("d", "x", 0, 10.0, 10)), emptyList()))
        assertNull(SessionDuration.of(session("e", "2026-08-01"), listOf(set("e", "x", 0, 1.0, 1, at = min)), emptyList()))
    }

    /* --------------------------------- progresso --------------------------------- */

    @Test
    fun `progresso do treino com segmentos proporcionais`() {
        val items = listOf(
            WorkoutItem("i1", "w", "a", 0, sets = 3),
            WorkoutItem("i2", "w", "b", 1, sets = 2),
            WorkoutItem("i3", "w", "corrida", 2, sets = 1, minutesMin = 10.0, minutesMax = 20.0),
            WorkoutItem("i4", "w", "a", 3, sets = 2),
        )
        val sets = listOf(
            set("s", "a", 0, 10.0, 10), set("s", "a", 1, 10.0, 10), set("s", "a", 2, 10.0, 10), set("s", "a", 3, 10.0, 10),
            set("s", "b", 0, 10.0, 10),
            set("s", "fora", 0, 10.0, 10),
            set("outra", "b", 1, 10.0, 10),
        )
        val times = listOf(TimeEntry("t", "s", "corrida", 0, 15.0))
        val p = WorkoutProgress.of(items.reversed(), "s", sets, times)
        assertEquals(6, p.done)
        assertEquals(8, p.total)
        assertEquals(listOf(3, 1, 1, 1), p.segments.map { it.done })
        assertEquals(listOf(3, 2, 1, 2), p.segments.map { it.total })
        assertEquals(listOf(true, false, true, false), p.segments.map { it.complete })
        assertEquals(listOf(3 / 8.0, 2 / 8.0, 1 / 8.0, 2 / 8.0), p.weights)
    }
}
