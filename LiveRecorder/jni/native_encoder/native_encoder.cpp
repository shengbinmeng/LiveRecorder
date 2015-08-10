#include <jni.h>
#include "jni_utils.h"

#define LOG_TAG "native_encoder"

int native_encoder_open(JNIEnv *env, jobject thiz, jint width, jint height)
{
	LOGI("open encoder \n");

	return 0;
}

int native_encoder_encode(JNIEnv *env, jobject thiz, jbyteArray pixels, jlong pts)
{
	LOGD("encode a frame \n");

	return 0;
}

int native_encoder_close()
{
	LOGI("close encoder \n");

	return 0;
}

static JNINativeMethod gMethods[] = {
    {"native_encoder_open", "(II)I", (void *)native_encoder_open},
    {"native_encoder_encode", "([BJ)I", (void *)native_encoder_encode},
    {"native_encoder_close", "()I", (void *)native_encoder_close},
};

int register_natives(JNIEnv *env)
{
	return jniRegisterNativeMethods(env, "me/shengbin/liverecorder/SoftwareVideoEncoder", gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}
