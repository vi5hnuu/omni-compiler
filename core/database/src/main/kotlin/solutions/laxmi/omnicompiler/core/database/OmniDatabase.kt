package solutions.laxmi.omnicompiler.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import solutions.laxmi.omnicompiler.core.database.dao.FileDao
import solutions.laxmi.omnicompiler.core.database.dao.PendingRunDao
import solutions.laxmi.omnicompiler.core.database.dao.ProjectDao
import solutions.laxmi.omnicompiler.core.database.dao.RunDao
import solutions.laxmi.omnicompiler.core.database.dao.RuntimeDao
import solutions.laxmi.omnicompiler.core.database.dao.SubmissionDao
import solutions.laxmi.omnicompiler.core.database.dao.TestCaseDao
import solutions.laxmi.omnicompiler.core.database.entity.FileEntity
import solutions.laxmi.omnicompiler.core.database.entity.PendingRunEntity
import solutions.laxmi.omnicompiler.core.database.entity.ProjectEntity
import solutions.laxmi.omnicompiler.core.database.entity.RunEntity
import solutions.laxmi.omnicompiler.core.database.entity.RunResultEntity
import solutions.laxmi.omnicompiler.core.database.entity.RuntimeEntity
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionCursorEntity
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionEntity
import solutions.laxmi.omnicompiler.core.database.entity.TestCaseEntity
import javax.inject.Singleton

/**
 * Local store. Schema changes must bump [version] and ship a Migration (schemas are exported to
 * `core/database/schemas` and committed) — user code lives only here, so data loss is not an option.
 */
@Database(
    entities = [
        ProjectEntity::class,
        FileEntity::class,
        TestCaseEntity::class,
        RunEntity::class,
        RunResultEntity::class,
        RuntimeEntity::class,
        SubmissionEntity::class,
        SubmissionCursorEntity::class,
        PendingRunEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun fileDao(): FileDao
    abstract fun testCaseDao(): TestCaseDao
    abstract fun runDao(): RunDao
    abstract fun runtimeDao(): RuntimeDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun pendingRunDao(): PendingRunDao
}

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): OmniDatabase =
        Room.databaseBuilder(context, OmniDatabase::class.java, "omni.db")
            .addMigrations(*OmniMigrations.ALL)
            .build()

    @Provides fun projectDao(db: OmniDatabase) = db.projectDao()
    @Provides fun fileDao(db: OmniDatabase) = db.fileDao()
    @Provides fun testCaseDao(db: OmniDatabase) = db.testCaseDao()
    @Provides fun runDao(db: OmniDatabase) = db.runDao()
    @Provides fun runtimeDao(db: OmniDatabase) = db.runtimeDao()
    @Provides fun submissionDao(db: OmniDatabase) = db.submissionDao()
    @Provides fun pendingRunDao(db: OmniDatabase) = db.pendingRunDao()
}
