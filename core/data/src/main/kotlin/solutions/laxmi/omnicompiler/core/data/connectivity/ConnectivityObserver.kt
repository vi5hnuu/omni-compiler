package solutions.laxmi.omnicompiler.core.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device currently has a network that offers internet. Android's "validated" flag is deliberately not
 * required: its probe fails behind some firewalls, VPNs and private-DNS setups where the internet works fine, and
 * real request failures already tell an unreachable server apart.
 */
interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
}

@Singleton
internal class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) : ConnectivityObserver {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentlyOnline()) }
            // During onLost the lost network can still be reported as active; don't count it.
            override fun onLost(network: Network) { trySend(currentlyOnline(excluding = network)) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(currentlyOnline()) }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(request, callback)
        trySend(currentlyOnline())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

    private fun currentlyOnline(excluding: Network? = null): Boolean {
        val active = manager.activeNetwork?.takeIf { it != excluding } ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
