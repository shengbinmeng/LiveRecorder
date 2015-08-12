#include <stdlib.h>
#include <jni.h>
#include <android/log.h>

#ifndef LOG_TAG
#define LOG_TAG "TANSPORT"
#endif

#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_me_shengbin_corerecorder_RtmpFlv_rtmpInit(JNIEnv * env, jobject obj, jstring url);
JNIEXPORT jboolean JNICALL Java_me_shengbin_corerecorder_RtmpFlv_rtmpSend(JNIEnv * env, jobject obj, jbyteArray array, jint type, jint timestamp);
JNIEXPORT void JNICALL Java_me_shengbin_corerecorder_RtmpFlv_rtmpClose(JNIEnv * env, jobject obj);

#ifdef __cplusplus
}
#endif
