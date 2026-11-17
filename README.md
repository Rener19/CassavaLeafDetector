# Cassava Leaf Health Detector 🌿📱

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2036-green.svg)](https://developer.android.com)
[![TensorFlow Lite](https://img.shields.io/badge/TFLite-v2.17.0%20(LiteRT)-orange.svg)](https://www.tensorflow.org/lite)
[![CameraX](https://img.shields.io/badge/CameraX-1.4.0-blue.svg)](https://developer.android.com/training/camerax)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-lightgrey.svg)]()

> **On-Device Edge AI Diagnosis & Dual-Model Comparative Benchmarking for Cassava Leaf Pathologies**  
> Developed as part of an advanced undergraduate research thesis evaluating **Dynamic Contrast-Limited Adaptive Histogram Equalization (D-CLAHE)** and **Layerwise Progressive Fine-Tuning** applied to MobileNetV3-Small architectures.

---

## 📌 Overview

Cassava (*Manihot esculenta*) is a staple root crop feeding over 800 million people across Sub-Saharan Africa, Latin America, and Southeast Asia. However, cassava production is severely threatened by viral and bacterial diseases that cause catastrophic yield loss when left undetected.

The **Cassava Leaf Health Detector** is a production-grade, offline-first Android application designed to provide instantaneous, lab-grade disease diagnoses directly in the field. It operates fully on-device without requiring internet connectivity, featuring a **Two-Stage Cascade Inference Pipeline** that filters non-cassava images and compares a baseline edge vision model against an enhanced D-CLAHE neural network in real-time.

---

## 🏗️ System Architecture & Inference Pipeline

The application incorporates a two-stage sequential cascade pipeline designed to eliminate false positives from off-target imagery while maximizing diagnostic precision.

```
+-------------------------------------------------------------------------------+
|                             IMAGE INPUT SOURCE                                |
|                   CameraX Live Viewfinder / Gallery Picker                    |
+-------------------------------------------------------------------------------+
                                        |
                                        v
+-------------------------------------------------------------------------------+
|                    STAGE 1: BINARY CASSAVA GATEKEEPER                         |
|   MobileNetV3-Small Binary Validator (Model: cassava_gatekeeper.tflite)       |
|               Input: 224x224x3 RGB  |  Output: [P(Leaf), P(Non-Leaf)]         |
+-------------------------------------------------------------------------------+
                                        |
                      [Is Cassava Leaf >= 70% Confidence?]
                                     /      \
                             YES    /        \   NO (< 70%)
                                   /          \
                                  v            v
+----------------------------------------+   +----------------------------------+
|      STAGE 2: DUAL-MODEL DIAGNOSIS     |   |         REJECTION CARD           |
|                                        |   |  "Not a Cassava Leaf"            |
|  [Model A: Baseline MobileNetV3-Small] |   |  Inference safely halted.        |
|  * Input: Raw 224x224x3 RGB            |   +----------------------------------+
|  * Output: 5 Disease Logits (Softmax)  |
|                                        |
|  [Model B: D-CLAHE Enhanced Network]   |
|  * Preprocessing: Dynamic CLAHE        |
|  * Input: Enhanced 224x224x3 RGB       |
|  * Output: 5 Disease Logits (Softmax)  |
+----------------------------------------+
                    |
                    v
+-------------------------------------------------------------------------------+
|                     DIAGNOSTIC DISPLAY & ACTIVE METRICS                       |
|   * Dual Probability Distributions & Confidence Gauges                        |
|   * Real-Time Apples-to-Apples D-CLAHE Visualizer (224x224)                   |
|   * Agronomic Pathology Information & Treatment Recommendations               |
|   * Single & Batch Test Logging with SharedPreferences Persistence            |
+-------------------------------------------------------------------------------+
```

---

## 🎯 Target Cassava Pathologies

The application diagnoses all 4 primary cassava diseases plus healthy foliage based on the Kaggle Cassava Leaf Disease classification standard:

| Class Index | Disease Name | Pathogen Type | Primary Visual Symptoms |
| :---: | :--- | :--- | :--- |
| **0** | **Cassava Bacterial Blight (CBB)** | *Xanthomonas axonopodis* | Water-soaked angular spots, leaf wilting, gum exudation |
| **1** | **Cassava Brown Streak Disease (CBSD)** | *Ipomoea virus* (Potyviridae) | Feathery chlorosis along secondary veins, root necrosis |
| **2** | **Cassava Green Mottle (CGM)** | *Cassava green mottle nepovirus* | Mosaic mottling, leaf curling, severely stunted young leaves |
| **3** | **Cassava Mosaic Disease (CMD)** | *Begomovirus* (Geminiviridae) | Severe chlorotic mosaic patterns, leaf deformation, puckering |
| **4** | **Healthy Foliage** | Uninfected | Vibrant green pigmentation, uniform laminae, intact veins |

---

## ⚡ Key Engineering Features

### 1. Two-Stage Cascade Inference Pipeline
* **Stage 1 (Gatekeeper)**: Evaluates image validity using a specialized binary classification model (`cassava_gatekeeper.tflite`). If an image is not a cassava leaf ($\text{Confidence} < 70\%$), inference halts immediately, preventing misleading disease diagnoses on random objects or unrelated foliage.
* **Stage 2 (Dual Model)**: Runs concurrent inference across **Baseline MobileNetV3-Small** (`model_base.tflite`) and **D-CLAHE Enhanced MobileNetV3-Small** (`model_enhanced.tflite`).

### 2. Real-Time D-CLAHE Image Visualizer
* Generates an in-memory normalized $224 \times 224$ visual preview comparing raw camera captures side-by-side with the D-CLAHE enhanced tensor output via `model_visualizer.tflite`.
* Provides immediate visual explanation of local contrast enhancement, edge sharpening, and vein illumination performed prior to classification.

### 3. Configurable Confidence Threshold Sliders
* Dynamic threshold sliders ($0\% \text{ to } 100\%$) for both **Single Scan** and **Batch Testing**.
* Predictions below the chosen threshold are safely flagged as low-confidence and excluded from evaluation.

### 4. Advanced Batch Testing Engine
* **Multi-Image Batch Upload**: Upload folders of images across all 5 ground truth categories simultaneously.
* **Persistent Upload State**: Uploaded images are retained after batch runs, enabling instant re-testing with different confidence thresholds without re-uploading.
* **Per-Class Clear Buttons**: Individual clear controls for each disease class plus a global "Clear All" confirmation dialog.
* **Two Evaluation Modes**:
  * **Mode 1 (Paired Evaluation)**: Evaluates both models strictly on the identical image subset accepted by the Enhanced model.
  * **Mode 2 (Independent Evaluation)**: Evaluates both models independently based on each model's confidence threshold and reports comparative coverage percentages.
* **Active-Class Macro Evaluation (Scikit-Learn Standard)**: Automatically detects the subset of uploaded ground truth classes ($K_{\text{active}}$) to compute unbiased Macro Precision, Macro Recall (TPR), Macro Specificity (TNR), Macro F1, and Matthews Correlation Coefficient (MCC).

### 5. In-App Thesis Statistical Benchmarks Dialog
* Access comprehensive experimental evaluation metrics directly in the app:
  * 5-Seed Overall Performance (Mean $\pm$ Std) for Training Time, Resource Usage (RAM/VRAM), and Weight Discrepancy ($L_2$ Norm).
  * Inference latency, Multiply-Accumulate operations (MACs), and parameter counts.
  * Welch's t-test statistical significance tests with Cohen's $d$ effect sizes ($p < 0.01$).

### 6. Edge Architecture & Lightweight Persistence
* **No Cloud Dependency**: Inference runs 100% locally using Android LiteRT (TensorFlow Lite Support 0.4.4 / TFLite 2.17.0).
* **Lightweight Storage**: Scan history and batch benchmark runs are persisted locally using native `org.json` serialization in `SharedPreferences`.

---

## 📊 Research Benchmarks & Empirical Findings

### 1. Test Set Evaluation (5-Seed Overall Performance Mean ± Std)

| Metric | Baseline MobileNetV3-Small | D-CLAHE Enhanced MobileNetV3-Small | Difference ($\Delta$) | Statistical Significance |
| :--- | :---: | :---: | :---: | :---: |
| **Accuracy** | $80.06\% \pm 0.71\%$ | **$81.72\% \pm 0.65\%$** | $+1.67\text{ pp}$ | $p < 0.01$ |
| **Precision (Macro)** | $80.88\% \pm 1.09\%$ | **$82.73\% \pm 0.94\%$** | $+1.85\text{ pp}$ | — |
| **Recall / TPR (Macro)** | $79.51\% \pm 1.01\%$ | **$81.07\% \pm 0.51\%$** | $+1.56\text{ pp}$ | — |
| **Specificity / TNR** | $94.95\% \pm 0.18\%$ | **$95.34\% \pm 0.15\%$** | $+0.38\text{ pp}$ | — |
| **Macro F1-Score** | $79.61\% \pm 0.86\%$ | **$81.54\% \pm 0.61\%$** | $+1.92\text{ pp}$ | — |
| **Matthews Correlation (MCC)** | $0.7510 \pm 0.0103$ | **$0.7711 \pm 0.0079$** | $+0.0201$ | **$t = 3.4529, p = 0.0096$** |
| **Inference Latency** | **$9.07\text{ ms} \pm 0.90\text{ ms}$** | $12.94\text{ ms} \pm 0.92\text{ ms}$ | $+3.87\text{ ms}$ | Real-time edge capable |
| **MAC Operations** | **$56,579,931$** | $98,483,212$ | — | Optimized mobile footprint |
| **Model Parameters** | **$944,885$** | $968,599$ | $+23,714$ | Sub-1M parameter footprint |

### 2. Real-World Mobile App Evaluation (5-Seed Field Testing)

| Operational Metric | Baseline Mobile App | Enhanced D-CLAHE Mobile App | Field Delta ($\Delta$) | Real-World Impact |
| :--- | :---: | :---: | :---: | :--- |
| **Accuracy** | $49.86\% \pm 10.42\%$ | **$60.42\% \pm 6.85\%$** | **$+10.56\text{ pp}$** | $+10.56\%$ average boost; variance reduced by $34\%$ |
| **Matthews Correlation (MCC)**| $0.4130 \pm 0.0359$ | **$0.5028 \pm 0.0575$** | **$+0.0898$** | $100\%$ win rate ($5/5$ seeds); $t = 2.9638, p = 0.0221$ |
| **Precision** | $81.92\% \pm 1.79\%$ | **$85.92\% \pm 3.31\%$** | **$+4.00\text{ pp}$** | $100\%$ win rate; peak precision $90.5\%$ |
| **Recall (Sensitivity)** | $52.06\% \pm 7.04\%$ | **$59.60\% \pm 7.10\%$** | **$+7.54\text{ pp}$** | Significant boost in detecting active foliar blight |
| **Specificity (TNR)** | $85.24\% \pm 4.62\%$ | **$87.62\% \pm 3.87\%$** | **$+2.38\text{ pp}$** | Consistently suppresses false disease alarms |
| **Macro F1-Score** | $58.88\% \pm 7.05\%$ | **$68.92\% \pm 5.22\%$** | **$+10.04\text{ pp}$** | Balanced class recognition across diverse fields |

---

## 📁 Repository Structure

```
CassavaLeafDetector/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── cassava_gatekeeper.tflite          # Stage 1 binary leaf validator
│   │   │   │   ├── model_base.tflite                  # Stage 2 Baseline MobileNetV3-Small
│   │   │   │   ├── model_enhanced.tflite              # Stage 2 D-CLAHE Enhanced MobileNetV3-Small
│   │   │   │   └── model_visualizer.tflite            # Stage 2 Real-Time D-CLAHE visualizer
│   │   │   ├── java/com/example/cassavaleafdetector/
│   │   │   │   ├── MainActivity.kt                    # Lifecycle, CameraX, UI, Batch Engine
│   │   │   │   ├── CassavaClassifier.kt               # TFLite inference pipeline & fallback
│   │   │   │   ├── HistoryAdapter.kt                  # Single scan history adapter
│   │   │   │   └── BatchHistoryAdapter.kt             # Batch evaluation history adapter
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       │   ├── activity_main.xml              # Main interface & viewfinder
│   │   │       │   ├── layout_batch_test.xml          # Batch testing dashboard & controls
│   │   │       │   └── dialog_thesis_metrics.xml      # Statistical benchmarks dialog
│   │   │       └── values/
│   │   │           ├── colors.xml / strings.xml / ids.xml
│   │   └── test/java/com/example/cassavaleafdetector/
│   │       └── ExampleUnitTest.kt                     # Unit tests for metrics, OvR, & Gatekeeper
│   └── build.gradle.kts                               # Module configuration & dependencies
├── docs/
│   ├── architecture_diagram.png                       # System architecture diagram
│   ├── DEFENSE_CODEBASE_GUIDE.txt                     # Technical defense walkthrough guide
│   └── Enhancing MobileNetV3-Small...pdf              # Full research thesis paper
├── gradle/
│   └── libs.versions.toml                             # Gradle Version Catalog
├── build.gradle.kts                                   # Root build script
├── settings.gradle.kts                                # Project settings
└── README.md                                          # Project documentation
```

---

## 🛠️ Tech Stack & Dependencies

* **Language**: [Kotlin 2.0.21](https://kotlinlang.org) (Target JVM: 11)
* **Build System**: Gradle 8.13 with Version Catalog (`libs.versions.toml`)
* **SDK Compatibility**: `minSdk = 24` (Android 7.0), `targetSdk = 36`, `compileSdk = 36`
* **On-Device Inference**:
  * [TensorFlow Lite Support 0.4.4](https://www.tensorflow.org/lite)
  * [LiteRT / TensorFlow Lite 2.17.0](https://ai.google.dev/edge/litert)
* **Camera Integration**: [AndroidX CameraX 1.4.0](https://developer.android.com/training/camerax) (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
* **UI & Styling**: Material Components (`com.google.android.material:material:1.12.0`), `ConstraintLayout`, `RecyclerView`, `Edge-to-Edge WindowInsets`
* **Concurrency**: Native Java `Executors.newSingleThreadExecutor()` & `Handler(Looper.getMainLooper())` for deterministic, leak-free async inference.

---

## 🚀 Building & Running

### Prerequisites
* **Android Studio**: Ladybug (2024.2) or newer
* **Java Development Kit**: JDK 17 or JDK 21 (Android Studio bundled JBR recommended)
* **Android Device / Emulator**: Running Android 7.0 (API Level 24) or higher

### Compilation Command
To build and execute unit tests:

```powershell
# Set Java Home to Android Studio's bundled JBR
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Run unit tests
.\gradlew.bat testThesisDebugUnitTest testUserDebugUnitTest

# Assemble debug APK
.\gradlew.bat assembleThesisDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/thesis/debug/app-thesis-debug.apk
```

---

## 🧪 Unit Testing Suite

The project includes unit tests in [ExampleUnitTest.kt](file:///C:/Users/raini/AndroidStudioProjects/CassavaLeafDetector/app/src/test/java/com/example/cassavaleafdetector/ExampleUnitTest.kt) covering:
1. **Gatekeeper Validation**: Binary classification decision boundary and 70% threshold logic.
2. **One-vs-Rest Confusion Matrix**: True Positive, False Positive, False Negative, and True Negative accuracy across 5 classes.
3. **Active-Class Macro Evaluation**: Dynamic macro metrics ($K_{\text{active}} \in [1, 5]$) verifying proper zero-division safety and penalty handling when un-uploaded classes are predicted.
4. **Softmax Numerical Stability**: Verifies logit exponential transformations without floating-point overflow.

Run all tests via Gradle:
```powershell
.\gradlew.bat test
```

---

## 📄 License & Attribution

Developed for academic research and edge AI deployment.  
Dataset provided by the **Kaggle Cassava Leaf Disease Classification Challenge** (Makerere University AI Lab & Crop Disease Surveillance Team).
