package com.laputa.rtmp

import android.media.*
import android.util.Log
import com.laputa.rtmp.entity.RTMPPacket
import java.nio.ByteBuffer

/**
 * Author by xpl, Date on 2021/5/27.
 */
class AudioCodec(private val show: Show) : Thread() {
    private lateinit var mMediaCodec: MediaCodec
    private lateinit var mAudioRecord: AudioRecord
    var isLive = true
    var minBufferSize: Int = 0

    fun startLive() {
        try {
            val mediaFormat =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1)
                    .apply {
                        // 质量
                        setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, 64_000)

                    }
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                .apply {
                    configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }

            // 获取最小缓冲区 44100 单声道 pcm16
            minBufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            mAudioRecord = AudioRecord(
                // 数据远
                MediaRecorder.AudioSource.MIC,
                // 采样率：1s内对声音信号的采样次数，越高越真实自然。 44100
                44100,
                // 声道 单声道
                AudioFormat.CHANNEL_IN_MONO,
                // 采样位 16位 2字节
                AudioFormat.ENCODING_PCM_16BIT,
                // 缓冲区
                minBufferSize
            )
            start()

        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun run() {
        super.run()
        mMediaCodec.start()
        // 在获取播放的音频数据之前，先发送audio special config
       show.addPacket( RTMPPacket(byteArrayOf(0x12, 0x08), RTMPPacket.RTMP_PACKET_TYPE_AUDIO_HEAD, 0))

        val buffer = ByteArray(minBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        while (isLive) {
            // 1得到采集的声音数据
            val len = mAudioRecord.read(buffer, 0, buffer.size)
            if (len <= 0) {
                continue
            }

            logger("AudioCodec ing ....")

            // 2交给编码器编码
            // 获取输入队列中能够使用的容器下标
            val index = mMediaCodec.dequeueInputBuffer(0) //阻塞
            if (index >= 0) {
                val byteBuffer: ByteBuffer? = mMediaCodec.getInputBuffer(index)
                byteBuffer?.clear()
                byteBuffer?.put(buffer, 0, len)
                // 通知容器我们使用完了，可以拿去编码了
                mMediaCodec.queueInputBuffer(index, 0, len,/*时间戳：微秒*/System.nanoTime() / 1000, 0)
            }

            // 获取编码之后的数据
            var bufferInfoIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0)
            // 每次从编码器取完，再往编码器塞数据
            while (bufferInfoIndex >= 0 && isLive) {
                val outputBuffer = mMediaCodec.getOutputBuffer(bufferInfoIndex)
                val data = ByteArray(bufferInfo.size)
                outputBuffer?.get(data)
                // TODO 送去推流
                // FLV格式相同
                if (startTime == 0L) {
                    startTime = bufferInfo.presentationTimeUs / 1000
                }
                logger("AudioCodec add packet ....")
                show.addPacket(RTMPPacket(
                    data,
                    RTMPPacket.RTMP_PACKET_TYPE_AUDIO_DATA,
                    bufferInfo.presentationTimeUs / 1000 - startTime
                ))

                // 释放输出队列，让其能存放数据
                mMediaCodec.releaseOutputBuffer(bufferInfoIndex, false)
                bufferInfoIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0)
            }
        }
        isLive = false
        startTime = 0
    }

    private var startTime: Long = 0

    fun stopLive() {
        isLive = false
        try {
            join()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}