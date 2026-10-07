package solutions.laxmi.omnicompiler.core.model

/**
 * Why something failed on the device, independent of language. `core/ui` maps every case to a
 * string resource; add a case there whenever one is added here.
 */
sealed interface ErrorReason {
    // Transport
    data object Offline : ErrorReason
    /** The device has a network but the server couldn't be reached (DNS failure, refused connection, server down). */
    data object ServerUnreachable : ErrorReason
    /** The TLS handshake failed (captive portal, intercepting proxy, wrong device clock). */
    data object SecureConnectionFailed : ErrorReason
    data object Timeout : ErrorReason
    data object NetworkError : ErrorReason
    data object BadResponse : ErrorReason
    data object TooManyRequests : ErrorReason
    data object ServerTrouble : ErrorReason
    data class RequestFailed(val status: Int) : ErrorReason
    data object Unknown : ErrorReason

    // Sign-in
    data object GoogleNotConfigured : ErrorReason
    data object GoogleNoAccount : ErrorReason
    data object GoogleUnsupportedCredential : ErrorReason
    data object GoogleInvalidToken : ErrorReason
    data object GoogleFailed : ErrorReason
    data object SignInIncomplete : ErrorReason

    // Projects and files
    data object ProjectNotFound : ErrorReason
    /** The projects folder on device storage can't be reached (access revoked, folder deleted, storage error). */
    data object ProjectsFolderUnavailable : ErrorReason
    /** An imported folder or file had nothing that can be edited as source (empty, binary, too large). */
    data object NothingToImport : ErrorReason

    // Git hosts
    data object GitTokenRejected : ErrorReason
    data object GitNotConnected : ErrorReason
    /** The remote branch moved since the last pull; pushing would overwrite someone else's commits. */
    data object GitPullFirst : ErrorReason
    data object GitNothingToCommit : ErrorReason
    data object GitResolveConflictsFirst : ErrorReason
    data object GitRequestFailed : ErrorReason
    /** 403: the token lacks a scope, SSO isn't authorised, or the repository forbids it. */
    data object GitAccessDenied : ErrorReason
    /** 403/429 with the host's rate-limit headers. */
    data object GitRateLimited : ErrorReason
    /** 404: the repository, branch or path doesn't exist or the token can't see it. */
    data object GitNotFound : ErrorReason
    /** A file pull or resolve was about to replace changed after it was compared; nothing was written. */
    data object GitLocalChanged : ErrorReason
    /** The remote version isn't text (binary or another encoding), so it can't be brought into the project. */
    data object GitFileNotText : ErrorReason
    /** A listing was too long to be read completely; syncing from a partial one could delete files. */
    data object GitListingTooLarge : ErrorReason
    /** The tracked folder is gone (or empty) on the remote; nothing is deleted locally on that basis. */
    data object GitFolderMissing : ErrorReason
    /** No runtime matches the imported file's extension. */
    data class UnknownFileLanguage(val fileName: String) : ErrorReason
    data object FileNotFound : ErrorReason
    data object EntryFileMissing : ErrorReason
    data object EntryNamedByRuntime : ErrorReason
    data class EntryNameTaken(val fileName: String, val runtimeId: String) : ErrorReason
    data object NameRequired : ErrorReason
    data object FileNameRequired : ErrorReason
    data object FlatWorkspace : ErrorReason
    data class FileNameTooLong(val max: Int) : ErrorReason
    data class FileExists(val name: String) : ErrorReason
    data class TooManyFiles(val max: Int) : ErrorReason
    data object LanguagesUnavailable : ErrorReason
    data object OpenProjectFirst : ErrorReason
    data object ExportFailed : ErrorReason

    // Picked documents
    data object DocumentOpenFailed : ErrorReason
    data class DocumentTooLarge(val maxKb: Int) : ErrorReason
    data object DocumentReadFailed : ErrorReason
    data object DocumentNoPermission : ErrorReason
    /** The file isn't UTF-8 text (binary, or another encoding), so it can't be edited as code. */
    data object DocumentNotText : ErrorReason

    // Runs
    data object RunInProgress : ErrorReason
    data object RunQueued : ErrorReason
    data object WriteCodeFirst : ErrorReason
    data class SourceTooLarge(val fileName: String, val maxKb: Int) : ErrorReason
    data object NoTests : ErrorReason
    data class TooManyTests(val max: Int) : ErrorReason
    data object RuntimeUnavailable : ErrorReason
}
