package solutions.laxmi.omnicompiler.core.datastore

import solutions.laxmi.omnicompiler.core.model.AppTheme
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import solutions.laxmi.omnicompiler.core.model.CodeFont
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.EditorTheme
import solutions.laxmi.omnicompiler.core.model.LineSpacing
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.RunSettings
import javax.inject.Inject
import javax.inject.Singleton

/** Non-sensitive user preferences. */
interface PreferencesStore {
    val editorSettings: Flow<EditorSettings>
    val runSettings: Flow<RunSettings>
    val recentRuntimeIds: Flow<List<String>>
    val lastProjectId: Flow<String?>
    val appTheme: Flow<AppTheme>

    /** The picked projects folder as `treeUri` + folder document id; null until chosen. Device-level, survives sign-out. */
    val projectsRoot: Flow<Pair<String, String>?>
    suspend fun setProjectsRoot(treeUri: String, docId: String)

    /** When the app first ran (recorded on the first call); ads stay quiet for a while after install. */
    suspend fun firstLaunchAt(now: Long): Long
    suspend fun lastInterstitialAt(): Long?
    suspend fun setLastInterstitialAt(at: Long)

    suspend fun updateEditor(transform: (EditorSettings) -> EditorSettings)
    suspend fun updateRun(transform: (RunSettings) -> RunSettings)
    suspend fun markRuntimeUsed(runtimeId: String)
    suspend fun setLastProjectId(id: String?)
    suspend fun setAppTheme(theme: AppTheme)

    /** Projects whose guest "keep your work" prompt was already shown. */
    suspend fun guestPromptedProjects(): Set<String>
    suspend fun markGuestPrompted(projectId: String)

    suspend fun clearUserScoped()
}

@Singleton
internal class DataStorePreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStore {

    override val editorSettings: Flow<EditorSettings> = dataStore.data.map { it.toEditorSettings() }.distinctUntilChanged()
    override val runSettings: Flow<RunSettings> = dataStore.data.map { it.toRunSettings() }.distinctUntilChanged()
    override val recentRuntimeIds: Flow<List<String>> = dataStore.data
        .map { prefs -> prefs[Keys.RECENT_RUNTIMES]?.split(SEPARATOR)?.filter(String::isNotBlank).orEmpty() }
        .distinctUntilChanged()
    override val lastProjectId: Flow<String?> = dataStore.data.map { it[Keys.LAST_PROJECT] }.distinctUntilChanged()
    override val appTheme: Flow<AppTheme> = dataStore.data.map { enumOr(it[Keys.APP_THEME], AppTheme.SYSTEM) }.distinctUntilChanged()
    override val projectsRoot: Flow<Pair<String, String>?> = dataStore.data
        .map { prefs -> prefs[Keys.ROOT_TREE]?.let { tree -> prefs[Keys.ROOT_DOC]?.let { tree to it } } }
        .distinctUntilChanged()

    override suspend fun firstLaunchAt(now: Long): Long {
        var first = now
        dataStore.edit { prefs ->
            val stored = prefs[Keys.FIRST_LAUNCH]
            if (stored == null) prefs[Keys.FIRST_LAUNCH] = now else first = stored
        }
        return first
    }

    override suspend fun lastInterstitialAt(): Long? = dataStore.data.first()[Keys.LAST_INTERSTITIAL]

    override suspend fun setLastInterstitialAt(at: Long) {
        dataStore.edit { it[Keys.LAST_INTERSTITIAL] = at }
    }

