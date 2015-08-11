#include <stdio.h>
#include <math.h>
#include <string.h>
#include <rtmp_sys.h>
#include "transport.h"


static RTMP *rtmp = NULL;

jboolean Java_me_shengbin_liverecorder_RtmpFlv_rtmpInit(JNIEnv * env, jobject obj, jstring url)
{
	char * rtmp_url = (char*)env->GetStringUTFChars(url, 0);
	LOGI("url %s",rtmp_url);
	rtmp = RTMP_Alloc();
	RTMP_Init(rtmp);
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

void send(char * buf, int bufLen, int type, unsigned int timestamp)
{
	LOGI("start sending");
	RTMPPacket *rtmp_pkt;
	//RTMPPacket_Reset(rtmp_pkt);
	//LOGI("packet reset");
	rtmp_pkt = (RTMPPacket*)malloc(sizeof(RTMPPacket));
	memset(rtmp_pkt,0,sizeof(RTMPPacket));
	RTMPPacket_Alloc(rtmp_pkt, bufLen);
	RTMPPacket_Reset(rtmp_pkt);
	LOGI("packet alloc");
	rtmp_pkt->m_packetType = type;
	rtmp_pkt->m_nBodySize = bufLen;
	rtmp_pkt->m_nTimeStamp = timestamp;
	rtmp_pkt->m_nChannel = 4;
	rtmp_pkt->m_headerType = RTMP_PACKET_SIZE_LARGE;
	rtmp_pkt->m_nInfoField2 = rtmp->m_stream_id;
	memcpy(rtmp_pkt->m_body, buf, bufLen);
	LOGI("length: %d", bufLen);
	bool ret = RTMP_SendPacket(rtmp, rtmp_pkt, 0);
	if(ret)
		LOGI("packet sent");
	else
		LOGI("packet sent err");
	RTMPPacket_Free(rtmp_pkt);
}

void Java_me_shengbin_liverecorder_RtmpFlv_rtmpSend(JNIEnv * env, jobject obj, jbyteArray array, jint type, jint timestamp)
{
	int frame_type;
	if (type == 8) frame_type = RTMP_PACKET_TYPE_AUDIO;
	else if (type == 9) frame_type = RTMP_PACKET_TYPE_VIDEO;
	else frame_type = RTMP_PACKET_TYPE_INFO;
	jbyte * data = env->GetByteArrayElements(array, 0);
	jsize length = env->GetArrayLength(array);
	send((char*)data, length, frame_type, timestamp);
	env->ReleaseByteArrayElements(array,data,0);
}

void Java_me_shengbin_liverecorder_RtmpFlv_rtmpClose(JNIEnv * env, jobject obj)
{
	if(rtmp == NULL)
		return;
	RTMP_Close(rtmp);
	RTMP_Free(rtmp);
}
