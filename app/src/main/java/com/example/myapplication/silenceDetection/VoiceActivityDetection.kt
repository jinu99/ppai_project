package com.example.myapplication.silenceDetection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.myapplication.MainActivity
import com.example.myapplication.imageProcessor.HumanSegmentation
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import org.pytorch.Module
import java.util.*
import kotlin.concurrent.thread

class VoiceActivityDetection(private val context: Context, private val listener: VadListener) {
    private lateinit var vad: VadSilero
    private lateinit var audioRecord: AudioRecord
    private lateinit var recordingThread: Thread

    private var isSpeech = false

    private var isRecordingAudio = false

    private val windowTime = 5 // 5s
    private val queue: Queue<Short> = LinkedList()
    private var queueSize = 0
    private var bufferSize = 0

    init {
        initializeVAD()
        initializeAudioRecord()
    }

    private fun initializeVAD() {
        vad = Vad.builder()
            .setContext(context)
            .setSampleRate(SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(FrameSize.FRAME_SIZE_512)
            .setMode(Mode.NORMAL)
            .setSilenceDurationMs(300)
            .setSpeechDurationMs(50)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudioRecord() {
        val sampleRate = if (vad.sampleRate == SampleRate.SAMPLE_RATE_16K) 16000 else 8000
        val frameSize = if (vad.frameSize == FrameSize.FRAME_SIZE_512) 512 else if (vad.frameSize == FrameSize.FRAME_SIZE_1024) 1024 else 1536
        bufferSize = frameSize * 2

        audioRecord = AudioRecord(
            AUDIO_SOURCE,
            sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        queueSize = windowTime * sampleRate / frameSize
    }

    fun startRecording() {
        // First check whether the above object actually initialized
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "error initializing AudioRecord");
            return
        } else {
            // Now start the audio recording
            audioRecord.startRecording()
            isRecordingAudio = true

            recordingThread = thread(true) {
                val data = ByteArray(bufferSize)
                while (isRecordingAudio) {
                    audioRecord.read(data, 0, data.size)

                    val result = movingAverage(if (vad.isSpeech(data)) 1 else 0)
//                    Log.d(TAG, result.toString())
                    val isSpeechDetected = result > SPEECH_THRESHOLD
                    if (!isSpeech && isSpeechDetected) listener.onSpeechDetected()
                    else if (isSpeech && !isSpeechDetected) listener.onSilenceDetected()
                    isSpeech = isSpeechDetected
                }
            }
        }
    }

    fun stopRecording() {
        audioRecord.stop()
        isRecordingAudio = false
    }

    fun destroy() {
        vad.close()
        stopRecording()
    }

    fun isRecording(): Boolean = isRecordingAudio

    private fun movingAverage(result: Short): Float {
        queue.add(result)
        if (queue.size > queueSize) {
            queue.remove()
        }

        return queue.sum().toFloat() / queueSize
    }

    interface VadListener {
        fun onSpeechDetected()
        fun onSilenceDetected()
    }

    companion object {
        const val TAG = "VoiceActivityDetection"
        const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SPEECH_THRESHOLD = 0.1f
    }
}