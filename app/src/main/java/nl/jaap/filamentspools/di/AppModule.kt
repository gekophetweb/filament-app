package nl.jaap.filamentspools.di

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.jaap.filamentspools.data.AppScope
import nl.jaap.filamentspools.data.Pairer
import nl.jaap.filamentspools.data.PairingRepository
import nl.jaap.filamentspools.data.SpoolActions
import nl.jaap.filamentspools.data.SpoolRepository
import nl.jaap.filamentspools.data.SyncRepository
import nl.jaap.filamentspools.data.Syncer
import nl.jaap.filamentspools.data.db.AppDatabase
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.session.DataStoreSessionStore
import nl.jaap.filamentspools.data.session.KeystoreTokenCipher
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.data.session.TokenCipher
import java.time.Clock
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "filament-spools.db").build()

    @Provides fun spoolDao(db: AppDatabase): SpoolDao = db.spoolDao()

    @Provides fun productDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides @Singleton
    fun sessionStore(
        @ApplicationContext context: Context,
        cipher: TokenCipher,
        @AppScope scope: CoroutineScope,
    ): DataStoreSessionStore = DataStoreSessionStore(
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("session") },
        cipher,
        scope,
    )

    @Provides fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Named("deviceName")
    fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
        .ifBlank { "Android phone" }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun sessionStore(impl: DataStoreSessionStore): SessionStore
    @Binds abstract fun tokenCipher(impl: KeystoreTokenCipher): TokenCipher
    @Binds abstract fun pairer(impl: PairingRepository): Pairer
    @Binds abstract fun syncer(impl: SyncRepository): Syncer
    @Binds abstract fun spoolActions(impl: SpoolRepository): SpoolActions
}
