package app.pulse.monitor.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_history",
    indices = [Index(value = ["website_id"]), Index(value = ["checked_at"])]
)
data class CheckHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "website_id")
    val websiteId: Long,
    @ColumnInfo(name = "checked_at")
    val checkedAt: Long,
    @ColumnInfo(name = "status_code")
    val statusCode: Int?,
    @ColumnInfo(name = "result")
    val result: String,
    @ColumnInfo(name = "latency_ms")
    val latencyMs: Long?,
    @ColumnInfo(name = "message")
    val message: String? = null
) {
    companion object {
        const val UP = "UP"
        const val DOWN = "DOWN"
        const val SKIPPED = "SKIPPED"
    }
}
