#include <stdio.h>
#include <math.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include "rtmp_sys.h"
#include "transport.h"
#include "SpsDecode.h"

#define RTMP_HEAD_SIZE   (sizeof(RTMPPacket)+RTMP_MAX_HEADER_SIZE)
#define NAL_SLICE 1
#define NAL_SLICE_DPA 2
#define NAL_SLICE_DPB 3
#define NAL_SLICE_DPC 4
#define NAL_SLICE_IDR 5
#define NAL_SEI 6
#define NAL_SPS 7
#define NAL_PPS 8
#define NAL_AUD 9
#define NAL_FILLER 12

#define FrameListSizeLimit 20

typedef struct _NaluUnit
{
    int type;
    int size;
    unsigned char *data;
} NaluUnit;

typedef struct _Frame
{
    unsigned char packet_type;
    unsigned char frame_type;
    int size;
    unsigned char * data;
    int pts;
    _Frame * next;
} Frame;

typedef struct _FrameList
{
    int size;
    Frame * head;
    Frame * tail;
} FrameList;


enum
{  
    FLV_CODECID_H264 = 7,
};

static RTMP 	*rtmp = NULL;
char * rtmp_connect_url = NULL;
unsigned int    sps_len;
unsigned char   *sps;
unsigned int    pps_len;
unsigned char   *pps;
long			start_time = -1;
bool			aac_spec_sent = false;
bool			sps_pps_sent = false;
bool            is_connected = false;
FrameList       * frameList;

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  cond =  PTHREAD_COND_INITIALIZER;
pthread_t       frame_sending_thread;
bool            frame_sending_thread_started = false;

char * put_byte( char *output, uint8_t nVal)
{
    output[0] = nVal;
    return output+1;
}
char * put_be16(char *output, uint16_t nVal)
{
    output[1] = nVal & 0xff;
    output[0] = nVal >> 8;
    return output+2;
}    
char * put_be24(char *output,uint32_t nVal)
{
    output[2] = nVal & 0xff;
    output[1] = nVal >> 8;
    output[0] = nVal >> 16;
    return output+3;
}
char * put_be32(char *output, uint32_t nVal)
{
    output[3] = nVal & 0xff;
    output[2] = nVal >> 8;
    output[1] = nVal >> 16;
    output[0] = nVal >> 24;
    return output+4;
}
char * put_be64(char *output, uint64_t nVal)
{
    output=put_be32( output, nVal >> 32 );
    output=put_be32( output, nVal );
    return output;
}
char * put_amf_string(char *c, const char *str)
{
    uint16_t len = strlen( str );
    c=put_be16( c, len );
    memcpy(c,str,len);
    return c+len;
}
char * put_amf_double(char *c, double d)
{
    *c++ = AMF_NUMBER;  /* type: Number */    
    {
        unsigned char *ci, *co;
        ci = (unsigned char *)&d;
        co = (unsigned char *)c;
        co[0] = ci[7];
        co[1] = ci[6];
        co[2] = ci[5];
        co[3] = ci[4];
        co[4] = ci[3];
        co[5] = ci[2];
        co[6] = ci[1];
        co[7] = ci[0];
    }
    return c+8;
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
    if(RTMP_IsConnected(rtmp)) {
        bool ret = RTMP_SendPacket(rtmp, rtmp_pkt, 0);
        RTMPPacket_Free(rtmp_pkt);
        if(ret)
            LOGD("packet sent");
        else
            LOGE("packet sent err");
        return ret;
    } else {
        RTMPPacket_Free(rtmp_pkt);
        is_connected = false;
        return false;
    }
	
}

bool readOneNaluFromBuf(NaluUnit &nalu, unsigned char * buf, int buf_size, int &cur_pos)
{
    int i = cur_pos;
    while(i + 2 < buf_size)
    {
        if(buf[i] == 0x00 && buf[i+1] == 0x00 && buf[i+2] == 0x01) {
        	i = i + 3;
            int pos = i;
            while (pos + 2 < buf_size)
            {
                if(buf[pos] == 0x00 && buf[pos+1] == 0x00 && buf[pos+2] == 0x01)
                    break;
                pos++;
            }
            if(pos+2 == buf_size) {
                nalu.size = pos+2-i;
            } else {
            	while(buf[pos-1] == 0x00)
            		pos--;
                nalu.size = pos-i;
            }
            nalu.type = buf[i] & 0x1f;
            nalu.data = buf + i;
            cur_pos = pos;
            return true;
        } else {
        	i++;
        }
    }
    return false;
}

