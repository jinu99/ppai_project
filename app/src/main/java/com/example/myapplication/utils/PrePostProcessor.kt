package com.example.myapplication.utils

import android.graphics.Rect
import com.example.myapplication.classes.YOLOResult

import java.util.ArrayList
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

class PrePostProcessor {
    // model output is of size (num_of_class+4) * 1344
    private val mOutputRow = 84 // x, y, width, height and 80 class probability
    private val mOutputColumn = 5376 // as decided by the YOLOv5 model for input image of size 256*256
    private val mConfThreshold = 0.25f // score above which a detection is generated
    private val mIoUThreshold = 0.5f // score above which a detection is generated
    private val mNmsLimit = 15

    // The two methods nonMaxSuppression and IOU below are ported from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift
    /**
     * Removes bounding boxes that overlap too much with other boxes that have
     * a higher score.
     * - Parameters:
     * - boxes: an array of bounding boxes and their scores
     * - limit: the maximum number of boxes that will be selected
     * - threshold: used to decide whether boxes overlap too much
     */
    private fun nonMaxSuppression(
        boxes: ArrayList<YOLOResult>,
        limit: Int,
        threshold: Float
    ): ArrayList<YOLOResult> {
        // Do an argsort on the confidence scores, from high to low.
        boxes.sortWith { o1, o2 -> o1.score.compareTo(o2.score) }
        val selected: ArrayList<YOLOResult> = ArrayList()
        val active = BooleanArray(boxes.size)
        Arrays.fill(active, true)
        var numActive = active.size

        // The algorithm is simple: Start with the box that has the highest score.
        // Remove any remaining boxes that overlap it more than the given threshold
        // amount. If there are any boxes left (i.e. these did not overlap with any
        // previous boxes), then repeat this procedure, until no more boxes remain
        // or the limit has been reached.
        var done = false
        var i = 0
        while (i < boxes.size && !done) {
            if (active[i]) {
                val boxA = boxes[i]
                selected.add(boxA)
                if (selected.size >= limit) break
                for (j in i + 1 until boxes.size) {
                    if (active[j]) {
                        val boxB = boxes[j]
                        if (IOU(boxA.rect, boxB.rect) > threshold) {
                            active[j] = false
                            numActive -= 1
                            if (numActive <= 0) {
                                done = true
                                break
                            }
                        }
                    }
                }
            }
            i++
        }
        return selected
    }

    /**
     * Computes intersection-over-union overlap between two bounding boxes.
     */
    private fun IOU(a: Rect, b: Rect): Float {
        val areaA: Int = (a.right - a.left) * (a.bottom - a.top)
        if (areaA <= 0) return 0.0f
        val areaB: Int = (b.right - b.left) * (b.bottom - b.top)
        if (areaB <= 0) return 0.0f
        val intersectionMinX: Int = max(a.left, b.left)
        val intersectionMinY: Int = max(a.top, b.top)
        val intersectionMaxX: Int = min(a.right, b.right)
        val intersectionMaxY: Int = min(a.bottom, b.bottom)
        val intersectionArea = max(intersectionMaxY - intersectionMinY, 0) *
                max(intersectionMaxX - intersectionMinX, 0)
        return intersectionArea.toFloat() / (areaA + areaB - intersectionArea).toFloat()
    }

    fun outputsToNMSPredictions(
        outputs: FloatArray,
        imgScaleX: Float,
        imgScaleY: Float,
        ivScaleX: Float,
        ivScaleY: Float,
        startX: Float,
        startY: Float
    ): ArrayList<YOLOResult> {
        val results: ArrayList<YOLOResult> = ArrayList()
        for (i in 0 until mOutputColumn) {
            val x = outputs[i]
            val y = outputs[i + mOutputColumn]
            val w = outputs[i + mOutputColumn * 2]
            val h = outputs[i + mOutputColumn * 3]
            val left = imgScaleX * (x - w / 2)
            val top = imgScaleY * (y - h / 2)
            val right = imgScaleX * (x + w / 2)
            val bottom = imgScaleY * (y + h / 2)
            var max = outputs[i + mOutputColumn * 4]
            var cls = 0
            for (j in 0 until mOutputRow - 4) {
                if (outputs[i + mOutputColumn * (j + 4)] > max) {
                    max = outputs[i + mOutputColumn * (j + 4)]
                    cls = j
                }
            }
            if (max > mConfThreshold) {
                val rect = Rect(
                    (startX + ivScaleX * left).toInt(),
                    (startY + ivScaleY * top).toInt(),
                    (startX + ivScaleX * right).toInt(),
                    (startY + ivScaleY * bottom).toInt()
                )
                val result = YOLOResult(cls, max, rect)
                results.add(result)
            }
        }
        return nonMaxSuppression(results, mNmsLimit, mIoUThreshold)
    }
}