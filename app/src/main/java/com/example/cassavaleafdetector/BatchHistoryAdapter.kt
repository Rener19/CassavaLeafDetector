package com.example.cassavaleafdetector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

data class Metrics(
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val tnr: Double,
    val macroF1: Double,
    val mcc: Double
)

data class BatchHistoryItem(
    val id: String,
    val date: String,
    val totalImages: Int,
    val baseMetrics: Metrics,
    val enhancedMetrics: Metrics
)

class BatchHistoryAdapter(
    private var historyList: List<BatchHistoryItem>
) : RecyclerView.Adapter<BatchHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDate: TextView = view.findViewById(android.R.id.text1)
        val txtDetails: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        holder.txtDate.text = "Batch Test: ${item.date} (${item.totalImages} images)"
        
        val details = String.format(
            Locale.US,
            "Base Acc: %.1f%% | Enhanced Acc: %.1f%%",
            item.baseMetrics.accuracy * 100,
            item.enhancedMetrics.accuracy * 100
        )
        holder.txtDetails.text = details
    }

    override fun getItemCount() = historyList.size

    fun updateData(newList: List<BatchHistoryItem>) {
        historyList = newList
        notifyDataSetChanged()
    }
}
