package com.blockline.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A tiny original UI click synthesized as PCM at runtime; no third-party or game assets.
 * SoundPool is only created when the user enables click sounds.
 */
class PixelClickSound(context: Context) : AutoCloseable {
    private val soundFile = File(context.cacheDir, "blockline-ui-click.wav")
    @Volatile private var loadedSoundId = 0
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    init {
        runCatching {
            writeWave(soundFile)
            pool.setOnLoadCompleteListener { _, soundId, status ->
                if (status == 0) loadedSoundId = soundId
            }
            pool.load(soundFile.absolutePath, 1)
        }.onFailure {
            loadedSoundId = 0
        }
    }

    fun play() {
        val id = loadedSoundId
        if (id != 0) pool.play(id, 0.22f, 0.22f, 1, 0, 1f)
    }

    override fun close() {
        loadedSoundId = 0
        runCatching { pool.release() }
        runCatching { soundFile.delete() }
    }

    private fun writeWave(target: File) {
        val sampleRate = 22_050
        val samples = sampleRate / 24
        val payloadBytes = samples * 2
        val buffer = ByteBuffer.allocate(44 + payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
        val ascii: Charset = Charsets.US_ASCII
        buffer.put("RIFF".toByteArray(ascii))
        buffer.putInt(36 + payloadBytes)
        buffer.put("WAVE".toByteArray(ascii))
        buffer.put("fmt ".toByteArray(ascii))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(ascii))
        buffer.putInt(payloadBytes)
        for (index in 0 until samples) {
            val time = index.toDouble() / sampleRate
            val envelope = exp(-index.toDouble() / (samples * 0.22))
            val tone = sin(2.0 * PI * 940.0 * time)
            val value = (tone * envelope * 5_000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer.putShort(value.toShort())
        }
        target.parentFile?.mkdirs()
        target.writeBytes(buffer.array())
    }
}
