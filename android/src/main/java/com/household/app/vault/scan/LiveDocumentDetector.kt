package com.household.app.vault.scan

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// ── Result types ──────────────────────────────────────────────────────────────

/**
 * Carries a detected quad plus the exact frame dimensions the quad coordinates live in,
 * and the sensor rotation needed to map those coordinates to display space.
 */
data class FrameDetectionResult(
    val quad: DocumentCornerDetector.Quad,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int
)

/**
 * Describes the current document detection state emitted to the UI.
 */
sealed class DetectionState {
    /** A document quad was found; result contains display-ready coordinates. */
    data class Found(val result: FrameDetectionResult) : DetectionState()
    /** Still scanning — no quad yet but not enough failures to warn. */
    object Searching : DetectionState()
    /** No document detected after [WARN_AFTER_FRAMES] consecutive failures. */
    object NoDocument : DetectionState()
}

// ── Detector ──────────────────────────────────────────────────────────────────

/**
 * Throttled document corner detector for CameraX ImageAnalysis.
 *
 * - Runs OpenCV detection at most once every [intervalMs].
 * - Passes frame dimensions and rotation to callers so the overlay can scale correctly.
 * - Emits [DetectionState.NoDocument] after [WARN_AFTER_FRAMES] consecutive misses so the
 *   UI can prompt the user to move to a plain surface.
 */
class LiveDocumentDetector(
    private val intervalMs: Long = 250L,
    private val onState: (DetectionState) -> Unit
) {
    private val lastRunAt = AtomicLong(0L)
    private val busy = AtomicBoolean(false)
    private var consecutiveFailures = 0

    fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastRunAt.get() < intervalMs || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastRunAt.set(now)

        try {
            val rotation = image.imageInfo.rotationDegrees
            val bitmap = image.toBitmap()   // CameraX built-in, no rotation applied
            val quad = DocumentCornerDetector.detect(bitmap)

            if (quad != null) {
                consecutiveFailures = 0
                onState(
                    DetectionState.Found(
                        FrameDetectionResult(
                            quad = quad,
                            frameWidth = bitmap.width,
                            frameHeight = bitmap.height,
                            rotationDegrees = rotation
                        )
                    )
                )
            } else {
                consecutiveFailures++
                onState(
                    if (consecutiveFailures >= WARN_AFTER_FRAMES) DetectionState.NoDocument
                    else DetectionState.Searching
                )
            }
        } finally {
            busy.set(false)
            image.close()
        }
    }

    companion object {
        private const val WARN_AFTER_FRAMES = 8
    }
}

// ── Coordinate transform ──────────────────────────────────────────────────────

/**
 * Transforms quad points from sensor space to display space using Android's Matrix,
 * then scales to view dimensions in a single pipeline.
 *
 * Android's postRotate(θ) is clockwise. Each rotation needs a translation to shift all
 * coordinates back into the positive quadrant after rotation around the origin:
 *   90°  → (H-y, x)   via postRotate(270) + postTranslate(H, 0)
 *   180° → (W-x, H-y) via postRotate(180) + postTranslate(W, H)
 *   270° → (y, W-x)   via postRotate(90)  + postTranslate(0, W)
 */
fun FrameDetectionResult.toViewQuad(viewWidth: Float, viewHeight: Float): DocumentCornerDetector.Quad {
    val fW = frameWidth.toFloat()
    val fH = frameHeight.toFloat()
    val matrix = android.graphics.Matrix()
    when (rotationDegrees) {
        90  -> { matrix.postRotate(270f); matrix.postTranslate(fH, 0f) }
        180 -> { matrix.postRotate(180f); matrix.postTranslate(fW, fH) }
        270 -> { matrix.postRotate(90f);  matrix.postTranslate(0f, fW) }
    }
    val displayW = if (rotationDegrees == 90 || rotationDegrees == 270) fH else fW
    val displayH = if (rotationDegrees == 90 || rotationDegrees == 270) fW else fH
    matrix.postScale(viewWidth / displayW, viewHeight / displayH)

    val pts = floatArrayOf(
        quad.topLeft.x,     quad.topLeft.y,
        quad.topRight.x,    quad.topRight.y,
        quad.bottomRight.x, quad.bottomRight.y,
        quad.bottomLeft.x,  quad.bottomLeft.y
    )
    matrix.mapPoints(pts)
    return DocumentCornerDetector.Quad(
        topLeft     = PointF(pts[0], pts[1]),
        topRight    = PointF(pts[2], pts[3]),
        bottomRight = PointF(pts[4], pts[5]),
        bottomLeft  = PointF(pts[6], pts[7])
    )
}

// ── Legacy ────────────────────────────────────────────────────────────────────

/** Legacy extension kept for any call sites that haven't migrated yet. */
fun DocumentCornerDetector.Quad.scaleTo(
    fromW: Float, fromH: Float,
    toW: Float, toH: Float
): DocumentCornerDetector.Quad {
    fun PointF.s() = PointF(x / fromW * toW, y / fromH * toH)
    return DocumentCornerDetector.Quad(topLeft.s(), topRight.s(), bottomRight.s(), bottomLeft.s())
}
