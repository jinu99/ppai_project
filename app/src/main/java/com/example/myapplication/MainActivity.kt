package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.drawable.toDrawable
import com.example.myapplication.classes.PreprocessedTable
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.silenceDetection.VoiceActivityDetection
import com.example.myapplication.utils.ImageVectorizer
import com.example.myapplication.utils.TableComparer


class MainActivity : AppCompatActivity(), VoiceActivityDetection.VadListener,
    ImageVectorizer.ImageVectorizerListener {
    private var _activityMainBinding: ActivityMainBinding? = null
    private lateinit var sim_list: List<Pair<String, String>>
    private var count: Int = 0
    private val activityMainBinding
        get() = _activityMainBinding!!

    private lateinit var voiceActivityDetection: VoiceActivityDetection
    private lateinit var imageVectorizer: ImageVectorizer
    private lateinit var preprocessedTableA: PreprocessedTable
    private lateinit var preprocessedTableB: PreprocessedTable

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
//        Log.d("TESTT","CLICK")
        activityMainBinding.button.setOnClickListener {
//            Log.d("CLICK","CLICK")
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
    override fun onTableReady(preprocessedTableA: PreprocessedTable,preprocessedTableB: PreprocessedTable) {
        var pat = preprocessedTableA.vectors[0].name
        var result = TableComparer().imageSimilarity(preprocessedTableA,preprocessedTableB)
        Log.d("READY",result.toString())
        this.sim_list = result
//        Log.d("SIMMLIST",sim_list.toString())
        this.preprocessedTableA = preprocessedTableA
        this.preprocessedTableB = preprocessedTableB
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
            //and (color == Color.RED)
            if ((!sim_list.isNullOrEmpty()) and (color == Color.RED) ) {
//                Log.d("Count",count.toString())
                count = count +1
//                Log.d("SIMI1",sim_list[count].toString())
                var images1 = imageVectorizer.getImagesFromPath(sim_list[count].first,"DCIM/UserA%")
                var images2 = imageVectorizer.getImagesFromPath(sim_list[count].second,"DCIM/UserB%")
                var index1 = images1.first.toInt()
                var index2 = images2.first.toInt()
//                Log.d("VIEW1",index1.toString())
//                Log.d("VIEW2",index2.toString())
//                Log.d("VIEW3",images1.second.toString())
//                Log.d("VIEW4",images2.second.toString())
                if (!images1.second.isNullOrEmpty() and !images2.second.isNullOrEmpty()){
                    activityMainBinding.alpha1.setImageURI(Uri.parse("https://cdn-icons-png.flaticon.com/512/75/75519.png"))
                    activityMainBinding.alpha3.setImageURI(Uri.parse("https://cdn-icons-png.flaticon.com/512/75/75519.png"))
                    activityMainBinding.beta1.setImageURI(Uri.parse("https://cdn-icons-png.flaticon.com/512/75/75519.png"))
                    activityMainBinding.beta3.setImageURI(Uri.parse("https://cdn-icons-png.flaticon.com/512/75/75519.png"))
                    activityMainBinding.alpha2.setImageDrawable(images1.second[index1].toDrawable(resources))
                    activityMainBinding.beta2.setImageDrawable(images2.second[index2].toDrawable(resources))
                    if (index1 > 0){
                        activityMainBinding.alpha1.setImageDrawable(images1.second[index1-1].toDrawable(resources))
                    }
                    if ((images1.second.size-1) > index1) {
                        activityMainBinding.alpha3.setImageDrawable(images1.second[index1+1].toDrawable(resources))
                    }
                    if (index2 > 0){
                        activityMainBinding.beta1.setImageDrawable(images2.second[index2-1].toDrawable(resources))
                    }
                    if ((images2.second.size-1) > index2) {
                        activityMainBinding.beta3.setImageDrawable(images2.second[index2+1].toDrawable(resources))
                    }
                }




            }
        }




    }

    companion object {
        const val TAG = "MainActivity"
        const val PREPROCESSED_FILE_NAME_A = "preprocessedTableA.json"
        const val PREPROCESSED_FILE_NAME_B = "preprocessedTableB.json"
    }
}