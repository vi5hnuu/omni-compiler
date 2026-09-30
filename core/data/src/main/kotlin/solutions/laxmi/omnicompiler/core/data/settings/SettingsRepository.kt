package solutions.laxmi.omnicompiler.core.data.settings

import kotlinx.coroutines.flow.Flow
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.RunSettings
import javax.inject.Inject

interface SettingsRepository {
    val editorSettings: Flow<EditorSettings>
    val runSettings: Flow<RunSettings>
    suspend fun updateEditor(transform: (EditorSettings) -> EditorSettings)
    suspend fun updateRun(transform: (RunSettings) -> RunSettings)
}

internal class DefaultSettingsRepository @Inject constructor(
    private val store: PreferencesStore,
) : SettingsRepository {
    override val editorSettings = store.editorSettings
    override val runSettings = store.runSettings
    override suspend fun updateEditor(transform: (EditorSettings) -> EditorSettings) = store.updateEditor(transform)
    override suspend fun updateRun(transform: (RunSettings) -> RunSettings) = store.updateRun(transform)
}
