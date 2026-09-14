package app.pulse.monitor.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.PopupMenu
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.view.View
import android.view.animation.AnimationUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import app.pulse.monitor.R
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.databinding.ItemWebsiteRowBinding
import app.pulse.monitor.utils.Print
import app.pulse.monitor.utils.Utils
import app.pulse.monitor.utils.Utils.currentDateTime
import app.pulse.monitor.utils.Utils.removeUrlProto
import java.util.*

/**
 * @author Naveen T P
 * @since 08/11/18
 */
class WebSiteEntryAdapter(todoEvents: WebSiteEntryEvents) : RecyclerView.Adapter<WebSiteEntryAdapter.ViewHolder>(), Filterable {

    private var mList: List<WebSiteEntry> = arrayListOf()
    private var filteredList: List<WebSiteEntry> = arrayListOf()
    private val listener: WebSiteEntryEvents = todoEvents
    private lateinit var itemWebsiteRowBinding: ItemWebsiteRowBinding

    // Track which items are currently showing a refresh loader
    private val refreshingIds = mutableSetOf<Long?>()

    // Callback for when filter results change
    var onFilterResultsChanged: ((isEmpty: Boolean, query: String?) -> Unit)? = null
    private var currentSearchQuery: String? = null

    enum class StatusFilter { ALL, DOWN, PAUSED }

