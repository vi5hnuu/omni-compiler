package solutions.laxmi.omnicompiler.core.network.di

import solutions.laxmi.omnicompiler.core.network.source.RetrofitGitNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.source.GitNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.api.GitLabApi
import solutions.laxmi.omnicompiler.core.network.api.GitHubApi
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
                    redactHeader("PRIVATE-TOKEN")
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

    /** Code hosts get the plain client plus the headers GitHub asks every API client to send. */
    @Provides
    @Singleton
    fun providesGitHubApi(base: OkHttpClient, json: Json): GitHubApi = retrofit(
        GITHUB_API,
        base.newBuilder().addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                    .build(),
            )
        }.build(),
        json,
    ).create(GitHubApi::class.java)

    @Provides
    @Singleton
    fun providesGitLabApi(base: OkHttpClient, json: Json): GitLabApi = retrofit(GITLAB_API, base, json).create(GitLabApi::class.java)

    private const val GITHUB_API = "https://api.github.com/"
    private const val GITHUB_API_VERSION = "2022-11-28"
    private const val GITLAB_API = "https://gitlab.com/api/v4/"

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

    @Binds
    fun bindsGitNetworkDataSource(impl: RetrofitGitNetworkDataSource): GitNetworkDataSource
}
