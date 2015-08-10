#include <jni.h>
#include "jni_utils.h"

extern "C" {
#include "../libx264/include/x264.h"
}

#define LOG_TAG "native_encoder"

static x264_t *h;

int native_encoder_open(JNIEnv *env, jobject thiz, jint width, jint height)
{
	LOGI("open encoder \n");

	static x264_param_t param;
	char *preset = "ultrafast";
	char *tune = NULL;

	if( x264_param_default_preset( &param, preset, tune ) < 0 )
		return -1;
	x264_param_parse( &param, "bitrate", "300" );
	x264_param_parse( &param, "fps", "30" );
	param.i_width = width;
	param.i_height= height;

	h = x264_encoder_open( &param );
	if (!h)
		return -1;

	return 0;
}

int native_encoder_encode(JNIEnv *env, jobject thiz, jbyteArray pixels, jbyteArray output, jlong pts)
{
	LOGD("encode a frame \n");
	int i_frame_size = 0;
	x264_nal_t *nal;
	int i_nal;
	x264_picture_t pic_out;
	x264_picture_t *pic;

	i_frame_size = x264_encoder_encode( h, &nal, &i_nal, pic, &pic_out );

//	output = nal[0].p_payload;

	return 0;
}

int native_encoder_close()
{
	LOGI("close encoder \n");
	if( h )
		x264_encoder_close( h );
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
