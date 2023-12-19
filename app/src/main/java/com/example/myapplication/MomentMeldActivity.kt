package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import com.example.myapplication.classes.PreprocessedTable
import com.example.myapplication.databinding.ActivityMomentMeldBinding
import com.example.myapplication.silenceDetection.VoiceActivityDetection
import com.example.myapplication.utils.SocketHandler
import com.example.myapplication.utils.TableComparer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.net.Socket


class MomentMeldActivity : AppCompatActivity(), VoiceActivityDetection.VadListener {
    private var _activityMomentMeldBinding: ActivityMomentMeldBinding? = null

    private val activityMomentMeldBinding
        get() = _activityMomentMeldBinding!!

    private lateinit var voiceActivityDetection: VoiceActivityDetection
    private var preprocessedTable: PreprocessedTable? = null
    private var preprocessedTableReceived: PreprocessedTable? = null

    private lateinit var role: String

    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    private val socket: Socket? = SocketHandler.socket
    private var isListening = false

    private lateinit var similarImageList: List<Pair<String, String>>
    private var isCalculated = false
    private var imageIdx = 0

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _activityMomentMeldBinding = ActivityMomentMeldBinding.inflate(layoutInflater)
        setContentView(activityMomentMeldBinding.root)

        if (intent.extras == null) {
            throw java.lang.Exception("No Intent")
        } else {
            role = intent.extras?.getString("role")!!
            preprocessedTable = Json.decodeFromString(intent.extras?.getString("preprocessedTable")!!)
        }

        if (role == MainActivity.HOST) { // Host
            initializeVAD()
            activityMomentMeldBinding.button.setOnClickListener {
                if (voiceActivityDetection.isRecording())
                    voiceActivityDetection.stopRecording()
                else
                    voiceActivityDetection.startRecording()
            }
            Thread {
                Thread.sleep(2000)
                var len: Int
                val buffer = ByteArray(1024)
                val byteArrayOutputStream = ByteArrayOutputStream()

                while (true) {
                    try {
                        len = socket?.inputStream?.read(buffer)!!
                        val data = buffer.copyOf(len)
                        byteArrayOutputStream.write(data)

                        socket.inputStream?.available()?.let { available ->
                            if (available == 0) {
                                preprocessedTableReceived = Json.decodeFromString(byteArrayOutputStream.toString())
                                runOnUiThread{activityMomentMeldBinding.logger.text = "${preprocessedTableReceived.hashCode()}"}
                                byteArrayOutputStream.reset()
                                onTableReady()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                }
            }.start()
        } else { // Client
            Thread {
                try {
                    outputStream = socket!!.getOutputStream()
                    outputStream.write(Json.encodeToString(preprocessedTable).encodeToByteArray())
                    outputStream.flush()
                    runOnUiThread{activityMomentMeldBinding.logger.text = "${preprocessedTable.hashCode()}"}
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()

            activityMomentMeldBinding.button.setBackgroundColor(Color.GRAY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceActivityDetection.destroy()
    }

    // VAD listener functions
    override fun onSpeechDetected() {
        changeButtonColor(Color.BLUE)
    }

    // VAD listener functions
    override fun onSilenceDetected() {
        changeButtonColor(Color.RED)
        outputStream = socket!!.getOutputStream()
        outputStream.write(similarImageList[imageIdx].second.encodeToByteArray())
        outputStream.flush()

        onReceiveMessage(similarImageList[imageIdx].first)
    }

    private fun onTableReady() { // HOST only
        voiceActivityDetection.startRecording()

        similarImageList = TableComparer().imageSimilarity(preprocessedTable!!, preprocessedTableReceived!!)
        isCalculated = true
    }

    private fun initializeVAD() {
        voiceActivityDetection = VoiceActivityDetection(this, this)
    }

    private fun changeButtonColor(color: Int) {
        runOnUiThread {
            activityMomentMeldBinding.button.setBackgroundColor(color)
        }
    }

    private fun startListening() {
        isListening = true
        Thread {
            while (isListening) {
                try {
                    var len: Int
                    val buffer = ByteArray(1024)
                    val byteArrayOutputStream = ByteArrayOutputStream()

                    while (true) {
                        len = socket?.inputStream?.read(buffer)!!
                        val data = buffer.copyOf(len)
                        byteArrayOutputStream.write(data)

                        socket.inputStream?.available()?.let { available ->
                            if (available == 0) {
                                onReceiveMessage(byteArrayOutputStream.toString())
                                byteArrayOutputStream.reset()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun onReceiveMessage(msg: String) {
        runOnUiThread {
            val bitmap = BitmapFactory.decodeFile(msg)
            activityMomentMeldBinding.logger.text = msg
            activityMomentMeldBinding.image.setImageDrawable(bitmap.toDrawable(resources))
        }
    }

    companion object {
        const val TAG = "MomentMeldActivity"
    }
}