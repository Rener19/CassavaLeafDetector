package com.example.cassavaleafdetector

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgHistory: ImageView = view.findViewById(R.id.img_history)
        val txtLabel: TextView = view.findViewById(R.id.txt_history_label)
        val txtDate: TextView = view.findViewById(R.id.txt_history_date)
        val txtConfidence: TextView = view.findViewById(R.id.txt_history_confidence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.txtLabel.text = item.label
        holder.txtDate.text = item.date
        holder.txtConfidence.text = String.format("%.0f%%", item.confidence * 100)
        
        // Change badge background color based on class type
        val context = holder.itemView.context
        val bgDrawable = when (item.index) {
            0 -> R.color.color_cbb
            1 -> R.color.color_cbsd
            2 -> R.color.color_cgm
            3 -> R.color.color_cmd
            else -> R.color.color_healthy
        }
        holder.txtConfidence.setBackgroundColor(context.getColor(bgDrawable))

        // Load image bitmap safely from local file storage path
        if (item.imagePath.isNotEmpty()) {
            val imgFile = File(item.imagePath)
            if (imgFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bitmap != null) {
                        holder.imgHistory.setImageBitmap(bitmap)
                    } else {
                        holder.imgHistory.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                } catch (e: Exception) {
                    holder.imgHistory.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            } else {
                holder.imgHistory.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } else {
            holder.imgHistory.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

data class HistoryItem(
    val id: String,
    val label: String,
    val confidence: Float,
    val date: String,
    val imagePath: String,
    val index: Int
)
