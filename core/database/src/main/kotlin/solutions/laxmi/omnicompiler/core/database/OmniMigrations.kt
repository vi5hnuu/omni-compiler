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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
