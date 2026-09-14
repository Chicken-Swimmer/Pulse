package app.pulse.monitor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WebSiteEntry::class, CheckHistory::class],
    version = 4,
    exportSchema = false
)
abstract class DbHelper: RoomDatabase() {

    abstract fun webSiteEntryDao(): WebSiteEntryDao
    abstract fun checkHistoryDao(): CheckHistoryDao

    companion object{
        private var INSTANCE: DbHelper? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE web_site_entry ADD COLUMN item_position INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE web_site_entry ADD COLUMN consecutive_failures INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE web_site_entry ADD COLUMN is_alerting_down INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE web_site_entry ADD COLUMN down_since INTEGER")
                db.execSQL("ALTER TABLE web_site_entry ADD COLUMN last_latency_ms INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `check_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`website_id` INTEGER NOT NULL, " +
                        "`checked_at` INTEGER NOT NULL, " +
                        "`status_code` INTEGER, " +
                        "`result` TEXT NOT NULL, " +
                        "`latency_ms` INTEGER, " +
                        "`message` TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_check_history_website_id` ON `check_history` (`website_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_check_history_checked_at` ON `check_history` (`checked_at`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE web_site_entry ADD COLUMN last_checked_at INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): DbHelper? {
            if (INSTANCE == null) {
               synchronized(DbHelper::class) {
                   INSTANCE = Room.databaseBuilder(context,
                       DbHelper::class.java,
                       "web_site_monitor_db")
                       .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                       .build()
               }
            }
            return INSTANCE
        }
    }

}
