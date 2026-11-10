package io.github.rosale137.happysilentroommate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Reads the microphone and emits one normalised mean-square power value per [CHUNK_MS].
 * Samples live only in a small reused buffer: nothing is written to disk or sent anywhere.
 * The caller needs the RECORD_AUDIO permission before collecting.
 */
class AudioLevelMeter {

    @SuppressLint("MissingPermission")
    fun powers(): Flow<Double> = flow {
        val chunkSamples = SAMPLE_RATE * CHUNK_MS / 1000
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBytes, chunkSamples * 2 * 2),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone not available" }
        val buffer = ShortArray(chunkSamples)
        try {
            recorder.startRecording()
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) emit(meanSquare(buffer, read))
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_MS = 100
    }
}
