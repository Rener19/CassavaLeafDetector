package com.example.cassavaleafdetector

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
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
    private var btnHealthyToggle: Button? = null
    private var btnUnhealthyToggle: Button? = null
    private var btnAllToggle: Button? = null
    private lateinit var btnClearHistory: Button
    private lateinit var historyAdapter: HistoryAdapter
    private val historyList = mutableListOf<HistoryItem>()
    private var historyListDisplay = mutableListOf<HistoryItem>()

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
    private var seekBatchThreshold: SeekBar? = null
    private var txtBatchThresholdValue: TextView? = null
    private var batchConfidenceThreshold: Float = 0.60f
    private var isIndependentMode: Boolean = false
    private var toggleBatchMode: MaterialButtonToggleGroup? = null
    private var txtBatchModeDesc: TextView? = null
    private var seekSingleThreshold: SeekBar? = null
    private var txtSingleThresholdValue: TextView? = null
    private var singleConfidenceThreshold: Float = 0.60f

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
            cameraExecutor.execute {
                try {
                    val bitmap = decodeAndCorrectOrientation(it)
                    if (bitmap != null) {
                        val localFilePath = saveBitmapToCache(bitmap)
                        runOnUiThread {
                            processDiagnosis(bitmap, localFilePath)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
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

        // Ensure top app bar does not overlap with status bar / camera cutouts (Android 15+ edge-to-edge)
        val appBarLayout = findViewById<View>(R.id.app_bar_layout)
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
                val statusBarInsets = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.updatePadding(top = statusBarInsets.top)
                insets
            }
        }

        classifier = CassavaClassifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        initViews()
        setupNavigation()
        setupRecyclerViews()
        setupClickListeners()
        loadSavedHistory()
        loadBatchHistory()
        btnAllToggle?.setOnClickListener { loadSavedHistory() }
        btnHealthyToggle?.setOnClickListener { toggleHealthy() }
        btnUnhealthyToggle?.setOnClickListener { toggleUnhealthy() }
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
        btnHealthyToggle = findViewById(R.id.healthyToggle)
        btnUnhealthyToggle = findViewById(R.id.unhealthyToggle)
        btnAllToggle = findViewById(R.id.allToggle)

        // Batch testing views (Thesis flavor only)
        btnRunBatchTest = findViewById(R.id.btn_run_batch_test)
        layoutBatchResults = findViewById(R.id.layout_batch_results)
        txtBatchMetrics = findViewById(R.id.txt_batch_metrics)
        progressBatch = findViewById(R.id.progress_batch)
        recyclerBatchHistory = findViewById(R.id.recycler_batch_history)
        txtNoBatchHistory = findViewById(R.id.txt_no_batch_history)
        btnClearBatchHistory = findViewById(R.id.btn_clear_batch_history)
        seekBatchThreshold = findViewById(R.id.seek_batch_threshold)
        txtBatchThresholdValue = findViewById(R.id.txt_batch_threshold_value)
        toggleBatchMode = findViewById(R.id.toggle_batch_mode)
        txtBatchModeDesc = findViewById(R.id.txt_batch_mode_desc)
        seekSingleThreshold = findViewById(R.id.seek_single_threshold)
        txtSingleThresholdValue = findViewById(R.id.txt_single_threshold_value)

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
            onItemClick = { batchItem ->
                showBatchHistoryItemDialog(batchItem)
            },
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

        // Batch test class individual clear buttons
        findViewById<Button>(R.id.btn_clear_cbb)?.setOnClickListener { clearBatchClass(0, "CBB") }
        findViewById<Button>(R.id.btn_clear_cbsd)?.setOnClickListener { clearBatchClass(1, "CBSD") }
        findViewById<Button>(R.id.btn_clear_cgm)?.setOnClickListener { clearBatchClass(2, "CGM") }
        findViewById<Button>(R.id.btn_clear_cmd)?.setOnClickListener { clearBatchClass(3, "CMD") }
        findViewById<Button>(R.id.btn_clear_healthy)?.setOnClickListener { clearBatchClass(4, "Healthy") }
        findViewById<Button>(R.id.btn_clear_all_batch_inputs)?.setOnClickListener { clearAllBatchInputs() }

        btnRunBatchTest?.setOnClickListener { runBatchTest() }
        btnClearBatchHistory?.setOnClickListener { clearBatchHistory() }

        toggleBatchMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_mode_paired -> {
                        isIndependentMode = false
                        txtBatchModeDesc?.text = "Mode 1 (Paired): Both models evaluated on the same image subset accepted by the Enhanced model."
                    }
                    R.id.btn_mode_independent -> {
                        isIndependentMode = true
                        txtBatchModeDesc?.text = "Mode 2 (Independent): Models evaluated independently on images exceeding their own threshold. Reports coverage metrics."
                    }
                }
            }
        }

        seekBatchThreshold?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                batchConfidenceThreshold = progress / 100.0f
                txtBatchThresholdValue?.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekSingleThreshold?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                singleConfidenceThreshold = progress / 100.0f
                txtSingleThresholdValue?.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
                    cameraExecutor.execute {
                        val bitmap = decodeAndCorrectOrientation(photoFile)
                        if (bitmap != null) {
                            runOnUiThread {
                                processDiagnosis(bitmap, photoFile.absolutePath)
                            }
                        }
                    }
                }
            }
        )
    }

    // =========================================================================
    // Single-Scan Diagnosis Pipeline
    // =========================================================================

    /**
     * Entry point for single-scan image diagnosis.
     * Stage 1: Executes Gatekeeper binary model to determine whether the image is a cassava leaf.
     * If rejected (< 70% confidence), prompts the user with options to retake or continue anyway.
     * If accepted (>= 70% confidence) or if user chooses "Continue", proceeds to Stage 2 classification.
     */
    private fun processDiagnosis(bitmap: Bitmap, imagePath: String, saveToHistory: Boolean = true) {
        if (saveToHistory) {
            cameraExecutor.execute {
                val gatekeeperResult = classifier.checkGatekeeper(bitmap)
                runOnUiThread {
                    if (!gatekeeperResult.isCassava) {
                        cardResult.visibility = View.GONE
                        val confPercent = (gatekeeperResult.probability * 100).toInt()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("⚠️ Not a Cassava Leaf")
                            .setMessage(
                                "It seems like this image is not a cassava leaf ($confPercent% Cassava confidence).\n\n" +
                                "Would you like to retake the photo or continue anyway?"
                            )
                            .setPositiveButton("Retake Photo") { _, _ ->
                                openCameraContainer()
                            }
                            .setNeutralButton("Upload Another") { _, _ ->
                                closeCameraContainer()
                                galleryLauncher.launch("image/*")
                            }
                            .setNegativeButton("Continue") { _, _ ->
                                runClassificationPipeline(bitmap, imagePath, saveToHistory, gatekeeperResult)
                            }
                            .show()
                        return@runOnUiThread
                    }
                    runClassificationPipeline(bitmap, imagePath, saveToHistory, gatekeeperResult)
                }
            }
        } else {
            // Browsing historical records directly
            runClassificationPipeline(bitmap, imagePath, saveToHistory = false, gatekeeperResult = null)
        }
    }

    /**
     * Stage 2: Dispatches multi-model disease inference through [CassavaClassifier],
     * enforces the 60% confidence threshold rejection filter, populates results into views,
     * updates confidence bars, and records the event into local history.
     */
    private fun runClassificationPipeline(
        bitmap: Bitmap,
        imagePath: String,
        saveToHistory: Boolean,
        gatekeeperResult: CassavaClassifier.GatekeeperResult? = null
    ) {
        cameraExecutor.execute {
            try {
                val result = classifier.classifyImage(bitmap, gatekeeperResult)
                runOnUiThread {
                    // Selective Classification: Rejection threshold check (user-configurable, default 60%)
                    // Only prompt for fresh scans (saveToHistory == true); browsing historical records opens directly.
                    if (saveToHistory && result.enhancedResult.confidence < singleConfidenceThreshold) {
                        cardResult.visibility = View.GONE
                        val confPercent = (result.enhancedResult.confidence * 100).toInt()
                        val thresholdPercent = (singleConfidenceThreshold * 100).toInt()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("⚠️ Low Confidence Prediction ($confPercent%)")
                            .setMessage(
                                "The model is uncertain about this leaf image (Confidence is below your $thresholdPercent% diagnostic threshold).\n\n" +
                                "This usually occurs when the photo is blurry, poorly lit, or contains background clutter.\n\n" +
                                "Would you like to take another photo or proceed anyway?"
                            )
                            .setPositiveButton("Retake Photo") { _, _ ->
                                openCameraContainer()
                            }
                            .setNeutralButton("Upload Another") { _, _ ->
                                closeCameraContainer()
                                galleryLauncher.launch("image/*")
                            }
                            .setNegativeButton("View Anyway") { _, _ ->
                                displayDiagnosisResult(result, bitmap, imagePath, saveToHistory)
                            }
                            .show()
                        return@runOnUiThread
                    }

                    displayDiagnosisResult(result, bitmap, imagePath, saveToHistory)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Model Execution Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun displayDiagnosisResult(
        result: CassavaClassifier.ComparisonResult,
        bitmap: Bitmap,
        imagePath: String,
        saveToHistory: Boolean
    ) {
        // Make result container visible
        cardResult.visibility = View.VISIBLE

        // 1. Display Original and Enhanced images side-by-side (standardized to 224x224 for an apples-to-apples model comparison)
        val rawModelInputBitmap = if (bitmap.width == 224 && bitmap.height == 224) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        }
        imgScanned.setImageBitmap(rawModelInputBitmap)
        if (result.enhancedBitmap != null) {
            imgEnhanced?.setImageBitmap(result.enhancedBitmap)
        } else {
            imgEnhanced?.setImageBitmap(rawModelInputBitmap)
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

        result.gatekeeperResult?.let { gk ->
            txtEnhancedMetrics?.text = String.format(
                Locale.getDefault(),
                "Gatekeeper: %s (%.0f%%) [%d ms] | Inference: %d ms | Size: %d KB",
                if (gk.isCassava) "Cassava Leaf" else "Non-Cassava Leaf",
                gk.probability * 100,
                gk.inferenceTimeMs,
                result.enhancedResult.inferenceTimeMs,
                result.enhancedResult.modelSizeKb
            )
        } ?: run {
            txtEnhancedMetrics?.text = String.format(
                Locale.getDefault(),
                "Inference Time: %d ms | Size: %d KB",
                result.enhancedResult.inferenceTimeMs,
                result.enhancedResult.modelSizeKb
            )
        }

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
            updateHistoryUI("all")
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
     * Decodes an image file and corrects any EXIF rotation (e.g. portrait camera capture).
     */
    private fun decodeAndCorrectOrientation(file: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotateBitmapIfNeeded(bitmap, orientation)
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Decodes an image content URI and corrects any EXIF rotation.
     */
    private fun decodeAndCorrectOrientation(uri: Uri): Bitmap? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) return null

        return try {
            val exifStream = contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(exifStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            exifStream.close()
            rotateBitmapIfNeeded(bitmap, orientation)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
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
        val jsonStr = prefs.getString("history_json", null)
        historyList.clear()
        if (jsonStr != null) {
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
        }
        updateHistoryUI("all")
    }

    private fun toggleHealthy() {
        updateHistoryUI("healthy")
    }

    private fun toggleUnhealthy() {
        updateHistoryUI("unhealthy")
    }

    private fun updateHistoryUI(toggle: String) {
        historyListDisplay.clear()
        when (toggle) {
            "healthy" -> historyListDisplay.addAll(historyList.filter { it.index == 4 || it.label.equals("Healthy", ignoreCase = true) })
            "unhealthy" -> historyListDisplay.addAll(historyList.filter { it.index != 4 && !it.label.equals("Healthy", ignoreCase = true) })
            else -> historyListDisplay.addAll(historyList)
        }

        if (historyListDisplay.isEmpty()) {
            txtNoHistory.visibility = View.VISIBLE
            recyclerHistory.visibility = View.GONE
            if (historyList.isEmpty()) {
                btnClearHistory.visibility = View.GONE
                btnHealthyToggle?.visibility = View.GONE
                btnUnhealthyToggle?.visibility = View.GONE
                btnAllToggle?.visibility = View.GONE
            } else {
                btnClearHistory.visibility = View.VISIBLE
                btnHealthyToggle?.visibility = View.VISIBLE
                btnUnhealthyToggle?.visibility = View.VISIBLE
                btnAllToggle?.visibility = View.VISIBLE
            }
        } else {
            txtNoHistory.visibility = View.GONE
            recyclerHistory.visibility = View.VISIBLE
            btnClearHistory.visibility = View.VISIBLE
            btnHealthyToggle?.visibility = View.VISIBLE
            btnUnhealthyToggle?.visibility = View.VISIBLE
            btnAllToggle?.visibility = View.VISIBLE
        }
        historyAdapter.updateData(historyListDisplay)
    }

    private fun deleteHistoryItem(item: HistoryItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Scan")
            .setMessage("Are you sure you want to delete this record?")
            .setPositiveButton("Delete") { _, _ ->
                val index = historyList.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    historyList.removeAt(index)
                    saveHistoryToSharedPrefs()
                    updateHistoryUI("all")
                    Toast.makeText(this, "Item removed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Delete History")
            .setMessage("Are you sure you want to delete ALL records?")
            .setPositiveButton("Delete") { _, _ ->
                historyList.clear()
                saveHistoryToSharedPrefs()
                updateHistoryUI("all")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =========================================================================
    // Batch Testing Pipeline & Multi-Class Evaluation Metrics
    // =========================================================================

    private fun launchBatchUpload(classIndex: Int) {
        currentBatchClass = classIndex
        batchGalleryLauncher.launch("image/*")
    }

    private fun clearBatchClass(classIndex: Int, className: String) {
        val count = batchImages[classIndex]?.size ?: 0
        batchImages[classIndex]?.clear()
        updateBatchCounts()
        if (count > 0) {
            Toast.makeText(this, "Cleared $count $className image(s)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "$className input is already empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAllBatchInputs() {
        val totalCount = batchImages.values.sumOf { it.size }
        if (totalCount == 0) {
            Toast.makeText(this, "Batch inputs are already empty", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Clear All Uploads")
            .setMessage("Are you sure you want to clear all $totalCount uploaded batch image(s)?")
            .setPositiveButton("Clear All") { _, _ ->
                batchImages.clear()
                updateBatchCounts()
                Toast.makeText(this, "Cleared all $totalCount uploaded image(s)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        val runIndependent = isIndependentMode

        cameraExecutor.execute {
            val basePredictions = mutableListOf<Int>()
            val baseGroundTruths = mutableListOf<Int>()
            val enhancedPredictions = mutableListOf<Int>()
            val enhancedGroundTruths = mutableListOf<Int>()
            val pairedGroundTruths = mutableListOf<Int>()

            var processedCount = 0
            var skippedNonCassava = 0
            var pairedSkippedLowConfidence = 0
            var baseSkippedLowConfidence = 0
            var enhancedSkippedLowConfidence = 0

            for ((classIndex, uris) in batchImages) {
                for (uri in uris) {
                    try {
                        val bitmap = decodeAndCorrectOrientation(uri)
                        if (bitmap != null) {
                            processedCount++
                            // Stage 1: Gatekeeper binary filter (tau = 0.70)
                            val gatekeeperResult = classifier.checkGatekeeper(bitmap)
                            if (!gatekeeperResult.isCassava) {
                                skippedNonCassava++
                                continue
                            }

                            // Stage 2: Disease classification
                            val result = classifier.classifyImage(bitmap, gatekeeperResult)

                            if (!runIndependent) {
                                // Mode 1: Paired evaluation (both models evaluated on images accepted by Enhanced model)
                                if (result.enhancedResult.confidence < batchConfidenceThreshold) {
                                    pairedSkippedLowConfidence++
                                    continue
                                }
                                basePredictions.add(result.baseResult.index)
                                enhancedPredictions.add(result.enhancedResult.index)
                                pairedGroundTruths.add(classIndex)
                            } else {
                                // Mode 2: Independent evaluation (each model evaluated on its own accepted images)
                                if (result.baseResult.confidence >= batchConfidenceThreshold) {
                                    basePredictions.add(result.baseResult.index)
                                    baseGroundTruths.add(classIndex)
                                } else {
                                    baseSkippedLowConfidence++
                                }

                                if (result.enhancedResult.confidence >= batchConfidenceThreshold) {
                                    enhancedPredictions.add(result.enhancedResult.index)
                                    enhancedGroundTruths.add(classIndex)
                                } else {
                                    enhancedSkippedLowConfidence++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val thresholdPercent = (batchConfidenceThreshold * 100).toInt()

            if (!runIndependent) {
                if (enhancedPredictions.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "All $totalImages image(s) were excluded from evaluation (Non-cassava: $skippedNonCassava, Low-confidence < $thresholdPercent%: $pairedSkippedLowConfidence).",
                            Toast.LENGTH_LONG
                        ).show()
                        btnRunBatchTest?.isEnabled = true
                        progressBatch?.visibility = View.GONE
                    }
                    return@execute
                }

                val evaluatedCount = enhancedPredictions.size
                val coveragePct = (evaluatedCount.toDouble() / totalImages.coerceAtLeast(1)) * 100.0
                val baseMetrics = calculateMetrics(basePredictions, pairedGroundTruths, 5, evaluatedCount, coveragePct)
                val enhancedMetrics = calculateMetrics(enhancedPredictions, pairedGroundTruths, 5, evaluatedCount, coveragePct)
                val currentThreshold = batchConfidenceThreshold

                val activeClassIndices = pairedGroundTruths.distinct().filter { it in 0 until 5 }.sorted()
                val activeClassNames = activeClassIndices.map { index ->
                    when (index) {
                        0 -> "CBB"
                        1 -> "CBSD"
                        2 -> "CGM"
                        3 -> "CMD"
                        4 -> "Healthy"
                        else -> "Class $index"
                    }
                }
                val activeInfo = if (activeClassIndices.size == 5) {
                    "All 5 Classes (CBB, CBSD, CGM, CMD, Healthy)"
                } else {
                    "${activeClassIndices.size}/5 Classes (${activeClassNames.joinToString(", ")})"
                }

                runOnUiThread {
                    displayBatchResults(
                        base = baseMetrics,
                        enhanced = enhancedMetrics,
                        total = totalImages,
                        evaluated = evaluatedCount,
                        skippedNonCassava = skippedNonCassava,
                        skippedLowConfidence = pairedSkippedLowConfidence,
                        confidenceThreshold = currentThreshold,
                        activeClassesInfo = activeInfo
                    )
                    saveBatchHistory(totalImages, baseMetrics, enhancedMetrics, activeInfo, "Mode 1 (Paired)")
                    btnRunBatchTest?.isEnabled = true
                    progressBatch?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Batch test finished ($evaluatedCount/$totalImages evaluated)", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Mode 2: Independent
                if (basePredictions.isEmpty() && enhancedPredictions.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "All $totalImages image(s) were excluded from both models (Non-cassava: $skippedNonCassava, Base low-conf: $baseSkippedLowConfidence, Enh low-conf: $enhancedSkippedLowConfidence).",
                            Toast.LENGTH_LONG
                        ).show()
                        btnRunBatchTest?.isEnabled = true
                        progressBatch?.visibility = View.GONE
                    }
                    return@execute
                }

                val baseCoverageCount = basePredictions.size
                val baseCoveragePercent = (baseCoverageCount.toDouble() / totalImages.coerceAtLeast(1)) * 100.0
                val enhancedCoverageCount = enhancedPredictions.size
                val enhancedCoveragePercent = (enhancedCoverageCount.toDouble() / totalImages.coerceAtLeast(1)) * 100.0

                val baseMetrics = calculateMetrics(basePredictions, baseGroundTruths, 5, baseCoverageCount, baseCoveragePercent)
                val enhancedMetrics = calculateMetrics(enhancedPredictions, enhancedGroundTruths, 5, enhancedCoverageCount, enhancedCoveragePercent)
                val currentThreshold = batchConfidenceThreshold

                val allGroundTruths = (baseGroundTruths + enhancedGroundTruths).distinct().filter { it in 0 until 5 }.sorted()
                val activeClassNames = allGroundTruths.map { index ->
                    when (index) {
                        0 -> "CBB"
                        1 -> "CBSD"
                        2 -> "CGM"
                        3 -> "CMD"
                        4 -> "Healthy"
                        else -> "Class $index"
                    }
                }
                val activeInfo = if (allGroundTruths.size == 5) {
                    "All 5 Classes (CBB, CBSD, CGM, CMD, Healthy)"
                } else {
                    "${allGroundTruths.size}/5 Classes (${activeClassNames.joinToString(", ")})"
                }

                runOnUiThread {
                    displayBatchResultsIndependent(
                        base = baseMetrics,
                        enhanced = enhancedMetrics,
                        total = totalImages,
                        skippedNonCassava = skippedNonCassava,
                        baseSkippedLowConf = baseSkippedLowConfidence,
                        enhancedSkippedLowConf = enhancedSkippedLowConfidence,
                        confidenceThreshold = currentThreshold,
                        activeClassesInfo = activeInfo
                    )
                    saveBatchHistory(totalImages, baseMetrics, enhancedMetrics, activeInfo, "Mode 2 (Independent)")
                    btnRunBatchTest?.isEnabled = true
                    progressBatch?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Batch test finished ($totalImages images evaluated)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        /**
         * Calculates multi-class confusion matrix and comprehensive evaluation metrics:
         *
         * 1. Overall Accuracy = Correct Predictions / Total Predictions
         * 2. Per-Class One-vs-Rest Confusion Matrix:
         *    - TP_c: Predicted = c AND Truth = c
         *    - FP_c: Predicted = c AND Truth != c
         *    - FN_c: Predicted != c AND Truth = c
         *    - TN_c: Predicted != c AND Truth != c
         * 3. Macro Precision = (1 / K_active) * sum(TP_c / (TP_c + FP_c))
         * 4. Macro Recall (Sensitivity) = (1 / K_active) * sum(TP_c / (TP_c + FN_c))
         * 5. Macro F1 = (1 / K_active) * sum(2 * Prec_c * Rec_c / (Prec_c + Rec_c))
         * 6. True Negative Rate (Macro Specificity) = (1 / K_active) * sum(TN_c / (TN_c + FP_c))
         * 7. Matthews Correlation Coefficient (MCC):
         *    MCC_c = (TP*TN - FP*FN) / sqrt((TP+FP)*(TP+FN)*(TN+FP)*(TN+FN))
         *    Macro MCC = (1 / K_active) * sum(MCC_c)
         *
         * Active-Class Macro Averaging (Scikit-Learn standard):
         * When evaluating a batch dataset containing only a subset of classes (e.g. 2 of 5),
         * macro metrics are averaged over the represented classes (ground truth or predicted).
         * This avoids artificial zero-division penalties on untested classes while properly
         * penalizing false alarms.
         */
        internal fun calculateMetrics(
            predictions: List<Int>,
            groundTruths: List<Int>,
            numClasses: Int = 5,
            coverageCount: Int = predictions.size,
            coveragePercent: Double = 100.0
        ): Metrics {
            // Build 5x5 multi-class confusion matrix (Rows = Actual Ground Truth, Columns = Predicted Class)
            val cm = Array(numClasses) { IntArray(numClasses) }
            for (i in predictions.indices) {
                val p = predictions[i]
                val t = groundTruths[i]
                if (t in 0 until numClasses && p in 0 until numClasses) {
                    cm[t][p]++
                }
            }
            val matrixList = cm.map { it.toList() }

            if (predictions.isEmpty()) return Metrics(
                accuracy = 0.0,
                precision = 0.0,
                recall = 0.0,
                tpr = 0.0,
                tnr = 0.0,
                f1Score = 0.0,
                macroF1 = 0.0,
                mcc = 0.0,
                confusionMatrix = matrixList,
                coverageCount = coverageCount,
                coveragePercent = coveragePercent
            )

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

            // Identify active classes strictly from the uploaded ground truths
            val activeClasses = groundTruths.distinct().filter { it in 0 until numClasses }.sorted()
            val activeK = activeClasses.size.coerceAtLeast(1)

            var macroPrecision = 0.0
            var macroRecall = 0.0
            var macroF1 = 0.0
            var macroTnr = 0.0
            var macroMcc = 0.0

            for (c in activeClasses) {
                val precision = if (tp[c] + fp[c] > 0) tp[c] / (tp[c] + fp[c]) else 0.0
                val recall = if (tp[c] + fn[c] > 0) tp[c] / (tp[c] + fn[c]) else 0.0
                val f1 = if (precision + recall > 0) 2.0 * precision * recall / (precision + recall) else 0.0
                val tnr = if (tn[c] + fp[c] > 0) {
                    tn[c] / (tn[c] + fp[c])
                } else if (fp[c] == 0.0) {
                    1.0
                } else {
                    0.0
                }

                val denominator = Math.sqrt((tp[c] + fp[c]) * (tp[c] + fn[c]) * (tn[c] + fp[c]) * (tn[c] + fn[c]))
                val mcc = if (denominator > 0) {
                    (tp[c] * tn[c] - fp[c] * fn[c]) / denominator
                } else if (tp[c] > 0 && fp[c] == 0.0 && fn[c] == 0.0) {
                    1.0
                } else {
                    0.0
                }

                macroPrecision += precision
                macroRecall += recall
                macroF1 += f1
                macroTnr += tnr
                macroMcc += mcc
            }

            macroPrecision /= activeK
            macroRecall /= activeK
            macroF1 /= activeK
            macroTnr /= activeK
            macroMcc /= activeK

            return Metrics(
                accuracy = accuracy,
                precision = macroPrecision,
                recall = macroRecall,
                tpr = macroRecall,
                tnr = macroTnr,
                f1Score = accuracy,
                macroF1 = macroF1,
                mcc = macroMcc,
                confusionMatrix = matrixList,
                coverageCount = coverageCount,
                coveragePercent = coveragePercent
            )
        }

        /**
         * Formats a 5x5 confusion matrix into a clean, monospace-aligned ASCII table.
         */
        internal fun formatConfusionMatrix(matrix: List<List<Int>>): String {
            if (matrix.isEmpty() || matrix.all { row -> row.all { it == 0 } }) return "  (No predictions evaluated)"
            val colLabels = listOf("CBB", "CBS", "CGM", "CMD", "HLT")
            val sb = StringBuilder()
            sb.append("Pred ->   CBB   CBS   CGM   CMD   HLT\n")
            sb.append("Truth -------------------------------\n")
            for (r in 0 until 5) {
                val rowLabel = colLabels.getOrElse(r) { "C$r" }.padEnd(5)
                sb.append(rowLabel).append("  ")
                for (c in 0 until 5) {
                    val count = if (r < matrix.size && c < matrix[r].size) matrix[r][c] else 0
                    sb.append(String.format(Locale.US, "%5d ", count))
                }
                if (r < 4) sb.append("\n")
            }
            return sb.toString()
        }
    }

    private fun displayBatchResults(
        base: Metrics,
        enhanced: Metrics,
        total: Int,
        evaluated: Int,
        skippedNonCassava: Int,
        skippedLowConfidence: Int,
        confidenceThreshold: Float,
        activeClassesInfo: String
    ) {
        layoutBatchResults?.visibility = View.VISIBLE
        val thresholdPercent = (confidenceThreshold * 100).toInt()
        val covStr = "$evaluated / $total (${formatPercent(evaluated.toDouble() / total.coerceAtLeast(1))})"

        val resultText = """
            ========================================
            EVALUATION: Mode 1 (Paired Evaluation)
            Same pool accepted by Enhanced Model
            ========================================
            BATCH SUMMARY:
            • Total Images Uploaded: $total
            • Evaluated Images: $covStr
            • Active Taxonomy Classes: $activeClassesInfo
            • Excluded (Not Cassava Leaf < 70%): $skippedNonCassava
            • Excluded (Low Confidence < $thresholdPercent%): $skippedLowConfidence
            
            BASE MODEL METRICS:
            Coverage: $covStr
            Accuracy: ${formatPercent(base.accuracy)}
            Precision: ${formatPercent(base.precision)}
            Recall: ${formatPercent(base.recall)}
            True Positive Rate: ${formatPercent(base.tpr)}
            True Negative Rate: ${formatPercent(base.tnr)}
            F1 Score: ${formatPercent(base.f1Score)}
            Macro F1: ${formatPercent(base.macroF1)}
            MCC: ${formatDouble(base.mcc)}

            ENHANCED MODEL METRICS:
            Coverage: $covStr
            Accuracy: ${formatPercent(enhanced.accuracy)}
            Precision: ${formatPercent(enhanced.precision)}
            Recall: ${formatPercent(enhanced.recall)}
            True Positive Rate: ${formatPercent(enhanced.tpr)}
            True Negative Rate: ${formatPercent(enhanced.tnr)}
            F1 Score: ${formatPercent(enhanced.f1Score)}
            Macro F1: ${formatPercent(enhanced.macroF1)}
            MCC: ${formatDouble(enhanced.mcc)}
        """.trimIndent()

        txtBatchMetrics?.text = resultText
    }

    private fun displayBatchResultsIndependent(
        base: Metrics,
        enhanced: Metrics,
        total: Int,
        skippedNonCassava: Int,
        baseSkippedLowConf: Int,
        enhancedSkippedLowConf: Int,
        confidenceThreshold: Float,
        activeClassesInfo: String
    ) {
        layoutBatchResults?.visibility = View.VISIBLE
        val thresholdPercent = (confidenceThreshold * 100).toInt()
        val baseCovStr = "${base.coverageCount} / $total (${formatPercent(base.coveragePercent / 100.0)})"
        val enhCovStr = "${enhanced.coverageCount} / $total (${formatPercent(enhanced.coveragePercent / 100.0)})"

        val resultText = """
            ========================================
            EVALUATION: Mode 2 (Independent)
            Independent thresholding for each model
            ========================================
            BATCH SUMMARY:
            • Total Images Uploaded: $total
            • Excluded (Not Cassava Leaf < 70%): $skippedNonCassava
            • Confidence Threshold: $thresholdPercent%
            • Active Taxonomy Classes: $activeClassesInfo
            
            BASE MODEL EVALUATION:
            • Coverage: $baseCovStr
            • Excluded (Low Conf < $thresholdPercent%): $baseSkippedLowConf
            • Accuracy: ${formatPercent(base.accuracy)}
            • Precision: ${formatPercent(base.precision)}
            • Recall: ${formatPercent(base.recall)}
            • True Positive Rate: ${formatPercent(base.tpr)}
            • True Negative Rate: ${formatPercent(base.tnr)}
            • F1 Score: ${formatPercent(base.f1Score)}
            • Macro F1: ${formatPercent(base.macroF1)}
            • MCC: ${formatDouble(base.mcc)}

            ENHANCED MODEL EVALUATION:
            • Coverage: $enhCovStr
            • Excluded (Low Conf < $thresholdPercent%): $enhancedSkippedLowConf
            • Accuracy: ${formatPercent(enhanced.accuracy)}
            • Precision: ${formatPercent(enhanced.precision)}
            • Recall: ${formatPercent(enhanced.recall)}
            • True Positive Rate: ${formatPercent(enhanced.tpr)}
            • True Negative Rate: ${formatPercent(enhanced.tnr)}
            • F1 Score: ${formatPercent(enhanced.f1Score)}
            • Macro F1: ${formatPercent(enhanced.macroF1)}
            • MCC: ${formatDouble(enhanced.mcc)}
        """.trimIndent()

        txtBatchMetrics?.text = resultText
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value * 100)
    private fun formatDouble(value: Double): String = String.format(Locale.US, "%.3f", value)

    // =========================================================================
    // Batch-Test History Persistence
    // =========================================================================

    private fun saveBatchHistory(
        totalImages: Int,
        baseMetrics: Metrics,
        enhancedMetrics: Metrics,
        activeClassesInfo: String = "",
        evaluationMode: String = "Mode 1 (Paired)"
    ) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val item = BatchHistoryItem(
            id = UUID.randomUUID().toString(),
            date = dateStr,
            totalImages = totalImages,
            baseMetrics = baseMetrics,
            enhancedMetrics = enhancedMetrics,
            activeClassesInfo = activeClassesInfo,
            evaluationMode = evaluationMode
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
            obj.put("activeInfo", h.activeClassesInfo)
            obj.put("evalMode", h.evaluationMode)

            val bObj = JSONObject()
            bObj.put("acc", h.baseMetrics.accuracy)
            bObj.put("prec", h.baseMetrics.precision)
            bObj.put("rec", h.baseMetrics.recall)
            bObj.put("tpr", h.baseMetrics.tpr)
            bObj.put("f1", h.baseMetrics.f1Score)
            bObj.put("tnr", h.baseMetrics.tnr)
            bObj.put("macroF1", h.baseMetrics.macroF1)
            bObj.put("mcc", h.baseMetrics.mcc)
            bObj.put("covCount", h.baseMetrics.coverageCount)
            bObj.put("covPct", h.baseMetrics.coveragePercent)
            if (h.baseMetrics.confusionMatrix.isNotEmpty()) {
                val cmArray = JSONArray()
                for (row in h.baseMetrics.confusionMatrix) {
                    val rowArray = JSONArray()
                    for (cell in row) rowArray.put(cell)
                    cmArray.put(rowArray)
                }
                bObj.put("cm", cmArray)
            }
            obj.put("baseMetrics", bObj)

            val eObj = JSONObject()
            eObj.put("acc", h.enhancedMetrics.accuracy)
            eObj.put("prec", h.enhancedMetrics.precision)
            eObj.put("rec", h.enhancedMetrics.recall)
            eObj.put("tpr", h.enhancedMetrics.tpr)
            eObj.put("f1", h.enhancedMetrics.f1Score)
            eObj.put("tnr", h.enhancedMetrics.tnr)
            eObj.put("macroF1", h.enhancedMetrics.macroF1)
            eObj.put("mcc", h.enhancedMetrics.mcc)
            eObj.put("covCount", h.enhancedMetrics.coverageCount)
            eObj.put("covPct", h.enhancedMetrics.coveragePercent)
            if (h.enhancedMetrics.confusionMatrix.isNotEmpty()) {
                val cmArray = JSONArray()
                for (row in h.enhancedMetrics.confusionMatrix) {
                    val rowArray = JSONArray()
                    for (cell in row) rowArray.put(cell)
                    cmArray.put(rowArray)
                }
                eObj.put("cm", cmArray)
            }
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
                val baseCm = parseConfusionMatrix(bObj)
                val baseMetrics = Metrics(
                    accuracy = bObj.getDouble("acc"),
                    precision = bObj.getDouble("prec"),
                    recall = bObj.getDouble("rec"),
                    tpr = bObj.optDouble("tpr", bObj.getDouble("rec")),
                    tnr = bObj.getDouble("tnr"),
                    f1Score = bObj.getDouble("f1"),
                    macroF1 = bObj.optDouble("macroF1", bObj.getDouble("f1")),
                    mcc = bObj.getDouble("mcc"),
                    confusionMatrix = baseCm,
                    coverageCount = bObj.optInt("covCount", 0),
                    coveragePercent = bObj.optDouble("covPct", 0.0)
                )

                val eObj = obj.getJSONObject("enhancedMetrics")
                val enhCm = parseConfusionMatrix(eObj)
                val enhancedMetrics = Metrics(
                    accuracy = eObj.getDouble("acc"),
                    precision = eObj.getDouble("prec"),
                    recall = eObj.getDouble("rec"),
                    tpr = eObj.optDouble("tpr", eObj.getDouble("rec")),
                    tnr = eObj.getDouble("tnr"),
                    f1Score = eObj.getDouble("f1"),
                    macroF1 = eObj.optDouble("macroF1", eObj.getDouble("f1")),
                    mcc = eObj.getDouble("mcc"),
                    confusionMatrix = enhCm,
                    coverageCount = eObj.optInt("covCount", 0),
                    coveragePercent = eObj.optDouble("covPct", 0.0)
                )

                batchHistoryList.add(
                    BatchHistoryItem(
                        id = obj.getString("id"),
                        date = obj.getString("date"),
                        totalImages = obj.getInt("totalImages"),
                        baseMetrics = baseMetrics,
                        enhancedMetrics = enhancedMetrics,
                        activeClassesInfo = obj.optString("activeInfo", ""),
                        evaluationMode = obj.optString("evalMode", "Mode 1 (Paired)")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateBatchHistoryUI()
    }

    private fun parseConfusionMatrix(json: JSONObject): List<List<Int>> {
        val cmArray = json.optJSONArray("cm") ?: return emptyList()
        val matrix = mutableListOf<List<Int>>()
        for (r in 0 until cmArray.length()) {
            val rowArray = cmArray.getJSONArray(r)
            val row = mutableListOf<Int>()
            for (c in 0 until rowArray.length()) {
                row.add(rowArray.getInt(c))
            }
            matrix.add(row)
        }
        return matrix
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

    private fun showBatchHistoryItemDialog(item: BatchHistoryItem) {
        val classLine = if (item.activeClassesInfo.isNotEmpty()) "• Tested Classes: ${item.activeClassesInfo}\n" else ""
        val modeLine = "• Evaluation: ${item.evaluationMode}\n"
        val isIndep = item.evaluationMode.contains("Independent", ignoreCase = true)
        val baseCovLine = if (isIndep && item.baseMetrics.coverageCount > 0) "• Coverage: ${item.baseMetrics.coverageCount}/${item.totalImages} (${formatPercent(item.baseMetrics.coveragePercent / 100.0)})\n" else ""
        val enhCovLine = if (isIndep && item.enhancedMetrics.coverageCount > 0) "• Coverage: ${item.enhancedMetrics.coverageCount}/${item.totalImages} (${formatPercent(item.enhancedMetrics.coveragePercent / 100.0)})\n" else ""

        val details = """
            Batch Test Date: ${item.date}
            Total Images: ${item.totalImages}
            $modeLine$classLine
            BASE MODEL METRICS:
            $baseCovLine• Accuracy: ${formatPercent(item.baseMetrics.accuracy)}
            • Precision: ${formatPercent(item.baseMetrics.precision)}
            • Recall: ${formatPercent(item.baseMetrics.recall)}
            • True Positive Rate: ${formatPercent(item.baseMetrics.tpr)}
            • True Negative Rate: ${formatPercent(item.baseMetrics.tnr)}
            • F1 Score: ${formatPercent(item.baseMetrics.f1Score)}
            • Macro F1: ${formatPercent(item.baseMetrics.macroF1)}
            • MCC: ${formatDouble(item.baseMetrics.mcc)}

            ENHANCED MODEL METRICS:
            $enhCovLine• Accuracy: ${formatPercent(item.enhancedMetrics.accuracy)}
            • Precision: ${formatPercent(item.enhancedMetrics.precision)}
            • Recall: ${formatPercent(item.enhancedMetrics.recall)}
            • True Positive Rate: ${formatPercent(item.enhancedMetrics.tpr)}
            • True Negative Rate: ${formatPercent(item.enhancedMetrics.tnr)}
            • F1 Score: ${formatPercent(item.enhancedMetrics.f1Score)}
            • Macro F1: ${formatPercent(item.enhancedMetrics.macroF1)}
            • MCC: ${formatDouble(item.enhancedMetrics.mcc)}
        """.trimIndent()

        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 28)
        }

        val textView = TextView(this).apply {
            text = details
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextColor(android.graphics.Color.DKGRAY)
        }
        linearLayout.addView(textView)

        val scrollView = NestedScrollView(this).apply {
            addView(linearLayout)
        }

        AlertDialog.Builder(this)
            .setTitle("Batch Test Record")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()
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
        val txtMobile = dialogView.findViewById<TextView>(R.id.metrics_mobile)
        val txtStatistical = dialogView.findViewById<TextView>(R.id.metrics_statistical)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close_metrics)

        txtTraining.text = """
            |• Training Time:
            |  Base: 31m 07s (±4m 37s)
            |  D-CLAHE: 1h 37m 10s (±6m 40s)
            |
            |• Memory Usage:
            |  Base: RAM: 3.08 GB | VRAM: 0.91 GB
            |  D-CLAHE: RAM: 4.48 GB | VRAM: 1.33 GB
            |
            |• Weight Discrepancy (L2 Norm):
            |  Base: 1847.54 ± 0.62
            |  D-CLAHE: 1845.65 ± 0.17
        """.trimMargin()

        txtClassification.text = """
            |• Accuracy:
            |  Base: 80.06% ± 0.71%
            |  D-CLAHE: 81.72% ± 0.65% (Δ = +1.67 pp)
            |
            |• Precision:
            |  Base: 80.88% ± 1.09%
            |  D-CLAHE: 82.73% ± 0.94% (Δ = +1.85 pp)
            |
            |• Recall:
            |  Base: 79.51% ± 1.01%
            |  D-CLAHE: 81.07% ± 0.51% (Δ = +1.56 pp)
            |
            |• F1 Score (Weighted):
            |  Base: 79.93% ± 0.65%
            |  D-CLAHE: 81.69% ± 0.61% (Δ = +1.76 pp)
            |
            |• True Positive Rate (Sensitivity):
            |  Base: 79.51% ± 1.01%
            |  D-CLAHE: 81.07% ± 0.51% (Δ = +1.56 pp)
            |
            |• True Negative Rate (Specificity):
            |  Base: 94.95% ± 0.18%
            |  D-CLAHE: 95.34% ± 0.15% (Δ = +0.38 pp)
            |
            |• Macro F1 Score:
            |  Base: 79.61% ± 0.86%
            |  D-CLAHE: 81.54% ± 0.61% (Δ = +1.92 pp)
            |
            |• Matthews Correlation (MCC):
            |  Base: 0.7510 ± 0.0103
            |  D-CLAHE: 0.7711 ± 0.0079 (Δ = +0.0201)
        """.trimMargin()

        txtInference.text = """
            |• Inference Latency:
            |  Base: 9.07 ms (±0.90 ms)
            |  D-CLAHE: 12.94 ms (±0.92 ms)
            |
            |• Multiply-Accumulate (MACs):
            |  Base: 56,579,931
            |  D-CLAHE: 98,483,212
            |
            |• Model Size (Parameters):
            |  Base: 944,885
            |  D-CLAHE: 968,599
        """.trimMargin()

        txtMobile.text = """
            |• Accuracy:
            |  Base: 49.86% ± 10.42%
            |  Enhanced: 60.42% ± 6.85% (Δ = +10.56 pp)
            |  Impact: +10.56% avg field boost; variance cut by 34%
            |
            |• Matthews Correlation (MCC):
            |  Base: 0.4130 ± 0.0359
            |  Enhanced: 0.5028 ± 0.0575 (Δ = +0.0898)
            |  Impact: 100% win rate (5/5 seeds); p = 0.0221
            |
            |• Precision:
            |  Base: 81.92% ± 1.79%
            |  Enhanced: 85.92% ± 3.31% (Δ = +4.00 pp)
            |  Impact: 100% win rate (5/5 seeds); peak 90.5%
            |
            |• Recall (Sensitivity):
            |  Base: 52.06% ± 7.04%
            |  Enhanced: 59.60% ± 7.10% (Δ = +7.54 pp)
            |  Impact: Significant boost in detecting active blight
            |
            |• Specificity (TNR):
            |  Base: 85.24% ± 4.62%
            |  Enhanced: 87.62% ± 3.87% (Δ = +2.38 pp)
            |  Impact: Consistently suppresses false disease alarms
            |
            |• Macro F1 Score:
            |  Base: 58.88% ± 7.05%
            |  Enhanced: 68.92% ± 5.22% (Δ = +10.04 pp)
            |  Impact: Balanced class recognition across categories
        """.trimMargin()

        txtStatistical.text = """
            |• Test Set Domain:
            |  Base MCC: 0.7510 ± 0.0103
            |  Enhanced MCC: 0.7711 ± 0.0079
            |  t = 3.4529 | df = 7.50 | p = 0.0096 | d = 2.18
            |  Conclusion: Highly Significant (p < 0.01)
            |
            |• Mobile App Test Domain:
            |  Base MCC: 0.4130 ± 0.0359
            |  Enhanced MCC: 0.5028 ± 0.0575
            |  t = 2.9638 | df = 6.71 | p = 0.0221 | d = 1.87
            |  Conclusion: Statistically Significant (p < 0.05)
        """.trimMargin()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}