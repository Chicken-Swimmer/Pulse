package app.pulse.monitor.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CheckHistoryDao {

    @Insert
    suspend fun insert(entry: CheckHistory)

    @Insert
    suspend fun insertAll(entries: List<CheckHistory>)

    @Query("SELECT * FROM check_history ORDER BY checked_at ASC")
    suspend fun getAll(): List<CheckHistory>

    @Query("SELECT * FROM check_history WHERE website_id = :websiteId ORDER BY checked_at DESC LIMIT :limit")
    fun getHistory(websiteId: Long, limit: Int = 200): LiveData<List<CheckHistory>>

    @Query("SELECT * FROM check_history WHERE website_id = :websiteId ORDER BY checked_at DESC LIMIT :limit")
    suspend fun getHistoryDirect(websiteId: Long, limit: Int = 200): List<CheckHistory>

    @Query("DELETE FROM check_history WHERE checked_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM check_history WHERE website_id = :websiteId")
    suspend fun deleteForWebsite(websiteId: Long)

    @Query("DELETE FROM check_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM check_history WHERE result = 'UP'")
    suspend fun countUp(): Int

    @Query("SELECT COUNT(*) FROM check_history WHERE result = 'DOWN'")
    suspend fun countDown(): Int

    @Query("SELECT * FROM check_history WHERE website_id = :websiteId AND checked_at >= :since ORDER BY checked_at ASC")
    suspend fun getSince(websiteId: Long, since: Long): List<CheckHistory>
}

