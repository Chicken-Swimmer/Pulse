package app.pulse.monitor.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WebSiteEntryDao {

    @Insert
    suspend fun saveWebSiteEntry(webSiteEntry: WebSiteEntry)

    @Insert
    suspend fun saveWebSiteEntryList(webSiteEntries: List<WebSiteEntry>)

    @Delete
    suspend fun deleteWebSiteEntry(webSiteEntry: WebSiteEntry)

    @Update
    suspend fun updateWebSiteEntry(webSiteEntry: WebSiteEntry)

    @Query("DELETE FROM web_site_entry")
    suspend fun deleteAll()

    @Query("SELECT * FROM web_site_entry ORDER BY id ASC")
    fun getAllWebSiteEntryList(): LiveData<List<WebSiteEntry>>

    @Query("SELECT * FROM web_site_entry ORDER BY id ASC")
    suspend fun getAllWebSiteEntryDirectList(): List<WebSiteEntry>

    @Query("SELECT * FROM web_site_entry WHERE is_paused = 0 ORDER BY id ASC")
    suspend fun getAllValidWebSiteEntryDirectList(): List<WebSiteEntry>

    @Query("SELECT * FROM web_site_entry WHERE id = :id LIMIT 1")
    fun getById(id: Long): LiveData<WebSiteEntry>
}
