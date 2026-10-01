package solutions.laxmi.omnicompiler.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import solutions.laxmi.omnicompiler.BuildConfig
import solutions.laxmi.omnicompiler.core.common.AppFeatures

@Module
@InstallIn(SingletonComponent::class)
object AppFeaturesModule {
    /** Debug builds keep every feature for development; release-type builds (Play, benchmarks) hide unfinished ones. */
    @Provides
    fun providesAppFeatures(): AppFeatures = AppFeatures(accountTools = BuildConfig.DEBUG)
}
