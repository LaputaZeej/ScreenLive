package com.laputa.rtmp

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import androidx.core.os.bundleOf
import com.laputa.rtmp.entity.RTMPPacket
import java.nio.channels.spi.SelectorProvider

/**
 * Author by xpl, Date on 2021/4/6.
 */

class VideoCodec(private val show: Show) : Thread() {
    private lateinit var mediaCodec: MediaCodec
    private var vd: VirtualDisplay? = null
    private var mMp: MediaProjection? = null
    private var start: Boolean = false
    private var timeStamp: Long = 0L

    fun startLive(mp: MediaProjection) {
        this.mMp = mp
        start = true
        // 开始编码
        // 配置编码器
        val mediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC/*h264*/, S_WIDTH, S_HEIGHT)
                .apply {
//                    setInteger(MediaFormat.KEY_MAX_WIDTH,S_WIDTH)
//                    setInteger(MediaFormat.KEY_MAX_HEIGHT,S_HEIGHT)
//                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,S_WIDTH*S_HEIGHT)
                    // 码率 4K
                    setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
                    // 帧率
                    setInteger(MediaFormat.KEY_FRAME_RATE, 15)
                    // 关键帧 i完整/p参照前面ip/前后ip
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // 2s
                    // 数据源格式

                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                }
        try {
            // 创建编码MediaCodec
            //mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val codecInfos = selectSupportCodec(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec = MediaCodec.createByCodecName(codecInfos?.name ?: "")

            logger("configure ing")
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // 创建surface 离屏画布
            val surface = mediaCodec.createInputSurface()
            logger("surface = $surface")
            vd = mp.createVirtualDisplay(
                "laputa_show", S_WIDTH, S_HEIGHT, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, null, null
            )
            start()
        }catch (e:Throwable){
            e.printStackTrace()
            logger("surface = ${e.message}")
        }
    }

    override fun run() {
        super.run()
        // 获取节码数据
        mediaCodec.start()
        val bufferInfo = MediaCodec.BufferInfo()
        while (start) {
            refreshIFrameByMySelf()
            // 获取编码之后的数据 -1：稍后重试；-2： -3：
            val index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10)
            // success -> index下标
            logger("VideoCodec img $index ....")
            if (index >= 0) {
                // 取出编码好的数据
                val data = ByteArray(bufferInfo.size)
                val buffer = mediaCodec.getOutputBuffer(index)
                buffer?.get(data)
                // TODO 按照rtmp格式封包，发送出去
                if (startTime == 0L) {
                    startTime = bufferInfo.presentationTimeUs / 1000
                }

                logger("Video add packet ....")
                show.addPacket(
                    RTMPPacket(
                        data,
                        RTMPPacket.RTMP_PACKET_TYPE_VIDEO,
                        bufferInfo.presentationTimeUs / 1000 - startTime
                    )
                )

                // 释放 让index位置能够存放其他数据
                mediaCodec.releaseOutputBuffer(index, false)

            }
        }
        start = false
        startTime = 0
        mediaCodec.stop()
        mediaCodec.release()
        vd?.release()
        vd=null
        mMp?.stop()
        mMp=null


    }

    fun stopLive() {
        start = false
        try {
            join()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // 手动刷新关键帧 -> mediaCodec 除了第一关键帧后面没有
    private fun refreshIFrameByMySelf() {
        if (timeStamp != 0L) {
            if (System.currentTimeMillis() - timeStamp >= 2000) {
                mediaCodec.setParameters(bundleOf(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME to 0))
                timeStamp = System.currentTimeMillis()
            }
        } else {
            timeStamp = System.currentTimeMillis()
        }
    }

    private var startTime: Long = 0

    companion object {
        private const val S_WIDTH = 360
        private const val S_HEIGHT = 480

        @JvmStatic
        fun selectSupportCodec(mimeType: String): MediaCodecInfo? {
            val numCodecs: Int = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo: MediaCodecInfo = MediaCodecList.getCodecInfoAt(i)
                // 判断是否为编码器，否则直接进入下一次循环
                if (!codecInfo.isEncoder) {
                    continue
                }
                // 如果是编码器，判断是否支持Mime类型
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        return codecInfo
                    }
                }
            }
            return null
        }

    }
}