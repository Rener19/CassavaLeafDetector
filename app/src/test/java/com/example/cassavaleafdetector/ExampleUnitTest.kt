package com.example.cassavaleafdetector

import org.junit.Assert.*
import org.junit.Test

/**
 * ============================================================================
 * ExampleUnitTest
 * ============================================================================
 * Unit test suite verifying core mathematical algorithms, taxonomy structures,
 * and multi-class statistical evaluation metrics for defense verification.
 */
class ExampleUnitTest {

    @Test
    fun testTaxonomyIntegrity() {
        // Assert that the app defines exactly 5 disease classes
        assertEquals(5, CassavaClassifier.LABELS.size)
        assertEquals(5, CassavaClassifier.DESCRIPTIONS.size)
        assertEquals(5, CassavaClassifier.TREATMENTS.size)

        // Verify class labels
        assertEquals("Cassava Bacterial Blight (CBB)", CassavaClassifier.LABELS[0])
        assertEquals("Cassava Brown Streak Disease (CBSD)", CassavaClassifier.LABELS[1])
        assertEquals("Cassava Green Mottle (CGM)", CassavaClassifier.LABELS[2])
        assertEquals("Cassava Mosaic Disease (CMD)", CassavaClassifier.LABELS[3])
        assertEquals("Healthy", CassavaClassifier.LABELS[4])
    }

    @Test
    fun testMetricsPerfectClassification() {
        val groundTruths = listOf(0, 1, 2, 3, 4)
        val predictions = listOf(0, 1, 2, 3, 4)
        val numClasses = 5

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
        assertEquals(1.0, accuracy, 0.001)

        for (c in 0 until numClasses) {
            assertEquals(1.0, tp[c], 0.001)
            assertEquals(0.0, fp[c], 0.001)
            assertEquals(0.0, fn[c], 0.001)
            assertEquals(4.0, tn[c], 0.001)
        }
    }

    @Test
    fun testSoftmaxNumericalStability() {
        // Test that Softmax handles large logit values without overflow (using max-subtraction)
        val logits = floatArrayOf(1000f, 1001f, 1002f, 1000f, 1000f)
        val maxVal = 1002f

        var expSum = 0.0
        for (v in logits) {
            expSum += Math.exp((v - maxVal).toDouble())
        }

        val probs = FloatArray(logits.size)
        var probSum = 0f
        for (i in logits.indices) {
            probs[i] = (Math.exp((logits[i] - maxVal).toDouble()) / expSum).toFloat()
            probSum += probs[i]
        }

        // Probability sum must equal 1.0
        assertEquals(1.0f, probSum, 0.001f)

        // Argmax (index 2) must have highest probability
        assertTrue(probs[2] > probs[0])
        assertTrue(probs[2] > probs[1])
    }

    @Test
    fun testMetricsDataClass() {
        val metrics = Metrics(
            accuracy = 0.85,
            precision = 0.86,
            recall = 0.84,
            tpr = 0.84,
            tnr = 0.96,
            f1Score = 0.85,
            macroF1 = 0.85,
            mcc = 0.81
        )
        assertEquals(0.85, metrics.accuracy, 0.0001)
        assertEquals(0.86, metrics.precision, 0.0001)
        assertEquals(0.84, metrics.recall, 0.0001)
        assertEquals(0.84, metrics.tpr, 0.0001)
        assertEquals(0.96, metrics.tnr, 0.0001)
        assertEquals(0.85, metrics.f1Score, 0.0001)
        assertEquals(0.85, metrics.macroF1, 0.0001)
        assertEquals(0.81, metrics.mcc, 0.0001)
    }

    @Test
    fun testConfidenceThresholdConstant() {
        // Assert that the selective classification confidence threshold is 60% (0.60f)
        assertEquals(0.60f, CassavaClassifier.CONFIDENCE_THRESHOLD, 0.0001f)
    }

    @Test
    fun testSelectiveClassificationFilter() {
        // Mock confidence values for 5 candidate predictions
        val confidences = listOf(0.85f, 0.52f, 0.74f, 0.59f, 0.91f)
        val groundTruths = listOf(0, 1, 2, 3, 4)
        val predictedIndices = listOf(0, 2, 2, 3, 4)

        val filteredPredictions = mutableListOf<Int>()
        val filteredTruths = mutableListOf<Int>()
        var excludedCount = 0

        for (i in confidences.indices) {
            if (confidences[i] >= CassavaClassifier.CONFIDENCE_THRESHOLD) {
                filteredPredictions.add(predictedIndices[i])
                filteredTruths.add(groundTruths[i])
            } else {
                excludedCount++
            }
        }

        // Two items (0.52 and 0.59) are below 0.60 threshold and must be excluded
        assertEquals(2, excludedCount)
        assertEquals(3, filteredPredictions.size)
        assertEquals(listOf(0, 2, 4), filteredPredictions)
        assertEquals(listOf(0, 2, 4), filteredTruths)
    }

