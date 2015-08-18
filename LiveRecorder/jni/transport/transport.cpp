#include <stdio.h>
#include <math.h>
#include <string.h>
#include "rtmp_sys.h"
#include "transport.h"


static RTMP *rtmp = NULL;

bool init(char * rtmp_url)
{
	rtmp = RTMP_Alloc();
	RTMP_Init(rtmp);
	rtmp->Link.timeout = 8;
	int err = RTMP_SetupURL(rtmp, rtmp_url);
	if (err <= 0)
	{
		LOGE("RTMP_SetupURL: err");
		return false;
	}
	RTMP_EnableWrite(rtmp);
	err = RTMP_Connect(rtmp, NULL);
	if (err <= 0)
	{
		LOGE("RTMP_Connect: err");
		return false;
	}
	err = RTMP_ConnectStream(rtmp, 0);
	if (err <= 0)
	{
		LOGE("RTMP_ConnectStream: err");
		return false;
	}
	return true;
}

void close()
{
	RTMP_Close(rtmp);
	RTMP_Free(rtmp);
}

bool send(char * buf, int bufLen, int type, unsigned int timestamp)
{
	LOGD("start sending");
	RTMPPacket *rtmp_pkt;
	rtmp_pkt = (RTMPPacket*)malloc(sizeof(RTMPPacket));
	memset(rtmp_pkt,0,sizeof(RTMPPacket));
	RTMPPacket_Alloc(rtmp_pkt, bufLen);
	RTMPPacket_Reset(rtmp_pkt);
	rtmp_pkt->m_packetType = type;
	rtmp_pkt->m_nBodySize = bufLen;
	rtmp_pkt->m_nTimeStamp = timestamp;
	rtmp_pkt->m_nChannel = 4;
	rtmp_pkt->m_headerType = RTMP_PACKET_SIZE_LARGE;
	rtmp_pkt->m_nInfoField2 = rtmp->m_stream_id;
	memcpy(rtmp_pkt->m_body, buf, bufLen);
	LOGD("length: %d", bufLen);
	bool ret = RTMP_SendPacket(rtmp, rtmp_pkt, 0);
	RTMPPacket_Free(rtmp_pkt);
	if(ret)
		LOGD("packet sent");
	else
		LOGE("packet sent err");
	return ret;
}

jboolean Java_me_shengbin_corerecorder_RtmpFlv_rtmpInit(JNIEnv * env, jobject obj, jstring url)
{
	char * rtmp_url = (char*)env->GetStringUTFChars(url, 0);
	LOGI("url %s",rtmp_url);
	return init(rtmp_url);
}

jboolean Java_me_shengbin_corerecorder_RtmpFlv_rtmpReconnect(JNIEnv * env, jobject obj, jstring url)
{
	char * rtmp_url = (char*)env->GetStringUTFChars(url, 0);
	close();
	return init(rtmp_url);
}

jboolean Java_me_shengbin_corerecorder_RtmpFlv_rtmpSend(JNIEnv * env, jobject obj, jbyteArray array, jint type, jint timestamp)
{
	int frame_type;
	if (type == 8) frame_type = RTMP_PACKET_TYPE_AUDIO;
	else if (type == 9) frame_type = RTMP_PACKET_TYPE_VIDEO;
	else frame_type = RTMP_PACKET_TYPE_INFO;
	jbyte * data = env->GetByteArrayElements(array, 0);
	jsize length = env->GetArrayLength(array);
	bool ret = send((char*)data, length, frame_type, timestamp);
	env->ReleaseByteArrayElements(array,data,0);
	return ret;
}

void Java_me_shengbin_corerecorder_RtmpFlv_rtmpClose(JNIEnv * env, jobject obj)
{
	if(rtmp == NULL)
		return;
	close();
}
