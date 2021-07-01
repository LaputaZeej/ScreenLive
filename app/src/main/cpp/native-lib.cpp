#include <jni.h>
#include <string>
#include "utils.h"
#include "packt.h"
#include "librtmp/rtmp.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_laputa_rtmp_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

Live *live = nullptr;


void handleVideo(jbyte *data, jint len, jlong tms);

void handleAudio(jbyte *data, jint len, jlong tms, jint type);

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_laputa_rtmp_Show_connect(JNIEnv *env, jobject thiz, jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    int ret;
    do {
        live = (Live *) malloc(sizeof(Live));
        memset(live, 0, sizeof(Live));
        live->rtmp = RTMP_Alloc();
        RTMP_Init(live->rtmp);
        live->rtmp->Link.timeout = 15;
        LOGI("connect %s", url);
        AVal *in_aval ;
        AVal *out_aval;
        RTMP_ParsePlaypath(in_aval,out_aval);
        if (!(ret = RTMP_SetupURL(live->rtmp, (char *) url))) break;
        RTMP_EnableWrite(live->rtmp);
        LOGI("RTMP_Connect");
        if (!(ret = RTMP_Connect(live->rtmp, 0))) {
            LOGI("connect %d", ret);
            break;
        }
        LOGI("RTMP_ConnectStream ");
        if (!(ret = RTMP_ConnectStream(live->rtmp, 0))) break;
        LOGI("connect success");
    } while (0);


    if (!ret && live) {
        free(live);
        live = nullptr;
    }

    env->ReleaseStringUTFChars(url_, url);
    return ret;
}

int sendPacket(RTMPPacket *packet) {
    int r = 0;
    while (1) {
        if (RTMP_IsConnected(live->rtmp)) {
            //            if(RTMPPacket_IsReady(packet)){
            r = RTMP_SendPacket(live->rtmp, packet, 1);
            LOGI("sendPacket result %d", r);
            RTMPPacket_Free(packet);
            free(packet);
            //            }
            break;
        } else {

            LOGI("sendPacket not connect");
       /*     live->rtmp = RTMP_Alloc();
            RTMP_EnableWrite(live->rtmp);
            LOGI("RTMP_Connect");
            if (!(r = RTMP_Connect(live->rtmp, 0))) {
                LOGI("connect %d", r);
                break;
            }
            LOGI("RTMP_ConnectStream ");
            if (!(r = RTMP_ConnectStream(live->rtmp, 0))) break;*/
            break;
        }
    }
    return r;
}

int sendVideo(int8_t *buf, int len, long tms) {
    int ret = 0;
    do {
        if (buf[4] == 0x67) {//sps pps
            if (live && (!live->pps || !live->sps)) {
                prepareVideo(buf, len, live);
            }
        } else {
            if (buf[4] == 0x65) {//关键帧
                RTMPPacket *packet = createVideoPackage(live);
                if (!(ret = sendPacket(packet))) {
                    break;
                }
            }
            //将编码之后的数据 按照 flv、rtmp的格式 拼好之后
            RTMPPacket *packet = createVideoPackage(buf, len, tms, live);
            ret = sendPacket(packet);
        }
    } while (0);
    LOGI("sendVideo ret 333 %d", ret);
    return ret;
}

int sendAudio(int8_t *buf, int len, int type, int tms) {
    int ret;
    do {
        RTMPPacket *packet = createAudioPacket(buf, len, type, tms, live);
        ret = sendPacket(packet);
    } while (0);
    return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_laputa_rtmp_Show_sendPacket(JNIEnv *env, jobject thiz, jbyteArray data_, jint len,
                                     jint type, jlong tms) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    int ret = 0;
    switch (type) {
        case 0: //video
            ret = sendVideo(data, len, tms);
            LOGI("send Video......");
            break;
        default: //audio
            ret = sendAudio(data, len, type, tms);
            LOGI("send Audio......");
            break;
    }
    env->ReleaseByteArrayElements(data_, data, 0);
    return ret;

}


extern "C"
JNIEXPORT void JNICALL
Java_com_laputa_rtmp_Show_disconnect(JNIEnv *env, jobject thiz) {

    if (live) {
        if (live->sps) {
            free(live->sps);
        }
        if (live->pps) {
            free(live->pps);
        }
        if (live->rtmp) {
            RTMP_Close(live->rtmp);
            RTMP_Free(live->rtmp);
        }
        free(live);
        live = nullptr;
    }
}