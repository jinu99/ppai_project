package com.example.myapplication.imageProcessor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.myapplication.utils.assetFilePath
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils

class Image2Vec(context: Context) {
    private val module: Module = Module.load(assetFilePath(context, MODEL_NAME))

    fun getVector(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, false)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resized,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
//        Log.d(TAG, inputTensor.shape().contentToString())
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val outputArray = outputTensor.dataAsFloatArray
//        Log.d(TAG, outputTensor.shape().contentToString())
        return outputArray
    }

    companion object {
        const val MODEL_NAME = "resnet18.ptl"
        const val IMAGE_SIZE = 224
        const val TAG = "Image2Vec"
    }
}

