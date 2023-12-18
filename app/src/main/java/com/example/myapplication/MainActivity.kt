package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.myapplication.classes.ImageVector
import com.example.myapplication.classes.PreprocessedTable
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.imageProcessor.HumanSegmentation
import com.example.myapplication.imageProcessor.Image2Vec
import com.example.myapplication.imageProcessor.ImageInpainting
import com.example.myapplication.imageProcessor.ObjectDetection
import com.example.myapplication.silenceDetection.VoiceActivityDetection
import com.example.myapplication.utils.ImageVectorizer
import com.example.myapplication.utils.TableComparer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File


class MainActivity : AppCompatActivity(), VoiceActivityDetection.VadListener,
    ImageVectorizer.ImageVectorizerListener {
    private var _activityMainBinding: ActivityMainBinding? = null

    private val activityMainBinding
        get() = _activityMainBinding!!

    private lateinit var voiceActivityDetection: VoiceActivityDetection
    private lateinit var imageVectorizer: ImageVectorizer
    private lateinit var preprocessedTable: PreprocessedTable

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return
        } else {
            initializeModels()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 1)
            return
        } else {
            initializeImageVectorizer()
        }

        activityMainBinding.button.setOnClickListener {
            if (voiceActivityDetection.isRecording())
                voiceActivityDetection.stopRecording()
            else
                voiceActivityDetection.startRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceActivityDetection.destroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) initializeModels()
        if (requestCode == 1) initializeImageVectorizer()
    }

    // VAD listener functions
    override fun onSpeechDetected() {
        changeButtonColor(Color.BLUE)
    }

    // VAD listener functions
    override fun onSilenceDetected() {
        changeButtonColor(Color.RED)
    }

    // ImageVectorizer listener functions
    override fun onTableReady(preprocessedTable: PreprocessedTable) {
        var pat = preprocessedTable.vectors[0].name
        Log.d("Table",pat)
        var lis = imageVectorizer.getImagesFromPath(pat)
        Log.d("Table",lis.toString())
        Log.d("Table",preprocessedTable.vectors.toString())
        var result = TableComparer().imageSimilarity(preprocessedTable,preprocessedTable)
        Log.d("Table",result.toString())
        this.preprocessedTable = preprocessedTable
    }

    private fun initializeModels() {
        voiceActivityDetection = VoiceActivityDetection(this, this)
    }

    private fun initializeImageVectorizer() {
        imageVectorizer = ImageVectorizer(this, this)
    }

    private fun changeButtonColor(color: Int) {
        runOnUiThread {
            activityMainBinding.button.setBackgroundColor(color)
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val PREPROCESSED_FILE_NAME = "preprocessedTable.json"
    }
}