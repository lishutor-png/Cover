package com.example.mp3

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes raw PCM 16-bit stereo audio data directly into high-fidelity MPEG-4 AAC (.m4a)
 * using Android's native MediaCodec hardware/software encoder and MediaMuxer.
 */
class PcmToAacMuxer(
    private val outputFile: File,
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 2,
    private val bitRate: Int = 192000
) {
    private val TAG = "PcmToAacMuxer"

    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioTrackIndex: Int = -1
    private var isMuxerStarted = false
    private var totalBytesRead = 0L

    fun init() {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mediaCodec = codec

            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.parentFile?.mkdirs()

            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            isMuxerStarted = false
            audioTrackIndex = -1
            totalBytesRead = 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PcmToAacMuxer", e)
            throw e
        }
    }

    /**
     * Feeds raw PCM 16-bit audio bytes to MediaCodec encoder.
     */
    fun feedPcmData(pcmBytes: ByteArray, length: Int, isEndOfStream: Boolean = false) {
        val codec = mediaCodec ?: return
        if (length <= 0 && !isEndOfStream) return

        try {
            val inputBufferIndex = codec.dequeueInputBuffer(10000L)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    if (length > 0) {
                        inputBuffer.put(pcmBytes, 0, length)
                    }

                    // Calculate presentation timestamp based on PCM 16-bit stereo (4 bytes per sample frame)
                    val bytesPerFrame = channelCount * 2
                    val sampleFrames = totalBytesRead / bytesPerFrame
                    val presentationTimeUs = (sampleFrames * 1_000_000L) / sampleRate

                    val flags = if (isEndOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    codec.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs, flags)

                    totalBytesRead += length
                }
            }

            drainEncoder(isEndOfStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error during feedPcmData", e)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    throw IllegalStateException("Output format changed after muxer started")
                }
                val newFormat = codec.outputFormat
                audioTrackIndex = muxer.addTrack(newFormat)
                muxer.start()
                isMuxerStarted = true
            } else if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && isMuxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    fun finishAndRelease(): File? {
        try {
            // Signal EOS
            feedPcmData(ByteArray(0), 0, isEndOfStream = true)
        } catch (_: Exception) {}

        try {
            mediaCodec?.stop()
        } catch (_: Exception) {}
        try {
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
            }
        } catch (_: Exception) {}
        try {
            mediaMuxer?.release()
        } catch (_: Exception) {}
        mediaMuxer = null
        isMuxerStarted = false

        return if (outputFile.exists() && outputFile.length() > 0) {
            outputFile
        } else {
            null
        }
    }
}