bool send_frame_data(unsigned char *buf, int size, int timestamp, unsigned char packet_type, unsigned char frame_type, bool is_sequential_header)
{
    RTMPPacket * packet;
    unsigned char * body;
    int body_size, data_size, tag_size, i;
    i = 0;
    if(packet_type == RTMP_PACKET_TYPE_VIDEO) {
        // video
        data_size = size + 5;
        tag_size = data_size;
        body_size = tag_size;
    } else if(packet_type == RTMP_PACKET_TYPE_AUDIO){
        // audio
        data_size = size + 2;
        tag_size = data_size;
        body_size = tag_size;
    }

    packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE + body_size);
    memset(packet,0,RTMP_HEAD_SIZE + body_size);
    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
    packet->m_nBodySize = body_size;
    body = (unsigned char *)packet->m_body;
    memset(body,0,body_size);

    if(packet_type == RTMP_PACKET_TYPE_VIDEO) {
        if (frame_type == NAL_SLICE_IDR || is_sequential_header)
            body[i++] = 0x17;
        else
            body[i++] = 0x27;

        if (is_sequential_header)
            body[i++] = 0x00;
        else
            body[i++] = 0x01;

        body[i++] = 0x00;
        body[i++] = 0x00;
        body[i++] = 0x00;
    } else if(packet_type == RTMP_PACKET_TYPE_AUDIO){
        body[i++] = 0xAF;
        if (is_sequential_header)
            body[i++] = 0x00;
        else
            body[i++] = 0x01;
    }
    memcpy(body+i,buf,size);
    i += size;
    
    packet->m_hasAbsTimestamp = 0;
    packet->m_packetType = packet_type;
    packet->m_nInfoField2 = rtmp->m_stream_id;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = timestamp;

    if(RTMP_IsConnected(rtmp)) {
        bool is_sent = RTMP_SendPacket(rtmp,packet,TRUE);
        if(is_sent)
            LOGD("frame data sent");
        else
            LOGD("frame data sending err");
        free(packet);
        return is_sent;
    } else {
        free(packet);
        is_connected = false;
        return false;
    }
}

bool rtmp_send_video_sequential_header(unsigned char * buf, int buf_size)
{
    int cur_pos = 0;
    NaluUnit naluUnit;
    bool sps_pps_get = false;
    int frame_size = 0;
    unsigned char * frame_data;
    while(readOneNaluFromBuf(naluUnit,buf,buf_size,cur_pos))
    {
        frame_size += naluUnit.size;
        if(naluUnit.type == NAL_SPS) {
            sps_len = naluUnit.size;
            sps = (unsigned char *)malloc(sps_len);
            memcpy(sps, naluUnit.data, sps_len);
        } else if(naluUnit.type == NAL_PPS) {
            pps_len = naluUnit.size;
            pps = (unsigned char *)malloc(pps_len);
            memcpy(pps, naluUnit.data, pps_len);
            sps_pps_get = true;
        }
    }

    if(sps_pps_get)
    {
        int i = 0;
        unsigned char * body;
        body = (unsigned char *)malloc(sps_len + pps_len + 11);
        memset(body, 0, sps_len + pps_len + 11);
        /*AVCDecoderConfigurationRecord*/
        body[i++] = 0x01;
        body[i++] = sps[1];
        body[i++] = sps[2];
        body[i++] = sps[3];
        body[i++] = 0xff;

        /*sps*/
        body[i++] = 0xE1;
        body[i++] = (sps_len >> 8) & 0xff;
        body[i++] = sps_len & 0xff;
        memcpy(&body[i],sps,sps_len);
        i += sps_len;

        /*pps*/
        body[i++] = 0x01;
        body[i++] = (pps_len >> 8) & 0xff;
        body[i++] = (pps_len) & 0xff;
        memcpy(&body[i],pps,pps_len);
        i += pps_len;

        // send metadata
        int width, height, frame_rate = 25;
        h264_decode_sps(sps, sps_len, width, height);
        char metadata[1024] = {0};
        char * p = (char *)metadata;
        p = put_byte(p, AMF_STRING );
        p = put_amf_string(p , "@setDataFrame");
        p = put_byte( p, AMF_STRING );
        p = put_amf_string( p, "onMetaData");
        p = put_byte(p, AMF_OBJECT );
        p = put_amf_string( p, "copyright");
        p = put_byte(p, AMF_STRING );
        p = put_amf_string( p, "strongen");
        p = put_amf_string( p, "width");
        p = put_amf_double( p, width);
        p = put_amf_string( p, "height");
        p = put_amf_double( p, height);
        p = put_amf_string( p, "framerate");
        p = put_amf_double( p, 25);
        p = put_amf_string( p, "videocodecid");
        p = put_amf_double( p, FLV_CODECID_H264);
        p = put_amf_string( p, "");
        p = put_byte( p, AMF_OBJECT_END);
        send(metadata, p - metadata, RTMP_PACKET_TYPE_INFO, 0);
        send_frame_data(body, i, 0, RTMP_PACKET_TYPE_VIDEO, NAL_SLICE_IDR, true);
        free(body);
        sps_pps_sent = true;
        return true;
    }
    return false;
}

