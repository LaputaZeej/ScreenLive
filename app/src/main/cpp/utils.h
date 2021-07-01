//
// Created by xpl on 2021/5/27.
//
#include "librtmp/rtmp.h"
#include <stdio.h>
#ifndef LAPUTA_RTMP_UTILS_H
#define LAPUTA_RTMP_UTILS_H


RTMPPacket *buildAudioPacket(int8_t *buf, int len, long tms, int type, int streamId) {
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    // 因为需要拼接两个音频字节-描述音频
    RTMPPacket_Alloc(packet, len + 2);
    packet[0].m_body[0] = 0XAF; // 音频参数4+2+1+1
    // 是否可播放1:可以;0不可以
    if (type == 1) {
        packet[0].m_body[1] = 0X00;
    } else {
        packet[0].m_body[1] = 0X01;
    }
    // 把数据放到m_body[2]
    memcpy(&packet->m_body[2], buf, len);
    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_hasAbsTimestamp = 0;//是否使用绝对时间
    packet->m_nBodySize = len + 2;
    packet->m_nTimeStamp = tms;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nChannel = 0x05;
    packet->m_nInfoField2 = streamId;

    return packet;

}

RTMPPacket *buildVideoPacket(int8_t *buf, int len, long tms, int type, int streamId) {

}

#endif //LAPUTA_RTMP_UTILS_H
