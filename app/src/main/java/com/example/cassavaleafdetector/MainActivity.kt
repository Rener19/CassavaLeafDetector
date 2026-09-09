package com.example.cassavaleafdetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ============================================================================
 * MainActivity
 * ============================================================================
 * Primary Activity and UI controller for the Cassava Leaf Disease Detector.
 *
 * Supported Modes / Flavors:
 * 1. Thesis Flavor:
 *    - Dual-tab interface ("Single Scan" and "Batch Test").
 *    - Side-by-side comparison of the Baseline MobileNetV3-Small vs the
 *      D-CLAHE Layerwise Progressive Fine-Tuned Enhanced model.
 *    - Batch evaluation module for computing multi-class classification metrics:
 *      Accuracy, Precision, Recall, Specificity (TNR), F1-Score, Macro F1, and MCC.
 *    - Detailed thesis performance dialog showcasing training, latency, MACs, and L2 norms.
 * 2. User Flavor:
 *    - Clean, farmer-oriented interface focused on clear disease diagnosis, descriptions,
 *      and agronomic treatment/prevention recommendations.
 *
 * Architectural Features:
 * - CameraX integration for high-performance live camera leaf capture.
 * - Non-blocking asynchronous inference executed on background worker threads.
 * - Lightweight local persistence of single and batch scan records using SharedPreferences + JSON.
 */
class MainActivity : AppCompatActivity() {

    // ML classifier instance managing TFLite interpreters and fallback heuristics
    private lateinit var classifier: CassavaClassifier

    // Single-thread executor for CameraX capture operations and background batch inferences
    private lateinit var cameraExecutor: ExecutorService

    // CameraX capture use-case reference
    private var imageCapture: ImageCapture? = null

    // =========================================================================
    // UI Layout Containers
    // =========================================================================
    private lateinit var layoutSingleScan: View
    private var layoutBatchTest: View? = null
    private var bottomNavigation: BottomNavigationView? = null

    // =========================================================================
    // Single-Scan UI Elements
    // =========================================================================
    private lateinit var cardCamera: View
    private lateinit var cardGallery: View
    private lateinit var cardResult: View
    private lateinit var imgScanned: ImageView
    private var imgEnhanced: ImageView? = null

    // Enhanced Model UI widgets (Thesis flavor)
    private var txtEnhancedLabel: TextView? = null
    private var txtEnhancedConfidence: TextView? = null
    private var progressEnhancedConfidence: ProgressBar? = null
    private var txtEnhancedOtherProbs: TextView? = null
    private var txtEnhancedMetrics: TextView? = null

    // Base Model UI widgets (Thesis flavor)
    private var txtBaseLabel: TextView? = null
    private var txtBaseConfidence: TextView? = null
    private var progressBaseConfidence: ProgressBar? = null
    private var txtBaseOtherProbs: TextView? = null
    private var txtBaseMetrics: TextView? = null

    // Agronomic Disease Details UI widgets (User flavor)
    private var txtDiseaseDesc: TextView? = null
    private var txtDiseaseTreatment: TextView? = null

    // Metrics popup button (Thesis flavor)
    private var btnViewThesisMetrics: Button? = null

    // Camera viewfinder container & controls
    private lateinit var cameraContainer: View
    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnCloseCamera: ImageButton

    // Single-scan history list elements
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var txtNoHistory: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var historyAdapter: HistoryAdapter
    private val historyList = mutableListOf<HistoryItem>()

    // =========================================================================
    // Batch-Test UI Elements (Thesis flavor)
    // =========================================================================
    // Stores selected image URIs partitioned by class index: 0=CBB, 1=CBSD, 2=CGM, 3=CMD, 4=Healthy
    private val batchImages = mutableMapOf<Int, MutableList<Uri>>()
    private var currentBatchClass: Int = -1

    private var btnRunBatchTest: Button? = null
    private var layoutBatchResults: View? = null
    private var txtBatchMetrics: TextView? = null
    private var progressBatch: ProgressBar? = null
    private var recyclerBatchHistory: RecyclerView? = null
    private var txtNoBatchHistory: TextView? = null
    private var btnClearBatchHistory: Button? = null
    private var batchHistoryAdapter: BatchHistoryAdapter? = null
    private val batchHistoryList = mutableListOf<BatchHistoryItem>()

