LOCAL_PATH := $(call my-dir)/ruby187
LOCAL_BUILD_PATH := $(call my-dir)/build-$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)

LOCAL_MODULE := ruby187
LOCAL_SRC_FILES := $(LOCAL_BUILD_PATH)/lib/libruby187.so
LOCAL_C_INCLUDES := $(LOCAL_BUILD_PATH)/include/ruby-1.8.7
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_BUILD_PATH)/include/ruby-1.8.7

include $(PREBUILT_SHARED_LIBRARY)