    var statusFilter: StatusFilter = StatusFilter.ALL
        set(value) {
            field = value
            applyFilters()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        itemWebsiteRowBinding = ItemWebsiteRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemWebsiteRowBinding)
    }

    override fun getItemCount(): Int = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with (holder) {
            with (filteredList[position]) {
                holder.itemView.tag = this
                holder.bind(this, listener, position)
            }
        }
    }

    inner class ViewHolder(val binding: ItemWebsiteRowBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(webSiteEntry: WebSiteEntry, listener: WebSiteEntryEvents, position: Int) {

            binding.root.apply {

                binding.txtWebSite.text = webSiteEntry.name
                binding.txtUrl.text = webSiteEntry.url
                val last = context.getString(
                    R.string.last_checked_fmt,
                    Utils.formatRelativeTime(context, webSiteEntry.lastCheckedAt)
                )
                val isDown = !webSiteEntry.isPaused && webSiteEntry.status != null && webSiteEntry.status !in 200..299
                val latency = webSiteEntry.lastLatencyMs?.takeIf { it > 0L }?.let {
                    " · " + context.getString(R.string.latency_ms, it.toInt())
                } ?: ""
                binding.txtStatus.text = if (isDown && webSiteEntry.downSince != null) {
                    last + " · " + context.getString(
                        R.string.down_for_short,
                        Utils.formatDuration(context, webSiteEntry.downSince!!)
                    ) + latency
                } else last + latency

                val status = webSiteEntry.status
                val tintColorRes = when {
                    webSiteEntry.isPaused -> R.color.status_unknown
                    status == null -> R.color.status_unknown
                    status in 200..299 -> R.color.chart_up
                    else -> R.color.chart_down
                }
                binding.statusDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, tintColorRes)
                )

                this.setOnClickListener { listener.onItemClicked(webSiteEntry) }
                binding.btnRowRefresh.setOnClickListener {
                    listener.onRefreshClicked(webSiteEntry)
                }
            }
        }
    }

    /**
     * Favicon loader with fallbacks (no user setting):
     * Order of best sources: 1) DuckDuckGo, 2) Google S2, 3) Site /favicon.ico
     * If all fail, use the default app icon.
     */
    private fun loadWebsiteIcon(url: String, imageView: android.widget.ImageView) {
        val host = extractHost(url)
        val ctx = imageView.context

        fun request(u: String) = Glide.with(ctx)
            .load(u)
            .apply(
                RequestOptions()
                    .circleCrop()
                    .timeout(4000)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
            )

        val ddg = "https://icons.duckduckgo.com/ip3/$host.ico"
        val google = "https://www.google.com/s2/favicons?domain=$host&sz=64"
        val site = "https://$host/favicon.ico"

        try {
            val siteReq = request(site)
                .error(R.drawable.ic_icon)
                .fallback(R.drawable.ic_icon)

            val googleReq = request(google)
                .error(siteReq)
                .fallback(R.drawable.ic_icon)

            request(ddg)
                .placeholder(R.drawable.ic_icon)
                .error(googleReq)
                .fallback(R.drawable.ic_icon)
                .into(imageView)
        } catch (e: Exception) {
            Print.log("Exception loading website icon: ${e.message}")
            imageView.setImageResource(R.drawable.ic_icon)
        }
    }

    private fun extractHost(url: String): String {
        return try {
            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            java.net.URI(normalized).host ?: url.removeUrlProto().substringBefore("/")
        } catch (e: Exception) {
            url.removeUrlProto().substringBefore("/")
        }
    }

    /**
     * No external fallback: we just set the default icon.
     */
    private fun loadFallbackIcon(cleanUrl: String, imageView: android.widget.ImageView) {
        imageView.setImageResource(R.drawable.ic_icon)
    }

    /**
     * Search Filter implementation
     * */
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(p0: CharSequence?): FilterResults {
                val charString = p0.toString()
                currentSearchQuery = if (charString.isEmpty()) null else charString

                val filterResults = FilterResults()
                filterResults.values = matching(mList, charString)
                return filterResults
            }

            override fun publishResults(p0: CharSequence?, p1: FilterResults?) {
                filteredList = sortForHome((p1?.values as List<*>).filterIsInstance<WebSiteEntry>())
                notifyDataSetChanged()

                // Notify about filter results change
                onFilterResultsChanged?.invoke(filteredList.isEmpty(), currentSearchQuery)
            }

        }
    }

    /**
     * Activity uses this method to update todoList with the help of LiveData
     * */
    fun setAllTodoItems(todoItems: List<WebSiteEntry>) {
        this.mList = todoItems
        applyFilters()
    }

    private fun matching(source: List<WebSiteEntry>, query: String?): List<WebSiteEntry> {
        val q = query?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        var list = source
        if (q.isNotEmpty()) {
            list = list.filter {
                it.name.lowercase(Locale.getDefault()).contains(q) ||
                    it.url.lowercase(Locale.getDefault()).contains(q)
            }
        }
        return when (statusFilter) {
            StatusFilter.ALL -> list
            StatusFilter.DOWN -> list.filter {
                !it.isPaused && it.status != null && it.status !in 200..299
            }
            StatusFilter.PAUSED -> list.filter { it.isPaused }
        }
    }

    private fun applyFilters() {
        filteredList = sortForHome(matching(mList, currentSearchQuery))
        notifyDataSetChanged()
        onFilterResultsChanged?.invoke(filteredList.isEmpty(), currentSearchQuery)
    }

    private fun sortForHome(items: List<WebSiteEntry>): List<WebSiteEntry> {
        fun rank(e: WebSiteEntry): Int = when {
            e.isPaused -> 3
            e.status != null && e.status !in 200..299 -> 0
            e.status == null -> 1
            else -> 2
        }
        if (app.pulse.monitor.utils.SharedPrefsManager.customPrefs.getInt(
                app.pulse.monitor.utils.Constants.SORT_MODE,
                app.pulse.monitor.utils.Constants.SORT_PRIORITY
            ) == app.pulse.monitor.utils.Constants.SORT_MANUAL) {
            return items.sortedBy { it.itemPosition ?: Int.MAX_VALUE }
        }
        return items.sortedWith(
            compareBy<WebSiteEntry> { rank(it) }
                .thenByDescending { it.isAlertingDown }
                .thenByDescending { it.downSince ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * RecycleView touch event callbacks
     * */
    // Public API to show/hide per-item loader from Activity
    fun refreshTimestamps() {
        if (filteredList.isEmpty()) return
        notifyItemRangeChanged(0, filteredList.size)
    }

    fun setRefreshing(entry: WebSiteEntry, refreshing: Boolean) {
        val id = entry.id
        if (refreshing) refreshingIds.add(id) else refreshingIds.remove(id)
        val idx = filteredList.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx) else notifyDataSetChanged()
    }

    fun clearRefreshing() {
        if (refreshingIds.isNotEmpty()) {
            refreshingIds.clear()
            notifyDataSetChanged()
        }
    }

    fun setAllRefreshing(refreshing: Boolean) {
        if (!refreshing) {
            if (refreshingIds.isNotEmpty()) {
                refreshingIds.clear()
                notifyDataSetChanged()
            }
            return
        }
        // Deprecated behavior: prefer setActiveRefreshingUrl to show only current item
        refreshingIds.clear()
        notifyDataSetChanged()
    }

    fun setActiveRefreshingUrl(url: String?) {
        val prevIds = refreshingIds.toSet()
        refreshingIds.clear()
        if (url != null) {
            val id = mList.firstOrNull { it.url == url }?.id
            if (id != null) refreshingIds.add(id)
        }
        // Notify changes for previous and new item only
        val changedIds = prevIds union refreshingIds
        if (changedIds.isEmpty()) return
        changedIds.forEach { id ->
            val idx = filteredList.indexOfFirst { it.id == id }
            if (idx >= 0) notifyItemChanged(idx)
        }
    }

    interface WebSiteEntryEvents {
        fun onDeleteClicked(webSiteEntry: WebSiteEntry)
        fun onViewClicked(webSiteEntry: WebSiteEntry, adapterPosition: Int)
        fun onEditClicked(webSiteEntry: WebSiteEntry)
        fun onRefreshClicked(webSiteEntry: WebSiteEntry)
        fun onPauseClicked(webSiteEntry: WebSiteEntry, adapterPosition: Int)
        fun onHistoryClicked(webSiteEntry: WebSiteEntry)
        fun onItemClicked(webSiteEntry: WebSiteEntry)
    }
}
