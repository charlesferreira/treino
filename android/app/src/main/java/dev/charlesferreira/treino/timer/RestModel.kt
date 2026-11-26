package dev.charlesferreira.treino.timer

/**
 * What the caller asks for when a rest starts. Phase 4 builds it from the real workout;
 * the Phase 0 spike screen passes sample data.
 *
 * [setNumber]/[setCount] describe the set that comes after the rest ("série 2 de 2"), and
 * [target] is its prefilled load and reps ("16 kg × 10"). The app's rest screens also show
 * [nextShort] ("16 × 10") and the set just logged ([loggedLabel] "Série 1", [loggedValue] "16 × 11").
 */
data class RestRequest(
    val durationMs: Long,
    val workoutName: String,
    val exerciseName: String,
    val setNumber: Int,
    val setCount: Int,
    val target: String,
    val progress: WorkoutProgress,
    val nextShort: String = target,
    val loggedLabel: String = "",
    val loggedValue: String = "",
) {
    init {
        require(durationMs > 0) { "durationMs must be positive" }
    }
}

/** Sets planned per exercise, in workout order, and how many sets of the whole workout are done. */
data class WorkoutProgress(val setsPerExercise: List<Int>, val setsDone: Int) {
    val totalSets: Int get() = setsPerExercise.sum()

    init {
        require(setsPerExercise.all { it > 0 }) { "every exercise needs at least one set" }
        require(setsDone in 0..setsPerExercise.sum()) { "setsDone out of range" }
    }

    companion object {
        val EMPTY = WorkoutProgress(emptyList(), 0)
    }
}

/** The persisted rest. Wall-clock based: nothing counts time, the remaining time is always derived. */
data class RestState(
    val endsAtMs: Long,
    val totalMs: Long,
    val workoutName: String,
    val exerciseName: String,
    val setNumber: Int,
    val setCount: Int,
    val target: String,
    val progress: WorkoutProgress,
    val finished: Boolean,
    val nextShort: String = target,
    val loggedLabel: String = "",
    val loggedValue: String = "",
) {
    /** When the rest began; an adjust moves the end and the total together, so this stays put. */
    val startedAtMs: Long get() = endsAtMs - totalMs

    /** Remaining over total, 1 at the start and 0 at the end (the ring's arc). */
    fun fraction(nowMs: Long): Float = if (totalMs <= 0) 0f else (remainingMs(nowMs).toFloat() / totalMs).coerceIn(0f, 1f)

    val setLabel: String get() = "série $setNumber de $setCount"

    fun remainingMs(nowMs: Long): Long = (endsAtMs - nowMs).coerceAtLeast(0)

    companion object {
        fun start(request: RestRequest, nowMs: Long) = RestState(
            endsAtMs = nowMs + request.durationMs,
            totalMs = request.durationMs,
            workoutName = request.workoutName,
            exerciseName = request.exerciseName,
            setNumber = request.setNumber,
            setCount = request.setCount,
            target = request.target,
            progress = request.progress,
            finished = false,
            nextShort = request.nextShort,
            loggedLabel = request.loggedLabel,
            loggedValue = request.loggedValue,
        )
    }
}

/** Pure rest-timer arithmetic, kept apart from Android so it is unit tested on the JVM. */
object RestMath {

    /**
     * Moves the end of the rest by [deltaMs] (−15 s, +15 s, +30 s). The end never goes before
     * [nowMs], so the remaining time never passes zero; the total grows or shrinks with it so a
     * progress ring stays meaningful. Adjusting a finished rest by a positive delta restarts it
     * with that much time left.
     */
    fun adjust(state: RestState, deltaMs: Long, nowMs: Long): RestState {
        if (state.finished) {
            if (deltaMs <= 0) return state
            return state.copy(endsAtMs = nowMs + deltaMs, totalMs = deltaMs, finished = false)
        }
        val newEnd = (state.endsAtMs + deltaMs).coerceAtLeast(nowMs)
        val elapsed = (nowMs - (state.endsAtMs - state.totalMs)).coerceAtLeast(0)
        val newTotal = (state.totalMs + (newEnd - state.endsAtMs)).coerceAtLeast(elapsed)
        return state.copy(endsAtMs = newEnd, totalMs = newTotal)
    }

    /** True when the rest is over at [nowMs] (including an adjust that hit zero). */
    fun isDue(state: RestState, nowMs: Long): Boolean = !state.finished && state.endsAtMs <= nowMs

    /** "1:30", "0:05": minutes and seconds, rounding up so 0:00 only shows when it is really over. */
    fun format(remainingMs: Long): String {
        val totalSeconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

/** One ProgressStyle segment per exercise, its length the number of sets, as plain data. */
data class SegmentSpec(val length: Int)

object RestProgress {

    /**
     * Segment lengths proportional to the sets of each exercise. ProgressStyle lays segments out by
     * their length against the sum, so the set count itself is the proportional length, and the
     * progress value is simply the number of sets done.
     */
    fun segments(progress: WorkoutProgress): List<SegmentSpec> =
        progress.setsPerExercise.map { SegmentSpec(it) }

    /** Progress value on the same scale as [segments]. */
    fun position(progress: WorkoutProgress): Int = progress.setsDone.coerceIn(0, progress.totalSets)

    /** Comma list for SharedPreferences and Intent extras ("3,3,2,2"). */
    fun encode(setsPerExercise: List<Int>): String = setsPerExercise.joinToString(",")

    fun decode(raw: String?): List<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
}
