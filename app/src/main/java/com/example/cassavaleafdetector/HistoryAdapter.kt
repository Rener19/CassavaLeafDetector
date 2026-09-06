package com.example.cassavaleafdetector

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/**
 * ============================================================================
 * HistoryItem
 * ============================================================================
 * Immutable data model representing a single recorded leaf diagnosis.
 *
 * @property id Unique identifier (UUID) for record indexing and deletion.
 * @property label Formatted title of the diagnosed condition.
 * @property confidence Prediction probability score [0.0 - 1.0].
 * @property date Human-readable timestamp string (e.g. "2026-09-06 14:30").
 * @property imagePath Local filesystem path to the cached leaf photo thumbnail.
 * @property index Taxonomy category index (0: CBB, 1: CBSD, 2: CGM, 3: CMD, 4: Healthy).
 */
data class HistoryItem(
    val id: String,
    val label: String,
    val confidence: Float,
    val date: String,
    val imagePath: String,
    val index: Int
)

/**
 * ============================================================================
 * HistoryAdapter
 * ============================================================================
 * Custom RecyclerView Adapter for rendering the persistent diagnosis history list.
 *
 * Key Architectural Highlights:
 * 1. Out-of-Memory (OOM) Protection:
 *    - Full-resolution camera photos (3-12 megapixels) consume 12-48 MB of RAM per uncompressed bitmap.
 *    - Decoding multiple full-size photos inside a scrolling list would quickly trigger an Android OOM error.
 *    - This adapter uses two-pass decoding via [BitmapFactory.Options.inJustDecodeBounds] to calculate
 *      [BitmapFactory.Options.inSampleSize], downsampling photos to ~100x100 thumbnail dimensions before memory allocation.
 * 2. Background Thread Decoding:
 *    - Uses a fixed thread pool ([Executors.newFixedThreadPool]) to decode image files off the main UI thread,
 *      preventing frame drops and ensuring 60fps smooth scrolling.
 * 3. Asynchronous Recycling Safety:
 *    - Recycled ViewHolders tag their ImageView with the target file path.
 *    - Upon decode completion, the main thread verifies that the tag still matches before applying the bitmap,
 *      preventing mismatched or flickering images during fast scrolls.
 */
class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit = {}
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // Background thread pool dedicated to image decoding tasks
    private val decodeExecutor = Executors.newFixedThreadPool(4)

    // Handler attached to the main UI looper for UI updates
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * ViewHolder caching layout view references to avoid expensive findViewById calls during scroll.
     */
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

        // 1. Bind textual diagnosis information
        holder.txtLabel.text = item.label
        holder.txtDate.text = item.date
        holder.txtConfidence.text = String.format("%.0f%%", item.confidence * 100)

        // 2. Set class-specific color theme for the confidence badge
        val context = holder.itemView.context
        val bgDrawable = when (item.index) {
            0 -> R.color.color_cbb
            1 -> R.color.color_cbsd
            2 -> R.color.color_cgm
            3 -> R.color.color_cmd
            else -> R.color.color_healthy
        }
        holder.txtConfidence.setBackgroundColor(context.getColor(bgDrawable))

        // 3. Safe Asynchronous Image Loading with Downsampling
        val imgPath = item.imagePath
        holder.imgHistory.tag = imgPath // Tag prevents recycled view flicker
        holder.imgHistory.setImageResource(android.R.drawable.ic_menu_report_image) // Fallback placeholder

        if (imgPath.isNotEmpty()) {
            val imgFile = File(imgPath)
            if (imgFile.exists()) {
                decodeExecutor.execute {
                    try {
                        // Pass 1: Decode image dimensions without loading pixel data into RAM
                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeFile(imgFile.absolutePath, options)

                        // Calculate optimal power-of-two downsample factor
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

                        // Pass 2: Decode downscaled bitmap into memory
                        options.inSampleSize = inSampleSize
                        options.inJustDecodeBounds = false
                        val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath, options)

                        // Post result back to main UI thread
                        mainHandler.post {
                            // Verify view has not been recycled for a different item during background decode
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

        // 4. Attach click listeners for item re-examination and individual deletion
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    /**
     * Updates the adapter's backing dataset and refreshes the RecyclerView.
     */
    fun updateData(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
