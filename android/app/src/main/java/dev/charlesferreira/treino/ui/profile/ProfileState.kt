package dev.charlesferreira.treino.ui.profile

import dev.charlesferreira.treino.data.loadReviewKey
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.LoadHistory
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.SuspiciousLoads
import dev.charlesferreira.treino.domain.backup.BackupSource
import dev.charlesferreira.treino.domain.backup.ImportPreview
import java.time.LocalDate

/* Pure state of Perfil, Backup and the load review: everything the screens print, testable on the JVM. */

/* ------------------------------------------------------------------ Perfil */

/** "22 jul"; another year than [today]'s gets it: "22 jul 2025". */
fun dateLabel(date: String, today: String, padDay: Boolean = false): String {
    val short = PtBr.shortDate(date, padDay)
    val year = LocalDate.parse(date).year
    return if (year == LocalDate.parse(today).year) short else "$short $year"
}

/** "treinando desde 22 jul", from the first day with a record. Null without any. */
fun trainingSince(firstDate: String?, today: String): String? =
    firstDate?.let { "treinando desde ${dateLabel(it, today)}" }

fun reviewSubtitle(pending: Int): String = when (pending) {
    0 -> "nada para conferir"
    1 -> "1 sessão para conferir"
    else -> "$pending sessões para conferir"
}

fun backupSubtitle(lastExport: String?, today: String): String =
    lastExport?.let { "último em ${dateLabel(it, today)}" } ?: "nenhum ainda"

/** "Último backup" on the Backup screen. */
fun lastBackupLabel(lastExport: String?, today: String): String =
    lastExport?.let { dateLabel(it, today) } ?: "nunca"

fun exportFileName(today: String): String = "treino-backup-$today.json"

/* ------------------------------------------------------------------ Backup */

/** The "Arquivo escolhido" card: what the file brings, before replacing anything. */
data class ImportPreviewUi(
    val fileName: String,
    /** "backup do app web, exportado em 20 nov". */
    val source: String,
    /** Number and label of the four boxes: dias de treino, séries, treinos, exercícios. */
    val stats: List<Pair<String, String>>,
    /** "22 jul a 20 nov. Substitui o que está neste celular." */
    val summary: String,
)

fun importPreviewUi(preview: ImportPreview, fileName: String, today: String): ImportPreviewUi {
    val origin = if (preview.source == BackupSource.FORMAT_3) "backup deste app" else "backup do app web"
    val exported = preview.exportedAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val source = if (exported != null) "$origin, exportado em ${dateLabel(exported.toString(), today)}" else origin

    // Times count as sets, as in Evolução > Dias.
    val stats = listOf(
        count(preview.days, "dia de treino", "dias de treino"),
        count(preview.sets + preview.times, "série", "séries"),
        count(preview.workouts, "treino", "treinos"),
        count(preview.exercises, "exercício", "exercícios"),
    )

    val parts = ArrayList<String>()
    parts += period(preview.firstDate, preview.lastDate, today)
    if (preview.seededFromExample) parts += "O arquivo não traz treinos: entram os do programa de exemplo."
    parts += "Substitui o que está neste celular."
    return ImportPreviewUi(fileName, source, stats, parts.joinToString(" "))
}

private fun count(n: Int, one: String, many: String) = PtBr.number(n.toDouble()) to if (n == 1) one else many

/** "22 jul a 20 nov.", "Em 20 nov.", "Nenhum dia de treino."; years when not all in [today]'s year. */
internal fun period(first: String?, last: String?, today: String): String {
    if (first == null || last == null) return "Nenhum dia de treino."
    val year = LocalDate.parse(today).year
    val withYear = LocalDate.parse(first).year != year || LocalDate.parse(last).year != year
    fun label(d: String) = if (withYear) "${PtBr.shortDate(d)} ${LocalDate.parse(d).year}" else PtBr.shortDate(d)
    return if (first == last) "Em ${label(first)}." else "${label(first)} a ${label(last)}."
}

/* ------------------------------------------------------------- Load review */

/** What the person chose for a flagged session. No entry = not decided yet. */
enum class LoadChoice {
    /** "Por halter, 36": the load stays. */
    KEEP,

    /** "Era o par, vira 18": every set of the session gets half the load. */
    HALVE,
}

/** A flagged session: the two chips and the sets with the suggestion. */
data class ReviewFlag(
    val key: String,
    val keepLabel: String,
    val halveLabel: String,
    val suggestedSets: List<SetEntry>,
)

/** A row of the list: "24 set  36 × 10, 36 × 9, 36 × 8  36 kg", with [flag] when it is flagged. */
data class ReviewRow(
    val sessionId: String,
    val date: String,
    val sets: String,
    val load: String,
    val flag: ReviewFlag?,
)

/** A point of the chart: the session's max load as written, and half of it when flagged. */
data class ReviewPoint(val sessionId: String, val kg: Double, val flagged: Boolean)

