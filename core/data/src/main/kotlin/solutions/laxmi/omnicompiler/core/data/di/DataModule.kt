package solutions.laxmi.omnicompiler.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.auth.CredentialManagerGoogleIdTokenProvider
import solutions.laxmi.omnicompiler.core.data.auth.DefaultAuthRepository
import solutions.laxmi.omnicompiler.core.data.auth.GoogleIdTokenProvider
import solutions.laxmi.omnicompiler.core.data.connectivity.AndroidConnectivityObserver
import solutions.laxmi.omnicompiler.core.data.connectivity.ConnectivityObserver
import solutions.laxmi.omnicompiler.core.data.execution.DefaultExecutionRepository
import solutions.laxmi.omnicompiler.core.data.execution.ExecutionRepository
import solutions.laxmi.omnicompiler.core.data.project.LocalProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.DefaultRuntimeRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.DefaultSettingsRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds fun bindsAuthRepository(impl: DefaultAuthRepository): AuthRepository
    @Binds fun bindsGoogleIdTokenProvider(impl: CredentialManagerGoogleIdTokenProvider): GoogleIdTokenProvider
    @Binds fun bindsRuntimeRepository(impl: DefaultRuntimeRepository): RuntimeRepository
    @Binds fun bindsProjectRepository(impl: LocalProjectRepository): ProjectRepository
    @Binds fun bindsSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository
    @Binds fun bindsExecutionRepository(impl: DefaultExecutionRepository): ExecutionRepository
    @Binds fun bindsConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver
}