    @Test
    fun testGatekeeperThresholdConstant() {
        // Assert that the Stage 1 Gatekeeper decision threshold is 70% (0.70f)
        assertEquals(0.70f, CassavaClassifier.GATEKEEPER_THRESHOLD, 0.0001f)
    }

    @Test
    fun testGatekeeperBinaryClassification() {
        // Test values around the 0.70 threshold
        val testProbabilities = listOf(0.05f, 0.699f, 0.70f, 0.701f, 0.99f)
        val expectedIsCassava = listOf(false, false, true, true, true)

        for (i in testProbabilities.indices) {
            val prob = testProbabilities[i]
            val isCassava = prob >= CassavaClassifier.GATEKEEPER_THRESHOLD
            assertEquals("Mismatch for probability $prob", expectedIsCassava[i], isCassava)
        }
    }

    @Test
    fun testTwoStageCascadePipeline() {
        // Mock a batch of 5 input images: (gatekeeper_prob, disease_confidence)
        // Image 0: Non-cassava (0.15) -> excluded at Stage 1
        // Image 1: Cassava (0.92), low disease conf (0.45) -> excluded at Stage 2
        // Image 2: Cassava (0.85), high disease conf (0.88) -> evaluated
        // Image 3: Non-cassava (0.65) -> excluded at Stage 1
        // Image 4: Cassava (0.75), high disease conf (0.62) -> evaluated
        val samples = listOf(
            Pair(0.15f, 0.90f),
            Pair(0.92f, 0.45f),
            Pair(0.85f, 0.88f),
            Pair(0.65f, 0.95f),
            Pair(0.75f, 0.62f)
        )

        var passedGatekeeper = 0
        var excludedNonCassava = 0
        var excludedLowConf = 0
        var fullyEvaluated = 0

        for ((gkProb, diseaseConf) in samples) {
            if (gkProb < CassavaClassifier.GATEKEEPER_THRESHOLD) {
                excludedNonCassava++
                continue
            }
            passedGatekeeper++
            if (diseaseConf < CassavaClassifier.CONFIDENCE_THRESHOLD) {
                excludedLowConf++
                continue
            }
            fullyEvaluated++
        }

        assertEquals(2, excludedNonCassava)
        assertEquals(3, passedGatekeeper)
        assertEquals(1, excludedLowConf)
        assertEquals(2, fullyEvaluated)
    }

    @Test
    fun testAdjustableBatchConfidenceThreshold() {
        val confidences = listOf(0.10f, 0.45f, 0.60f, 0.75f, 0.95f)

        // Threshold = 0.00 (All images pass)
        val passedAtZero = confidences.count { it >= 0.00f }
        assertEquals(5, passedAtZero)

        // Threshold = 0.60 (Default: 0.60, 0.75, 0.95 pass)
        val passedAtDefault = confidences.count { it >= 0.60f }
        assertEquals(3, passedAtDefault)

        // Threshold = 0.80 (Only 0.95 passes)
        val passedAtEighty = confidences.count { it >= 0.80f }
        assertEquals(1, passedAtEighty)

        // Threshold = 1.00 (Strict: none pass)
        val passedAtHundred = confidences.count { it >= 1.00f }
        assertEquals(0, passedAtHundred)
    }

    @Test
    fun testActiveClassesMacroMetricsSingleClass() {
        // Scenario: Exactly 1 image evaluated (Healthy = 4), predicted correctly as 4
        val predictions = listOf(4)
        val groundTruths = listOf(4)
        val metrics = MainActivity.calculateMetrics(predictions, groundTruths, 5)

        // With active-class averaging, metrics reflect the 1 active class without 0% penalties from untested classes
        assertEquals(1.0, metrics.accuracy, 0.0001)
        assertEquals(1.0, metrics.precision, 0.0001)
        assertEquals(1.0, metrics.recall, 0.0001)
        assertEquals(1.0, metrics.tpr, 0.0001)
        assertEquals(1.0, metrics.f1Score, 0.0001)
        assertEquals(1.0, metrics.macroF1, 0.0001)
        assertEquals(1.0, metrics.tnr, 0.0001)
        assertEquals(1.0, metrics.mcc, 0.0001)
    }

