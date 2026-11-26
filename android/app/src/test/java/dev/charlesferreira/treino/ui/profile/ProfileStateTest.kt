package dev.charlesferreira.treino.ui.profile

import dev.charlesferreira.treino.data.loadReviewKey
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.backup.BackupImporter
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileStateTest {
    private val today = "2026-11-26"

    @Test
    fun `profile lines`() {
        assertEquals("treinando desde 22 jul", trainingSince("2026-07-22", today))
        assertEquals("treinando desde 22 jul 2025", trainingSince("2025-07-22", today))
        assertNull(trainingSince(null, today))
        assertEquals("nada para conferir", reviewSubtitle(0))
        assertEquals("1 sessão para conferir", reviewSubtitle(1))
        assertEquals("3 sessões para conferir", reviewSubtitle(3))
        assertEquals("último em 20 nov", backupSubtitle("2026-11-20", today))
        assertEquals("nenhum ainda", backupSubtitle(null, today))
        assertEquals("20 nov", lastBackupLabel("2026-11-20", today))
        assertEquals("nunca", lastBackupLabel(null, today))
        assertEquals("treino-backup-2026-11-26.json", exportFileName(today))
    }

    @Test
    fun `import preview of a PWA backup`() {
        val preview = BackupImporter.parse(File("../domain/src/test/resources/backup-format2.json").readText())
        val ui = importPreviewUi(preview, "treino-2026-10-21.json", today)
        assertEquals("treino-2026-10-21.json", ui.fileName)
        assertEquals("backup do app web, exportado em 21 out", ui.source)
        assertEquals(
            listOf(
                preview.days.toString() to "dias de treino",
                (preview.sets + preview.times).toString() to "séries",
                preview.workouts.toString() to if (preview.workouts == 1) "treino" else "treinos",
                preview.exercises.toString() to "exercícios",
            ),
            ui.stats,
        )
        assertEquals("${period(preview.firstDate, preview.lastDate, today)} Substitui o que está neste celular.", ui.summary)
        assertTrue(ui.summary.startsWith("29 set a "))
    }

    @Test
    fun `import preview without a plan, singulars and thousands`() {
        val preview = BackupImporter.parse(File("../domain/src/test/resources/logs-raw.json").readText())
        val ui = importPreviewUi(preview, "logs.json", today)
        assertEquals("backup do app web", ui.source)
        assertTrue(ui.summary.contains("O arquivo não traz treinos: entram os do programa de exemplo."))
        assertTrue(ui.summary.endsWith("Substitui o que está neste celular."))

        assertEquals("22 jul a 20 nov.", period("2026-07-22", "2026-11-20", today))
        assertEquals("22 jul 2025 a 20 nov 2026.", period("2025-07-22", "2026-11-20", today))
        assertEquals("Em 20 nov.", period("2026-11-20", "2026-11-20", today))
        assertEquals("Nenhum dia de treino.", period(null, null, today))
        val big = preview.copy(days = 1, sets = 1400, times = 12, workouts = 1, exercises = 1)
        assertEquals(
            listOf("1" to "dia de treino", "1.412" to "séries", "1" to "treino", "1" to "exercício"),
            importPreviewUi(big, "x.json", today).stats,
        )
    }

    /* `dumbbell-review`: 14, 16, 16, 36, 36, 18 kg. */
    private val press = Exercise("press", name = "Supino inclinado c/ halteres", group = "peito", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val dates = listOf("2026-08-27", "2026-09-03", "2026-09-10", "2026-09-24", "2026-10-01", "2026-10-08")
    private val loads = listOf(14.0, 16.0, 16.0, 36.0, 36.0, 18.0)
    private val sessions = dates.mapIndexed { i, d -> Session("s$i", d) }
    private val sets = loads.flatMapIndexed { i, kg -> listOf(10, 9, 8).mapIndexed { p, r -> SetEntry("e$i$p", "s$i", "press", p, kg, r) } }

    @Test
    fun `review list, chart and labels`() {
        val review = buildReview(listOf(press), sessions, sets, emptySet(), today)
        assertEquals(1, review.size)
        val ex = review.single()
        assertEquals("2 sessões com carga perto do dobro das vizinhas", ex.subtitle)
        assertEquals(listOf("10 set", "24 set", "01 out", "08 out"), ex.rows.map { it.date })
        assertEquals("36 × 10, 36 × 9, 36 × 8", ex.rows[1].sets)
        assertEquals(listOf("16 kg", "36 kg", "36 kg", "18 kg"), ex.rows.map { it.load })
        assertEquals(listOf(false, true, true, false), ex.rows.map { it.flag != null })
        val flag = ex.rows[1].flag!!
        assertEquals("Por halter, 36", flag.keepLabel)
        assertEquals("Era o par, vira 18", flag.halveLabel)
        assertEquals(listOf(18.0, 18.0, 18.0), flag.suggestedSets.map { it.weightKg })
        assertEquals(loadReviewKey("s3", "press"), flag.key)
        assertEquals(2, pendingReviewCount(review))

        assertEquals(loads, ex.points.map { it.kg })
        assertEquals(listOf(14.0, 16.0, 16.0, 18.0, 18.0, 18.0), correctedLine(ex.points, emptyMap()))
        assertEquals(listOf(14.0, 16.0, 16.0, 18.0, 36.0, 18.0), correctedLine(ex.points, mapOf("s4" to LoadChoice.KEEP)))
        assertEquals(22f, reviewChartY(36.0, 14.0, 36.0))
        assertEquals(101f, reviewChartY(14.0, 14.0, 36.0))
        assertEquals(61.5f, reviewChartY(25.0, 14.0, 36.0))
    }

    @Test
    fun `reviewed sessions do not come back`() {
        val one = buildReview(listOf(press), sessions, sets, setOf(loadReviewKey("s3", "press")), today).single()
        assertEquals("1 sessão com carga perto do dobro das vizinhas", one.subtitle)
        assertEquals(listOf("s4"), one.flaggedSessionIds)
        assertEquals(listOf("24 set", "01 out", "08 out"), one.rows.map { it.date })

        val none = buildReview(listOf(press), sessions, sets, setOf(loadReviewKey("s3", "press"), loadReviewKey("s4", "press")), today)
        assertTrue(none.isEmpty())
        assertEquals(0, pendingReviewCount(none))
    }

    @Test
    fun `apply label and current exercise`() {
        assertNull(applyLabel(emptyList()))
        assertEquals("Aplicar 1 correção", applyLabel(listOf(LoadChoice.HALVE, LoadChoice.KEEP)))
        assertEquals("Aplicar 2 correções", applyLabel(listOf(LoadChoice.HALVE, LoadChoice.HALVE)))
        assertEquals("Confirmar", applyLabel(listOf(LoadChoice.KEEP)))

        val ex = buildReview(listOf(press), sessions, sets, emptySet(), today).single()
        val other = ex.copy(exerciseId = "curl")
        assertEquals(0 to ex, currentReview(listOf("press", "curl"), 0, listOf(ex, other)))
        assertEquals(1 to other, currentReview(listOf("press", "curl"), 0, listOf(other)))
        assertEquals(1 to other, currentReview(listOf("press", "curl"), 1, listOf(ex, other)))
        assertNull(currentReview(listOf("press", "curl"), 2, listOf(ex, other)))
    }
}
