package dev.charlesferreira.treino.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.charlesferreira.treino.domain.backup.BackupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The Profile switches and the name. Sound and vibration default to on, like the PWA. */
data class Settings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val keepScreenOn: Boolean = false,
    val profileName: String = "",
    /** "YYYY-MM-DD" of the last export, null before the first. */
    val lastExport: String? = null,
    /** Sessions the load review already settled, as [loadReviewKey]. They do not come back. */
    val reviewedLoads: Set<String> = emptySet(),
)

/** Key of a reviewed session in [Settings.reviewedLoads]: one exercise in one session. */
fun loadReviewKey(sessionId: String, exerciseId: String): String = "$sessionId:$exerciseId"

/** Where sound and vibration also have to land: the rest timer reads its own store from a receiver. */
interface TimerSwitches {
    fun setSound(enabled: Boolean)
    fun setVibration(enabled: Boolean)
}

/**
 * Settings in DataStore (Preferences). Sound and vibration are mirrored into the timer through
 * [TimerSwitches] on every write and once at app start ([syncTimer]), so the timer, which runs
 * from receivers in a fresh process, never reads DataStore.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val timer: TimerSwitches,
) {
    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setSound(enabled: Boolean) {
        dataStore.edit { it[SOUND] = enabled }
        timer.setSound(enabled)
    }

    suspend fun setVibration(enabled: Boolean) {
        dataStore.edit { it[VIBRATION] = enabled }
        timer.setVibration(enabled)
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setProfileName(name: String) {
        dataStore.edit { it[PROFILE_NAME] = name.trim() }
    }

    suspend fun setLastExport(date: String) {
        dataStore.edit { it[LAST_EXPORT] = date }
    }

    /** Remembers sessions of the load review (kept as they are, or corrected) so they don't come back. */
    suspend fun addReviewedLoads(keys: Collection<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { it[REVIEWED_LOADS] = (it[REVIEWED_LOADS] ?: emptySet()) + keys }
    }

    /** Applies what a backup carries; a null field leaves the current value. */
    suspend fun apply(backup: BackupSettings) {
        dataStore.edit { prefs ->
            backup.sound?.let { prefs[SOUND] = it }
            backup.vibration?.let { prefs[VIBRATION] = it }
            backup.keepScreenOn?.let { prefs[KEEP_SCREEN_ON] = it }
            backup.profileName?.let { prefs[PROFILE_NAME] = it.trim() }
        }
        syncTimer()
    }

    suspend fun toBackup(): BackupSettings = current().let {
        BackupSettings(
            sound = it.sound,
            vibration = it.vibration,
            keepScreenOn = it.keepScreenOn,
            profileName = it.profileName.takeIf { name -> name.isNotEmpty() },
        )
    }

    suspend fun syncTimer() {
        val s = current()
        timer.setSound(s.sound)
        timer.setVibration(s.vibration)
    }

    private fun Preferences.toSettings() = Settings(
        sound = this[SOUND] ?: true,
        vibration = this[VIBRATION] ?: true,
        keepScreenOn = this[KEEP_SCREEN_ON] ?: false,
        profileName = this[PROFILE_NAME] ?: "",
        lastExport = this[LAST_EXPORT],
        reviewedLoads = this[REVIEWED_LOADS] ?: emptySet(),
    )

    private companion object {
        val SOUND = booleanPreferencesKey("sound")
        val VIBRATION = booleanPreferencesKey("vibration")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val LAST_EXPORT = stringPreferencesKey("last_export")
        val REVIEWED_LOADS = stringSetPreferencesKey("reviewed_loads")
    }
}
