package solutions.laxmi.omnicompiler.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import solutions.laxmi.omnicompiler.BuildConfig
import solutions.laxmi.omnicompiler.core.review.ReviewConfig

@Module
@InstallIn(SingletonComponent::class)
object ReviewConfigModule {
    @Provides
    fun providesReviewConfig(): ReviewConfig = ReviewConfig(storePackage = BuildConfig.STORE_PACKAGE)
}
