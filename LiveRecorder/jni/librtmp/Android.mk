LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES += \
    amf.c \
    hashswf.c \
    log.c \
    parseurl.c \
    rtmp.c 
    
LOCAL_CFLAGS := -D__STDC_CONSTANT_MACROS -DNO_CRYPTO
LOCAL_LDLIBS := -llog
LOCAL_MODULE := rtmp

include $(BUILD_SHARED_LIBRARY)