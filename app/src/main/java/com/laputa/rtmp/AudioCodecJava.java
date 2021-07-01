package com.laputa.rtmp;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import com.laputa.rtmp.entity.RTMPPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by xiang on 2017/9/11.
 */

public class AudioCodecJava extends Thread {

    private final Show screenLive;


    private MediaCodec mediaCodec;
    private AudioRecord audioRecord;
    private boolean isRecoding;

    private long startTime;
    private int minBufferSize;

    public AudioCodecJava(Show screenLive) {
        this.screenLive = screenLive;
    }


    @Override
    public void run() {
        isRecoding = true;
        byte[] audioDecoderSpecificInfo = {0x12, 0x08};
        RTMPPacket rtmpPackage = new RTMPPacket(audioDecoderSpecificInfo,RTMPPacket.RTMP_PACKET_TYPE_AUDIO_HEAD,0);
        screenLive.addPacket(rtmpPackage);
        audioRecord.startRecording();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        byte[] buffer = new byte[minBufferSize];
        while (isRecoding) {
            int len = audioRecord.read(buffer, 0, buffer.length);
            if (len <= 0) {
                continue;
            }
            //立即得到有效输入缓冲区
            int index = mediaCodec.dequeueInputBuffer(0);
            if (index >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(index);
                inputBuffer.clear();
                inputBuffer.put(buffer, 0, len);
                //填充数据后再加入队列
                mediaCodec.queueInputBuffer(index, 0, len,
                        System.nanoTime() / 1000, 0);
            }
            index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            while (index >= 0 && isRecoding) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (startTime == 0) {
                    startTime = bufferInfo.presentationTimeUs / 1000;
                }
                long tms = (bufferInfo.presentationTimeUs / 1000) - startTime;
                RTMPPacket rtmpPackage2 = new RTMPPacket(outData,RTMPPacket.RTMP_PACKET_TYPE_AUDIO_DATA,tms);
                screenLive.addPacket(rtmpPackage2);
                mediaCodec.releaseOutputBuffer(index, false);
                index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        }
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;

        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        startTime = 0;
        isRecoding = false;
    }

    public void startLive() {
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100,
                1);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel
                .AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64_000);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);

            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();

            /**
             * 获得创建AudioRecord所需的最小缓冲区
             * 采样+单声道+16位pcm
             */
            minBufferSize = AudioRecord.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            /**
             * 创建录音对象
             * 麦克风+采样+单声道+16位pcm+缓冲区大小
             */
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT, 44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
            start();

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void stopLive() {
        isRecoding = false;
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
