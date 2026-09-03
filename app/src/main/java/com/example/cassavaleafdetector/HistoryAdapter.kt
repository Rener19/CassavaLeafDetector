package com.example.cassavaleafdetector

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

import android.widget.ImageButton

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit = {}
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgHistory: ImageView = view.findViewById(R.id.img_history)
        val txtLabel: TextView = view.findViewById(R.id.txt_history_label)
        val txtDate: TextView = view.findViewById(R.id.txt_history_date)
        val txtConfidence: TextView = view.findViewById(R.id.txt_history_confidence)
        val btnDelete: ImageButton? = view.findViewById(R.id.btn_delete_item)
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

        // Load image bitmap safely from local file storage path without blocking UI
        val imgPath = item.imagePath
        holder.imgHistory.tag = imgPath // tag to prevent async mismatch
        holder.imgHistory.setImageResource(android.R.drawable.ic_menu_report_image) // default placeholder

        if (imgPath.isNotEmpty()) {
            val imgFile = File(imgPath)
            if (imgFile.exists()) {
                executor.execute {
                    try {
                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeFile(imgFile.absolutePath, options)
                        
                        // Calculate sample size to downscale (aiming for ~100x100 thumbnail)
                        var inSampleSize = 1
                        val reqWidth = 100
                        val reqHeight = 100
                        val height = options.outHeight
                        val width = options.outWidth
                        if (height > reqHeight || width > reqWidth) {
                            val halfHeight = height / 2
                            val halfWidth = width / 2
                            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                                inSampleSize *= 2
                            }
                        }
                        
                        options.inSampleSize = inSampleSize
                        options.inJustDecodeBounds = false
                        val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath, options)
                        
                        mainHandler.post {
                            if (holder.imgHistory.tag == imgPath && bitmap != null) {
                                holder.imgHistory.setImageBitmap(bitmap)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
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
