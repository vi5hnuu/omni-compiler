package solutions.laxmi.omnicompiler.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {

    /** File names referenced by the app's backup exclusion rules — keep them in sync. */
    private const val SESSION_FILE = "datastore/session.enc"
    private const val KEYSET_PREFS = "omni_keyset_prefs"
    private const val KEYSET_NAME = "omni_session_keyset"
    private const val MASTER_KEY_URI = "android-keystore://omni_session_master_key"

    @Provides
    @Singleton
    fun providesSessionDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(OmniDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<StoredSession?> {
        // Keystore access is slow; build the AEAD lazily on the IO thread the first time it is needed.
        val aead = lazy {
            AeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
        return DataStoreFactory.create(
            serializer = SessionSerializer(aead, Json { ignoreUnknownKeys = true }),
            corruptionHandler = ReplaceFileCorruptionHandler { null },
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { File(context.filesDir, SESSION_FILE) },
        )
    }

    @Provides
    @Singleton
    fun providesPreferencesDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(OmniDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(io + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("omni_preferences") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DataStoreBindings {
    @Binds
    fun bindsSessionStore(impl: EncryptedSessionStore): SessionStore

    @Binds
    fun bindsPreferencesStore(impl: DataStorePreferencesStore): PreferencesStore
}