    @Test
    fun testActiveClassesMacroMetricsPartialBatch() {
        // Scenario: 2 classes tested (CBB = 0, Healthy = 4), 1 of each, both predicted correctly
        val predictions = listOf(0, 4)
        val groundTruths = listOf(0, 4)
        val metrics = MainActivity.calculateMetrics(predictions, groundTruths, 5)

        assertEquals(1.0, metrics.accuracy, 0.0001)
        assertEquals(1.0, metrics.precision, 0.0001)
        assertEquals(1.0, metrics.recall, 0.0001)
        assertEquals(1.0, metrics.tpr, 0.0001)
        assertEquals(1.0, metrics.f1Score, 0.0001)
        assertEquals(1.0, metrics.macroF1, 0.0001)
        assertEquals(1.0, metrics.tnr, 0.0001)
        assertEquals(1.0, metrics.mcc, 0.0001)
    }

    @Test
    fun testActiveClassesMacroMetricsPartialBatchWithConfusion() {
        // Scenario: 2 classes tested (CBB = 0, Healthy = 4), both predicted as CBB = 0
        // Ground truths: [0, 4], Predictions: [0, 0]
        val predictions = listOf(0, 0)
        val groundTruths = listOf(0, 4)
        val metrics = MainActivity.calculateMetrics(predictions, groundTruths, 5)

        // Class 0: TP=1, FP=1, FN=0 -> Precision=0.5, Recall=1.0, F1=0.667, TNR=0.0
        // Class 4: TP=0, FP=0, FN=1 -> Precision=0.0, Recall=0.0, F1=0.0, TNR=1.0
        // Macro: (0.5+0.0)/2 = 0.25 precision, (1.0+0.0)/2 = 0.50 recall, accuracy = 0.50
        assertEquals(0.50, metrics.accuracy, 0.0001)
        assertEquals(0.25, metrics.precision, 0.0001)
        assertEquals(0.50, metrics.recall, 0.0001)
        assertEquals(0.50, metrics.tpr, 0.0001)
        assertEquals(0.50, metrics.tnr, 0.0001)
    }

    @Test
    fun testActiveClassesMacroMetricsFullBatch() {
        // Scenario: All 5 classes tested and predicted correctly
        val predictions = listOf(0, 1, 2, 3, 4)
        val groundTruths = listOf(0, 1, 2, 3, 4)
        val metrics = MainActivity.calculateMetrics(predictions, groundTruths, 5)

        assertEquals(1.0, metrics.accuracy, 0.0001)
        assertEquals(1.0, metrics.precision, 0.0001)
        assertEquals(1.0, metrics.recall, 0.0001)
        assertEquals(1.0, metrics.tpr, 0.0001)
        assertEquals(1.0, metrics.f1Score, 0.0001)
        assertEquals(1.0, metrics.macroF1, 0.0001)
        assertEquals(1.0, metrics.tnr, 0.0001)
        assertEquals(1.0, metrics.mcc, 0.0001)
    }

    @Test
    fun testActiveClassesMacroMetricsThreeClasses() {
        // Scenario: 3 classes tested (0, 1, 4), 1 of each, all predicted correctly
        val predictions = listOf(0, 1, 4)
        val groundTruths = listOf(0, 1, 4)
        val metrics = MainActivity.calculateMetrics(predictions, groundTruths, 5)

        assertEquals(1.0, metrics.accuracy, 0.0001)
        assertEquals(1.0, metrics.precision, 0.0001)
        assertEquals(1.0, metrics.recall, 0.0001)
        assertEquals(1.0, metrics.tpr, 0.0001)
        assertEquals(1.0, metrics.tnr, 0.0001)
        assertEquals(1.0, metrics.f1Score, 0.0001)
        assertEquals(1.0, metrics.macroF1, 0.0001)
        assertEquals(1.0, metrics.mcc, 0.0001)
    }

    @Test
    fun testActiveClassesMacroMetricsFourClasses() {
        // Scenario: 4 classes tested (0, 1, 2, 4), 1 of each, all predicted correctly
        val predictions = listOf(0, 1, 2, 4)
        val groundTruths = listOf(0, 1, 2, 4)
        val metrics = MainActivity.calculateMetrics(predictions, groundTruths, 5)

        assertEquals(1.0, metrics.accuracy, 0.0001)
        assertEquals(1.0, metrics.precision, 0.0001)
        assertEquals(1.0, metrics.recall, 0.0001)
        assertEquals(1.0, metrics.tpr, 0.0001)
        assertEquals(1.0, metrics.tnr, 0.0001)
        assertEquals(1.0, metrics.f1Score, 0.0001)
        assertEquals(1.0, metrics.macroF1, 0.0001)
        assertEquals(1.0, metrics.mcc, 0.0001)
    }
}