package com.household.app.vault.scan

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

/**
 * Warps a bitmap to a rectangle using the four detected document corners.
 * Output dimensions are derived from the detected quad's edge lengths.
 */
object PerspectiveCorrector {

    /**
     * Applies perspective correction to [bitmap] given [quad].
     * Returns the corrected bitmap, or the original if correction fails.
     */
    fun correct(bitmap: Bitmap, quad: DocumentCornerDetector.Quad): Bitmap {
        return runCatching {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            val tl = quad.topLeft
            val tr = quad.topRight
            val br = quad.bottomRight
            val bl = quad.bottomLeft

            // Compute output dimensions from detected edge lengths
            val widthTop  = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
            val widthBot  = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
            val dstW = max(widthTop, widthBot).toInt()

            val heightLeft  = hypot((tr.x - br.x).toDouble(), (tr.y - br.y).toDouble())
            val heightRight = hypot((tl.x - bl.x).toDouble(), (tl.y - bl.y).toDouble())
            val dstH = max(heightLeft, heightRight).toInt()

            if (dstW <= 0 || dstH <= 0) return bitmap

            val srcPts = org.opencv.core.MatOfPoint2f(
                Point(tl.x.toDouble(), tl.y.toDouble()),
                Point(tr.x.toDouble(), tr.y.toDouble()),
                Point(br.x.toDouble(), br.y.toDouble()),
                Point(bl.x.toDouble(), bl.y.toDouble())
            )
            val dstPts = org.opencv.core.MatOfPoint2f(
                Point(0.0, 0.0),
                Point(dstW.toDouble(), 0.0),
                Point(dstW.toDouble(), dstH.toDouble()),
                Point(0.0, dstH.toDouble())
            )

            val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            val warped = Mat()
            Imgproc.warpPerspective(src, warped, transform, Size(dstW.toDouble(), dstH.toDouble()))

            val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, result)
            result
        }.getOrDefault(bitmap)
    }
}
