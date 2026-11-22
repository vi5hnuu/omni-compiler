package solutions.laxmi.omnicompiler.core.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.network.humancheck.HumanCheckTokens
import javax.inject.Singleton

/** [TokenProviderSource] backed by Google's standard Integrity API. */
internal class PlayTokenProviderSource(private val context: Context) : TokenProviderSource {

    private val manager by lazy { IntegrityManagerFactory.createStandard(context) }

    override suspend fun prepare(cloudProjectNumber: Long): TokenProvider {
        val play = manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build(),
        ).await()
        return TokenProvider { hash ->
            try {
                play.request(StandardIntegrityTokenRequest.builder().setRequestHash(hash).build()).await().token()
            } catch (e: StandardIntegrityException) {
                if (e.errorCode == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw ProviderInvalidException(e)
                throw e
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object PlayIntegrityModule {

    @Provides
    @Singleton
    fun providesIntegrityTokens(
        @ApplicationContext context: Context,
        config: IntegrityConfig,
        @ApplicationScope scope: CoroutineScope,
    ): IntegrityTokens = IntegrityTokens(PlayTokenProviderSource(context), config, scope)

    @Provides
    fun providesHumanCheckTokens(tokens: IntegrityTokens): HumanCheckTokens = tokens

    @Provides
    fun providesAppIntegrity(tokens: IntegrityTokens): AppIntegrity = tokens
}
