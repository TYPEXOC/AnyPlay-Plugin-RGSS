# Prebuilt Ruby shared library (libruby.so built by the Makefile).
#
# This file is included once per Ruby build ("r3", "r18" and, when BUILD_R34=1, "r34").
# Android.mk sets these variables before each include:
#   RUBY_PB_MODULE : ndk-build module name. The file that ends up in the APK is
#                    lib<module>.so (libruby_r3.so / libruby_r18.so).
#   RUBY_PB_DIR    : directory (inside jni/) holding that build:
#                    build-<abi> for r3, build-r18-<abi> for r18, build-r34-<abi> for r34.
#   RUBY_PB_API    : Ruby's library version, i.e. the include/ruby-<API> directory
#                    (3.1.0 for r3 and r18, 3.4.0 for r34). Defaults to 3.1.0.
#
# Both libraries have the SAME internal SONAME ("libruby.so"), so the engine that
# is linked against them records "NEEDED libruby.so". Only one of them is ever
# loaded in a process (MainActivity loads exactly one pair), and Android's linker
# satisfies that NEEDED entry with the library that is already loaded. There is
# deliberately NO file called libruby.so in the APK, so a wrong match fails loudly
# ("library libruby.so not found") instead of silently loading the other Ruby.
RUBY_PB_JNI := $(call my-dir)

LOCAL_PATH := $(RUBY_PB_JNI)
LOCAL_BUILD_PATH := $(RUBY_PB_JNI)/$(RUBY_PB_DIR)

include $(CLEAR_VARS)

LOCAL_MODULE := $(RUBY_PB_MODULE)

# Android.mk sets RUBY_PB_API before every include; 3.1.0 is the default.
RUBY_PB_API ?= 3.1.0
RUBY_PB_INC := $(LOCAL_BUILD_PATH)/include/ruby-$(RUBY_PB_API)

ifeq ($(RUBY_PB_API),3.1.0)

# r3 and r18 (Ruby 3.1): the arch directories below are the proven ones, unchanged.
LOCAL_C_INCLUDES := $(LOCAL_BUILD_PATH)/include/ruby-3.1.0

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_BUILD_PATH)/include/ruby-3.1.0

ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
	LOCAL_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/armv7a-linux-androideabi-android
	LOCAL_EXPORT_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/armv7a-linux-androideabi-android
else ifeq ($(TARGET_ARCH_ABI), arm64-v8a)
	LOCAL_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/aarch64-linux-android-android
	LOCAL_EXPORT_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/aarch64-linux-android-android
else ifeq ($(TARGET_ARCH_ABI), x86)
	LOCAL_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/i686-linux-android-android
	LOCAL_EXPORT_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/i686-linux-android-android
else ifeq ($(TARGET_ARCH_ABI), x86_64)
	LOCAL_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/x86_64-linux-android-android
	LOCAL_EXPORT_C_INCLUDES += $(LOCAL_BUILD_PATH)/include/ruby-3.1.0/x86_64-linux-android-android
endif

else

# r34 (Ruby 3.4): take whatever machine-specific header directory (the one that holds
# ruby/config.h) the build produced, so its exact name does not have to be guessed.
RUBY_PB_ARCH_INC := $(patsubst %/ruby/config.h,%,$(wildcard $(RUBY_PB_INC)/*/ruby/config.h))

LOCAL_C_INCLUDES := $(RUBY_PB_INC) $(RUBY_PB_ARCH_INC)

LOCAL_EXPORT_C_INCLUDES := $(RUBY_PB_INC) $(RUBY_PB_ARCH_INC)

endif

LOCAL_SRC_FILES := $(LOCAL_BUILD_PATH)/lib/libruby.so

include $(PREBUILT_SHARED_LIBRARY)
