package dev.charlesferreira.treino.ui.profile

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.charlesferreira.treino.data.Settings
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.backup.BackupExporter
import dev.charlesferreira.treino.domain.backup.BackupFormatException
import dev.charlesferreira.treino.domain.backup.BackupImporter
import dev.charlesferreira.treino.domain.backup.ImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The review over the real data, minus what was already reviewed. */
private fun TreinoRepository.observeReview(): Flow<List<ReviewExercise>> = combine(
    observeExercises(),
    observeSessions(),
    observeAllSets(),
    settings.settings,
) { ex, sessions, sets, s -> buildReview(ex, sessions, sets, s.reviewedLoads, today()) }

data class ProfileUi(
    val settings: Settings,
    val trainingSince: String?,
    val pendingReview: Int,
    val backup: String,
)

class ProfileViewModel(private val repository: TreinoRepository) : ViewModel() {
    val state: StateFlow<ProfileUi?> = combine(
        repository.settings.settings,
        repository.observeDays(),
        repository.observeReview(),
    ) { s, days, review ->
        val today = repository.today()
        ProfileUi(
            settings = s,
            trainingSince = trainingSince(days.minOfOrNull { it.session.date }, today),
            pendingReview = pendingReviewCount(review),
            backup = backupSubtitle(s.lastExport, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSound(on: Boolean) {
        viewModelScope.launch { repository.settings.setSound(on) }
    }

    fun setVibration(on: Boolean) {
        viewModelScope.launch { repository.settings.setVibration(on) }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch { repository.settings.setKeepScreenOn(on) }
    }

    fun setName(name: String) {
        viewModelScope.launch { repository.settings.setProfileName(name) }
    }

    /** Debug only. */
    fun createSampleWorkout() {
        viewModelScope.launch { dev.charlesferreira.treino.debug.SampleData.createSuperiorB(repository) }
    }
}

data class LoadReviewUi(
    /** Null when nothing is left to decide. */
    val current: ReviewExercise?,
    /** "1 de 2". */
    val position: String?,
    val choices: Map<String, LoadChoice>,
)

/**
 * `dumbbell-review`: one exercise at a time, in the order they had when the screen opened. Nothing
 * is written until "Aplicar" or "Manter tudo como está"; both remember the sessions decided.
 */
class LoadReviewViewModel(private val repository: TreinoRepository) : ViewModel() {
    private val order = MutableStateFlow<List<String>?>(null)
    private val index = MutableStateFlow(0)
    private val choices = MutableStateFlow<Map<String, LoadChoice>>(emptyMap())

    val state: StateFlow<LoadReviewUi?> = combine(repository.observeReview(), order, index, choices) { live, o, i, c ->
        val ids = o ?: live.map { it.exerciseId }.also { order.value = it }
        val cur = currentReview(ids, i, live)
        LoadReviewUi(
            current = cur?.second,
            position = cur?.let { "${it.first + 1} de ${ids.size}" }?.takeIf { ids.size > 1 },
            choices = c,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(sessionId: String, choice: LoadChoice) {
        choices.update { if (it[sessionId] == choice) it - sessionId else it + (sessionId to choice) }
    }

    /** "Aplicar": halves what was chosen, remembers every decided session, goes to the next exercise. */
    fun apply() {
        val ui = state.value ?: return
        val ex = ui.current ?: return
        val chosen = ex.rows.mapNotNull { row -> row.flag?.let { f -> ui.choices[row.sessionId]?.let { f to it } } }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val halve = chosen.filter { it.second == LoadChoice.HALVE }.flatMap { it.first.suggestedSets }
            if (halve.isNotEmpty()) repository.updateSets(halve)
            repository.settings.addReviewedLoads(chosen.map { it.first.key })
            next(ui)
        }
    }

    /** "Manter tudo como está": every flagged session of this exercise stays and does not come back. */
    fun keepAll() {
        val ui = state.value ?: return
        val ex = ui.current ?: return
        viewModelScope.launch {
            repository.settings.addReviewedLoads(ex.flags.map { it.key })
            next(ui)
        }
    }

    /** Something was decided here: when nothing is left, the screen closes. */
    var decidedAny = false
        private set

    private fun next(ui: LoadReviewUi) {
        decidedAny = true
        val ids = order.value ?: return
        val at = ids.indexOf(ui.current?.exerciseId)
        index.value = at + 1
        choices.value = emptyMap()
    }
}

/** The file picked for import: reading, its preview, or why it can't be imported. */
sealed interface ChosenFile {
    val name: String

    data class Reading(override val name: String) : ChosenFile
    data class Ready(override val name: String, val preview: ImportPreview, val ui: ImportPreviewUi) : ChosenFile
    data class Failed(override val name: String, val message: String) : ChosenFile
}

data class BackupUi(
    val lastBackup: String,
    val chosen: ChosenFile?,
    /** A failed export. */
    val error: String?,
    val importing: Boolean,
)

class BackupViewModel(private val repository: TreinoRepository, private val resolver: ContentResolver) : ViewModel() {
    private val chosen = MutableStateFlow<ChosenFile?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val importing = MutableStateFlow(false)

    val state: StateFlow<BackupUi?> = combine(repository.settings.settings, chosen, error, importing) { s, c, e, i ->
        BackupUi(lastBackupLabel(s.lastExport, repository.today()), c, e, i)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun exportFileName(): String = exportFileName(repository.today())

    /** Writes the format 3 backup to [uri] (from `ACTION_CREATE_DOCUMENT`) and records the date. */
    fun export(uri: Uri) {
        error.value = null
        viewModelScope.launch {
            val today = repository.today()
            val ok = runCatching {
                val text = withContext(Dispatchers.Default) { BackupExporter.export(repository.snapshot(), today) }
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            if (ok) repository.settings.setLastExport(today) else error.value = "Não deu para salvar o arquivo."
        }
    }

    /** Reads and checks the file from `ACTION_OPEN_DOCUMENT`. Nothing is written yet. */
    fun pick(uri: Uri) {
        error.value = null
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { displayName(uri) } ?: uri.lastPathSegment.orEmpty()
            chosen.value = ChosenFile.Reading(name)
            chosen.value = try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val preview = withContext(Dispatchers.Default) { BackupImporter.parse(text) }
                ChosenFile.Ready(name, preview, importPreviewUi(preview, name, repository.today()))
            } catch (e: BackupFormatException) {
                ChosenFile.Failed(name, e.message ?: "Não deu para importar este arquivo.")
            } catch (e: Exception) {
                ChosenFile.Failed(name, "Não deu para ler o arquivo.")
            }
        }
    }

    /** "Importar": replaces everything with the file's data and settings. */
    fun confirmImport(then: () -> Unit) {
        val ready = chosen.value as? ChosenFile.Ready ?: return
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            try {
                repository.replaceAll(ready.preview.data)
                chosen.value = null
                then()
            } catch (e: Exception) {
                chosen.value = ChosenFile.Failed(ready.name, "Não deu para importar: nada foi alterado.")
            } finally {
                importing.value = false
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
