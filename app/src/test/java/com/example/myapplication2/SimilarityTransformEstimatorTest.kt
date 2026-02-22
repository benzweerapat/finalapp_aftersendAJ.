package com.example.myapplication2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SimilarityTransformEstimatorTest {

    @Test
    fun `estimate transform from two points`() {
        val transform = SimilarityTransformEstimator.estimate(
            listOf(
                SimilarityTransformEstimator.Correspondence(0.0, 0.0, 10.0, 20.0),
                SimilarityTransformEstimator.Correspondence(10.0, 0.0, 20.0, 20.0)
            )
        )

        assertNotNull(transform)
        val mapped = transform!!.map(5.0, 0.0)
        assertEquals(15.0, mapped.first, 1e-6)
        assertEquals(20.0, mapped.second, 1e-6)
    }

    @Test
    fun `estimate transform with rotation`() {
        val transform = SimilarityTransformEstimator.estimate(
            listOf(
                SimilarityTransformEstimator.Correspondence(0.0, 0.0, 0.0, 0.0),
                SimilarityTransformEstimator.Correspondence(10.0, 0.0, 0.0, 10.0),
                SimilarityTransformEstimator.Correspondence(0.0, 10.0, -10.0, 0.0)
            )
        )

        assertNotNull(transform)
        assertEquals(1.0, transform!!.scale, 1e-6)
        assertEquals(90.0, transform.rotationDegrees, 1e-6)
    }
}
