package solutions.laxmi.omnicompiler.core.network.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import solutions.laxmi.omnicompiler.core.network.BuildConfig
import solutions.laxmi.omnicompiler.core.network.RateLimitInterceptor
import solutions.laxmi.omnicompiler.core.network.api.AuthAccountApi
import solutions.laxmi.omnicompiler.core.network.api.AuthPublicApi
import solutions.laxmi.omnicompiler.core.network.api.JudgeApi
import solutions.laxmi.omnicompiler.core.network.auth.BearerInterceptor
import solutions.laxmi.omnicompiler.core.network.auth.TokenAuthenticator
import solutions.laxmi.omnicompiler.core.network.source.AuthNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.source.RetrofitAuthNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.source.RetrofitJudgeNetworkDataSource
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Client that carries the user's bearer token and transparently refreshes it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Authenticated

/** Client for calls that must go out without credentials (sign-in, refresh). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class Anonymous

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesBaseClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                    redactHeader("X-API-Key")
                })
            }
        }
        .build()

    @Provides
    @Singleton
    @Anonymous
    fun providesAnonymousClient(base: OkHttpClient): OkHttpClient = base

    @Provides
    @Singleton
    @Authenticated
    fun providesAuthenticatedClient(
        base: OkHttpClient,
        bearer: BearerInterceptor,
        authenticator: TokenAuthenticator,
        rateLimits: RateLimitInterceptor,
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(bearer)
        .addInterceptor(rateLimits)
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    fun providesAuthPublicApi(@Anonymous client: OkHttpClient, json: Json): AuthPublicApi =
        retrofit(BuildConfig.AUTH_BASE_URL, client, json).create(AuthPublicApi::class.java)

    @Provides
    @Singleton
    fun providesAuthAccountApi(@Authenticated client: OkHttpClient, json: Json): AuthAccountApi =
        retrofit(BuildConfig.AUTH_BASE_URL, client, json).create(AuthAccountApi::class.java)

    @Provides
    @Singleton
    fun providesJudgeApi(@Authenticated client: OkHttpClient, json: Json): JudgeApi =
        retrofit(BuildConfig.API_BASE_URL, client, json).create(JudgeApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {
    @Binds
    fun bindsAuthNetworkDataSource(impl: RetrofitAuthNetworkDataSource): AuthNetworkDataSource

    @Binds
    fun bindsJudgeNetworkDataSource(impl: RetrofitJudgeNetworkDataSource): JudgeNetworkDataSource
}
