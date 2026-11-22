package solutions.laxmi.omnicompiler.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import solutions.laxmi.omnicompiler.BuildConfig
import solutions.laxmi.omnicompiler.core.appupdate.UpdateConfig
import solutions.laxmi.omnicompiler.core.integrity.IntegrityConfig

/** Google Play settings from the build (gradle.properties / local.properties). */
@Module
@InstallIn(SingletonComponent::class)
object PlayConfigModule {
    @Provides
    fun providesIntegrityConfig(): IntegrityConfig = IntegrityConfig(cloudProjectNumber = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER)

    @Provides
    fun providesUpdateConfig(): UpdateConfig = UpdateConfig(immediatePriority = BuildConfig.IMMEDIATE_UPDATE_PRIORITY)
}
