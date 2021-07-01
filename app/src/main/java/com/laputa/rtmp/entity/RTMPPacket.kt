package com.laputa.rtmp.entity

/**
 * Author by xpl, Date on 2021/5/27.
 */
data class RTMPPacket(val buff: ByteArray?=null, val type: Int=0, val tms: Long=0) {
    companion object {
        const val RTMP_PACKET_TYPE_VIDEO = 0
        const val RTMP_PACKET_TYPE_AUDIO_HEAD = 1
        const val RTMP_PACKET_TYPE_AUDIO_DATA = 2

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RTMPPacket

        if (buff != null) {
            if (other.buff == null) return false
            if (!buff.contentEquals(other.buff)) return false
        } else if (other.buff != null) return false
        if (type != other.type) return false
        if (tms != other.tms) return false

        return true
    }

    override fun hashCode(): Int {
        var result = buff?.contentHashCode() ?: 0
        result = 31 * result + type
        result = 31 * result + tms.hashCode()
        return result
    }
}