int rtmp_send_video(unsigned char * buf, int len, unsigned char frame_type, int timestamp)
{
    send_frame_data(buf, len, timestamp, RTMP_PACKET_TYPE_VIDEO, frame_type, false);
}

int rtmp_send_aac_spec()
{
    unsigned char spec[2] = {0x12,0x10};
    send_frame_data(spec, 2, 0, RTMP_PACKET_TYPE_AUDIO, 0, true);
    aac_spec_sent = true;
    return TRUE;
}

void rtmp_send_audio(unsigned char * buf, int len, int timestamp)
{
    send_frame_data(buf, len, timestamp, RTMP_PACKET_TYPE_AUDIO, 0, false);
}

void *frame_task_execute(void *arg)
{
    Frame * frame = NULL;
    int frame_size = 0, i = 0;
    unsigned char frame_type = NAL_SLICE;
    int cur_pos = 0;
    unsigned char * frame_data;
    NaluUnit naluUnit;

    while(TRUE) {
        pthread_mutex_lock(&mutex);
        while(!frameList || frameList->size == 0)
        {
            pthread_cond_wait(&cond, &mutex);
        }
        if(!frame_sending_thread_started)
        {
            pthread_mutex_unlock(&mutex);
            break;
        }
        frameList->size --;
        frame = frameList->head;
        frameList->head = frameList->head->next;
        if(frameList->size == 0)
        {
            frameList->tail = NULL;
        }
        pthread_mutex_unlock(&mutex);
        if(frame->packet_type == RTMP_PACKET_TYPE_VIDEO)
            rtmp_send_video(frame->data, frame->size, frame->frame_type, frame->pts);
        else if(frame->packet_type == RTMP_PACKET_TYPE_AUDIO)
            rtmp_send_audio(frame->data, frame->size, frame->pts);
        free(frame->data);
        free(frame);
    }
}

bool init(char * rtmp_url)
{
    frameList = (FrameList*)malloc(sizeof(FrameList));
    frameList->size = 0;
    frameList->head = NULL;
    frameList->tail = NULL;
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
	frame_sending_thread_started = true;
    if (pthread_create(&frame_sending_thread, NULL, frame_task_execute, NULL))
    {
        LOGE("error creating thread.");
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        frame_sending_thread_started = false;
        return false;
    }
	is_connected = true;
	return true;
}

void close()
{
    frame_sending_thread_started = false;
    pthread_cond_broadcast(&cond);
    if(frameList)
    {
        pthread_mutex_lock(&mutex);
        Frame * frame;
        while(frameList->size > 0)
        {
            frame = frameList->head;
            frameList->head = frameList->head->next;
            frameList->size --;
            if(frameList->size == 0)
                frameList->tail = NULL;
            free(frame->data);
            free(frame);
        }
        free(frameList);
        frameList = NULL;
        pthread_mutex_unlock(&mutex);
    }

	RTMP_Close(rtmp);
	RTMP_Free(rtmp);
	aac_spec_sent = false;
    sps_pps_sent = false;
    is_connected = false;
}

