package solutions.laxmi.omnicompiler.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every migration must upgrade real data from the exported schema of the previous version. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OmniDatabase::class.java)

    @Test
    fun migrate1To2_keepsRunsAndAddsTestNames() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO projects VALUES ('p1', 'two-sum', 'cpp-23', 5000, 256, 'AC', 0, 0)")
            db.execSQL(
                "INSERT INTO runs (id, project_id, job_id, runtime_id, mode, status, verdict, total_time_ms, test_count, " +
                    "compile_output, diag_line, diag_column, diag_message, error_message, from_cache, started_at) " +
                    "VALUES ('r1', 'p1', 'j1', 'cpp-23', 'TESTS', 'DONE', 'AC', 12, 3, NULL, NULL, NULL, NULL, NULL, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true, OmniMigrations.MIGRATION_1_2).use { db ->
            db.query("SELECT verdict, test_names FROM runs WHERE id = 'r1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("AC")
                assertThat(cursor.getString(1)).isEmpty()
            }
        }
    }

    @Test
    fun migrate2To3_keepsRunsAndAddsTestIds() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO projects VALUES ('p1', 'two-sum', 'cpp-23', 5000, 256, 'AC', 0, 0)")
            db.execSQL(
                "INSERT INTO runs (id, project_id, job_id, runtime_id, mode, status, verdict, total_time_ms, test_count, test_names, " +
                    "compile_output, diag_line, diag_column, diag_message, error_message, from_cache, started_at) " +
                    "VALUES ('r1', 'p1', 'j1', 'cpp-23', 'TESTS', 'DONE', 'WA', 12, 2, 'a\nb', NULL, NULL, NULL, NULL, NULL, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true, OmniMigrations.MIGRATION_2_3).use { db ->
            db.query("SELECT test_names, test_ids FROM runs WHERE id = 'r1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("a\nb")
                assertThat(cursor.getString(1)).isEmpty()
            }
        }
    }

    @Test
    fun migrate3To4_keepsFilesAndStartsVersionsAtZero() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO projects VALUES ('p1', 'two-sum', 'cpp-23', 5000, 256, NULL, 0, 0)")
            db.execSQL("INSERT INTO files (id, project_id, name, content, is_entry, position) VALUES ('f1', 'p1', 'main.cpp', 'int main(){}', 1, 0)")
        }

        helper.runMigrationsAndValidate(DB, 4, true, OmniMigrations.MIGRATION_3_4).use { db ->
            db.query("SELECT content, content_version FROM files WHERE id = 'f1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("int main(){}")
                assertThat(cursor.getInt(1)).isEqualTo(0)
            }
        }
    }

    @Test
    fun migrate1ToLatest_runsEveryStep() {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, 4, true, *OmniMigrations.ALL).close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
