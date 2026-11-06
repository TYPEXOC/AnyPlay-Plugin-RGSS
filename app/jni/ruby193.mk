LOCAL_PATH := $(call my-dir)/ruby193
LOCAL_BUILD_PATH := $(call my-dir)/build-$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)

LOCAL_MODULE := ruby193
LOCAL_SRC_FILES := $(LOCAL_BUILD_PATH)/lib/libruby193.so
LOCAL_C_INCLUDES := \
	$(LOCAL_BUILD_PATH)/include/ruby-1.9.3/ruby-1.9.1 \
	$(LOCAL_BUILD_PATH)/include/ruby-1.9.3/ruby-1.9.1/arm-eabi/
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_C_INCLUDES)

include $(PREBUILT_SHARED_LIBRARY)