void Java_me_shengbin_corerecorder_LiveStreamOutput_rtmpSendVideoData(JNIEnv * env, jobject obj, jbyteArray array, jint timestamp)
{
    int frame_size = 0, i = 0;
    unsigned char frame_type = NAL_SLICE;
    unsigned char * frame_data;
    int cur_pos = 0;

    if(!is_connected)
    {
        close();
        if(!rtmp_connect_url)
            return;
        if(!init(rtmp_connect_url))
            return;
        else {
            is_connected = true;
            sps_pps_sent = false;
            aac_spec_sent = false;
        }
    }

	jbyte * data = env->GetByteArrayElements(array, 0);
	jsize length = env->GetArrayLength(array);
	NaluUnit naluUnit;
    
    if(!sps_pps_sent)
    {
        bool is_sent = rtmp_send_video_sequential_header((unsigned char*)data,length);
        if(is_sent) {
            rtmp_send_aac_spec();
        } else {
            env->ReleaseByteArrayElements(array,data,0);
            return;
        }
    } 
    
    while(readOneNaluFromBuf(naluUnit,(unsigned char*)data,length,cur_pos))
    {
        if(naluUnit.type == NAL_SPS || naluUnit.type == NAL_PPS || naluUnit.type == NAL_AUD)
            continue;
        frame_size += naluUnit.size + 4;
    }
    cur_pos = 0;
    frame_data = (unsigned char *)malloc(frame_size);
    memset(frame_data, 0, frame_size);
    while(readOneNaluFromBuf(naluUnit,(unsigned char*)data,length,cur_pos))
    {
        if(naluUnit.type == NAL_SPS || naluUnit.type == NAL_PPS || naluUnit.type == NAL_AUD)
            continue;
        frame_data[i++] = (naluUnit.size >> 24) & 0xff;
        frame_data[i++] = (naluUnit.size >> 16) & 0xff;
        frame_data[i++] = (naluUnit.size >> 8) & 0xff;
        frame_data[i++] = naluUnit.size & 0xff;
        memcpy(frame_data+i, naluUnit.data, naluUnit.size);
        i += naluUnit.size;
        frame_type = naluUnit.type;
    }
    pthread_mutex_lock(&mutex);
    if (frameList && frameList->size < FrameListSizeLimit)
    {
        Frame * frame = (Frame*)malloc(sizeof(Frame));
        frame->size = frame_size;
        frame->data = frame_data;
        frame->packet_type = RTMP_PACKET_TYPE_VIDEO;
        frame->frame_type = frame_type;
        frame->pts = timestamp;
        frame->next = NULL;
        if(frameList->size == 0)
        {
            frameList->head = frame;
            frameList->tail = frame;
        } else {
            frameList->tail->next = frame;
            frameList->tail = frame;
        }
        frameList->size ++;
        pthread_cond_broadcast(&cond);
    } else {
        free(frame_data);
    }
    pthread_mutex_unlock(&mutex);
    //rtmp_send_video(frame_data, frame_size, frame_type, timestamp);
    //free(frame_data);
	env->ReleaseByteArrayElements(array,data,0);
}


void Java_me_shengbin_corerecorder_LiveStreamOutput_rtmpSendAudioData(JNIEnv * env, jobject obj, jbyteArray array, jint timestamp)
{
    if(!is_connected)
        return;
	jbyte * data = env->GetByteArrayElements(array, 0);
	jsize length = env->GetArrayLength(array);
    
	if(!aac_spec_sent)
    {
        //rtmp_send_aac_spec();
        env->ReleaseByteArrayElements(array,data,0);
        return;
    }
    unsigned char * frame_data;
    frame_data = (unsigned char *)malloc(length);
    memset(frame_data, 0, length);
    memcpy(frame_data, (unsigned char *)data, length);

    pthread_mutex_lock(&mutex);
    if (frameList && frameList->size < FrameListSizeLimit)
    {
        Frame * frame = (Frame*)malloc(sizeof(Frame));
        frame->size = length;
        frame->data = frame_data;
        frame->pts = timestamp;
        frame->packet_type = RTMP_PACKET_TYPE_AUDIO;
        frame->next = NULL;
        if(frameList->size == 0)
        {
            frameList->head = frame;
            frameList->tail = frame;
        } else {
            frameList->tail->next = frame;
            frameList->tail = frame;
        }
        frameList->size ++;
        pthread_cond_broadcast(&cond);
    } else {
        free(frame_data);
    }
    pthread_mutex_unlock(&mutex);
	//rtmp_send_audio((unsigned char *)data,length,timestamp);
	env->ReleaseByteArrayElements(array,data,0);
}

jboolean Java_me_shengbin_corerecorder_LiveStreamOutput_rtmpInit(JNIEnv * env, jobject obj, jstring url)
{
	char * rtmp_url = (char*)env->GetStringUTFChars(url, 0);
    if(rtmp_connect_url)
        free(rtmp_connect_url);
    rtmp_connect_url = (char*)malloc(strlen(rtmp_url) + 1);
    strcpy(rtmp_connect_url, rtmp_url);
	LOGI("url %s",rtmp_url);
	return init(rtmp_url);
}

jboolean Java_me_shengbin_corerecorder_LiveStreamOutput_rtmpReconnect(JNIEnv * env, jobject obj, jstring url)
{
	char * rtmp_url = (char*)env->GetStringUTFChars(url, 0);
	close();
	return init(rtmp_url);
}

jboolean Java_me_shengbin_corerecorder_LiveStreamOutput_rtmpSend(JNIEnv * env, jobject obj, jbyteArray array, jint type, jint timestamp)
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

void Java_me_shengbin_corerecorder_LiveStreamOutput_rtmpClose(JNIEnv * env, jobject obj)
{
	if(rtmp == NULL)
		return;
	close();
}
