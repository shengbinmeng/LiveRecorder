#include <jni.h>
#include <stdio.h>
#include "jni_utils.h"
extern "C" {
#include "x264.h"
}

#define LOG_TAG "native_encoder"

static x264_t *h;
static x264_param_t param;
static int d_count = 0;

static void  int_to_str(int value, char *str) {
	sprintf(str, "%d", value);
}

int native_encoder_open(JNIEnv *env, jobject thiz, jint width, jint height, jint fps, jint bitrate)
{
	LOGD("open encoder \n");
	char bitrate_str[20];
	char fps_str[20];
	char vbv_bufsize_str[20];
	char vbv_maxrate[20];
	int x264_bitrate = bitrate/1000;
	int b_cbr = 0;

	if (x264_bitrate <= 0 || fps <= 0)
		return -1;

	int_to_str(fps, fps_str);
	int_to_str(x264_bitrate, bitrate_str);
	int_to_str(x264_bitrate*2, vbv_maxrate);
	int_to_str(x264_bitrate/fps, vbv_bufsize_str);

	if( x264_param_default_preset( &param, "superfast", "zerolatency" ) < 0 )
		return -1;

	x264_param_parse( &param, "bitrate", bitrate_str );
	x264_param_parse( &param, "vbv-maxrate", vbv_maxrate);
	x264_param_parse( &param, "vbv-bufsize", b_cbr ? vbv_bufsize_str : bitrate_str);

	x264_param_parse( &param, "fps", fps_str );
	x264_param_parse( &param, "keyint", fps_str);

	param.i_width = width;
	param.i_height= height;

	LOGD("fps_num = %d, fps_den = %d, bitrate = %d, rc method = %d\n", param.i_fps_num, param.i_fps_den, param.rc.i_bitrate, param.rc.i_rc_method);
	LOGD("b_deblocking_filter = %d, i_deblocking_filter_alphac0 = %d, i_deblocking_filter_beta = %d", param.b_deblocking_filter, param.i_deblocking_filter_alphac0, param.i_deblocking_filter_beta);
	LOGD("b_cabac = %d, i_threads = %d", param.b_cabac, param.i_threads);
	LOGD("b_repeat_headers = %d", param.b_repeat_headers);
	h = x264_encoder_open( &param );

	if (!h)
		return -1;

	return 0;
}

int native_encoder_encode(JNIEnv *env, jobject thiz, jbyteArray pixels, jobject outstream, jlong pts, jlongArray encap)
{
	if (h == NULL)
		return -1;

	int i_nal_size = 0;
	x264_nal_t *nal;
	int i_nal;
	x264_picture_t pic_out;
	x264_picture_t pic;
	uint8_t *frame_buf;
	int i;
	int payload_size = 0;
	jclass class_OutputStream;
	jmethodID write_id;
	jmethodID flush_id;
	jbyteArray bytebuf;
	long long int frameEncap[3];

	if (pixels != NULL) {
		//fill pic as x264 input
		x264_picture_init(&pic);
		pic.i_pts = pts;
		frame_buf = (uint8_t*) env->GetByteArrayElements(pixels, NULL);
		pic.img.i_csp = param.i_csp;
		pic.img.i_plane = 3;
		pic.img.i_stride[0] = param.i_width;
		pic.img.i_stride[1] = pic.img.i_stride[2] = param.i_width/2;
		pic.img.plane[0] = frame_buf;
		pic.img.plane[2] = pic.img.plane[0] + param.i_width * param.i_height;
		pic.img.plane[1] = pic.img.plane[2] + param.i_width * param.i_height/4;
		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, &pic, &pic_out );
		env->ReleaseByteArrayElements(pixels, (jbyte*)frame_buf, JNI_ABORT);
	} else {
		LOGD("input NULL, do encoder flush\n");
		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, NULL, &pic_out );
	}
	LOGD("native_encoder_encode encode %d\n", d_count++);
	if (i_nal_size > 0) {
		//output bitstream
		payload_size = 0;
		for (i = 0; i < i_nal; i++) {
//			LOGD("i_nal = %d, i_nal_size = %d, p_payload = %d\n", i, nal[i].i_payload, nal[i].p_payload);
			payload_size += nal[i].i_payload;
		}
		class_OutputStream = env->GetObjectClass(outstream);
		if ( NULL == class_OutputStream ) {
			LOGE("jni GetObjectClass failed!");
			goto end;
		}
		write_id = env->GetMethodID(class_OutputStream, "write", "([BII)V");
		flush_id = env->GetMethodID(class_OutputStream, "flush", "()V");
		if ( 0 == write_id || 0 == flush_id ) {
			LOGE("jni GetMethodID failed!");
			goto end;
		}
		bytebuf = env->NewByteArray(payload_size);
		if ( bytebuf == NULL ) {
			LOGE("jni NewByteArray(%d) failed!", payload_size);
			goto end;
		}
		env->SetByteArrayRegion(bytebuf, 0, payload_size, (jbyte*)nal[0].p_payload);
		env->CallVoidMethod(outstream, write_id, bytebuf, 0, payload_size);
		env->CallVoidMethod(outstream, flush_id);
		LOGD("nal size: %d", payload_size);

		frameEncap[0] = pic_out.i_pts;
		frameEncap[1] = pic_out.b_keyframe;
		frameEncap[2] = 0;
		env->SetLongArrayRegion(encap, 0, sizeof(frameEncap)/sizeof(frameEncap[0]), frameEncap);
//		LOGD("b_keyframe = %d", pic_out.b_keyframe);
		if ( NULL != bytebuf ) {
			env->DeleteLocalRef(bytebuf);
		}
		return payload_size;
	} else if (i_nal_size < 0) {
		LOGE("x264 encode error.\n");
		goto end;
	}

	return i_nal_size;

