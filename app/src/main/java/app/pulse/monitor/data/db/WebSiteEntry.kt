package app.pulse.monitor.data.db

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "web_site_entry")
@Parcelize
data class WebSiteEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "status")
    var status: Int? = null,
    @ColumnInfo(name = "is_paused")
    var isPaused: Boolean = false,
    @ColumnInfo(name = "updated_at")
    var updatedAt: String? = null,
    @ColumnInfo(name = "item_position")
    var itemPosition: Int? = null,
    @ColumnInfo(name = "consecutive_failures", defaultValue = "0")
    var consecutiveFailures: Int = 0,
    @ColumnInfo(name = "is_alerting_down", defaultValue = "0")
    var isAlertingDown: Boolean = false,
    @ColumnInfo(name = "down_since")
    var downSince: Long? = null,
    @ColumnInfo(name = "last_latency_ms")
    var lastLatencyMs: Long? = null,
    @ColumnInfo(name = "last_checked_at", defaultValue = "0")
    var lastCheckedAt: Long = 0L
) : Parcelable