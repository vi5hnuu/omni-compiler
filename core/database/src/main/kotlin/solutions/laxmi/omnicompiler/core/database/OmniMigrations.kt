package solutions.laxmi.omnicompiler.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Every schema step, in order. Each must match the exported schema JSON of its target version. */
object OmniMigrations {

    /** v2: runs remember their test labels so the console can name tests after edits/deletes. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE runs ADD COLUMN test_names TEXT NOT NULL DEFAULT ''")
        }
    }

    /** v3: runs remember which tests they ran, so a partial run's results land on the right test rows. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE runs ADD COLUMN test_ids TEXT NOT NULL DEFAULT ''")
        }
    }

    /** v4: files carry a content version so the editor reloads only on replacements it didn't make. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE files ADD COLUMN content_version INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v5: projects live in folders on device storage; the index remembers where and what it last saw there. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE projects ADD COLUMN folder_doc_id TEXT")
            connection.execSQL("ALTER TABLE projects ADD COLUMN issues TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE projects ADD COLUMN origin_uri TEXT")
            connection.execSQL("ALTER TABLE projects ADD COLUMN remote_json TEXT")
            connection.execSQL("ALTER TABLE files ADD COLUMN doc_id TEXT")
            connection.execSQL("ALTER TABLE files ADD COLUMN last_modified INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE files ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v6: projects remember their manifest's last-modified time, so rescans skip manifests that didn't change; files
     * flag editor saves not yet written to the project folder.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE projects ADD COLUMN manifest_modified INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE files ADD COLUMN disk_dirty INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
