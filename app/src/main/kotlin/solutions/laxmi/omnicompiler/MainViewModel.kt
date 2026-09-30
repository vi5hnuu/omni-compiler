package solutions.laxmi.omnicompiler

import solutions.laxmi.omnicompiler.core.model.AppTheme
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.model.Session
import javax.inject.Inject

/** Coarse auth state that decides the root destination. */
enum class AuthGate { Loading, SignedOut, SignedIn }

@HiltViewModel
class MainViewModel @Inject constructor(auth: AuthRepository, settings: SettingsRepository) : ViewModel() {
    val gate: StateFlow<AuthGate> = auth.session
        .map {
            when (it) {
                Session.Loading -> AuthGate.Loading
                Session.SignedOut -> AuthGate.SignedOut
                is Session.Active -> AuthGate.SignedIn
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthGate.Loading)

    val appTheme: StateFlow<AppTheme> = settings.appTheme.stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)
}
