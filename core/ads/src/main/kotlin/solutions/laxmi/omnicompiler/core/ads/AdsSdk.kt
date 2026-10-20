package solutions.laxmi.omnicompiler.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consent and SDK start-up, shared by every ad format. Consent comes first (Google's UMP form where the law requires
 * it); the SDK starts only once ads may be requested, and [ready] turns true after that.
 */
@Singleton
internal class AdsSdk @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val allowed = MutableStateFlow(consent.canRequestAds())
    private val optionsRequired = MutableStateFlow(false)
    private val started = MutableStateFlow(false)
    private val starting = AtomicBoolean(false)

    val canRequestAds: StateFlow<Boolean> = allowed.asStateFlow()
    val privacyOptionsRequired: StateFlow<Boolean> = optionsRequired.asStateFlow()

    /** True once consent allows ads and the SDK has initialised; loads wait for it. */
    val ready: StateFlow<Boolean> = combine(started, allowed) { started, allowed -> started && allowed }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun start(activity: Activity) {
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> onConsentKnown() }
        }, { _ ->
            // No network or a UMP outage: fall back to what was decided before (canRequestAds is cached).
            onConsentKnown()
        })
        // Consent from an earlier session lets ads start in parallel with the refresh above.
        if (consent.canRequestAds()) onConsentKnown()
    }

    fun showPrivacyOptions(activity: Activity) {
        // A changed choice takes effect at once: ads stop, or the SDK starts if it never had consent before.
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> onConsentKnown() }
    }

    /** Suspends until the SDK may load ads. */
    suspend fun awaitReady() {
        ready.first { it }
    }

    private fun onConsentKnown() {
        allowed.value = consent.canRequestAds()
        optionsRequired.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!allowed.value || !starting.compareAndSet(false, true)) return
        scope.launch(Dispatchers.Main.immediate) {
            // Initialisation does disk work; keep it off the main thread.
            withContext(io) { MobileAds.initialize(context) }
            started.value = true
        }
    }
}
