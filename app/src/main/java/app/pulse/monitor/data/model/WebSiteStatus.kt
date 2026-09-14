package app.pulse.monitor.data.model

data class WebSiteStatus (
    val name: String,
    val url: String,
    val status: Int,
    val isSuccessful: Boolean,
    val message: String,
    val skippedOffline: Boolean = false,
    val latencyMs: Long? = null,
    val notifyDown: Boolean = false,
    val notifyUp: Boolean = false,
    val downSince: Long? = null,
    val transportError: Boolean = false,
    val id: Long? = null
)