package com.example.cassavaleafdetector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * ============================================================================
 * Metrics
 * ============================================================================
 * Quantitative performance evaluation metrics for a classifier on a batch dataset.
 *
 * @property accuracy Proportion of correctly classified leaves: (TP + TN) / (Total).
 * @property precision Macro-averaged positive predictive value: Mean of TP / (TP + FP) across all classes.
 * @property recall Macro-averaged sensitivity / true positive rate: Mean of TP / (TP + FN) across all classes.
 * @property f1Score Micro-averaged F1 score (mathematically equivalent to overall accuracy in single-label multi-class problems).
 * @property tnr True Negative Rate (Macro-averaged Specificity): Mean of TN / (TN + FP) across all classes.
 * @property macroF1 Macro-averaged F1 score: Mean of per-class harmonic means 2*(P*R)/(P+R).
 * @property mcc Matthews Correlation Coefficient: Balanced quality metric resilient to severe class imbalance.
 */
data class Metrics(
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val tnr: Double,
    val macroF1: Double,
    val mcc: Double
)

/**
 * ============================================================================
 * BatchHistoryItem
 * ============================================================================
 * Record representing an executed batch evaluation test run comparing Base and Enhanced models.
 *
 * @property id Unique UUID identifier for item tracking and deletion.
 * @property date Formatted execution timestamp string (e.g. "2026-09-06 15:45").
 * @property totalImages Count of total test images processed across all 5 classes.
 * @property baseMetrics Evaluation metrics calculated for the standard MobileNetV3 Base Model.
 * @property enhancedMetrics Evaluation metrics calculated for the D-CLAHE Enhanced Model.
 */
data class BatchHistoryItem(
    val id: String,
    val date: String,
    val totalImages: Int,
    val baseMetrics: Metrics,
    val enhancedMetrics: Metrics
)

/**
 * ============================================================================
 * BatchHistoryAdapter
 * ============================================================================
 * RecyclerView Adapter for displaying past batch evaluation test results in the Batch Test tab.
 */
class BatchHistoryAdapter(
    private var historyList: List<BatchHistoryItem>,
    private val onDeleteClick: (BatchHistoryItem) -> Unit = {}
) : RecyclerView.Adapter<BatchHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txt_batch_title)
        val txtDetails: TextView = view.findViewById(R.id.txt_batch_details)
        val btnDelete: ImageButton? = view.findViewById(R.id.btn_delete_batch_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_batch_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        holder.txtTitle.text = "Batch Test: ${item.date} (${item.totalImages} images)"

        val details = String.format(
            Locale.US,
            "Base Acc: %.1f%% | Enhanced Acc: %.1f%%",
            item.baseMetrics.accuracy * 100,
            item.enhancedMetrics.accuracy * 100
        )
        holder.txtDetails.text = details

        // Handle individual item deletion
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = historyList.size

    /**
     * Replaces current dataset with updated list and refreshes views.
     */
    fun updateData(newList: List<BatchHistoryItem>) {
        historyList = newList
        notifyDataSetChanged()
    }
}
