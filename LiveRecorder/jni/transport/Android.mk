LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := transport

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../librtmp/
LOCAL_SRC_FILES := transport.cpp
LOCAL_SHARED_LIBRARIES := rtmp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)