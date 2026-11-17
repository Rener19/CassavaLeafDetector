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
 * @property precision Macro-averaged positive predictive value: Mean of TP / (TP + FP) across active classes.
 * @property recall Macro-averaged sensitivity / true positive rate: Mean of TP / (TP + FN) across active classes.
 * @property tpr True Positive Rate (Sensitivity / Recall): Mean of TP / (TP + FN) across active classes.
 * @property tnr True Negative Rate (Macro-averaged Specificity): Mean of TN / (TN + FP) across active classes.
 * @property f1Score Micro-averaged F1 score (mathematically equivalent to overall accuracy in single-label multi-class problems).
 * @property macroF1 Macro-averaged F1 score: Mean of per-class harmonic means 2*(P*R)/(P+R).
 * @property mcc Matthews Correlation Coefficient: Balanced quality metric resilient to severe class imbalance.
 * @property confusionMatrix 5x5 confusion matrix (Rows = Actual Ground Truth, Columns = Predicted Class).
 * @property coverageCount Number of batch test images that satisfied the confidence threshold and were evaluated.
 * @property coveragePercent Percentage of uploaded images evaluated by the model.
 */
data class Metrics(
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val tpr: Double = recall,
    val tnr: Double,
    val f1Score: Double,
    val macroF1: Double,
    val mcc: Double,
    val confusionMatrix: List<List<Int>> = emptyList(),
    val coverageCount: Int = 0,
    val coveragePercent: Double = 0.0
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
 * @property activeClassesInfo Information about active ground truth classes in the run.
 * @property evaluationMode Testing protocol used: "Mode 1 (Paired)" or "Mode 2 (Independent)".
 */
data class BatchHistoryItem(
    val id: String,
    val date: String,
    val totalImages: Int,
    val baseMetrics: Metrics,
    val enhancedMetrics: Metrics,
    val activeClassesInfo: String = "",
    val evaluationMode: String = "Mode 1 (Paired)"
)

/**
 * ============================================================================
 * BatchHistoryAdapter
 * ============================================================================
 * RecyclerView Adapter for displaying past batch evaluation test results in the Batch Test tab.
 */
class BatchHistoryAdapter(
    private var historyList: List<BatchHistoryItem>,
    private val onItemClick: (BatchHistoryItem) -> Unit = {},
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
        val activeSummary = if (item.activeClassesInfo.isNotEmpty()) " [${item.activeClassesInfo.split(" (").first()}]" else ""
        val modeSummary = if (item.evaluationMode.contains("Independent", ignoreCase = true)) " [Indep]" else ""
        holder.txtTitle.text = "Batch Test: ${item.date} (${item.totalImages} images)$activeSummary$modeSummary"

        val details = if (item.evaluationMode.contains("Independent", ignoreCase = true)) {
            String.format(
                Locale.US,
                "Base: %.1f%% (Cov: %.1f%%) | Enh: %.1f%% (Cov: %.1f%%)",
                item.baseMetrics.accuracy * 100,
                item.baseMetrics.coveragePercent,
                item.enhancedMetrics.accuracy * 100,
                item.enhancedMetrics.coveragePercent
            )
        } else {
            String.format(
                Locale.US,
                "Base Acc: %.1f%% | Enhanced Acc: %.1f%%",
                item.baseMetrics.accuracy * 100,
                item.enhancedMetrics.accuracy * 100
            )
        }
        holder.txtDetails.text = details

        // Item click shows historical metrics details
        val openDialog = View.OnClickListener { onItemClick(item) }
        holder.itemView.setOnClickListener(openDialog)
        holder.txtTitle.setOnClickListener(openDialog)
        holder.txtDetails.setOnClickListener(openDialog)

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
