package app.pulse.monitor.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pulse.monitor.R
import app.pulse.monitor.data.db.CheckHistory
import app.pulse.monitor.data.repository.WebSiteEntryRepository
import app.pulse.monitor.databinding.ActivityHistoryBinding
import app.pulse.monitor.utils.Constants
import java.text.DateFormat
import java.util.Date

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getLongExtra(Constants.EXTRA_WEBSITE_ID, -1L)
        val name = intent.getStringExtra(Constants.EXTRA_WEBSITE_NAME) ?: getString(R.string.history)
        supportActionBar?.title = name

        val adapter = HistoryAdapter()
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        if (id < 0) {
            binding.txtEmpty.visibility = View.VISIBLE
            return
        }

        WebSiteEntryRepository(applicationContext).getHistory(id)?.observe(this) { rows ->
            adapter.submit(rows)
            binding.txtEmpty.visibility = if (rows.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
    }
}

private class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {
    private var items: List<CheckHistory> = emptyList()
    private val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

    fun submit(list: List<CheckHistory>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return Holder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val result = when (item.result) {
            CheckHistory.UP -> ctx.getString(R.string.history_result_up)
            CheckHistory.DOWN -> ctx.getString(R.string.history_result_down)
            else -> ctx.getString(R.string.history_result_skipped)
        }
        val latency = item.latencyMs?.let { " · ${it} ms" } ?: ""
        val code = item.statusCode?.toString() ?: "—"
        holder.title.text = "$result  ($code)$latency"
        holder.subtitle.text = fmt.format(Date(item.checkedAt))
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
    }
}
