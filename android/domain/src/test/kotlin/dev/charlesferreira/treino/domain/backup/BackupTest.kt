package dev.charlesferreira.treino.domain.backup

import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Ids
import dev.charlesferreira.treino.domain.SuspiciousLoads
import dev.charlesferreira.treino.domain.WorkoutSequence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {

    private fun resource(name: String): String =
        BackupTest::class.java.classLoader.getResourceAsStream(name)!!.use { it.readBytes().toString(Charsets.UTF_8) }

    private val format2 by lazy { resource("backup-format2.json") }

    private fun ImportedData.exercise(legacyId: String) = exercises.single { it.legacyId == legacyId }

    /* --------------------------------- format 2 --------------------------------- */

    @Test
    fun `previa do format 2`() {
        val p = BackupImporter.parse(format2)
        assertEquals(BackupSource.FORMAT_2, p.source)
        assertEquals("2026-10-21", p.exportedAt)
        assertEquals(9, p.days)
        assertEquals(24, p.sets)
        assertEquals(4, p.times)
        assertEquals(4, p.workouts)
        // supino-inclinado-halteres, puxada-aberta, leg-press, mesa-flexora, rosca-alternada-x,
        // prancha, corrida, remada-maluca.
        assertEquals(8, p.exercises)
        assertEquals("2026-09-29", p.firstDate)
        assertEquals("2026-10-20", p.lastDate)
        assertTrue(p.hasPlan)
        assertFalse(p.seededFromExample)
        assertEquals(p.data.sessions.map { it.date }.sorted(), p.data.sessions.map { it.date })
        assertEquals(BackupSettings(sound = true, profileName = "Charles"), p.data.settings)
    }

    @Test
    fun `exercicios - catalogo, do aparelho e citados sem definicao`() {
        val d = BackupImporter.parse(format2).data
        assertTrue(d.exercises.size > Catalog.exercises.size)
        // Catálogo inteiro está lá, com os mesmos UUIDs.
        val ids = d.exercises.map { it.id }.toSet()
        assertTrue(Catalog.exercises.all { it.id in ids })

        val puxada = d.exercise("puxada-aberta")
        assertEquals("Puxada aberta (pegada pronada)", puxada.name) // o do aparelho sobrepõe o catálogo
        assertEquals("meus", puxada.group)
        assertNull(puxada.notes)
        assertEquals(Equipment.CABLE, puxada.equipment)

        val maluca = d.exercise("remada-maluca")
        assertEquals("Remada maluca", maluca.name)
        assertEquals("meus", maluca.group)
        assertEquals(ExerciseKind.LOAD, maluca.kind)

        val leg = d.exercise("leg-press")
        assertEquals(Equipment.MACHINE, leg.equipment)
        assertNull(leg.plateKg)

        assertEquals(ExerciseKind.TIME, d.exercise("prancha").kind)
        assertEquals(ExerciseKind.TIME, d.exercise("corrida").kind)
    }

    @Test
    fun `halteres detectados pelo nome, carga mantida`() {
        val d = BackupImporter.parse(format2).data
        val supino = d.exercise("supino-inclinado-halteres")
        val rosca = d.exercise("rosca-alternada-x") // "c/ Halter"
        listOf(supino, rosca).forEach {
            assertEquals(Equipment.DUMBBELL, it.equipment)
            assertTrue(it.countsTwoDumbbells)
        }
        assertFalse(d.exercise("mesa-flexora").countsTwoDumbbells)
        val weights = d.setEntries.filter { it.exerciseId == supino.id }.map { it.weightKg }
        assertEquals(listOf(16.0, 16.0, 16.0, 36.0, 36.0, 36.0, 36.0, 36.0, 36.0, 18.0, 18.0, 18.0), weights)
        assertEquals(12.5, d.setEntries.last { it.exerciseId == rosca.id }.weightKg)

        // O detector acha as duas sessões de 36 nos dados importados.
        val flagged = SuspiciousLoads.detect(d.exercises, d.sessions, d.setEntries)
        assertEquals(listOf("2026-10-06", "2026-10-13"), flagged.single { it.exercise == supino }.sessions.map { it.date })
    }

    @Test
    fun `rir descartado, cardio migrado, notas e treinos`() {
        val d = BackupImporter.parse(format2).data
        // Nada de RIR no modelo: a série com rir vira só carga e reps.
        val first = d.setEntries.first()
        assertEquals(Ids.setEntry("2026-09-29", "supino-inclinado-halteres", 0), first.id)
        assertEquals(16.0 to 10, first.weightKg to first.reps)
        assertNull(first.loggedAt)

        val corrida = d.exercise("corrida")
        val cardio = d.timeEntries.single { it.sessionId == Ids.session("2026-10-01") }
        assertEquals(corrida.id, cardio.exerciseId)
        assertEquals(35.0, cardio.minutes)
        assertEquals(5.2, cardio.km)
        assertEquals("parque", cardio.place)
        // Dia que já tinha times.corrida: o cardio antigo não sobrescreve.
        val run = d.timeEntries.single { it.sessionId == Ids.session("2026-10-08") }
        assertEquals(40.0, run.minutes)
        assertEquals("esteira", run.place)

        val day = d.sessions.single { it.date == "2026-10-02" }
        assertEquals("Ombro incomodou.", day.note)
        assertEquals(Ids.workout("treino-5"), day.workoutId)
        assertNull(day.startedAt)

        assertEquals(listOf("monday", "tuesday", "wednesday", "treino-5"), d.workouts.sortedBy { it.position }.map { it.legacyId })
        val run5 = d.workoutItems.filter { it.workoutId == Ids.workout("treino-5") }
        assertEquals(2, run5.size)
        assertEquals(10 to 12, run5[0].repsMin to run5[0].repsMax)
        assertEquals(90, run5[0].restSec)
        assertEquals(1.0 to 2.0, run5[1].minutesMin to run5[1].minutesMax)
        assertNull(run5[1].repsMin)
        val z2 = d.workoutItems.single { it.workoutId == Ids.workout("wednesday") }
        assertEquals("Ritmo de conversa.", z2.notes)
    }

    @Test
    fun `sequencia nos dados importados`() {
        val d = BackupImporter.parse(format2).data
        val executed = WorkoutSequence.executedSessionIds(d.setEntries, d.timeEntries)
        fun suggest(date: String) = WorkoutSequence.suggestWorkout(d.workouts, d.sessions, executed, date)?.legacyId
        fun next(date: String) = WorkoutSequence.nextInSequence(d.workouts, d.sessions, executed, date)?.legacyId

        assertEquals("tuesday", suggest("2026-10-21"))
        // 07/10 só tem o treino escolhido, sem registro: não conta. A âncora é 06/10 (monday).
        assertFalse(Ids.session("2026-10-07") in executed)
        assertEquals("tuesday", next("2026-10-08"))
        // Mas o treino escolhido no próprio dia ganha.
        assertEquals("wednesday", suggest("2026-10-08"))
        assertEquals("tuesday", suggest("2026-10-07"))
        // Depois do treino-5 (o último da sequência), volta para o primeiro.
        assertEquals("monday", next("2026-10-03"))
    }

    @Test
    fun `uuids deterministicos - reimportar nao duplica`() {
        val a = BackupImporter.parse(format2).data
        val b = BackupImporter.parse(format2).data
        assertEquals(a, b)
        assertEquals(
            UUID.nameUUIDFromBytes("treino:exercise:supino-inclinado-halteres".toByteArray()).toString(),
            a.exercise("supino-inclinado-halteres").id,
        )
        assertEquals(UUID.nameUUIDFromBytes("treino:workout:monday".toByteArray()).toString(), Ids.workout("monday"))
        assertEquals(UUID.nameUUIDFromBytes("treino:session:2026-10-06".toByteArray()).toString(), Ids.session("2026-10-06"))
        listOf(a.exercises.map { it.id }, a.workouts.map { it.id }, a.workoutItems.map { it.id }, a.sessions.map { it.id },
            a.setEntries.map { it.id }, a.timeEntries.map { it.id }).forEach { assertEquals(it.size, it.toSet().size) }
    }

    /* ------------------------------ format 1 e logs crus ------------------------------ */

    @Test
    fun `format 1 sem plano semeia o programa de exemplo`() {
        val p = BackupImporter.parse(resource("backup-format1.json"))
        assertEquals(BackupSource.FORMAT_1, p.source)
        assertEquals(2, p.days)
        assertEquals(2, p.sets)
        assertEquals(1, p.times)
        assertFalse(p.hasPlan)
        assertTrue(p.seededFromExample)
        assertEquals(7, p.workouts)
        assertEquals(listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"),
            p.data.workouts.sortedBy { it.position }.map { it.legacyId })
        assertEquals(BackupSettings(sound = false), p.data.settings)

        val d = p.data
        // O nome do programa sobrepõe o catálogo, como o seedExercises do PWA.
        val supino = d.exercise("supino-reto")
        assertEquals("Supino reto (barra ou halteres)", supino.name)
        assertEquals("meus", supino.group)
        assertEquals("2 séries de aproximação leves antes da 1ª série válida.", supino.notes)
        assertTrue(supino.countsTwoDumbbells)

        val wednesday = d.sessions.single { it.date == "2026-08-05" }
        assertEquals(Ids.workout("wednesday"), wednesday.workoutId)
        assertEquals(d.exercise("corrida").id, d.timeEntries.single().exerciseId)
        val z2 = d.workoutItems.single { it.workoutId == Ids.workout("wednesday") }
        assertEquals(30.0 to 40.0, z2.minutesMin to z2.minutesMax)
        assertEquals(6, d.workoutItems.count { it.workoutId == Ids.workout("monday") })

        assertEquals("tuesday", WorkoutSequence.nextInSequence(d.workouts, d.sessions,
            WorkoutSequence.executedSessionIds(d.setEntries, d.timeEntries), "2026-08-04")?.legacyId)
    }

    @Test
    fun `logs crus`() {
        val p = BackupImporter.parse(resource("logs-raw.json"))
        assertEquals(BackupSource.RAW_LOGS, p.source)
        assertNull(p.exportedAt)
        assertEquals(1, p.days)
        assertEquals(2, p.sets)
        assertEquals("2026-07-22", p.firstDate)
        assertEquals("2026-07-22", p.lastDate)
        assertTrue(p.seededFromExample)
        assertEquals(BackupSettings(), p.data.settings)
    }

    @Test
    fun `backup vazio nao semeia`() {
        val p = BackupImporter.parse("""{"app":"treino","format":2,"exportedAt":"2026-11-01","logs":{},"settings":{},"plan":null,"exercises":null,"profile":null}""")
        assertEquals(0, p.days)
        assertEquals(0, p.workouts)
        assertFalse(p.seededFromExample)
        assertNull(p.firstDate)
        assertEquals(Catalog.exercises, p.data.exercises)
    }

    /* --------------------------------- format 3 --------------------------------- */

    @Test
    fun `ida e volta format 3`() {
        val original = BackupImporter.parse(format2).data
        // Campos que só o app nativo tem.
        val supino = original.exercise("supino-inclinado-halteres")
        val leg = original.exercise("leg-press")
        val edited = original.copy(
            exercises = original.exercises.map {
                when (it.id) {
                    supino.id -> it.copy(stepKg = 2.5)
                    leg.id -> it.copy(plateKg = 10.0, name = "Leg press 45°")
                    else -> it
                }
            },
            sessions = original.sessions.map { if (it.date == "2026-10-20") it.copy(startedAt = 1_000L, endedAt = 3_600_000L) else it },
            setEntries = original.setEntries.mapIndexed { i, s -> if (i == 0) s.copy(loggedAt = 1_234L) else s },
            settings = original.settings.copy(vibration = false, keepScreenOn = true),
        )

        val json = BackupExporter.export(edited, "2026-11-26")
        val back = BackupImporter.parse(json)
        assertEquals(BackupSource.FORMAT_3, back.source)
        assertEquals("2026-11-26", back.exportedAt)
        assertEquals(edited, back.data)
        assertEquals(9, back.days)
        assertEquals(24, back.sets)
        assertEquals(4, back.times)
        assertEquals(4, back.workouts)
        assertEquals(8, back.exercises)
        assertTrue(back.hasPlan)

        // Exportar de novo dá o mesmo texto.
        assertEquals(json, BackupExporter.export(back.data, "2026-11-26"))
    }

    @Test
    fun `format 3 continua legivel como format 2 pelo PWA`() {
        val original = BackupImporter.parse(format2).data
        val root = Json.parseToJsonElement(BackupExporter.export(original, "2026-11-26")).jsonObject
        listOf("app", "format", "exportedAt", "logs", "settings", "plan", "exercises", "profile", "native").forEach {
            assertNotNull(root[it], it)
        }
        val logs = root["logs"]!!.jsonObject
        val day = logs["2026-10-02"]!!.jsonObject
        assertEquals("treino-5", day["template"].asString())
        assertEquals("""[{"w":12,"r":12},{"w":12.5,"r":10}]""", day["sets"]!!.jsonObject["rosca-alternada-x"].toString())
        assertEquals("""{"min":40,"km":6.1,"local":"esteira"}""", logs["2026-10-08"]!!.jsonObject["times"]!!.jsonObject["corrida"]!!.let { (it as kotlinx.serialization.json.JsonArray)[0].toString() })
        assertFalse("rir" in root.toString())

        // Só o que difere do catálogo vai em `exercises`.
        val exercises = root["exercises"]!!.jsonObject
        assertTrue("puxada-aberta" in exercises)
        assertTrue("rosca-alternada-x" in exercises)
        assertTrue("remada-maluca" in exercises)
        assertFalse("mesa-flexora" in exercises)

        // Sem o bloco native, o mesmo arquivo é lido pelo caminho do PWA com o mesmo conteúdo.
        val legacyOnly = JsonObject(root - "native")
        val p = BackupImporter.parse(legacyOnly.toString())
        assertEquals(BackupSource.FORMAT_3, p.source)
        assertEquals(original.sessions, p.data.sessions)
        assertEquals(original.setEntries, p.data.setEntries)
        assertEquals(original.timeEntries, p.data.timeEntries)
        assertEquals(original.workouts, p.data.workouts)
        assertEquals(original.workoutItems, p.data.workoutItems)
        assertEquals(original.exercises.map { it.id }.toSet(), p.data.exercises.map { it.id }.toSet())
        assertEquals(original.settings, p.data.settings)
    }

    @Test
    fun `exercicio e treino criados no app saem com o UUID nas chaves do format 2`() {
        val base = BackupImporter.parse(resource("logs-raw.json")).data
        val ex = base.exercises.first().copy(id = Ids.random(), legacyId = null, name = "Remada nova")
        val session = base.sessions.single()
        val set = base.setEntries.first().copy(id = Ids.random(), exerciseId = ex.id, position = 5)
        val data = base.copy(exercises = base.exercises + ex, setEntries = base.setEntries + set)
        val root = Json.parseToJsonElement(BackupExporter.export(data, "2026-11-26")).jsonObject
        val sets = root["logs"]!!.jsonObject[session.date]!!.jsonObject["sets"]!!.jsonObject
        assertTrue(ex.id in sets)
        assertTrue(ex.id in root["exercises"]!!.jsonObject)
        assertEquals(data, BackupImporter.parse(root.toString()).data)
    }

    /* ---------------------------------- erros ---------------------------------- */

    @Test
    fun `erros de formato em portugues`() {
        fun error(json: String) = assertFailsWith<BackupFormatException> { BackupImporter.parse(json) }.message

        assertEquals("Arquivo inválido: não é um JSON.", error("isto não é json"))
        assertEquals("Formato inválido: o arquivo não traz os registros de treino.", error("[1, 2]"))
        assertEquals("Formato inválido: o arquivo não traz os registros de treino.", error("""{"app":"treino","format":2,"logs":[]}"""))
        assertEquals("Formato inválido: o arquivo não traz os registros de treino.", error("42"))
        assertEquals("Registro inválido: 2026-8-01 (a data deve ser AAAA-MM-DD).", error("""{"2026-8-01":{"template":"monday","sets":{}}}"""))
        assertEquals("Registro inválido: 2026-02-31 (a data deve ser AAAA-MM-DD).", error("""{"logs":{"2026-02-31":{"sets":{}}}}"""))
        assertEquals("Registro inválido: 2026-08-01 (o dia deve ser um objeto).", error("""{"logs":{"2026-08-01":3}}"""))
        assertEquals("Este backup é de uma versão mais nova do app (format 9).", error("""{"app":"treino","format":9,"logs":{}}"""))
        assertEquals("Backup inválido: o bloco do app nativo está corrompido.", error("""{"app":"treino","format":3,"logs":{},"native":{"sessions":[{"id":1}]}}"""))
    }

    @Test
    fun `series malformadas sao ignoradas sem derrubar a importacao`() {
        val p = BackupImporter.parse(
            """{"2026-08-01":{"template":"x","sets":{"a":[{"w":"20","r":10},{"w":null,"r":5},{"r":3},"lixo",{"w":"2,5","r":"8"}],"b":[]}}}""",
        )
        assertEquals(listOf(20.0 to 10, 2.5 to 8), p.data.setEntries.map { it.weightKg to it.reps })
        assertNull(p.data.sessions.single().workoutId) // template desconhecido
        assertFalse(p.data.exercises.any { it.legacyId == "b" })
    }
}
