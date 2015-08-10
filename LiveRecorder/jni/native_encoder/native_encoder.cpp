#include <jni.h>
#include "jni_utils.h"

extern "C" {
#include "../libx264/include/x264.h"
}

#define LOG_TAG "native_encoder"

static x264_t *h;
static x264_param_t param;

int native_encoder_open(JNIEnv *env, jobject thiz, jint width, jint height)
{
	LOGI("open encoder \n");

//	char *preset = "ultrafast";
	char *tune = NULL;

	if( x264_param_default_preset( &param, "ultrafast", tune ) < 0 )
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
	int i_nal_size = 0;
	x264_nal_t *nal;
	int i_nal;
	x264_picture_t pic_out;
	x264_picture_t pic;
	uint8_t *frame_buf;

	frame_buf = (uint8_t*) env->GetByteArrayElements(pixels, NULL);
	x264_picture_init(&pic);
	pic.img.i_stride[0] = param.i_width;
	pic.img.i_stride[1] = pic.img.i_stride[2] = param.i_width/2;
	pic.img.plane[0] = frame_buf;
	pic.img.plane[1] = pic.img.plane[0] + param.i_width * param.i_height;
	pic.img.plane[2] = pic.img.plane[1] + param.i_width * param.i_height/4;

	i_nal_size = x264_encoder_encode( h, &nal, &i_nal, &pic, &pic_out );

	if( i_nal_size < 0 ) {
		return -1;
	} else if (i_nal_size) {
		env->SetByteArrayRegion(output, 0, i_nal_size, (signed char *)nal[0].p_payload);
		return i_nal_size;
	}

	return 0;
}

int native_encoder_encoding(JNIEnv *env, jobject thiz, jbyteArray output, jlong pts)
{
	int i_nal_size = 0;
	x264_picture_t pic_out;
	int i_nal;
	x264_nal_t *nal;

	if (x264_encoder_delayed_frames(h)) {
		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, NULL, &pic_out );

		if( i_nal_size < 0 ) {
			return -1;
		} else if (i_nal_size) {
			env->SetByteArrayRegion(output, 0, i_nal_size, (signed char *)nal[0].p_payload);
			return i_nal_size;
		}
	} else {
		return 0;
	}
}

int native_encoder_close( )
{
	LOGI("close encoder \n");
	if( h )
		x264_encoder_close( h );

	return 0;
}

static JNINativeMethod gMethods[] = {
    {"native_encoder_open", "(II)I", (void *)native_encoder_open},
    {"native_encoder_encode", "([B[BJ)I", (void *)native_encoder_encode},
	{"native_encoder_encoding", "([BJ)I", (void *)native_encoder_encoding},
    {"native_encoder_close", "()I", (void *)native_encoder_close},
};

int register_natives(JNIEnv *env)
{
	return jniRegisterNativeMethods(env, "me/shengbin/liverecorder/SoftwareVideoEncoder", gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}
