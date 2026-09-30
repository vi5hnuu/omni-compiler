package solutions.laxmi.omnicompiler.core.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import solutions.laxmi.omnicompiler.core.data.BuildConfig
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Outcome
import javax.inject.Inject

/** Result of the system Google account picker. */
sealed interface GoogleIdTokenResult {
    data class Token(val idToken: String) : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data class Failed(val error: AppError) : GoogleIdTokenResult
}

/** Obtains a Google ID token via Credential Manager; the auth service verifies it server-side. */
interface GoogleIdTokenProvider {
    val isAvailable: Boolean

    /** [activityContext] must be an Activity: Credential Manager shows UI over it. It is not retained. */
    suspend fun requestIdToken(activityContext: Context): GoogleIdTokenResult
}

internal class CredentialManagerGoogleIdTokenProvider @Inject constructor() : GoogleIdTokenProvider {

    private val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    override val isAvailable: Boolean get() = serverClientId.isNotBlank()

    override suspend fun requestIdToken(activityContext: Context): GoogleIdTokenResult {
        if (!isAvailable) return GoogleIdTokenResult.Failed(AppError.NotAvailable(reason = ErrorReason.GoogleNotConfigured))
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleIdTokenResult.Failed(AppError.Unknown(reason = ErrorReason.GoogleUnsupportedCredential))
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleIdTokenResult.Failed(AppError.NotAvailable(reason = ErrorReason.GoogleNoAccount))
        } catch (e: GetCredentialException) {
            GoogleIdTokenResult.Failed(AppError.Unknown(reason = ErrorReason.GoogleFailed))
        } catch (e: GoogleIdTokenParsingException) {
            GoogleIdTokenResult.Failed(AppError.Unknown(reason = ErrorReason.GoogleInvalidToken))
        }
    }
}

