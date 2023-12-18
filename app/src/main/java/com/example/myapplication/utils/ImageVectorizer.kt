package com.example.myapplication.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import com.example.myapplication.MainActivity
import com.example.myapplication.classes.ImageVector
import com.example.myapplication.classes.PreprocessedTable
import com.example.myapplication.imageProcessor.Image2Vec
import com.example.myapplication.imageProcessor.ObjectDetection
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

class ImageVectorizer(private val context: Context, private val listener: ImageVectorizerListener) {
    private val objectDetection: ObjectDetection = ObjectDetection(context)
    private val image2Vec: Image2Vec = Image2Vec(context)

    init {
        Thread { loadTable() }.start()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun loadTable() {
        val cachedFile = File(context.externalCacheDir, MainActivity.PREPROCESSED_FILE_NAME)

        if (cachedFile.exists()) {
            val inputStream = cachedFile.inputStream()
            listener.onTableReady(Json.decodeFromStream(inputStream))
            inputStream.close()

            Log.d(MainActivity.TAG, "LOAD_CACHE_DONE")
        } else {
            val preprocessedTable = readImages()
            listener.onTableReady(preprocessedTable)

            val outputStream = cachedFile.outputStream()
            Json.encodeToStream(preprocessedTable, outputStream)
            outputStream.close()

            Log.d(MainActivity.TAG, "READ_IMAGE_DONE")
        }
    }

    fun getImagesFromPath(path: String):Pair<Number,ArrayList<Bitmap>>{
        val imageFile = File(path)

        if (!imageFile.exists() || !imageFile.isFile) {
            println("Invalid image file path.")
            return Pair(0,arrayListOf<Bitmap>())
        }
        val time = imageFile.lastModified()/1000
        val name = imageFile.name
        val imageBitmapList = arrayListOf<Bitmap>()
        var origin = 0

        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )

        val selection = "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? ) AND (${MediaStore.Images.ImageColumns.DATE_ADDED} BETWEEN ? AND ?) "
        val selectionArgs = arrayOf("DCIM/PPAI%",(time-10000).toString(),(time+10000).toString())
        val sortOrder = "${MediaStore.Images.ImageColumns.DATE_ADDED} ASC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use {
            val data = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)


            while (it.moveToNext()) {
                val imageLocation = it.getString(data)
                val imageFile = File(imageLocation)
                if (imageFile.exists()) {
                    if (imageFile.name == name){
                        origin = imageBitmapList.size
                    }
                    val bm = BitmapFactory.decodeFile(imageLocation)
                    Log.d(MainActivity.TAG, imageLocation)
                    imageBitmapList.add(bm)

                }
            }
        }
        return Pair(origin,imageBitmapList)
        }
    @SuppressLint("InlinedApi")
    private fun readImages(): PreprocessedTable {
        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("DCIM/PPAI%") // Test was my folder name
        val sortOrder = "${MediaStore.Images.ImageColumns.DATE_ADDED} DESC"
        val imageVectorList = arrayListOf<ImageVector>()

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use {
            val data = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val imageLocation = it.getString(data)
                val imageFile = File(imageLocation)
                if (imageFile.exists()) {
                    val bm = BitmapFactory.decodeFile(imageLocation)
                    Log.d(MainActivity.TAG, imageLocation)
                    val objectVec = objectDetection.inference(bm)
                    val embeddingVec = image2Vec.getVector(bm)

                    imageVectorList.add(ImageVector(imageLocation, objectVec, embeddingVec))
                }
            }
        }
        return PreprocessedTable(imageVectorList)
    }

    interface ImageVectorizerListener {
        fun onTableReady(preprocessedTable: PreprocessedTable)
    }
}