/** One exercise of the review, with its sessions still to decide. */
data class ReviewExercise(
    val exerciseId: String,
    val name: String,
    /** "2 sessões com carga perto do dobro das vizinhas". */
    val subtitle: String,
    val rows: List<ReviewRow>,
    val points: List<ReviewPoint>,
) {
    val flags: List<ReviewFlag> get() = rows.mapNotNull { it.flag }
    val flaggedSessionIds: List<String> get() = rows.filter { it.flag != null }.map { it.sessionId }
}

/**
 * Runs [SuspiciousLoads.detect] and keeps the sessions not reviewed yet ([reviewed] holds
 * [loadReviewKey]s). Rows: each flagged session with the session right before and after it.
 * Chart: from [SuspiciousLoads.WINDOW] sessions before the first flagged one to as many after the last.
 */
fun buildReview(
    exercises: List<Exercise>,
    sessions: List<Session>,
    sets: List<SetEntry>,
    reviewed: Set<String>,
    today: String,
): List<ReviewExercise> {
    val setsByExercise = sets.groupBy { it.exerciseId }
    return SuspiciousLoads.detect(exercises, sessions, sets).mapNotNull { sus ->
        val ex = sus.exercise
        val pending = sus.sessions.filter { loadReviewKey(it.sessionId, ex.id) !in reviewed }
        if (pending.isEmpty()) return@mapNotNull null
        val bySession = pending.associateBy { it.sessionId }
        val exSets = setsByExercise[ex.id].orEmpty()
        val loads = LoadHistory.of(ex.id, sessions, exSets).loads
        val flaggedIdx = loads.indices.filter { loads[it].sessionId in bySession }

        val rowIdx = flaggedIdx.flatMap { listOf(it - 1, it, it + 1) }.filter { it in loads.indices }.distinct().sorted()
        val rows = rowIdx.map { i ->
            val load = loads[i]
            val sessionSets = exSets.filter { it.sessionId == load.sessionId }.sortedBy { it.position }
            val flagged = bySession[load.sessionId]
            ReviewRow(
                sessionId = load.sessionId,
                date = dateLabel(load.date, today, padDay = true),
                sets = sessionSets.joinToString(", ") { "${PtBr.number(it.weightKg)} × ${it.reps}" },
                load = PtBr.kg(load.maxKg),
                flag = flagged?.let {
                    ReviewFlag(
                        key = loadReviewKey(it.sessionId, ex.id),
                        keepLabel = "Por halter, ${PtBr.number(it.maxKg)}",
                        halveLabel = "Era o par, vira ${PtBr.number(it.suggestedMaxKg)}",
                        suggestedSets = it.suggestedSets,
                    )
                },
            )
        }

        val from = maxOf(0, flaggedIdx.first() - SuspiciousLoads.WINDOW)
        val to = minOf(loads.lastIndex, flaggedIdx.last() + SuspiciousLoads.WINDOW)
        val points = (from..to).map { ReviewPoint(loads[it].sessionId, loads[it].maxKg, loads[it].sessionId in bySession) }

        val n = pending.size
        ReviewExercise(
            exerciseId = ex.id,
            name = ex.name,
            subtitle = "$n ${if (n == 1) "sessão" else "sessões"} com carga perto do dobro das vizinhas",
            rows = rows,
            points = points,
        )
    }
}

/** Sessions still to decide, the number on Perfil. */
fun pendingReviewCount(review: List<ReviewExercise>): Int = review.sumOf { it.flags.size }

/** The "Com as correções" line: half the load where flagged, unless the person chose to keep it. */
fun correctedLine(points: List<ReviewPoint>, choices: Map<String, LoadChoice>): List<Double> =
    points.map { if (it.flagged && choices[it.sessionId] != LoadChoice.KEEP) it.kg / 2 else it.kg }

/**
 * Chart scale of `dumbbell-review` (130 tall): the highest value at y 22, the lowest at y 101,
 * linear in between. All equal: the middle.
 */
fun reviewChartY(value: Double, min: Double, max: Double, top: Float = 22f, bottom: Float = 101f): Float =
    if (max <= min) (top + bottom) / 2 else (bottom - (value - min) / (max - min) * (bottom - top)).toFloat()

/** "Aplicar 1 correção"; only "keep" chosen: "Confirmar"; nothing chosen: null (button off). */
fun applyLabel(choices: Collection<LoadChoice>): String? {
    val halves = choices.count { it == LoadChoice.HALVE }
    return when {
        halves == 1 -> "Aplicar 1 correção"
        halves > 1 -> "Aplicar $halves correções"
        choices.isNotEmpty() -> "Confirmar"
        else -> null
    }
}

/**
 * Which exercise is on screen: the first of [order] (the exercises when the screen opened), from
 * [index] on, that still has sessions to decide. Null when none is left.
 */
fun currentReview(order: List<String>, index: Int, live: List<ReviewExercise>): Pair<Int, ReviewExercise>? {
    val byId = live.associateBy { it.exerciseId }
    for (i in index until order.size) byId[order[i]]?.let { return i to it }
    return null
}
