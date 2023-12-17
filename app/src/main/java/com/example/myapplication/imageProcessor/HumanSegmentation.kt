package com.example.myapplication.imageProcessor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.example.myapplication.utils.assetFilePath
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.exp
import kotlin.math.max

class HumanSegmentation(private val context: Context) {
    private val module: Module = Module.load(assetFilePath(context, MODEL_NAME))
    val TAG = "HumanSegmentation"

    fun inference(imagePath: String): Pair<Bitmap, Bitmap> {
        val time = System.currentTimeMillis()
        Log.d(TAG, "start inference")
        val bitmap = BitmapFactory.decodeStream(context.assets.open(imagePath))
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, false)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        val batchNumber = outputTensor.shape()[0].toInt()
        val channelNumber = outputTensor.shape()[1].toInt()
        val heightNumber = outputTensor.shape()[2].toInt()
        val widthNumber = outputTensor.shape()[3].toInt()

        val newBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        softmax(scores, outputTensor.shape())

        for (h in 0 until heightNumber) {
            for (w in 0 until widthNumber) {
                val color = if (IS_NNAPI) {
                    if (scores[(h * widthNumber + w) * channelNumber + 1] > 0.5f) Color.BLACK else Color.WHITE
                } else {
                    if (scores[widthNumber*heightNumber + h*widthNumber + w] > 0.5f) Color.BLACK else Color.WHITE
                }
                newBitmap.setPixel(w, h, color)
            }
        }

        Log.d(TAG, "end inference. ${System.currentTimeMillis()-time}ms")

        return Pair(resizedBitmap, newBitmap)
    }

    fun softmax(floatArray: FloatArray, shape: LongArray) {
        val batchNumber = shape[0].toInt()
        val channelNumber = shape[1].toInt()
        val heightNumber = shape[2].toInt()
        val widthNumber = shape[3].toInt()

        if (IS_NNAPI) {
            for (batch in 0 until batchNumber) {
                for (h in 0 until heightNumber) {
                    for (w in 0 until widthNumber) {
                        val a = floatArray[(h * widthNumber + w) * channelNumber]
                        val b = floatArray[(h * widthNumber + w) * channelNumber + 1]
                        val m = max(a, b)
                        val expsum = exp(a-m) + exp(b-m)
                        floatArray[(h * widthNumber + w) * channelNumber] = exp(a-m) / expsum
                        floatArray[(h * widthNumber + w) * channelNumber + 1] = exp(b-m) / expsum
                    }
                }
            }
        } else {
            for (batch in 0 until batchNumber) {
                for (h in 0 until heightNumber) {
                    for (w in 0 until widthNumber) {
                        val a = floatArray[h * widthNumber + w]
                        val b = floatArray[heightNumber * widthNumber + h * widthNumber + w]
                        val m = max(a, b)
                        val expsum = exp(a-m) + exp(b-m)
                        floatArray[h * widthNumber + w] = exp(a-m) / expsum
                        floatArray[heightNumber * widthNumber + h * widthNumber + w] =
                            exp(b-m) / expsum
                    }
                }
            }
        }
    }

    companion object {
        const val IS_NNAPI = false
        val MODEL_NAME = if (IS_NNAPI) "HS_nnapi_notlite.pt" else "no_opt_mobile_HS_model.ptl"
    }
}