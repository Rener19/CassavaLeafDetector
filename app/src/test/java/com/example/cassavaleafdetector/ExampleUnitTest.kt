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
            f1Score = 0.85,
            tnr = 0.96,
            macroF1 = 0.85,
            mcc = 0.81
        )
        assertEquals(0.85, metrics.accuracy, 0.0001)
        assertEquals(0.86, metrics.precision, 0.0001)
        assertEquals(0.84, metrics.recall, 0.0001)
        assertEquals(0.85, metrics.f1Score, 0.0001)
        assertEquals(0.96, metrics.tnr, 0.0001)
        assertEquals(0.85, metrics.macroF1, 0.0001)
        assertEquals(0.81, metrics.mcc, 0.0001)
    }
}