    override suspend fun setProjectsRoot(treeUri: String, docId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ROOT_TREE] = treeUri
            prefs[Keys.ROOT_DOC] = docId
        }
    }

    override suspend fun updateEditor(transform: (EditorSettings) -> EditorSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toEditorSettings())) }
    }

    override suspend fun updateRun(transform: (RunSettings) -> RunSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toRunSettings())) }
    }

    override suspend fun markRuntimeUsed(runtimeId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_RUNTIMES]?.split(SEPARATOR).orEmpty()
            val updated = (listOf(runtimeId) + current.filter { it != runtimeId && it.isNotBlank() }).take(MAX_RECENTS)
            prefs[Keys.RECENT_RUNTIMES] = updated.joinToString(SEPARATOR)
        }
    }

    override suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { prefs -> prefs[Keys.APP_THEME] = theme.name }
    }

    override suspend fun setLastProjectId(id: String?) {
        dataStore.edit { prefs -> if (id == null) prefs.remove(Keys.LAST_PROJECT) else prefs[Keys.LAST_PROJECT] = id }
    }

    override suspend fun guestPromptedProjects(): Set<String> = dataStore.data.first()[Keys.GUEST_PROMPTED].orEmpty()

    override suspend fun markGuestPrompted(projectId: String) {
        dataStore.edit { prefs -> prefs[Keys.GUEST_PROMPTED] = prefs[Keys.GUEST_PROMPTED].orEmpty() + projectId }
    }

    override suspend fun clearUserScoped() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.LAST_PROJECT)
            prefs.remove(Keys.RECENT_RUNTIMES)
            prefs.remove(Keys.GUEST_PROMPTED)
        }
    }

    private fun Preferences.toEditorSettings(): EditorSettings {
        val d = EditorSettings()
        return EditorSettings(
            theme = enumOr(this[Keys.THEME], d.theme),
            font = enumOr(this[Keys.FONT], d.font),
            fontSizeSp = (this[Keys.FONT_SIZE] ?: d.fontSizeSp).coerceIn(EditorSettings.MIN_FONT_SP, EditorSettings.MAX_FONT_SP),
            ligatures = this[Keys.LIGATURES] ?: d.ligatures,
            indentGuides = this[Keys.INDENT_GUIDES] ?: d.indentGuides,
            lineSpacing = enumOr(this[Keys.LINE_SPACING], d.lineSpacing),
            minimap = this[Keys.MINIMAP] ?: d.minimap,
            symbolRow = this[Keys.SYMBOL_ROW] ?: d.symbolRow,
            autocomplete = this[Keys.AUTOCOMPLETE] ?: d.autocomplete,
            wordWrap = this[Keys.WORD_WRAP] ?: d.wordWrap,
            tabSize = this[Keys.TAB_SIZE] ?: d.tabSize,
            showInvisibles = this[Keys.SHOW_INVISIBLES] ?: d.showInvisibles,
            stickyScroll = this[Keys.STICKY_SCROLL] ?: d.stickyScroll,
            hardwareKeyboardOnly = this[Keys.HARDWARE_KEYBOARD_ONLY] ?: d.hardwareKeyboardOnly,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: EditorSettings) {
        this[Keys.THEME] = s.theme.name
        this[Keys.FONT] = s.font.name
        this[Keys.FONT_SIZE] = s.fontSizeSp
        this[Keys.LIGATURES] = s.ligatures
        this[Keys.INDENT_GUIDES] = s.indentGuides
        this[Keys.LINE_SPACING] = s.lineSpacing.name
        this[Keys.MINIMAP] = s.minimap
        this[Keys.SYMBOL_ROW] = s.symbolRow
        this[Keys.AUTOCOMPLETE] = s.autocomplete
        this[Keys.WORD_WRAP] = s.wordWrap
        this[Keys.TAB_SIZE] = s.tabSize
        this[Keys.SHOW_INVISIBLES] = s.showInvisibles
        this[Keys.STICKY_SCROLL] = s.stickyScroll
        this[Keys.HARDWARE_KEYBOARD_ONLY] = s.hardwareKeyboardOnly
    }

    private fun Preferences.toRunSettings(): RunSettings {
        val d = RunSettings()
        return RunSettings(
            defaultRuntimeId = this[Keys.DEFAULT_RUNTIME],
            defaultLimits = Limits(
                timeMs = this[Keys.DEFAULT_TIME_MS] ?: d.defaultLimits.timeMs,
                memMb = this[Keys.DEFAULT_MEM_MB] ?: d.defaultLimits.memMb,
            ),
            runOnCtrlEnter = this[Keys.CTRL_ENTER] ?: d.runOnCtrlEnter,
            bypassCache = this[Keys.BYPASS_CACHE] ?: d.bypassCache,
            sendQueuedWhenOnline = this[Keys.SEND_WHEN_ONLINE] ?: d.sendQueuedWhenOnline,
            benchmarkCopies = this[Keys.BENCHMARK_COPIES] ?: d.benchmarkCopies,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: RunSettings) {
        val runtimeId = s.defaultRuntimeId
        if (runtimeId == null) remove(Keys.DEFAULT_RUNTIME) else this[Keys.DEFAULT_RUNTIME] = runtimeId
        this[Keys.DEFAULT_TIME_MS] = s.defaultLimits.timeMs
        this[Keys.DEFAULT_MEM_MB] = s.defaultLimits.memMb
        this[Keys.CTRL_ENTER] = s.runOnCtrlEnter
        this[Keys.BYPASS_CACHE] = s.bypassCache
        this[Keys.SEND_WHEN_ONLINE] = s.sendQueuedWhenOnline
        this[Keys.BENCHMARK_COPIES] = s.benchmarkCopies
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, fallback: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: fallback

    private object Keys {
        val THEME = stringPreferencesKey("editor_theme")
        val APP_THEME = stringPreferencesKey("app_theme")
        val ROOT_TREE = stringPreferencesKey("projects_root_tree")
        val ROOT_DOC = stringPreferencesKey("projects_root_doc")
        val FIRST_LAUNCH = longPreferencesKey("first_launch_at")
        val LAST_INTERSTITIAL = longPreferencesKey("last_interstitial_at")
        val FONT = stringPreferencesKey("editor_font")
        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val LIGATURES = booleanPreferencesKey("editor_ligatures")
        val INDENT_GUIDES = booleanPreferencesKey("editor_indent_guides")
        val LINE_SPACING = stringPreferencesKey("editor_line_spacing")
        val MINIMAP = booleanPreferencesKey("editor_minimap")
        val SYMBOL_ROW = booleanPreferencesKey("editor_symbol_row")
        val AUTOCOMPLETE = booleanPreferencesKey("editor_autocomplete")
        val WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
        val TAB_SIZE = intPreferencesKey("editor_tab_size")
        val SHOW_INVISIBLES = booleanPreferencesKey("editor_show_invisibles")
        val STICKY_SCROLL = booleanPreferencesKey("editor_sticky_scroll")
        val HARDWARE_KEYBOARD_ONLY = booleanPreferencesKey("editor_hardware_keyboard_only")
        val DEFAULT_RUNTIME = stringPreferencesKey("run_default_runtime")
        val DEFAULT_TIME_MS = intPreferencesKey("run_default_time_ms")
        val DEFAULT_MEM_MB = intPreferencesKey("run_default_mem_mb")
        val CTRL_ENTER = booleanPreferencesKey("run_ctrl_enter")
        val BYPASS_CACHE = booleanPreferencesKey("run_bypass_cache")
        val SEND_WHEN_ONLINE = booleanPreferencesKey("run_send_when_online")
        val BENCHMARK_COPIES = intPreferencesKey("run_benchmark_copies")
        val RECENT_RUNTIMES = stringPreferencesKey("recent_runtimes")
        val LAST_PROJECT = stringPreferencesKey("last_project")
        val GUEST_PROMPTED = stringSetPreferencesKey("guest_prompted_projects")
    }

    private companion object {
        const val SEPARATOR = ","
        const val MAX_RECENTS = 8
    }
}
