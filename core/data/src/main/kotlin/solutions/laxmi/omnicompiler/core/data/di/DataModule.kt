package solutions.laxmi.omnicompiler.core.data.di

import solutions.laxmi.omnicompiler.core.data.git.GitRepository
import solutions.laxmi.omnicompiler.core.data.git.DefaultGitRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectFolderRepository
import solutions.laxmi.omnicompiler.core.data.project.DefaultProjectFolderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import solutions.laxmi.omnicompiler.core.data.account.AccountRepository
import solutions.laxmi.omnicompiler.core.data.account.AppConfig
import solutions.laxmi.omnicompiler.core.data.account.BuildAppConfig
import solutions.laxmi.omnicompiler.core.data.account.DefaultAccountRepository
import solutions.laxmi.omnicompiler.core.data.account.DefaultDeveloperRepository
import solutions.laxmi.omnicompiler.core.data.account.DeveloperRepository
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.auth.CredentialManagerGoogleIdTokenProvider
import solutions.laxmi.omnicompiler.core.data.auth.DefaultAuthRepository
import solutions.laxmi.omnicompiler.core.data.auth.DefaultGuestPromptRepository
import solutions.laxmi.omnicompiler.core.data.auth.GuestPromptRepository
import solutions.laxmi.omnicompiler.core.data.auth.GoogleIdTokenProvider
import solutions.laxmi.omnicompiler.core.data.connectivity.AndroidConnectivityObserver
import solutions.laxmi.omnicompiler.core.data.connectivity.ConnectivityObserver
import solutions.laxmi.omnicompiler.core.data.execution.DefaultExecutionRepository
import solutions.laxmi.omnicompiler.core.data.files.ContentResolverTextReader
import solutions.laxmi.omnicompiler.core.data.history.DefaultHistoryRepository
import solutions.laxmi.omnicompiler.core.data.history.HistoryRepository
import solutions.laxmi.omnicompiler.core.data.files.TextDocumentReader
import solutions.laxmi.omnicompiler.core.data.execution.ExecutionRepository
import solutions.laxmi.omnicompiler.core.data.practice.CatalogPracticeRepository
import solutions.laxmi.omnicompiler.core.data.practice.PracticeRepository
import solutions.laxmi.omnicompiler.core.data.project.CacheProjectExporter
import solutions.laxmi.omnicompiler.core.data.project.LocalProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectExporter
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.DefaultRuntimeRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.DefaultSettingsRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds fun bindsAuthRepository(impl: DefaultAuthRepository): AuthRepository
    @Binds fun bindsGuestPromptRepository(impl: DefaultGuestPromptRepository): GuestPromptRepository
    @Binds fun bindsGoogleIdTokenProvider(impl: CredentialManagerGoogleIdTokenProvider): GoogleIdTokenProvider
    @Binds fun bindsRuntimeRepository(impl: DefaultRuntimeRepository): RuntimeRepository
    @Binds fun bindsProjectRepository(impl: LocalProjectRepository): ProjectRepository
    @Binds fun bindsProjectFolderRepository(impl: DefaultProjectFolderRepository): ProjectFolderRepository
    @Binds fun bindsGitRepository(impl: DefaultGitRepository): GitRepository
    @Binds fun bindsSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository
    @Binds fun bindsExecutionRepository(impl: DefaultExecutionRepository): ExecutionRepository
    @Binds fun bindsConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver
    @Binds fun bindsTextDocumentReader(impl: ContentResolverTextReader): TextDocumentReader
    @Binds fun bindsPracticeRepository(impl: CatalogPracticeRepository): PracticeRepository
    @Binds fun bindsProjectExporter(impl: CacheProjectExporter): ProjectExporter
    @Binds fun bindsHistoryRepository(impl: DefaultHistoryRepository): HistoryRepository
    @Binds fun bindsAccountRepository(impl: DefaultAccountRepository): AccountRepository
    @Binds fun bindsDeveloperRepository(impl: DefaultDeveloperRepository): DeveloperRepository
    @Binds fun bindsAppConfig(impl: BuildAppConfig): AppConfig
}
