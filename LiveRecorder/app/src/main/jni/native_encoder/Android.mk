LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := native_encoder
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libx264/include/
LOCAL_SRC_FILES := native_encoder.cpp jni_utils.cpp
LOCAL_SHARED_LIBRARIES += x264
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
