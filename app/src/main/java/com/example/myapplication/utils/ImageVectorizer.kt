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
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

class ImageVectorizer(private val context: Context, private val listener: ImageVectorizerListener) {
    private val objectDetection: ObjectDetection = ObjectDetection(context)
    private val image2Vec: Image2Vec = Image2Vec(context)

    init {
        Thread { loadTable() }.start()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun loadTable() {
        val cachedFileA = File(context.externalCacheDir, MainActivity.PREPROCESSED_FILE_NAME_A)
//        Log.d("CACHE1",cachedFileA.toString())
        val cachedFileB = File(context.externalCacheDir, MainActivity.PREPROCESSED_FILE_NAME_B)
//        Log.d("CACHE2",cachedFileB.toString())
        if (cachedFileA.exists() and cachedFileB.exists()) {
            val inputStreamA = cachedFileA.inputStream()
            val inputStreamB = cachedFileB.inputStream()
            listener.onTableReady(Json.decodeFromStream(inputStreamA),Json.decodeFromStream(inputStreamB))
            inputStreamA.close()
            inputStreamB.close()

            Log.d(MainActivity.TAG, "LOAD_CACHE_DONE")
        } else {
            val preprocessedTable = readImages()
            listener.onTableReady(preprocessedTable.first,preprocessedTable.second)
            val outputStreamA = cachedFileA.outputStream()
            Json.encodeToStream(preprocessedTable.first, outputStreamA)
            outputStreamA.close()
            val outputStreamB = cachedFileA.outputStream()
            Json.encodeToStream(preprocessedTable.second, outputStreamB)
            outputStreamB.close()




            Log.d(MainActivity.TAG, "READ_IMAGE_DONE")
        }
    }


    fun getImagesFromPath(path: String,which:String):Pair<Number,ArrayList<Bitmap>>{
        val imageFile = File(path)

        if (!imageFile.exists() || !imageFile.isFile) {
            println("Invalid image file path.")
            return Pair(0,arrayListOf<Bitmap>())
        }
        val filePath = Paths.get(path)
        val attributes: BasicFileAttributes = Files.readAttributes(filePath, BasicFileAttributes::class.java)
        val time = attributes.creationTime().toMillis()
//        Log.d("TIME",time.toString())
        val name = imageFile.name
//        Log.d("NAME",name.toString())
        val imageBitmapList = arrayListOf<Bitmap>()
        var origin = -1

        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )
// AND ${MediaStore.Images.ImageColumns.DATE_ADDED} BETWEEN ? AND ?     ,(time-10000000000).toString(),(time+10000000000).toString())
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.ImageColumns.DATE_TAKEN} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(which,(time-12000000).toString(),(time+12000000).toString())
        val sortOrder = "${MediaStore.Images.ImageColumns.DATE_TAKEN} ASC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use {
            val data = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val t = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (it.moveToNext()) {
                val imageLocation = it.getString(data)
                val tt = it.getString(t)
//                if(tt!= null){
//                    Log.d("TAKEN",tt)
//                }
                val imageFile = File(imageLocation)
                if (imageFile.exists()) {
                    if (imageFile.name == name){
                        origin = imageBitmapList.size
                    }
                    val bm = BitmapFactory.decodeFile(imageLocation)
//                    Log.d(MainActivity.TAG, imageLocation)
                    imageBitmapList.add(bm)

                }
            }
        }
        if (origin < 0){
//            Log.d("NOMATCH",path)
            return Pair(0,arrayListOf<Bitmap>(BitmapFactory.decodeFile(path)))

        }
        return Pair(origin,imageBitmapList)
    }


    @SuppressLint("InlinedApi")
    private fun readImages(): Pair<PreprocessedTable,PreprocessedTable> {
        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgsA = arrayOf("DCIM/UserA%") // Test was my folder name
        val selectionArgsB = arrayOf("DCIM/UserB%") // Test was my folder name
        val sortOrder = "${MediaStore.Images.ImageColumns.DATE_ADDED} DESC"
        val imageVectorListA = arrayListOf<ImageVector>()
        val imageVectorListB = arrayListOf<ImageVector>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgsA,
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

                    imageVectorListA.add(ImageVector(imageLocation, objectVec, embeddingVec))
                }
            }
        }
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgsB,
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

                    imageVectorListB.add(ImageVector(imageLocation, objectVec, embeddingVec))
                }
            }
        }
        return Pair<PreprocessedTable,PreprocessedTable>(PreprocessedTable(imageVectorListA),PreprocessedTable(imageVectorListB))
    }

    interface ImageVectorizerListener {
        fun onTableReady(preprocessedTableA: PreprocessedTable,preprocessedTableB: PreprocessedTable)
    }
}