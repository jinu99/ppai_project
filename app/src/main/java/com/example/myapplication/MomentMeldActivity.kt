package com.example.myapplication

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.classes.PreprocessedTable
import com.example.myapplication.databinding.ActivityMomentMeldBinding
import com.example.myapplication.silenceDetection.VoiceActivityDetection
import com.example.myapplication.utils.SocketHandler
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
    }

    private fun onTableReady() {
        voiceActivityDetection.startRecording()
    }

    private fun initializeVAD() {
        voiceActivityDetection = VoiceActivityDetection(this, this)
    }

    private fun changeButtonColor(color: Int) {
        runOnUiThread {
            activityMomentMeldBinding.button.setBackgroundColor(color)
        }
    }

    companion object {
        const val TAG = "MomentMeldActivity"
    }
}