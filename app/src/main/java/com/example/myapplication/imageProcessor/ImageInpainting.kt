package com.example.myapplication.imageProcessor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.myapplication.utils.assetFilePath
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImageInpainting(context: Context) {
    private val module: Module = Module.load(assetFilePath(context, MODEL_NAME))

    val img_size = 256
    val TAG = "ImageInpainting"

    fun getPixelFromFloatArray(floatArray: FloatArray, w: Int, h: Int, c: Int): Float {
        return floatArray[c*img_size*img_size + h*img_size + w]
    }

    fun inference(original: Bitmap, mask: Bitmap): Bitmap {
        val time = System.currentTimeMillis()
        Log.d(TAG, "start inference")

        val originalTensor = TensorImageUtils.bitmapToFloat32Tensor(
            original,
            floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f)
        )

        val maskTensor = TensorImageUtils.bitmapToFloat32Tensor(
            mask,
            floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(1f, 1f, 1f)
        )

        val originalFloatArray = originalTensor.dataAsFloatArray
        val maskFloatArray = maskTensor.dataAsFloatArray
        val data_size = img_size*img_size
        val inputFloatArray = FloatArray(originalFloatArray.size * 4 / 3) {
            if (it < data_size) {
                maskFloatArray[it]
            } else {
                if (maskFloatArray[it%data_size] > 0) originalFloatArray[it - data_size]
                else 0f
            }
        }

        val inputTensor = Tensor.fromBlob(inputFloatArray, longArrayOf(1, 4, img_size.toLong(), img_size.toLong()))

        Log.d(TAG, inputTensor.shape().contentToString())

        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        val widthNumber = outputTensor.shape()[2].toInt()
        val heightNumber = outputTensor.shape()[3].toInt()
        Log.d(TAG, scores.max().toString())
        Log.d(TAG, scores.min().toString())

        val newBitmap = floatArrayToBitmap(originalFloatArray, maskFloatArray, scores, widthNumber, heightNumber)

        Log.d(TAG, "end inference. ${System.currentTimeMillis()-time}ms")

        return newBitmap
    }

    private fun floatArrayToBitmap(origFloatArray: FloatArray, maskFloatArray: FloatArray, resultFloatArray: FloatArray, width: Int, height: Int): Bitmap {
        // create empty bitmap in argb format
        val bmp: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height * 4)

        // define if float min..max will be mapped to 0..255 or 255..0
        val conversion = { v: Float -> (min(1f, max((v * 0.5f + 0.5f), 0f)) * 255.0f).roundToInt()}

        val data_size = img_size*img_size
        // copy each value from float array to rgb channels
        for (i in 0 until width * height) {
            if (maskFloatArray[i] > 0) {
                val r = conversion(origFloatArray[i])
                val g = conversion(origFloatArray[i + width*height])
                val b = conversion(origFloatArray[i + 2*width*height])
                pixels[i] = Color.argb(255, r, g, b) // you might need to import for rgb()
            } else {
                val r = conversion(resultFloatArray[i])
                val g = conversion(resultFloatArray[i + width*height])
                val b = conversion(resultFloatArray[i + 2*width*height])
                pixels[i] = Color.argb(255, r, g, b) // you might need to import for rgb()
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        return bmp
    }

    companion object {
        const val IS_NNAPI = false
        val MODEL_NAME = if (IS_NNAPI) "migan_nnapi_notlite.pt" else "no_opt_mobile_migan_model.ptl"
    }
}