    // =========================================================================
    // Activity Result Launchers
    // =========================================================================

    /**
     * Single-scan gallery image picker launcher.
     * Reads selected image stream, saves locally to application cache, and launches diagnosis.
     */
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val localFilePath = saveBitmapToCache(bitmap)
                    processDiagnosis(bitmap, localFilePath)
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Batch-test multi-image picker launcher.
     * Adds selected image URIs into the currently selected ground-truth disease class bucket.
     */
    private val batchGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && currentBatchClass != -1) {
            val list = batchImages.getOrPut(currentBatchClass) { mutableListOf() }
            list.addAll(uris)
            updateBatchCounts()
        }
    }

    /**
     * Runtime camera permission request contract launcher.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCameraContainer()
        } else {
            Toast.makeText(this, "Camera permission is required to scan leaves", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // Activity Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enforce Light Mode for consistent academic and clinical visual presentation
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)

        classifier = CassavaClassifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        initViews()
        setupNavigation()
        setupRecyclerViews()
        setupClickListeners()
        loadSavedHistory()
        loadBatchHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        classifier.close()
    }

    // =========================================================================
    // Initialization & View Binding
    // =========================================================================

    /**
     * Binds XML layout views to member fields with flavor-safe nullable handling.
     */
    private fun initViews() {
        cardCamera = findViewById(R.id.card_camera)
        cardGallery = findViewById(R.id.card_gallery)
        cardResult = findViewById(R.id.card_result)
        imgScanned = findViewById(R.id.img_scanned)
        imgEnhanced = findViewById(R.id.img_enhanced)

        // Thesis enhanced model views
        txtEnhancedLabel = findViewById(R.id.txt_enhanced_label)
        txtEnhancedConfidence = findViewById(R.id.txt_enhanced_confidence)
        progressEnhancedConfidence = findViewById(R.id.progress_enhanced_confidence)
        txtEnhancedOtherProbs = findViewById(R.id.txt_enhanced_other_probs)
        txtEnhancedMetrics = findViewById(R.id.txt_enhanced_metrics)

        // Thesis base model views
        txtBaseLabel = findViewById(R.id.txt_base_label)
        txtBaseConfidence = findViewById(R.id.txt_base_confidence)
        progressBaseConfidence = findViewById(R.id.progress_base_confidence)
        txtBaseOtherProbs = findViewById(R.id.txt_base_other_probs)
        txtBaseMetrics = findViewById(R.id.txt_base_metrics)

        btnViewThesisMetrics = findViewById(R.id.btn_view_thesis_metrics)

        // User flavor disease description & treatment views
        txtDiseaseDesc = findViewById(R.id.txt_disease_desc)
        txtDiseaseTreatment = findViewById(R.id.txt_disease_treatment)

        // Camera viewfinder views
        cameraContainer = findViewById(R.id.camera_container)
        previewView = findViewById(R.id.preview_view)
        btnCapture = findViewById(R.id.btn_capture)
        btnCloseCamera = findViewById(R.id.btn_close_camera)

        // Single scan history views
        recyclerHistory = findViewById(R.id.recycler_history)
        txtNoHistory = findViewById(R.id.txt_no_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        // Batch testing views (Thesis flavor only)
        btnRunBatchTest = findViewById(R.id.btn_run_batch_test)
        layoutBatchResults = findViewById(R.id.layout_batch_results)
        txtBatchMetrics = findViewById(R.id.txt_batch_metrics)
        progressBatch = findViewById(R.id.progress_batch)
        recyclerBatchHistory = findViewById(R.id.recycler_batch_history)
        txtNoBatchHistory = findViewById(R.id.txt_no_batch_history)
        btnClearBatchHistory = findViewById(R.id.btn_clear_batch_history)

        // Model status badge
        findViewById<TextView>(R.id.txt_model_badge)?.text =
            if (classifier.isBaseModelLoaded && classifier.isEnhancedModelLoaded) "TFLite Models" else "Model Error"
    }

    /**
     * Sets up BottomNavigationView switching between Single Scan and Batch Test tabs.
     */
    private fun setupNavigation() {
        layoutSingleScan = findViewById(R.id.layout_single_scan)
        layoutBatchTest = findViewById(R.id.layout_batch_test)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_single_scan -> {
                    layoutSingleScan.visibility = View.VISIBLE
                    layoutBatchTest?.visibility = View.GONE
                    true
                }
                R.id.nav_batch_test -> {
                    layoutSingleScan.visibility = View.GONE
                    layoutBatchTest?.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Sets up RecyclerView adapters for Single Scan and Batch Test history lists.
     */
    private fun setupRecyclerViews() {
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(
            items = historyList,
            onItemClick = { historyItem ->
                // Reload and redisplay past diagnosis upon item click
                if (historyItem.imagePath.isNotEmpty()) {
                    val file = File(historyItem.imagePath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            processDiagnosis(bitmap, historyItem.imagePath, saveToHistory = false)
                        }
                    }
                }
            },
            onDeleteClick = { historyItem ->
                deleteHistoryItem(historyItem)
            }
        )
        recyclerHistory.adapter = historyAdapter

        recyclerBatchHistory?.layoutManager = LinearLayoutManager(this)
        batchHistoryAdapter = BatchHistoryAdapter(
            historyList = batchHistoryList,
            onDeleteClick = { batchItem ->
                deleteBatchHistoryItem(batchItem)
            }
        )
        recyclerBatchHistory?.adapter = batchHistoryAdapter
    }

    /**
     * Registers all UI click event listeners.
     */
    private fun setupClickListeners() {
        cardCamera.setOnClickListener { checkCameraPermissionAndOpen() }
        cardGallery.setOnClickListener {
            closeCameraContainer()
            galleryLauncher.launch("image/*")
        }
        btnCapture.setOnClickListener { takePhoto() }
        btnCloseCamera.setOnClickListener { closeCameraContainer() }
        btnClearHistory.setOnClickListener { clearHistory() }
        btnViewThesisMetrics?.setOnClickListener { showThesisMetricsDialog() }

        // Batch test class upload buttons (0: CBB, 1: CBSD, 2: CGM, 3: CMD, 4: Healthy)
        findViewById<Button>(R.id.btn_upload_cbb)?.setOnClickListener { launchBatchUpload(0) }
        findViewById<Button>(R.id.btn_upload_cbsd)?.setOnClickListener { launchBatchUpload(1) }
        findViewById<Button>(R.id.btn_upload_cgm)?.setOnClickListener { launchBatchUpload(2) }
        findViewById<Button>(R.id.btn_upload_cmd)?.setOnClickListener { launchBatchUpload(3) }
        findViewById<Button>(R.id.btn_upload_healthy)?.setOnClickListener { launchBatchUpload(4) }

        btnRunBatchTest?.setOnClickListener { runBatchTest() }
        btnClearBatchHistory?.setOnClickListener { clearBatchHistory() }
    }

    // =========================================================================
    // CameraX Lifecycle & Capture Management
    // =========================================================================

    /**
     * Checks Camera permission; if granted, opens the live viewfinder, otherwise requests it.
     */
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCameraContainer()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCameraContainer() {
        cardResult.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE
        startCamera()
    }

    private fun closeCameraContainer() {
        cameraContainer.visibility = View.GONE
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Binds CameraX Preview and ImageCapture use-cases to the Activity lifecycle.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera bind failed: ${exc.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Captures a still image via CameraX and stores it in the local app cache directory.
     */
    private fun takePhoto() {
        val capture = imageCapture ?: return
        val photoFile = File(cacheDir, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        btnCapture.isEnabled = false

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    btnCapture.isEnabled = true
                    Toast.makeText(baseContext, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    btnCapture.isEnabled = true
                    closeCameraContainer()
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap != null) {
                        processDiagnosis(bitmap, photoFile.absolutePath)
                    }
                }
            }
        )
    }

    // =========================================================================
    // Single-Scan Diagnosis Pipeline
    // =========================================================================

    /**
     * Dispatches inference through [CassavaClassifier], populates results into views,
     * updates confidence bars, and records the event into local history.
     */
    private fun processDiagnosis(bitmap: Bitmap, imagePath: String, saveToHistory: Boolean = true) {
        val result = try {
            classifier.classifyImage(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Model Execution Error: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Make result container visible
        cardResult.visibility = View.VISIBLE

        // 1. Display Original and Enhanced images side-by-side
        imgScanned.setImageBitmap(bitmap)
        if (result.enhancedBitmap != null) {
            imgEnhanced?.setImageBitmap(result.enhancedBitmap)
        } else {
            imgEnhanced?.setImageBitmap(bitmap)
        }

        // 2. Populate Enhanced Model outputs
        txtEnhancedLabel?.text = result.enhancedResult.label
        val enhancedConfPercent = result.enhancedResult.confidence * 100
        txtEnhancedConfidence?.text = String.format(Locale.getDefault(), "%.0f%% Conf.", enhancedConfPercent)
        progressEnhancedConfidence?.progress = enhancedConfPercent.toInt()

        val enhancedLabelColor = getColorForIndex(result.enhancedResult.index)
        txtEnhancedConfidence?.setTextColor(getColor(enhancedLabelColor))
        progressEnhancedConfidence?.progressTintList = ContextCompat.getColorStateList(this, enhancedLabelColor)

        result.enhancedResult.probabilities?.let { probs ->
            if (probs.size >= 5) {
                txtEnhancedOtherProbs?.text = String.format(
                    Locale.getDefault(),
                    "CBB: %.0f%% | CBSD: %.0f%% | CGM: %.0f%% | CMD: %.0f%% | Healthy: %.0f%%",
                    probs[0] * 100, probs[1] * 100, probs[2] * 100, probs[3] * 100, probs[4] * 100
                )
            }
        }

        txtEnhancedMetrics?.text = String.format(
            Locale.getDefault(),
            "Inference Time: %d ms | Size: %d KB",
            result.enhancedResult.inferenceTimeMs,
            result.enhancedResult.modelSizeKb
        )

        // 3. Populate User Flavor agronomic disease description and management
        txtDiseaseDesc?.text = result.description
        txtDiseaseTreatment?.text = result.treatment

        // 4. Populate Base Model outputs (Thesis flavor)
        txtBaseLabel?.text = result.baseResult.label
        val baseConfPercent = result.baseResult.confidence * 100
        txtBaseConfidence?.text = String.format(Locale.getDefault(), "%.0f%%", baseConfPercent)
        progressBaseConfidence?.progress = baseConfPercent.toInt()

        val baseLabelColor = getColorForIndex(result.baseResult.index)
        txtBaseConfidence?.setTextColor(getColor(baseLabelColor))
        progressBaseConfidence?.progressTintList = ContextCompat.getColorStateList(this, baseLabelColor)

        result.baseResult.probabilities?.let { probs ->
            if (probs.size >= 5) {
                txtBaseOtherProbs?.text = String.format(
                    Locale.getDefault(),
                    "CBB: %.0f%% | CBSD: %.0f%% | CGM: %.0f%% | CMD: %.0f%% | Healthy: %.0f%%",
                    probs[0] * 100, probs[1] * 100, probs[2] * 100, probs[3] * 100, probs[4] * 100
                )
            }
        }

        txtBaseMetrics?.text = String.format(
            Locale.getDefault(),
            "Inference Time: %d ms | Size: %d KB",
            result.baseResult.inferenceTimeMs,
            result.baseResult.modelSizeKb
        )

        // 5. Save record to local persistent history
        if (saveToHistory) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val historyItem = HistoryItem(
                id = UUID.randomUUID().toString(),
                label = result.enhancedResult.label,
                confidence = result.enhancedResult.confidence,
                date = dateStr,
                imagePath = imagePath,
                index = result.enhancedResult.index
            )
            historyList.add(0, historyItem)
            saveHistoryToSharedPrefs()
            updateHistoryUI()
        }

        // Smooth scroll to top of scrollview so user sees analysis result immediately
        findViewById<NestedScrollView>(R.id.nestedScrollView)?.post {
            findViewById<NestedScrollView>(R.id.nestedScrollView)?.smoothScrollTo(0, 0)
        }
    }

    /**
     * Maps disease category index to associated color resource.
     */
    private fun getColorForIndex(index: Int): Int {
        return when (index) {
            0 -> R.color.color_cbb
            1 -> R.color.color_cbsd
            2 -> R.color.color_cgm
            3 -> R.color.color_cmd
            else -> R.color.color_healthy
        }
    }

    /**
     * Caches gallery bitmap to disk so it can be reloaded in history without holding in RAM.
     */
    private fun saveBitmapToCache(bitmap: Bitmap): String {
        val file = File(cacheDir, "gallery_" + System.currentTimeMillis() + ".jpg")
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return file.absolutePath
    }

    // =========================================================================
    // Single-Scan History Persistence (SharedPreferences + JSON)
    // =========================================================================

    private fun saveHistoryToSharedPrefs() {
        val prefs = getSharedPreferences("cassava_history", MODE_PRIVATE)
        val array = JSONArray()
        for (item in historyList) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            obj.put("confidence", item.confidence.toDouble())
            obj.put("date", item.date)
            obj.put("imagePath", item.imagePath)
            obj.put("index", item.index)
            array.put(obj)
        }
        prefs.edit().putString("history_json", array.toString()).apply()
    }

    private fun loadSavedHistory() {
        val prefs = getSharedPreferences("cassava_history", MODE_PRIVATE)
        val jsonStr = prefs.getString("history_json", null) ?: return
        historyList.clear()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                historyList.add(
                    HistoryItem(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        confidence = obj.getDouble("confidence").toFloat(),
                        date = obj.getString("date"),
                        imagePath = obj.getString("imagePath"),
                        index = obj.getInt("index")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateHistoryUI()
    }

    private fun updateHistoryUI() {
        if (historyList.isEmpty()) {
            txtNoHistory.visibility = View.VISIBLE
            recyclerHistory.visibility = View.GONE
            btnClearHistory.visibility = View.GONE
        } else {
            txtNoHistory.visibility = View.GONE
            recyclerHistory.visibility = View.VISIBLE
            btnClearHistory.visibility = View.VISIBLE
            historyAdapter.updateData(historyList)
        }
    }

    private fun deleteHistoryItem(item: HistoryItem) {
        val index = historyList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            historyList.removeAt(index)
            saveHistoryToSharedPrefs()
            updateHistoryUI()
            Toast.makeText(this, "Item removed from history", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearHistory() {
        historyList.clear()
        saveHistoryToSharedPrefs()
        updateHistoryUI()
    }

    // =========================================================================
    // Batch Testing Pipeline & Multi-Class Evaluation Metrics
    // =========================================================================

    private fun launchBatchUpload(classIndex: Int) {
        currentBatchClass = classIndex
        batchGalleryLauncher.launch("image/*")
    }

    private fun updateBatchCounts() {
        findViewById<TextView>(R.id.txt_count_cbb)?.text = "${batchImages[0]?.size ?: 0} images selected"
        findViewById<TextView>(R.id.txt_count_cbsd)?.text = "${batchImages[1]?.size ?: 0} images selected"
        findViewById<TextView>(R.id.txt_count_cgm)?.text = "${batchImages[2]?.size ?: 0} images selected"
        findViewById<TextView>(R.id.txt_count_cmd)?.text = "${batchImages[3]?.size ?: 0} images selected"
        findViewById<TextView>(R.id.txt_count_healthy)?.text = "${batchImages[4]?.size ?: 0} images selected"
    }

    /**
     * Executes batch model evaluation across all uploaded images in background thread.
     */
    private fun runBatchTest() {
        val totalImages = batchImages.values.sumOf { it.size }
        if (totalImages == 0) {
            Toast.makeText(this, "No images selected for batch test", Toast.LENGTH_SHORT).show()
            return
        }

        btnRunBatchTest?.isEnabled = false
        progressBatch?.visibility = View.VISIBLE
        layoutBatchResults?.visibility = View.GONE

        cameraExecutor.execute {
            val basePredictions = mutableListOf<Int>()
            val enhancedPredictions = mutableListOf<Int>()
            val groundTruths = mutableListOf<Int>()

            for ((classIndex, uris) in batchImages) {
                for (uri in uris) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (bitmap != null) {
                            val result = classifier.classifyImage(bitmap)
                            basePredictions.add(result.baseResult.index)
                            enhancedPredictions.add(result.enhancedResult.index)
                            groundTruths.add(classIndex)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Compute rigorous academic metrics across the 5 disease classes
            val baseMetrics = calculateMetrics(basePredictions, groundTruths, 5)
            val enhancedMetrics = calculateMetrics(enhancedPredictions, groundTruths, 5)

            runOnUiThread {
                displayBatchResults(baseMetrics, enhancedMetrics, totalImages)
                saveBatchHistory(totalImages, baseMetrics, enhancedMetrics)
                btnRunBatchTest?.isEnabled = true
                progressBatch?.visibility = View.GONE

                // Reset batch inputs
                batchImages.clear()
                updateBatchCounts()
            }
        }
    }

    /**
     * Calculates multi-class confusion matrix and comprehensive evaluation metrics:
     *
     * 1. Overall Accuracy = Correct Predictions / Total Predictions
     * 2. Per-Class One-vs-Rest Confusion Matrix:
     *    - TP_c: Predicted = c AND Truth = c
     *    - FP_c: Predicted = c AND Truth != c
     *    - FN_c: Predicted != c AND Truth = c
     *    - TN_c: Predicted != c AND Truth != c
     * 3. Macro Precision = (1 / K) * sum(TP_c / (TP_c + FP_c))
     * 4. Macro Recall (Sensitivity) = (1 / K) * sum(TP_c / (TP_c + FN_c))
     * 5. Macro F1 = (1 / K) * sum(2 * Prec_c * Rec_c / (Prec_c + Rec_c))
     * 6. True Negative Rate (Macro Specificity) = (1 / K) * sum(TN_c / (TN_c + FP_c))
     * 7. Matthews Correlation Coefficient (MCC):
     *    MCC_c = (TP*TN - FP*FN) / sqrt((TP+FP)*(TP+FN)*(TN+FP)*(TN+FN))
     *    Macro MCC = (1 / K) * sum(MCC_c)
     */
    private fun calculateMetrics(predictions: List<Int>, groundTruths: List<Int>, numClasses: Int): Metrics {
        if (predictions.isEmpty()) return Metrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        var correct = 0
        val tp = DoubleArray(numClasses)
        val tn = DoubleArray(numClasses)
        val fp = DoubleArray(numClasses)
        val fn = DoubleArray(numClasses)

        for (i in predictions.indices) {
            val p = predictions[i]
            val t = groundTruths[i]
            if (p == t) correct++

            for (c in 0 until numClasses) {
                if (p == c && t == c) tp[c]++
                else if (p == c && t != c) fp[c]++
                else if (p != c && t == c) fn[c]++
                else tn[c]++
            }
        }

        val accuracy = correct.toDouble() / predictions.size

        var macroPrecision = 0.0
        var macroRecall = 0.0
        var macroF1 = 0.0
        var macroTnr = 0.0
        var macroMcc = 0.0

        for (c in 0 until numClasses) {
            val precision = if (tp[c] + fp[c] > 0) tp[c] / (tp[c] + fp[c]) else 0.0
            val recall = if (tp[c] + fn[c] > 0) tp[c] / (tp[c] + fn[c]) else 0.0
            val f1 = if (precision + recall > 0) 2.0 * precision * recall / (precision + recall) else 0.0
            val tnr = if (tn[c] + fp[c] > 0) tn[c] / (tn[c] + fp[c]) else 0.0

            val denominator = Math.sqrt((tp[c] + fp[c]) * (tp[c] + fn[c]) * (tn[c] + fp[c]) * (tn[c] + fn[c]))
            val mcc = if (denominator > 0) (tp[c] * tn[c] - fp[c] * fn[c]) / denominator else 0.0

            macroPrecision += precision
            macroRecall += recall
            macroF1 += f1
            macroTnr += tnr
            macroMcc += mcc
        }

        macroPrecision /= numClasses
        macroRecall /= numClasses
        macroF1 /= numClasses
        macroTnr /= numClasses
        macroMcc /= numClasses

        // Note: Micro F1 in multi-class single-label is mathematically equal to Accuracy
        return Metrics(
            accuracy = accuracy,
            precision = macroPrecision,
            recall = macroRecall,
            f1Score = accuracy,
            tnr = macroTnr,
            macroF1 = macroF1,
            mcc = macroMcc
        )
    }

    private fun displayBatchResults(base: Metrics, enhanced: Metrics, total: Int) {
        layoutBatchResults?.visibility = View.VISIBLE
        val resultText = """
            Total Images Tested: $total
            
            BASE MODEL METRICS:
            Accuracy: ${formatPercent(base.accuracy)}
            Precision: ${formatPercent(base.precision)}
            Recall: ${formatPercent(base.recall)}
            F1 Score: ${formatPercent(base.f1Score)}
            True Negative Rate: ${formatPercent(base.tnr)}
            Macro F1: ${formatPercent(base.macroF1)}
            MCC: ${formatDouble(base.mcc)}
            
            ENHANCED MODEL METRICS:
            Accuracy: ${formatPercent(enhanced.accuracy)}
            Precision: ${formatPercent(enhanced.precision)}
            Recall: ${formatPercent(enhanced.recall)}
            F1 Score: ${formatPercent(enhanced.f1Score)}
            True Negative Rate: ${formatPercent(enhanced.tnr)}
            Macro F1: ${formatPercent(enhanced.macroF1)}
            MCC: ${formatDouble(enhanced.mcc)}
        """.trimIndent()

        txtBatchMetrics?.text = resultText
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value * 100)
    private fun formatDouble(value: Double): String = String.format(Locale.US, "%.3f", value)

    // =========================================================================
    // Batch-Test History Persistence
    // =========================================================================

    private fun saveBatchHistory(totalImages: Int, baseMetrics: Metrics, enhancedMetrics: Metrics) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val item = BatchHistoryItem(
            id = UUID.randomUUID().toString(),
            date = dateStr,
            totalImages = totalImages,
            baseMetrics = baseMetrics,
            enhancedMetrics = enhancedMetrics
        )
        batchHistoryList.add(0, item)
        saveBatchHistoryToSharedPrefs()
        updateBatchHistoryUI()
    }

    private fun saveBatchHistoryToSharedPrefs() {
        val prefs = getSharedPreferences("cassava_batch_history", MODE_PRIVATE)
        val array = JSONArray()
        for (h in batchHistoryList) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("date", h.date)
            obj.put("totalImages", h.totalImages)

            val bObj = JSONObject()
            bObj.put("acc", h.baseMetrics.accuracy)
            bObj.put("prec", h.baseMetrics.precision)
            bObj.put("rec", h.baseMetrics.recall)
            bObj.put("f1", h.baseMetrics.f1Score)
            bObj.put("tnr", h.baseMetrics.tnr)
            bObj.put("macroF1", h.baseMetrics.macroF1)
            bObj.put("mcc", h.baseMetrics.mcc)
            obj.put("baseMetrics", bObj)

            val eObj = JSONObject()
            eObj.put("acc", h.enhancedMetrics.accuracy)
            eObj.put("prec", h.enhancedMetrics.precision)
            eObj.put("rec", h.enhancedMetrics.recall)
            eObj.put("f1", h.enhancedMetrics.f1Score)
            eObj.put("tnr", h.enhancedMetrics.tnr)
            eObj.put("macroF1", h.enhancedMetrics.macroF1)
            eObj.put("mcc", h.enhancedMetrics.mcc)
            obj.put("enhancedMetrics", eObj)

            array.put(obj)
        }
        prefs.edit().putString("batch_history_json", array.toString()).apply()
    }

    private fun loadBatchHistory() {
        val prefs = getSharedPreferences("cassava_batch_history", MODE_PRIVATE)
        val jsonStr = prefs.getString("batch_history_json", null) ?: return
        batchHistoryList.clear()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                val bObj = obj.getJSONObject("baseMetrics")
                val baseMetrics = Metrics(
                    accuracy = bObj.getDouble("acc"),
                    precision = bObj.getDouble("prec"),
                    recall = bObj.getDouble("rec"),
                    f1Score = bObj.getDouble("f1"),
                    tnr = bObj.getDouble("tnr"),
                    macroF1 = bObj.optDouble("macroF1", bObj.getDouble("f1")),
                    mcc = bObj.getDouble("mcc")
                )

                val eObj = obj.getJSONObject("enhancedMetrics")
                val enhancedMetrics = Metrics(
                    accuracy = eObj.getDouble("acc"),
                    precision = eObj.getDouble("prec"),
                    recall = eObj.getDouble("rec"),
                    f1Score = eObj.getDouble("f1"),
                    tnr = eObj.getDouble("tnr"),
                    macroF1 = eObj.optDouble("macroF1", eObj.getDouble("f1")),
                    mcc = eObj.getDouble("mcc")
                )

                batchHistoryList.add(
                    BatchHistoryItem(
                        id = obj.getString("id"),
                        date = obj.getString("date"),
                        totalImages = obj.getInt("totalImages"),
                        baseMetrics = baseMetrics,
                        enhancedMetrics = enhancedMetrics
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateBatchHistoryUI()
    }

    private fun updateBatchHistoryUI() {
        if (batchHistoryList.isEmpty()) {
            txtNoBatchHistory?.visibility = View.VISIBLE
            recyclerBatchHistory?.visibility = View.GONE
            btnClearBatchHistory?.visibility = View.GONE
        } else {
            txtNoBatchHistory?.visibility = View.GONE
            recyclerBatchHistory?.visibility = View.VISIBLE
            btnClearBatchHistory?.visibility = View.VISIBLE
            batchHistoryAdapter?.updateData(batchHistoryList)
        }
    }

    private fun deleteBatchHistoryItem(item: BatchHistoryItem) {
        val index = batchHistoryList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            batchHistoryList.removeAt(index)
            saveBatchHistoryToSharedPrefs()
            updateBatchHistoryUI()
            Toast.makeText(this, "Batch test record removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearBatchHistory() {
        batchHistoryList.clear()
        getSharedPreferences("cassava_batch_history", MODE_PRIVATE).edit().clear().apply()
        updateBatchHistoryUI()
    }

    // =========================================================================
    // Thesis Metrics Dialog (Comparative Benchmarking Presentation)
    // =========================================================================

    private fun showThesisMetricsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_thesis_metrics, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTraining = dialogView.findViewById<TextView>(R.id.metrics_training)
        val txtClassification = dialogView.findViewById<TextView>(R.id.metrics_classification)
        val txtInference = dialogView.findViewById<TextView>(R.id.metrics_inference)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close_metrics)

        txtTraining.text = """
            |Training Time:
            |Base: 40 mins  |  Enhanced: 2 hrs 30 mins
            |
            |Memory Usage:
            |Base: System RAM: 4.6GB, GPU RAM: 1.1GB
            |Enhanced: System RAM: 6.0GB, GPU RAM: 2.1GB
            |
            |Weight Discrepancy (L2 Norm):
            |Base: 1855.56  |  Enhanced: 1846.71 (Lower = Better)
        """.trimMargin()

        txtClassification.text = """
            |Accuracy:
            |Base: 80.14%  |  Enhanced: 82.22% (+2.08%)
            |
            |Precision (Macro):
            |Base: 82.52%  |  Enhanced: 84.07% (+1.55%)
            |
            |Recall (Macro):
            |Base: 78.67%  |  Enhanced: 81.34% (+2.67%)
            |
            |F1 Score (Macro):
            |Base: 79.81%  |  Enhanced: 82.24% (+2.43%)
            |
            |True Negative Rate (Specificity):
            |Base: 94.84%  |  Enhanced: 95.41% (+0.57%)
            |
            |Matthews Correlation Coefficient (MCC):
            |Base: 0.7525  |  Enhanced: 0.7783 (+0.0258)
        """.trimMargin()

        txtInference.text = """
            |Avg. Inference Latency (Per Image):
            |Base: 11.75 ms  |  Enhanced: 18.04 ms (+6.29 ms)
            |
            |Multiply-Accumulate Operations (MACs):
            |Base: 56,577,051  |  Enhanced: 98,254,540 (+41,677,489)
            |
            |Model Size (Total Parameters):
            |Base: 942,005  |  Enhanced: 965,719 (+23,714)
        """.trimMargin()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
