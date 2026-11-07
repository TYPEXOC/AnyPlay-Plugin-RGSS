L_PATH := $(call my-dir)

# Include dependecies
include $(L_PATH)/SDL2/Android.mk 
include $(L_PATH)/SDL2_image/Android.mk
include $(L_PATH)/SDL2_ttf/Android.mk
include $(L_PATH)/SDL2_sound.mk
include $(L_PATH)/libogg.mk
include $(L_PATH)/libvorbis.mk
include $(L_PATH)/libtheora.mk
include $(L_PATH)/openal.mk
include $(L_PATH)/pixman.mk
include $(L_PATH)/physfs.mk
include $(L_PATH)/uchardet.mk
include $(L_PATH)/libiconv.mk
include $(L_PATH)/openssl.mk

# ---------------------------------------------------------------------------
# Runtimes. A runtime = one libruby + one engine, loaded together in a fresh
# process (one process = one runtime; Ruby cannot be initialised twice).
#
#   id    libruby (file in APK)  engine (file in APK)       Ruby
#   r3    libruby_r3.so          libmkxp-z-r3.so            3.1, no compat patches
#   r18   libruby_r18.so         libmkxp-z-r18.so           3.1 + Ruby 1.8 compatibility
#                                                           layer (ruby-legacy/), reports
#                                                           itself as 1.8.7 to game code
#   r34   libruby_r34.so         libmkxp-z-r34.so           Ruby 3.4.9 (+ YJIT on arm64
#                                                           when BUILD_R34_YJIT=1).
#                                                           Only built when BUILD_R34=1.
#
# r3 and r18 libraries both have the internal SONAME "libruby.so" (see ruby.mk).
# The real Ruby 1.8.7 build (r187 / libruby18.so) was removed in Phase B.
# ---------------------------------------------------------------------------

# Ruby 3.1 libraries (prebuilt by the Makefile)
RUBY_PB_MODULE := ruby_r3
RUBY_PB_DIR    := build-$(TARGET_ARCH_ABI)
RUBY_PB_API    := 3.1.0
include $(L_PATH)/ruby.mk

RUBY_PB_MODULE := ruby_r18
RUBY_PB_DIR    := build-r18-$(TARGET_ARCH_ABI)
RUBY_PB_API    := 3.1.0
include $(L_PATH)/ruby.mk

# Ruby 3.4 (optional, BUILD_R34=1)
ifeq ($(BUILD_R34),1)
RUBY_PB_MODULE := ruby_r34
RUBY_PB_DIR    := build-r34-$(TARGET_ARCH_ABI)
RUBY_PB_API    := 3.4.0
include $(L_PATH)/ruby.mk
endif

# r3 engine
MKXPZ_MODULE := mkxp-z-r3
MKXPZ_RUBY_MODULE := ruby_r3
MKXPZ_RUNTIME_ID := r3
MKXPZ_LEGACY_RUBY := 0
MKXPZ_RUBY18_COMPAT := 0
MKXPZ_YJIT := 0
include $(L_PATH)/mkxp-z.mk

# r18 engine (same engine source, compiled against the r18 Ruby headers; it also calls
# the mkxp_legacy_* functions that only the patched r18 libruby exports)
MKXPZ_MODULE := mkxp-z-r18
MKXPZ_RUBY_MODULE := ruby_r18
MKXPZ_RUNTIME_ID := r18
MKXPZ_LEGACY_RUBY := 0
MKXPZ_RUBY18_COMPAT := 1
MKXPZ_YJIT := 0
include $(L_PATH)/mkxp-z.mk

# r34 engine (optional). Compiled against the Ruby 3.4 headers. YJIT only exists in the
# 64-bit ARM build of Ruby 3.4 (it is not supported on 32-bit ARM), so -DMKXPZ_YJIT is
# only set for arm64-v8a and only when BUILD_R34_YJIT=1.
ifeq ($(BUILD_R34),1)
MKXPZ_MODULE := mkxp-z-r34
MKXPZ_RUBY_MODULE := ruby_r34
MKXPZ_RUNTIME_ID := r34
MKXPZ_LEGACY_RUBY := 0
MKXPZ_RUBY18_COMPAT := 0
MKXPZ_YJIT := 0
ifeq ($(BUILD_R34_YJIT),1)
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
MKXPZ_YJIT := 1
endif
endif
include $(L_PATH)/mkxp-z.mk
endif
