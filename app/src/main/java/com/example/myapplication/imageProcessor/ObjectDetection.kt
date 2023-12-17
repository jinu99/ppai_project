package com.example.myapplication.imageProcessor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.example.myapplication.utils.PrePostProcessor
import com.example.myapplication.utils.Word2Vec
import com.example.myapplication.utils.assetFilePath
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils


class ObjectDetection(context: Context) {
    private val module: Module
    private val prePostProcessor: PrePostProcessor
    private val word2Vec: Word2Vec

    val TAG = "ObjectDetection"

    init {
        module = Module.load(assetFilePath(context, MODEL_NAME))
        prePostProcessor = PrePostProcessor()
        word2Vec = Word2Vec(context, W2V_FILE_NAME)
    }

    fun inference(bitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, false)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f)
        )

        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        val results = prePostProcessor.outputsToNMSPredictions(scores, 1f, 1f, 1f, 1f, 0f, 0f)
        Log.d(TAG, results.joinToString(" / ") { "${it.classIndex}, ${it.score}" })

        return word2Vec.vectorize(results.map { Pair(it.classIndex, 1f) })
    }

    companion object {
        const val MODEL_NAME = "yolov8n_512.ptl"
        const val W2V_FILE_NAME = "yolo_w2v.json"
        const val IMAGE_SIZE = 512
    }
}