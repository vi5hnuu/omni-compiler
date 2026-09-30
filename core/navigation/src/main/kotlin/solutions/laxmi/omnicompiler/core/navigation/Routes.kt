package solutions.laxmi.omnicompiler.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination in the app. Features depend on these keys, never on each other; the app module
 * maps keys to feature screens. Keys are serializable so the back stack survives process death.
 */
@Serializable sealed interface Route : NavKey

// Auth
@Serializable data object WelcomeRoute : Route
@Serializable data object SignInRoute : Route
@Serializable data class SignUpRoute(val convertGuest: Boolean = false) : Route
@Serializable data class CheckInboxRoute(val email: String) : Route
@Serializable data class ForgotPasswordRoute(val email: String = "") : Route

// Workspace
@Serializable data class EditorRoute(val projectId: String? = null) : Route
@Serializable data class LanguagePickerRoute(val projectId: String) : Route
/** Renders a project's HTML or Markdown file, or runs a JavaScript project in a browser page ([fileId] null). */
@Serializable data class PreviewRoute(val projectId: String, val fileId: String? = null) : Route
@Serializable data object ProjectsRoute : Route
@Serializable data object ExamplesRoute : Route
@Serializable data class ProblemRoute(val slug: String) : Route

/** Choose where projects are stored. [change] is true when opened from Settings rather than required at start. */
@Serializable data class ProjectFolderRoute(val change: Boolean = false) : Route

// History
@Serializable data object HistoryRoute : Route
@Serializable data class JobDetailRoute(val jobId: String, val runtimeId: String? = null) : Route

// Account + developer + settings
@Serializable data object UsageRoute : Route
@Serializable data object ProfileRoute : Route
@Serializable data object DeveloperRoute : Route
@Serializable data object SettingsRoute : Route
@Serializable data object AppearanceRoute : Route
@Serializable data object OpenSourceRoute : Route
