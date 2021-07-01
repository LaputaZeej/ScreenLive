package com.laputa.rtmp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.laputa.rtmp.entity.RTMPPacket
import java.util.concurrent.LinkedBlockingQueue

/**
 * Author by xpl, Date on 2021/4/6.
 */
class Show : Runnable {
    private lateinit var manager: MediaProjectionManager
    private var url: String = ""
    private var mediaProjection: MediaProjection? = null
    private val mQueue: LinkedBlockingQueue<RTMPPacket> = LinkedBlockingQueue()
    private var isLiving = false

    fun start(activity: Activity, url: String) {
        this.url = url
        manager =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(manager.createScreenCaptureIntent(), RQ)
    }

    fun stopLive() {
        addPacket(RTMPPacket())
        isLiving = false
        disconnect()
    }


    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (RQ == requestCode && Activity.RESULT_OK == resultCode) {
            val mp = manager.getMediaProjection(resultCode, data!!)
            this.mediaProjection = mp
            // 连接
            LiveTaskManager.getInstance().execute(this)
        }
    }


    fun addPacket(rtmpPacket: RTMPPacket) {
        mQueue.offer(rtmpPacket)
    }

    private fun doWork(mp: MediaProjection) {
        logger("show ing ...")
        // 连接服务器
        if (!connect(url)) {
            // connect fail
            logger("show fail !")
            return
        }
        isLiving = true
        // 视频编码
        val videoCodec = VideoCodecJava(this)
        videoCodec.startLive(mediaProjection!!)
        // 音频编码
        val audioCodec = AudioCodecJava(this)
        audioCodec.startLive()
        // 发送数据包
        while (isLiving) {
            try {
                val take = mQueue.take()
                logger("show send packet ... $take")
                if (take?.buff?.isNotEmpty() == true) {
                    var sendPacket = sendPacket(take.buff, take.buff.size, take.type, take.tms)
//                    while (!sendPacket){
//                        Thread.sleep(100)
//                        sendPacket = sendPacket(take.buff, take.buff.size, take.type, take.tms)
//                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        isLiving = false
        videoCodec.stopLive()
        audioCodec.stopLive()
        mQueue.clear()
        disconnect()
    }


    external fun connect(url: String): Boolean
    external fun disconnect()
    external fun sendPacket(data: ByteArray, len: Int, type: Int, tms: Long): Boolean

    companion object {
        const val RQ = 100
    }

    override fun run() {
        doWork(mediaProjection!!)
    }
}