LOCAL_PATH := $(call my-dir)
ARCH_ABI := $(TARGET_ARCH_ABI)
#
# Prebuilt Shared library
#
include $(CLEAR_VARS)
LOCAL_MODULE	:= x264_prebuilt
LOCAL_SRC_FILES	:= lib/$(ARCH_ABI)/libx264.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := x264
LOCAL_WHOLE_STATIC_LIBRARIES := x264_prebuilt
include $(BUILD_SHARED_LIBRARY)