end:
	if (NULL != bytebuf)
		env->DeleteLocalRef(bytebuf);
	return -1;
}

int native_encoder_encoding(JNIEnv *env, jobject thiz)
{
	if (h == NULL)
		return -1;
	if (x264_encoder_delayed_frames(h)) {
		LOGD("x264 still encoding.\n");
		return 1;
	} else {
		return 0;
	}
}

int native_encoder_close()
{
	LOGD("close encoder \n");
	if (h)
		x264_encoder_close( h );
	return 0;
}

int native_encoder_update_bitrate(JNIEnv *env, jobject thiz, jint bitrate)
{
	char bitrate_str[20];
	char vbv_maxrate[20];
	int x264_bitrate = bitrate/1000;
	int ret;
	LOGI("x264_bitrate = %d", x264_bitrate);
	if (h == NULL)
		return -1;

	if (x264_bitrate <= 0)
		return -1;

	int_to_str(x264_bitrate, bitrate_str);
	int_to_str(x264_bitrate*2, vbv_maxrate);

	x264_param_parse( &param, "bitrate", bitrate_str );
	x264_param_parse( &param, "vbv-maxrate", vbv_maxrate);
	x264_param_parse( &param, "vbv-bufsize", bitrate_str);

	LOGI("reconfig: fps_num = %d, fps_den = %d, bitrate = %d, rc method = %d, vbv_bufsize = %d, vbv_maxrate = %d\n", param.i_fps_num, param.i_fps_den, param.rc.i_bitrate, param.rc.i_rc_method, param.rc.i_vbv_buffer_size, param.rc.i_vbv_max_bitrate);

	ret = x264_encoder_reconfig(h, &param);

	return ret;
}

static JNINativeMethod gMethods[] = {
    {"native_encoder_open", "(IIII)I", (void *)native_encoder_open},
    {"native_encoder_encode", "([BLjava/io/ByteArrayOutputStream;J[J)I", (void *)native_encoder_encode},
	{"native_encoder_encoding", "()I", (void *)native_encoder_encoding},
    {"native_encoder_close", "()I", (void *)native_encoder_close},
	{"native_encoder_update_bitrate", "(I)I", (void *)native_encoder_update_bitrate},
};

int register_natives(JNIEnv *env)
{
	return jniRegisterNativeMethods(env, "me/shengbin/corerecorder/SoftwareVideoEncoder", gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}
