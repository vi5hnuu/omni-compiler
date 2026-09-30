package solutions.laxmi.omnicompiler.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.account.AccountRepository
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.BillingInfo
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.PlanInfo
import solutions.laxmi.omnicompiler.core.model.RateLimitSnapshot
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.UsageStats
import solutions.laxmi.omnicompiler.core.model.getOrNull
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject

data class UsageUiState(
    val loading: Boolean = true,
    val signedIn: Boolean = true,
    val error: UiText? = null,
    val plan: PlanInfo? = null,
    val billing: BillingInfo? = null,
    val stats: UsageStats? = null,
    val rateLimit: RateLimitSnapshot? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val account: AccountRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val state = MutableStateFlow(UsageUiState())
    private val events = Channel<UiText>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<UsageUiState> = combine(state, auth.session, account.rateLimit) { s, session, limit ->
        s.copy(signedIn = session is Session.Active, rateLimit = limit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            val plan = async { account.plan() }
            val billing = async { account.billing() }
            val stats = async { account.stats() }
            val planResult = plan.await()
            state.update {
                it.copy(
                    loading = false,
                    plan = planResult.getOrNull(),
                    billing = billing.await().getOrNull(),
                    stats = stats.await().getOrNull(),
                    error = (planResult as? Outcome.Failure)?.error?.takeUnless { e -> e is AppError.Unauthorized }?.toUiText(),
                )
            }
        }
    }

    /** Pulls the latest subscription status from the payment provider (e.g. after paying on the web). */
    fun sync() = act {
        when (val result = account.syncBilling()) {
            is Outcome.Success -> events.send(UiText.Res(if (result.value.applied) R.string.usage_billing_updated else R.string.usage_billing_current))
            is Outcome.Failure -> events.send(result.error.toUiText())
        }
        load()
    }

    fun cancelSubscription() = act {
        when (val result = account.cancelSubscription()) {
            is Outcome.Success -> events.send(UiText.Res(R.string.usage_cancelled))
            is Outcome.Failure -> events.send(result.error.toUiText())
        }
        load()
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            block()
            state.update { it.copy(busy = false) }
        }
    }
}
