LOCAL_PATH := $(call my-dir)/ruby187
LOCAL_BUILD_PATH := $(call my-dir)/build-$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)

LOCAL_MODULE := ruby187
LOCAL_SRC_FILES := $(LOCAL_BUILD_PATH)/lib/libruby187.so
# Ruby 1.8.7 installs headers under its configured archdir, which is not the
# --includedir used by newer Ruby versions. Match the source-header layout used
# by MTool and export it to mkxp-z-187's binding compilation.
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

include $(PREBUILT_SHARED_LIBRARY)
