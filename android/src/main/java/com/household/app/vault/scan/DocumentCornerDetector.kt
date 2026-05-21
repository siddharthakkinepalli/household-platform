package com.household.app.vault.scan

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds the four corners of a document in a bitmap using Canny edge detection + contour analysis.
 * Returns null if no suitable quadrilateral is found (caller should skip correction in that case).
 */
object DocumentCornerDetector {

    data class Quad(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomRight: PointF,
        val bottomLeft: PointF
    )

    fun detect(bitmap: Bitmap): Quad? {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // Blur + Canny
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 75.0, 200.0)

        // Dilate slightly to close small gaps
        val dilated = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, dilated, kernel)

        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val imageArea = (bitmap.width * bitmap.height).toDouble()
        val minArea = imageArea * 0.15  // must be at least 15% of image

        // Find the largest contour that approximates to a quadrilateral
        val quad = contours
            .sortedByDescending { Imgproc.contourArea(it) }
            .firstNotNullOfOrNull { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < minArea) return@firstNotNullOfOrNull null

                val contour2f = MatOfPoint2f(*contour.toArray())
                val perimeter = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)

                if (approx.total() == 4L) approx else null
            } ?: return null

        val pts = quad.toArray()
        return orderPoints(pts)
    }

    /** Orders 4 points as top-left, top-right, bottom-right, bottom-left */
    private fun orderPoints(pts: Array<Point>): Quad {
        // Sort by sum: top-left has smallest sum, bottom-right has largest
        val sorted = pts.sortedBy { it.x + it.y }
        val tl = sorted[0]
        val br = sorted[3]
        // Of remaining two: top-right has smaller y, bottom-left has larger y
        val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.y }
        val tr = remaining[0]
        val bl = remaining[1]

        return Quad(
            topLeft     = PointF(tl.x.toFloat(), tl.y.toFloat()),
            topRight    = PointF(tr.x.toFloat(), tr.y.toFloat()),
            bottomRight = PointF(br.x.toFloat(), br.y.toFloat()),
            bottomLeft  = PointF(bl.x.toFloat(), bl.y.toFloat())
        )
    }
}
