package app.pulse.monitor.data.model

data class CustomMonitorData (
    var runningDelay: Long = 0,
    var runningDelayValue: String = "",
    var showNotification: Boolean = true
)