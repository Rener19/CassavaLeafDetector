package com.example.cassavaleafdetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

class MainActivity : AppCompatActivity() {

    private lateinit var classifier: CassavaClassifier
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    // UI elements
    private lateinit var cardCamera: View
    private lateinit var cardGallery: View
    private lateinit var cardResult: View
    private lateinit var imgScanned: ImageView
    private lateinit var imgEnhanced: ImageView
    private lateinit var txtBaseLabel: TextView
    private lateinit var txtBaseConfidence: TextView
    private lateinit var progressBaseConfidence: ProgressBar
    private lateinit var txtBaseTime: TextView
    private lateinit var txtBaseMetrics: TextView
    
    private lateinit var txtEnhancedLabel: TextView
    private lateinit var txtEnhancedConfidence: TextView
    private lateinit var progressEnhancedConfidence: ProgressBar
    private lateinit var txtEnhancedTime: TextView
    private lateinit var txtEnhancedMetrics: TextView
    
    private lateinit var txtDiseaseDesc: TextView
    private lateinit var txtDiseaseTreatment: TextView
    private lateinit var cameraContainer: View
    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnCloseCamera: ImageButton
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var txtNoHistory: TextView
    private lateinit var btnClearHistory: Button

    private lateinit var historyAdapter: HistoryAdapter
    private val historyList = mutableListOf<HistoryItem>()

