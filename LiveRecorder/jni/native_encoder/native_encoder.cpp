#include <jni.h>
#include "jni_utils.h"
extern "C" {
#include "x264.h"
}

#define LOG_TAG "native_encoder"
#include <stdio.h>
static x264_t *h;
static x264_param_t param;
static FILE *out_file;
static FILE *out_file_yuv;
static int d_count = 0;
static int bitstream_outputed = 0;
static int b_entered = 0;

#define OUTPUT_YUV 0
#define OUTPUT_BS 0

int native_encoder_open(JNIEnv *env, jobject thiz, jint width, jint height, jint bitrate)
{
	LOGD("open encoder \n");

//	char *preset = "ultrafast";
	char *tune = NULL;
	bitstream_outputed = 0;

	if( x264_param_default_preset( &param, "veryfast", tune ) < 0 )
		return -1;
//	x264_param_parse( &param, "bitrate", "10000" );
	x264_param_parse( &param, "qp", "25" );
	x264_param_parse( &param, "fps", "15" );
//	x264_param_parse( &param, "keyint", "1" );
	param.i_width = width;
	param.i_height= height;

	h = x264_encoder_open( &param );
#if OUTPUT_BS
	out_file = fopen("/sdcard/test.264", "w");
	if (out_file == NULL) {
		LOGD("can't open output .264 \n");
		return -1;
	}
#endif
#if OUTPUT_YUV
	out_file_yuv = fopen("/sdcard/test.yuv", "w");
	if (out_file_yuv == NULL) {
		LOGD("can't open output .yuv \n");
		return -1;
	}
#endif
	if (!h)
		return -1;

	return 0;
}

int test() {
	FILE *input = fopen("/sdcard/test.yuv", "r");
	int size = (param.i_width * param.i_height * 3)/2;
	uint8_t *buf = (uint8_t *)malloc(size);
	int i_nal_size = 0;
	x264_nal_t *nal;
	int i_nal;
	int payload_size = 0;
	int i;
	x264_picture_t pic_out;
	x264_picture_t pic;
	int frame = 1;
	if (input == NULL) {
		LOGD("can't open input \n");
		return 0;
	}
	while(fread(buf, 1, size, input)) {
		x264_picture_init(&pic);
		pic.img.i_csp = param.i_csp;
		pic.img.i_plane = 3;
		pic.img.i_stride[0] = param.i_width;
		pic.img.i_stride[1] = pic.img.i_stride[2] = param.i_width/2;
		pic.img.plane[0] = buf;
		pic.img.plane[1] = pic.img.plane[0] + param.i_width * param.i_height;
		pic.img.plane[2] = pic.img.plane[1] + param.i_width * param.i_height/4;

		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, &pic, &pic_out );
		payload_size = 0;
		for (i = 0; i < i_nal; i++){
			payload_size += nal[i].i_payload;
		}

		if (i_nal_size > 0) {
			LOGD("encode frame %d\n", frame++);
			fwrite(nal[0].p_payload+bitstream_outputed, 1, payload_size, out_file);
		}
	}
	LOGD("input exhaust.\n");
	while (x264_encoder_delayed_frames(h)) {
		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, NULL, &pic_out );
		payload_size = 0;
		for (i = 0; i < i_nal; i++){
			payload_size += nal[i].i_payload;
		}

		if (i_nal_size > 0) {
			LOGD("encode frame %d\n", frame++);
			fwrite(nal[0].p_payload+bitstream_outputed, 1, payload_size, out_file);
		}
	}

	free(buf);
	fclose(input);
	fclose(out_file);
	if (out_file_yuv)
		fclose(out_file_yuv);
	if( h )
		x264_encoder_close( h );
	LOGD("encoder closed. \n");
	b_entered =1;

}

int native_encoder_encode(JNIEnv *env, jobject thiz, jbyteArray pixels, jobject outstream, jlong pts, jlongArray encap)
{
	if (0)//test encoder.
	{
		if (b_entered == 0)
			test();
		return 0;
	}

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
#if OUTPUT_YUV
		if (out_file_yuv) {
			fwrite(pic.img.plane[0], 1, param.i_width * param.i_height, out_file_yuv);
			fwrite(pic.img.plane[1], 1, param.i_width * param.i_height/4, out_file_yuv);
			fwrite(pic.img.plane[2], 1, param.i_width * param.i_height/4, out_file_yuv);
		}
#endif
		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, &pic, &pic_out );
	} else {
		LOGD("input NULL, do encoder flush\n");
		i_nal_size = x264_encoder_encode( h, &nal, &i_nal, NULL, &pic_out );
	}
	LOGD("native_encoder_encode encode %d\n", d_count++);
	if (i_nal_size > 0) {
		//output bitstream
		payload_size = 0;
		for (i = 0; i < i_nal; i++){
//			LOGD("i_nal = %d, i_nal_size = %d, p_payload = %d\n", i, nal[i].i_payload, nal[i].p_payload);
			payload_size += nal[i].i_payload;
		}
#if OUTPUT_BS
		if (out_file)
			fwrite(nal[0].p_payload+bitstream_outputed, 1, payload_size, out_file);
#endif
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
		env->SetByteArrayRegion(bytebuf, 0, payload_size, (jbyte*)nal[0].p_payload+bitstream_outputed);
		env->CallVoidMethod(outstream, write_id, bytebuf, 0, payload_size);
		env->CallVoidMethod(outstream, flush_id);

		frameEncap[0] = pic_out.i_pts;
		frameEncap[1] = pic_out.b_keyframe;
		frameEncap[2] = 0;
		env->SetLongArrayRegion(encap, 0, sizeof(frameEncap)/sizeof(frameEncap[0]), frameEncap);
		LOGD("b_keyframe = %d", pic_out.b_keyframe);
		return payload_size;
	} else if (i_nal_size < 0) {
		LOGE("x264 encode error.\n");
		goto end;
	}

	return i_nal_size;

end:
	if ( NULL != bytebuf )
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
	LOGI("close encoder \n");
	if( h )
		x264_encoder_close( h );
#if OUTPUT_BS
	if (out_file)
		fclose(out_file);
#endif
#if OUTPUT_YUV
	if (out_file_yuv)
		fclose(out_file_yuv);
#endif
	return 0;
}

static JNINativeMethod gMethods[] = {
    {"native_encoder_open", "(III)I", (void *)native_encoder_open},
    {"native_encoder_encode", "([BLjava/io/ByteArrayOutputStream;J[J)I", (void *)native_encoder_encode},
	{"native_encoder_encoding", "()I", (void *)native_encoder_encoding},
    {"native_encoder_close", "()I", (void *)native_encoder_close},
};

int register_natives(JNIEnv *env)
{
	return jniRegisterNativeMethods(env, "me/shengbin/corerecorder/SoftwareVideoEncoder", gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}
