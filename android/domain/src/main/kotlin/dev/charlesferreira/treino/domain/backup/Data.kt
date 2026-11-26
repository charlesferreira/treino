package dev.charlesferreira.treino.domain.backup

import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import kotlinx.serialization.Serializable

/** Preferências que viajam no backup. Nulo = o arquivo não dizia. */
@Serializable
data class BackupSettings(
    val sound: Boolean? = null,
    val vibration: Boolean? = null,
    val keepScreenOn: Boolean? = null,
    val profileName: String? = null,
)

/** Tudo o que o app guarda, pronto para gravar no Room (importação) ou exportar. */
data class TreinoData(
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val workoutItems: List<WorkoutItem>,
    val sessions: List<Session>,
    val setEntries: List<SetEntry>,
    val timeEntries: List<TimeEntry>,
    val settings: BackupSettings = BackupSettings(),
)

typealias ImportedData = TreinoData

enum class BackupSource {
    /** Só o objeto de logs, sem envelope. */
    RAW_LOGS,

    /** `{app, format: 1, logs, settings}` do PWA antigo. */
    FORMAT_1,

    /** `{app, format: 2, ..., plan, exercises, profile}` do PWA. */
    FORMAT_2,

    /** O nosso, com o bloco `native`. */
    FORMAT_3,
}

/** O que a tela de importação mostra antes de confirmar, e os dados prontos para gravar. */
data class ImportPreview(
    val source: BackupSource,
    /** `exportedAt` do arquivo, se veio. */
    val exportedAt: String?,
    /** Dias de treino (sessões). */
    val days: Int,
    val sets: Int,
    val times: Int,
    val workouts: Int,
    /**
     * Exercícios que o arquivo traz: os citados nas séries, tempos e treinos, mais os criados pela
     * pessoa. Não conta o catálogo embutido, que vem em [data] de qualquer forma.
     */
    val exercises: Int,
    val firstDate: String?,
    val lastDate: String?,
    /** O arquivo tinha programa (`plan`). */
    val hasPlan: Boolean,
    /** Sem programa e com histórico: os treinos vieram do programa de exemplo, como o PWA faz. */
    val seededFromExample: Boolean,
    val data: ImportedData,
)

/** Arquivo que não dá para importar. A mensagem vai direto para a tela. */
class BackupFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
