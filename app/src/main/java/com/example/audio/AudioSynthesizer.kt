package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

class AudioSynthesizer(context: Context) {
    private val tag = "AudioSynthesizer"
    private var isMuted = false

    private val soundPool: SoundPool
    private var jumpSoundId = 0
    private var scoreSoundId = 0
    private var hitSoundId = 0
    private var gameOverSoundId = 0

    init {
        // Configure SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Move all heavy synthesis and disk I/O onto a background thread
        // to prevent main-thread freezing and input channel errors (ANRs)
        Thread {
            try {
                // Synthesize short PCM audio clips once at startup
                val sampleRate = 22050

                val jumpSamples = generateSweep(420f, 850f, 0.08f, sampleRate)
                val scoreSamples = generateScoreSound(sampleRate)
                val hitSamples = generateSweep(600f, 150f, 0.15f, sampleRate)
                val gameOverSamples = generateSweep(250f, 40f, 0.45f, sampleRate)

                // Convert raw clips into temporary WAV files and load into SoundPool
                val jumpFile = createWavTempFile(context, "jump", jumpSamples, sampleRate)
                val scoreFile = createWavTempFile(context, "score", scoreSamples, sampleRate)
                val hitFile = createWavTempFile(context, "hit", hitSamples, sampleRate)
                val gameOverFile = createWavTempFile(context, "gameover", gameOverSamples, sampleRate)

                if (jumpFile != null) jumpSoundId = soundPool.load(jumpFile.absolutePath, 1)
                if (scoreFile != null) scoreSoundId = soundPool.load(scoreFile.absolutePath, 1)
                if (hitFile != null) hitSoundId = soundPool.load(hitFile.absolutePath, 1)
                if (gameOverFile != null) gameOverSoundId = soundPool.load(gameOverFile.absolutePath, 1)

            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize synthesized sound pool", e)
            }
        }.start()
    }

    fun toggleMute() {
        isMuted = !isMuted
    }

    fun isMuted(): Boolean = isMuted

    fun playJump() {
        if (isMuted || jumpSoundId == 0) return
        soundPool.play(jumpSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun playScore() {
        if (isMuted || scoreSoundId == 0) return
        soundPool.play(scoreSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun playHit() {
        if (isMuted || hitSoundId == 0) return
        soundPool.play(hitSoundId, 1.0f, 1.0f, 2, 0, 1.0f)
    }

    fun playGameOver() {
        if (isMuted || gameOverSoundId == 0) return
        soundPool.play(gameOverSoundId, 1.0f, 1.0f, 2, 0, 1.0f)
    }

    /**
     * Clean up SoundPool when application / ViewModel is finished
     */
    fun release() {
        try {
            soundPool.release()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun generateNote(frequency: Float, duration: Float, sampleRate: Int): ShortArray {
        val numSamples = (duration * sampleRate).toInt()
        val samples = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val angle = 2.0 * Math.PI * frequency * t
            val envelope = Math.exp(-3.0 * t)
            samples[i] = (sin(angle) * 16383.0 * envelope).toInt().toShort()
        }
        return samples
    }

    private fun generateSweep(startFreq: Float, endFreq: Float, duration: Float, sampleRate: Int): ShortArray {
        val numSamples = (duration * sampleRate).toInt()
        val samples = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val ratio = i.toFloat() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * ratio
            val t = i.toDouble() / sampleRate
            val angle = 2.0 * Math.PI * currentFreq * t
            val envelope = if (ratio > 0.8f) (1.0f - ratio) / 0.2f else 1.0f
            samples[i] = (sin(angle) * 16383.0 * envelope).toInt().toShort()
        }
        return samples
    }

    private fun generateScoreSound(sampleRate: Int): ShortArray {
        val part1 = generateNote(523.25f, 0.05f, sampleRate)
        val part2 = generateNote(659.25f, 0.12f, sampleRate)
        val combined = ShortArray(part1.size + part2.size)
        System.arraycopy(part1, 0, combined, 0, part1.size)
        System.arraycopy(part2, 0, combined, part1.size, part2.size)
        return combined
    }

    /**
     * Writes ShortArray PCM audio data as a temporary standardized WAV file in application cache.
     */
    private fun createWavTempFile(context: Context, prefix: String, samples: ShortArray, sampleRate: Int): File? {
        return try {
            val tempFile = File.createTempFile("synth_$prefix", ".wav", context.cacheDir)
            tempFile.deleteOnExit() // Autoclean on VM exit

            val totalAudioLen = samples.size * 2
            val totalDataLen = totalAudioLen + 36
            val channels = 1
            val byteRate = sampleRate * channels * 2

            val header = ByteArray(44)
            header[0] = 'R'.toByte() // RIFF
            header[1] = 'I'.toByte()
            header[2] = 'F'.toByte()
            header[3] = 'F'.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.toByte() // WAVE
            header[9] = 'A'.toByte()
            header[10] = 'V'.toByte()
            header[11] = 'E'.toByte()
            header[12] = 'f'.toByte() // 'fmt '
            header[13] = 'm'.toByte()
            header[14] = 't'.toByte()
            header[15] = ' '.toByte()
            header[16] = 16 // Subchunk1Size
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // Format (PCM)
            header[21] = 0
            header[22] = channels.toByte() // Channels count = 1
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte() // Sample rate
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte() // Byte rate
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2.toByte() // Block align = Channels * bytesPerSample / 8
            header[33] = 0
            header[34] = 16 // Bits per sample
            header[35] = 0
            header[36] = 'd'.toByte() // 'data' chunk
            header[37] = 'a'.toByte()
            header[38] = 't'.toByte()
            header[39] = 'a'.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            FileOutputStream(tempFile).use { fos ->
                fos.write(header)
                // Convert short elements to low-endian byte array
                val dataBuffer = ByteArray(totalAudioLen)
                for (i in samples.indices) {
                    val s = samples[i]
                    dataBuffer[i * 2] = (s.toInt() and 0xff).toByte()
                    dataBuffer[i * 2 + 1] = ((s.toInt() shr 8) and 0xff).toByte()
                }
                fos.write(dataBuffer)
            }
            tempFile
        } catch (e: Exception) {
            Log.e(tag, "Failed to create WAV cache file for $prefix", e)
            null
        }
    }
}
