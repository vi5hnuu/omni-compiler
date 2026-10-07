package solutions.laxmi.omnicompiler.core.ui

import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason

/**
 * User-facing text for an error. Server explanations are shown as sent (product decision);
 * device-detected causes are localized from their [ErrorReason].
 */
fun AppError.toUiText(): UiText {
    val base: UiText = when {
        message.isNotBlank() -> UiText.Raw(message)
        reason != null -> reason!!.toUiText()
        else -> UiText.Res(R.string.common_error_unknown)
    }
    return when (this) {
        is AppError.RateLimited -> retryAfterSeconds?.let { UiText.Res(R.string.common_error_retry_after, base, it) } ?: base
        is AppError.Server -> requestId?.let { UiText.Res(R.string.common_error_with_reference, base, it.take(8)) } ?: base
        else -> base
    }
}

fun ErrorReason.toUiText(): UiText = when (this) {
    ErrorReason.Offline -> UiText.Res(R.string.common_error_offline)
    ErrorReason.ServerUnreachable -> UiText.Res(R.string.common_error_server_unreachable)
    ErrorReason.SecureConnectionFailed -> UiText.Res(R.string.common_error_secure_connection)
    ErrorReason.Timeout -> UiText.Res(R.string.common_error_timeout)
    ErrorReason.NetworkError -> UiText.Res(R.string.common_error_network)
    ErrorReason.BadResponse -> UiText.Res(R.string.common_error_bad_response)
    ErrorReason.TooManyRequests -> UiText.Res(R.string.common_error_too_many_requests)
    ErrorReason.ServerTrouble -> UiText.Res(R.string.common_error_server_trouble)
    is ErrorReason.RequestFailed -> UiText.Res(R.string.common_error_request_failed, status)
    ErrorReason.Unknown -> UiText.Res(R.string.common_error_unknown)
    ErrorReason.GoogleNotConfigured -> UiText.Res(R.string.common_error_google_not_configured)
    ErrorReason.GoogleNoAccount -> UiText.Res(R.string.common_error_google_no_account)
    ErrorReason.GoogleUnsupportedCredential -> UiText.Res(R.string.common_error_google_unsupported)
    ErrorReason.GoogleInvalidToken -> UiText.Res(R.string.common_error_google_invalid_token)
    ErrorReason.GoogleFailed -> UiText.Res(R.string.common_error_google_failed)
    ErrorReason.SignInIncomplete -> UiText.Res(R.string.common_error_sign_in_incomplete)
    ErrorReason.ProjectNotFound -> UiText.Res(R.string.common_error_project_not_found)
    ErrorReason.FileNotFound -> UiText.Res(R.string.common_error_file_not_found)
    ErrorReason.EntryFileMissing -> UiText.Res(R.string.common_error_entry_missing)
    ErrorReason.EntryNamedByRuntime -> UiText.Res(R.string.common_error_entry_named_by_runtime)
    is ErrorReason.EntryNameTaken -> UiText.Res(R.string.common_error_entry_name_taken, fileName, runtimeId)
    ErrorReason.NameRequired -> UiText.Res(R.string.common_error_name_required)
    ErrorReason.FileNameRequired -> UiText.Res(R.string.common_error_file_name_required)
    ErrorReason.InvalidPath -> UiText.Res(R.string.common_error_invalid_path)
    is ErrorReason.PathTooDeep -> UiText.Res(R.string.common_error_path_too_deep, max)
    is ErrorReason.TotalSizeTooLarge -> UiText.Res(R.string.common_error_total_too_large, maxMb)
    is ErrorReason.FileNameTooLong -> UiText.Plural(R.plurals.common_error_file_name_too_long, max, max)
    is ErrorReason.FileExists -> UiText.Res(R.string.common_error_file_exists, name)
    is ErrorReason.TooManyFiles -> UiText.Plural(R.plurals.common_error_too_many_files, max, max)
    ErrorReason.LanguagesUnavailable -> UiText.Res(R.string.common_error_languages_unavailable)
    ErrorReason.OpenProjectFirst -> UiText.Res(R.string.common_error_open_project_first)
    ErrorReason.ExportFailed -> UiText.Res(R.string.common_error_export_failed)
    ErrorReason.ProjectsFolderUnavailable -> UiText.Res(R.string.common_error_projects_folder)
    ErrorReason.NothingToImport -> UiText.Res(R.string.common_error_nothing_to_import)
    ErrorReason.GitTokenRejected -> UiText.Res(R.string.common_error_git_token)
    ErrorReason.GitNotConnected -> UiText.Res(R.string.common_error_git_not_connected)
    ErrorReason.GitPullFirst -> UiText.Res(R.string.common_error_git_pull_first)
    ErrorReason.GitNothingToCommit -> UiText.Res(R.string.common_error_git_nothing)
    ErrorReason.GitResolveConflictsFirst -> UiText.Res(R.string.common_error_git_conflicts)
    ErrorReason.GitRequestFailed -> UiText.Res(R.string.common_error_git_failed)
    ErrorReason.GitAccessDenied -> UiText.Res(R.string.common_error_git_access_denied)
    ErrorReason.GitRateLimited -> UiText.Res(R.string.common_error_git_rate_limited)
    ErrorReason.GitNotFound -> UiText.Res(R.string.common_error_git_not_found)
    ErrorReason.GitLocalChanged -> UiText.Res(R.string.common_error_git_local_changed)
    ErrorReason.GitFileNotText -> UiText.Res(R.string.common_error_git_not_text)
    ErrorReason.GitListingTooLarge -> UiText.Res(R.string.common_error_git_listing_too_large)
    ErrorReason.GitFolderMissing -> UiText.Res(R.string.common_error_git_folder_missing)
    is ErrorReason.UnknownFileLanguage -> UiText.Res(R.string.common_error_unknown_file_language, fileName)
    ErrorReason.DocumentOpenFailed -> UiText.Res(R.string.common_error_document_open_failed)
    is ErrorReason.DocumentTooLarge -> UiText.Res(R.string.common_error_document_too_large, maxKb)
    ErrorReason.DocumentNotText -> UiText.Res(R.string.common_error_document_not_text)
    ErrorReason.DocumentReadFailed -> UiText.Res(R.string.common_error_document_read_failed)
    ErrorReason.DocumentNoPermission -> UiText.Res(R.string.common_error_document_no_permission)
    ErrorReason.RunInProgress -> UiText.Res(R.string.common_error_run_in_progress)
    ErrorReason.RunQueued -> UiText.Res(R.string.common_error_run_queued)
    ErrorReason.WriteCodeFirst -> UiText.Res(R.string.common_error_write_code_first)
    is ErrorReason.SourceTooLarge -> UiText.Res(R.string.common_error_source_too_large, fileName, maxKb)
    ErrorReason.NoTests -> UiText.Res(R.string.common_error_no_tests)
    is ErrorReason.TooManyTests -> UiText.Plural(R.plurals.common_error_too_many_tests, max, max)
    ErrorReason.RuntimeUnavailable -> UiText.Res(R.string.common_error_runtime_unavailable)
}