    // Gallery Picker launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    // Save bitmap to cache and run classification
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

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCameraContainer()
        } else {
            Toast.makeText(this, "Camera permission is required to scan leaves", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize classifier
        classifier = CassavaClassifier(this)
        
        // Initialize single thread executor for camera operations
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Bind Views
        initViews()

        // Setup RecyclerView
        setupRecyclerView()

        // Setup Click Listeners
        setupClickListeners()

        // Load saved history
        loadSavedHistory()
    }

    private fun initViews() {
        cardCamera = findViewById(R.id.card_camera)
        cardGallery = findViewById(R.id.card_gallery)
        cardResult = findViewById(R.id.card_result)
        imgScanned = findViewById(R.id.img_scanned)
        imgEnhanced = findViewById(R.id.img_enhanced)
        txtBaseLabel = findViewById(R.id.txt_base_label)
        txtBaseConfidence = findViewById(R.id.txt_base_confidence)
        progressBaseConfidence = findViewById(R.id.progress_base_confidence)
        txtBaseTime = findViewById(R.id.txt_base_time)
        txtBaseMetrics = findViewById(R.id.txt_base_metrics)
        
        txtEnhancedLabel = findViewById(R.id.txt_enhanced_label)
        txtEnhancedConfidence = findViewById(R.id.txt_enhanced_confidence)
        progressEnhancedConfidence = findViewById(R.id.progress_enhanced_confidence)
        txtEnhancedTime = findViewById(R.id.txt_enhanced_time)
        txtEnhancedMetrics = findViewById(R.id.txt_enhanced_metrics)
        txtDiseaseDesc = findViewById(R.id.txt_disease_desc)
        txtDiseaseTreatment = findViewById(R.id.txt_disease_treatment)
        cameraContainer = findViewById(R.id.camera_container)
        previewView = findViewById(R.id.preview_view)
        btnCapture = findViewById(R.id.btn_capture)
        btnCloseCamera = findViewById(R.id.btn_close_camera)
        recyclerHistory = findViewById(R.id.recycler_history)
        txtNoHistory = findViewById(R.id.txt_no_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)
        
        findViewById<TextView>(R.id.txt_model_badge).text = 
            if (classifier.isBaseModelLoaded && classifier.isEnhancedModelLoaded) "TFLite Models" else "Smart Diagnostics"
    }

    private fun setupRecyclerView() {
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(historyList) { historyItem ->
            showDiseaseDetailDialog(
                historyItem.label,
                historyItem.index,
                historyItem.imagePath
            )
        }
        recyclerHistory.adapter = historyAdapter
    }

    private fun setupClickListeners() {
        cardCamera.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        cardGallery.setOnClickListener {
            // Close camera if open
            closeCameraContainer()
            galleryLauncher.launch("image/*")
        }

        btnCapture.setOnClickListener {
            takePhoto()
        }

        btnCloseCamera.setOnClickListener {
            closeCameraContainer()
        }

        btnClearHistory.setOnClickListener {
            clearHistory()
        }

        // Library items click listeners
        findViewById<LinearLayout>(R.id.lib_cbb).setOnClickListener {
            showDiseaseDetailDialog(CassavaClassifier.LABELS[0], 0, "")
        }
        findViewById<LinearLayout>(R.id.lib_cbsd).setOnClickListener {
            showDiseaseDetailDialog(CassavaClassifier.LABELS[1], 1, "")
        }
        findViewById<LinearLayout>(R.id.lib_cgm).setOnClickListener {
            showDiseaseDetailDialog(CassavaClassifier.LABELS[2], 2, "")
        }
        findViewById<LinearLayout>(R.id.lib_cmd).setOnClickListener {
            showDiseaseDetailDialog(CassavaClassifier.LABELS[3], 3, "")
        }
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCameraContainer()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCameraContainer() {
        cardResult.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE
        startCamera()
    }

    private fun closeCameraContainer() {
        cameraContainer.visibility = View.GONE
        // Stop CameraX bindings by unbinding all
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera bind failed: ${exc.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            cacheDir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        btnCapture.isEnabled = false

        imageCapture.takePicture(
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
                    
                    // Decode captured image
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap != null) {
                        processDiagnosis(bitmap, photoFile.absolutePath)
                    } else {
                        Toast.makeText(baseContext, "Failed to read captured image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun processDiagnosis(bitmap: Bitmap, imagePath: String) {
        val result = classifier.classifyImage(bitmap)

        // Show UI results
        cardResult.visibility = View.VISIBLE
        imgScanned.setImageBitmap(bitmap)
        
        if (result.enhancedBitmap != null) {
            imgEnhanced.setImageBitmap(result.enhancedBitmap)
        } else {
            imgEnhanced.setImageBitmap(bitmap)
        }
        
        // Base Model Results
        txtBaseLabel.text = result.baseResult.label
        val baseConfPercent = result.baseResult.confidence * 100
        txtBaseConfidence.text = String.format("%.0f%% Conf.", baseConfPercent)
        progressBaseConfidence.progress = baseConfPercent.toInt()
        txtBaseTime.text = "${result.baseResult.inferenceTimeMs}ms"
        txtBaseMetrics.text = "Shape: ${result.baseResult.inputShape} | Type: ${result.baseResult.dataType} | Size: ${result.baseResult.modelSizeKb}KB"
        
        val baseLabelColor = when (result.baseResult.index) {
            4 -> R.color.color_healthy
            0 -> R.color.color_cbb
            1 -> R.color.color_cbsd
            2 -> R.color.color_cgm
            else -> R.color.color_cmd
        }
        txtBaseConfidence.setTextColor(getColor(baseLabelColor))
        progressBaseConfidence.progressTintList = ContextCompat.getColorStateList(this, baseLabelColor)

        // Enhanced Model Results
        txtEnhancedLabel.text = result.enhancedResult.label
        val enhancedConfPercent = result.enhancedResult.confidence * 100
        txtEnhancedConfidence.text = String.format("%.0f%% Conf.", enhancedConfPercent)
        progressEnhancedConfidence.progress = enhancedConfPercent.toInt()
        txtEnhancedTime.text = "${result.enhancedResult.inferenceTimeMs}ms"
        txtEnhancedMetrics.text = "Shape: ${result.enhancedResult.inputShape} | Type: ${result.enhancedResult.dataType} | Size: ${result.enhancedResult.modelSizeKb}KB"
        
        val enhancedLabelColor = when (result.enhancedResult.index) {
            4 -> R.color.color_healthy
            0 -> R.color.color_cbb
            1 -> R.color.color_cbsd
            2 -> R.color.color_cgm
            else -> R.color.color_cmd
        }
        txtEnhancedConfidence.setTextColor(getColor(enhancedLabelColor))
        progressEnhancedConfidence.progressTintList = ContextCompat.getColorStateList(this, enhancedLabelColor)

        txtDiseaseDesc.text = result.description
        txtDiseaseTreatment.text = result.treatment

        // Add to history (using enhanced model as primary result for logging)
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
        
        // Scroll to top of nested scroll view so results are visible
        findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)?.post {
            findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)?.smoothScrollTo(0, 0)
        }
    }

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

    private fun showDiseaseDetailDialog(label: String, index: Int, imagePath: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_disease_detail, null)
        builder.setView(dialogView)

        val txtTitle: TextView = dialogView.findViewById(R.id.dialog_title)
        val txtDesc: TextView = dialogView.findViewById(R.id.dialog_desc)
        val txtTreatment: TextView = dialogView.findViewById(R.id.dialog_treatment)
        val imgLeaf: ImageView = dialogView.findViewById(R.id.dialog_img)
        val btnClose: Button = dialogView.findViewById(R.id.dialog_btn_close)

        txtTitle.text = label
        txtDesc.text = CassavaClassifier.DESCRIPTIONS[index]
        txtTreatment.text = CassavaClassifier.TREATMENTS[index]

        // Color theme the dialog title based on disease
        val labelColor = when (index) {
            4 -> R.color.color_healthy
            0 -> R.color.color_cbb
            1 -> R.color.color_cbsd
            2 -> R.color.color_cgm
            else -> R.color.color_cmd
        }
        txtTitle.setTextColor(getColor(labelColor))

        if (imagePath.isNotEmpty()) {
            val file = File(imagePath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    imgLeaf.setImageBitmap(bitmap)
                    imgLeaf.visibility = View.VISIBLE
                } else {
                    imgLeaf.visibility = View.GONE
                }
            } else {
                imgLeaf.visibility = View.GONE
            }
        } else {
            imgLeaf.visibility = View.GONE
        }

        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnClose.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

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

    private fun clearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to clear all diagnostic history?")
            .setPositiveButton("Yes") { _, _ ->
                // Clean up cached images
                for (item in historyList) {
                    if (item.imagePath.isNotEmpty()) {
                        val file = File(item.imagePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }
                historyList.clear()
                saveHistoryToSharedPrefs()
                updateHistoryUI()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
