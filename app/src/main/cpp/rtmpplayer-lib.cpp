#include <jni.h>
#include <string>
#include <android/log.h>
#include <fstream>

#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, "player", FORMAT, ##__VA_ARGS__);
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, "player", FORMAT, ##__VA_ARGS__);

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include "libavdevice/avdevice.h"
}


static AVPacket *pPacket;
static AVFrame *pAvFrame, *pFrameNv21;
static AVCodecContext *pCodecCtx;
struct SwsContext *pImgConvertCtx;
static AVFormatContext *pFormatCtx;
uint8_t *v_out_buffer;
jobject frameCallback = NULL;
bool stop;
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_rtmpplaydemo_RtmpPlayer_nativePrepare(JNIEnv *env, jobject, jstring url) {
    // 初始化
#if LIBAVCODEC_VERSION_INT < AV_VERSION_INT(55, 28, 1)
#define av_frame_alloc  avcodec_alloc_frame
#endif
    if (frameCallback == NULL) {
        return -1;
    }
    pAvFrame = av_frame_alloc();
    pFrameNv21 = av_frame_alloc();
    const char* temporary = env->GetStringUTFChars(url,NULL);
    char input_str[500] = {0};
    strcpy(input_str,temporary);
    env->ReleaseStringUTFChars(url,temporary);

    //初始化
    avcodec_register_all();
    av_register_all();         //注册库中所有可用的文件格式和编码器
    avformat_network_init();
    avdevice_register_all();

    pFormatCtx = avformat_alloc_context();
    int openInputCode = avformat_open_input(&pFormatCtx, input_str, NULL, NULL);
    LOGI("openInputCode = %d", openInputCode);
    if (openInputCode < 0)
        return -1;
    avformat_find_stream_info(pFormatCtx, NULL);

    int videoIndex = -1;
    for (unsigned int i = 0; i < pFormatCtx->nb_streams; i++) //遍历各个流，找到第一个视频流,并记录该流的编码信息
    {
        if (pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;                                     //这里获取到的videoindex的结果为1.
            break;
        }
    }
    if (videoIndex == -1) {
        return -1;
    }
    pCodecCtx = pFormatCtx->streams[videoIndex]->codec;
    AVCodec *pCodec = avcodec_find_decoder(pCodecCtx->codec_id);
    avcodec_open2(pCodecCtx, pCodec, NULL);

    int width = pCodecCtx->width;
    int height = pCodecCtx->height;
    LOGI("width = %d , height = %d", width, height);
    int numBytes = av_image_get_buffer_size(AV_PIX_FMT_NV21, width, height, 1);
    v_out_buffer = (uint8_t *) av_malloc(numBytes * sizeof(uint8_t));
    av_image_fill_arrays(pFrameNv21->data, pFrameNv21->linesize, v_out_buffer, AV_PIX_FMT_NV21,
                         width,
                         height, 1);
    pImgConvertCtx = sws_getContext(
            pCodecCtx->width,             //原始宽度
            pCodecCtx->height,            //原始高度
            pCodecCtx->pix_fmt,           //原始格式
            pCodecCtx->width,             //目标宽度
            pCodecCtx->height,            //目标高度
            AV_PIX_FMT_NV21,               //目标格式
            SWS_FAST_BILINEAR,                    //选择哪种方式来进行尺寸的改变,关于这个参数,可以参考:http://www.cnblogs.com/mmix2009/p/3585524.html
            NULL,
            NULL,
            NULL);
    pPacket = (AVPacket *) av_malloc(sizeof(AVPacket));
    jclass clazz = env->GetObjectClass(frameCallback);
    jmethodID onPreparedId = env->GetMethodID(clazz, "onPrepared", "(II)V");
    env->CallVoidMethod(frameCallback, onPreparedId, width, height);
    env->DeleteLocalRef(clazz);
    return videoIndex;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rtmpplaydemo_RtmpPlayer_nativeStop(JNIEnv *env, jobject) {
    stop = true;
    if (frameCallback == NULL) {
        return;
    }
    jclass clazz = env->GetObjectClass(frameCallback);
    jmethodID onPlayFinishedId = env->GetMethodID(clazz, "onPlayFinished", "()V");
    env->CallVoidMethod(frameCallback, onPlayFinishedId);
    env->DeleteLocalRef(clazz);
    sws_freeContext(pImgConvertCtx);
    av_free(pPacket);
    av_free(pFrameNv21);
    avcodec_close(pCodecCtx);
    avformat_close_input(&pFormatCtx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rtmpplaydemo_RtmpPlayer_nativeSetCallback(JNIEnv *env, jobject,
                                                           jobject callback) {
    if (frameCallback != NULL) {
        env->DeleteGlobalRef(frameCallback);
        frameCallback = NULL;
    }
    frameCallback = (env)->NewGlobalRef(callback);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rtmpplaydemo_RtmpPlayer_nativeStart(JNIEnv *env, jobject) {
    stop = false;
    if (frameCallback == NULL) {
        return;
    }
    // 读取数据包
    int count = 0;
    while (!stop) {
        if (av_read_frame(pFormatCtx, pPacket) >= 0) {
            //解码
            int gotPicCount = 0;

            int decode_video2_size = avcodec_decode_video2(pCodecCtx, pAvFrame, &gotPicCount,
                                                           pPacket);
            LOGI("decode_video2_size = %d , gotPicCount = %d", decode_video2_size, gotPicCount);
            LOGI("pAvFrame->linesize  %d  %d %d", pAvFrame->linesize[0], pAvFrame->linesize[1],
                 pCodecCtx->height);
            if (gotPicCount != 0) {
                count++;
                sws_scale(
                        pImgConvertCtx,
                        (const uint8_t *const *) pAvFrame->data,
                        pAvFrame->linesize,
                        0,
                        pCodecCtx->height,
                        pFrameNv21->data,
                        pFrameNv21->linesize);
                int dataSize = pCodecCtx->height * (pAvFrame->linesize[0] + pAvFrame->linesize[1]);
                LOGI("pAvFrame->linesize  %d  %d %d %d", pAvFrame->linesize[0],
                     pAvFrame->linesize[1], pCodecCtx->height, dataSize);
                jbyteArray data = env->NewByteArray(dataSize);
                env->SetByteArrayRegion(data, 0, dataSize,
                                        reinterpret_cast<const jbyte *>(v_out_buffer));
                // send data to java
                jclass clazz = env->GetObjectClass(frameCallback);
                jmethodID onFrameAvailableId = env->GetMethodID(clazz, "onFrameAvailable", "([B)V");
                env->CallVoidMethod(frameCallback, onFrameAvailableId, data);
                env->DeleteLocalRef(clazz);
                env->DeleteLocalRef(data);

            }
        }
        av_packet_unref(pPacket);
    }
}