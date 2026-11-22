package com.medidiary.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Phase 1 (CLAUDE.md §4.1): PCM 16 kHz / 16-bit / mono WAV into app-private cache.
 * The file is deleted by the caller after transcription.
 */
class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 60
    }

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    @Volatile private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    /** Suspends until [requestStop], cancellation, or [MAX_SECONDS]. Requires RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    suspend fun record(output: File): File = withContext(Dispatchers.IO) {
        stopRequested = false
        _elapsedMs.value = 0
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2) // ~200 ms
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize,
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }

        val raf = RandomAccessFile(output, "rw")
        raf.setLength(0)
        raf.write(ByteArray(44)) // header placeholder
        var dataBytes = 0L
        val buf = ShortArray(bufSize / 2)
        val bytes = ByteArray(buf.size * 2)
        val maxSamples = SAMPLE_RATE.toLong() * MAX_SECONDS
        var samples = 0L
        try {
            rec.startRecording()
            while (coroutineContext.isActive && !stopRequested && samples < maxSamples) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buf[i].toInt()
                    bytes[i * 2] = (s and 0xff).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
                    sum += s.toDouble() * s
                }
                raf.write(bytes, 0, n * 2)
                dataBytes += n * 2
                samples += n
                _amplitude.value = (sqrt(sum / n) / 6000.0).toFloat().coerceIn(0f, 1f)
                _elapsedMs.value = samples * 1000 / SAMPLE_RATE
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            writeHeader(raf, dataBytes)
            raf.close()
            _amplitude.value = 0f
        }
        output
    }

    private fun writeHeader(raf: RandomAccessFile, dataLen: Long) {
        val byteRate = SAMPLE_RATE * 2
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + dataLen).toInt()).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        h.putInt(SAMPLE_RATE).putInt(byteRate).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(dataLen.toInt())
        raf.seek(0)
        raf.write(h.array())
    }
}
