NDK       := ${ANDROID_NDK_HOME}
TOOLCHAIN := $(NDK)/toolchains/llvm/prebuilt/${ARCH}
NPROC     := $(shell nproc)

# OpenSSL's Android config requires both variables to detect modern NDK clang.
export ANDROID_NDK_ROOT := $(NDK)
export PATH := $(TOOLCHAIN)/bin:$(PATH)

# Android platform API version to build:
# (Default: 21 - Android 5.0 Lollipop)
# See https://apilevels.com/ for Android API versions.
# Note: If you building for 64-bit, you should target 21 and higher.
# api 23 supports 64 bits
API ?= 23

# Android CPU specific variables to build:
# (Default: ARMv7-A 32-bit)
# For ARMv7-A 32-bit:
# - HOST:   armv7a-linux-androideabi
# - TARGET: arm-linux-androideabi
# - ABI:    armeabi-v7a
# For ARMv8-A 64-bit:
# - HOST:   aarch64-linux-android
# - TARGET: aarch64-linux-android
# - ABI:    arm64-v8a
# For x86 32-bit:
# - HOST:   i686-linux-android
# - TARGET: i686-linux-android
# - ABI:    x86
# For x86_64 64-bit:
# - HOST:   x86_64-linux-android
# - TARGET: x86_64-linux-android
# - ABI:    x86_64
HOST   ?= armv7a-linux-androideabi
TARGET ?= arm-linux-androideabi
ABI    ?= armeabi-v7a

# Define directories
PREFIX            := $(HOST)$(API)
BUILD_PREFIX      := ${PWD}/build-$(ABI)
INCDIR            := $(BUILD_PREFIX)/include
LIBDIR            := $(BUILD_PREFIX)/lib
PKG_CONFIG_LIBDIR := $(BUILD_PREFIX)/lib/pkgconfig

ifneq (, ${MSYSTEM})
	RUBY_PREFIX := $(shell cygpath -m ${BUILD_PREFIX})
else ifneq (, $(findstring /cygdrive/,$(PATH)))
	RUBY_PREFIX := $(shell cygpath -m ${BUILD_PREFIX})
else
	RUBY_PREFIX := $(BUILD_PREFIX)
endif

# Toolchain tools
CC      := $(TOOLCHAIN)/bin/$(PREFIX)-clang
CXX     := $(TOOLCHAIN)/bin/$(PREFIX)-clang++
AS      := $(TOOLCHAIN)/bin/llvm-as
LD      := $(TOOLCHAIN)/bin/ld
AR      := $(TOOLCHAIN)/bin/llvm-ar
NM      := $(TOOLCHAIN)/bin/llvm-nm
OBJCOPY := $(TOOLCHAIN)/bin/llvm-objcopy
OBJDUMP := $(TOOLCHAIN)/bin/llvm-objdump
RANLIB  := $(TOOLCHAIN)/bin/llvm-ranlib
STRIP   := $(TOOLCHAIN)/bin/llvm-strip

# OpenSSL configure has own targets for Android building
ifeq ($(ABI), armeabi-v7a)
	OPENSSL_TARGET := android-arm
else ifeq ($(ABI), arm64-v8a)
	OPENSSL_TARGET := android-arm64
else ifeq ($(ABI), x86)
	OPENSSL_TARGET := android-x86
else ifeq ($(ABI), x86_64)
	OPENSSL_TARGET := android-x86_64
endif

# Some flags for ARM arch
ifeq ($(ABI), armeabi-v7a)
	CROSSFLAGS := -D__ARM_ARCH_7A__ -D__arm__ -march=armv7-a -mtune=cortex-a7 -mfpu=neon -mfloat-abi=softfp
else ifeq ($(ABI), arm64-v8a)
	CROSSFLAGS := -D__ARM_ARCH_8A__ -D__aarch64__ -march=armv8-a -mtune=cortex-a53
endif

# Compiler/linker flags
CFLAGS      := -O0 $(CROSSFLAGS) --sysroot=$(TOOLCHAIN)/sysroot -I$(TOOLCHAIN)/sysroot/usr/include -I$(INCDIR)
LDFLAGS     := -L$(LIBDIR) -lm -lz
CLANG_FLAGS := -target $(TARGET) --gcc-toolchain $(TOOLCHAIN)

# CMake arguments
CMAKE_ARGS := \
	-DCMAKE_TOOLCHAIN_FILE="$(NDK)/build/cmake/android.toolchain.cmake" \
	-DCMAKE_SYSTEM_NAME=Android \
	-DCMAKE_SYSTEM_VERSION=$(API) \
	-DANDROID_PLATFORM=$(API) \
	-DANDROID_ABI=$(ABI) \
	-DCMAKE_ANDROID_ARCH_ABI=$(ABI) \
	-DCMAKE_ANDROID_STL_TYPE=c++_shared \
	-DCMAKE_INSTALL_PREFIX="$(BUILD_PREFIX)" \
	-DCMAKE_PREFIX_PATH="$(BUILD_PREFIX)" \
	-DCMAKE_BUILD_TYPE=RelWithDebInfo \
	-DCMAKE_C_FLAGS="$(CFLAGS)" \
	-G "Unix Makefiles"

# Autoconf environment variables
CONFIGURE_ENV := \
	CC="$(CC)" CXX="$(CXX)" AS="$(AS)" LD="$(LD)" AR="$(AR)" NM="$(NM)" \
	RANLIB="$(RANLIB)" STRIP="$(STRIP)" OBJCOPY="$(OBJCOPY)" OBJDUMP="$(OBJDUMP)" \
	CFLAGS="$(CFLAGS)" LDFLAGS="$(LDFLAGS)" CLANG_FLAGS="$(CLANG_FLAGS)" \
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)"

# Autoconf arguments
CONFIGURE_ARGS := --host=$(HOST) --prefix="$(BUILD_PREFIX)"

# Only Android 8.0 Oreo and higher can have some POSIX functions like:
# getpwent(), setpwent(), endpwent(), nl_langinfo(), and nl_langinfo_l()
ifeq ($(shell test $(API) -ge 26; echo $$?), 1)
	CONFIGURE_ARGS += ac_cv_header_pwd_h=no ac_cv_header_langinfo_h=no
endif

# OpenAL CMake arguments to build
OPENAL_CONFIGURE_ARGS := \
	-DALSOFT_REQUIRE_OPENSL=ON \
	-DALSOFT_EXAMPLES=OFF \
	-DALSOFT_UTILS=OFF

# FluidSynth only enables SF3 decoding when libsndfile is built with its Xiph
# codecs. libsndfile 1.2.2 checks for Vorbis, FLAC, and Opus together, so build
# these pinned upstream libraries as static PIC archives and link them into
# FluidSynth. The APK needs no additional decoder libraries at runtime.
CODEC_CMAKE_ARGS := \
	-DCMAKE_TOOLCHAIN_FILE="$(NDK)/build/cmake/android.toolchain.cmake" \
	-DCMAKE_SYSTEM_NAME=Android \
	-DCMAKE_SYSTEM_VERSION=$(API) \
	-DANDROID_PLATFORM=android-$(API) \
	-DANDROID_ABI=$(ABI) \
	-DCMAKE_ANDROID_ARCH_ABI=$(ABI) \
	-DCMAKE_INSTALL_PREFIX="$(BUILD_PREFIX)" \
	-DCMAKE_INSTALL_LIBDIR=lib \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DCMAKE_PREFIX_PATH="$(BUILD_PREFIX)" \
	-DBUILD_SHARED_LIBS=OFF \
	-G "Unix Makefiles"

OGG_BUILD_DIR := libogg/cmakebuild-$(ABI)
VORBIS_BUILD_DIR := libvorbis/cmakebuild-$(ABI)
FLAC_BUILD_DIR := flac/cmakebuild-$(ABI)
OPUS_BUILD_DIR := opus/cmakebuild-$(ABI)
SNDFILE_BUILD_DIR := libsndfile/cmakebuild-$(ABI)
SNDFILE_CMAKE_CONFIG_DIR := $(BUILD_PREFIX)/tyn-cmake/SndFile
OGG_CMAKE_ARGS := $(CODEC_CMAKE_ARGS) -DBUILD_TESTING=OFF -DINSTALL_DOCS=OFF
VORBIS_CMAKE_ARGS := $(CODEC_CMAKE_ARGS) \
	-DBUILD_TESTING=OFF \
	-DOGG_ROOT="$(BUILD_PREFIX)" \
	-DOGG_INCLUDE_DIR="$(INCDIR)" \
	-DOGG_LIBRARY="$(LIBDIR)/libogg.a"
FLAC_CMAKE_ARGS := $(CODEC_CMAKE_ARGS) \
	-DBUILD_CXXLIBS=OFF \
	-DBUILD_PROGRAMS=OFF \
	-DBUILD_EXAMPLES=OFF \
	-DBUILD_TESTING=OFF \
	-DBUILD_DOCS=OFF \
	-DBUILD_UTILS=OFF \
	-DINSTALL_MANPAGES=OFF \
	-DOGG_INCLUDE_DIR="$(INCDIR)" \
	-DOGG_LIBRARY="$(LIBDIR)/libogg.a" \
	-DWITH_OGG=ON
OPUS_CMAKE_ARGS := $(CODEC_CMAKE_ARGS) \
	-DOPUS_BUILD_TESTING=OFF \
	-DOPUS_BUILD_PROGRAMS=OFF \
	-DOPUS_INSTALL_CMAKE_CONFIG_MODULE=ON \
	-DOPUS_INSTALL_PKG_CONFIG_MODULE=ON
SNDFILE_CMAKE_ARGS := \
	-DCMAKE_TOOLCHAIN_FILE="$(NDK)/build/cmake/android.toolchain.cmake" \
	-DCMAKE_SYSTEM_NAME=Android \
	-DCMAKE_SYSTEM_VERSION=$(API) \
	-DANDROID_PLATFORM=android-$(API) \
	-DANDROID_ABI=$(ABI) \
	-DCMAKE_ANDROID_ARCH_ABI=$(ABI) \
	-DCMAKE_INSTALL_PREFIX="$(BUILD_PREFIX)" \
	-DCMAKE_INSTALL_LIBDIR=lib \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DCMAKE_PREFIX_PATH="$(BUILD_PREFIX)" \
	-DBUILD_SHARED_LIBS=OFF \
	-DENABLE_EXTERNAL_LIBS=ON \
	-DENABLE_MPEG=OFF \
	-DBUILD_PROGRAMS=OFF \
	-DBUILD_EXAMPLES=OFF \
	-DBUILD_TESTING=OFF \
	-DENABLE_CPACK=OFF \
	-DINSTALL_MANPAGES=OFF \
	-DOGG_INCLUDE_DIR="$(INCDIR)" \
	-DOGG_LIBRARY="$(LIBDIR)/libogg.a" \
	-DVorbis_Vorbis_INCLUDE_DIR="$(INCDIR)" \
	-DVorbis_Vorbis_LIBRARY="$(LIBDIR)/libvorbis.a" \
	-DVorbis_Enc_INCLUDE_DIR="$(INCDIR)" \
	-DVorbis_Enc_LIBRARY="$(LIBDIR)/libvorbisenc.a" \
	-DFLAC_INCLUDE_DIR="$(INCDIR)" \
	-DFLAC_LIBRARY="$(LIBDIR)/libFLAC.a" \
	-DOPUS_INCLUDE_DIR="$(INCDIR)" \
	-DOPUS_LIBRARY="$(LIBDIR)/libopus.a" \
	-G "Unix Makefiles"

# FluidSynth is used as a synthesizer only: mkxp-z renders its PCM output into
# its existing OpenAL stream, so platform audio/MIDI drivers are unnecessary.
FLUIDSYNTH_BUILD_DIR := fluidsynth/cmakebuild-$(ABI)
FLUIDSYNTH_CMAKE_ARGS := \
	-DCMAKE_TOOLCHAIN_FILE="$(NDK)/build/cmake/android.toolchain.cmake" \
	-DCMAKE_SYSTEM_VERSION=$(API) \
	-DANDROID_PLATFORM=android-$(API) \
	-DANDROID_ABI=$(ABI) \
	-DCMAKE_ANDROID_ARCH_ABI=$(ABI) \
	-DCMAKE_ANDROID_STL_TYPE=c++_shared \
	-DCMAKE_FIND_PACKAGE_PREFER_CONFIG=ON \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_INSTALL_PREFIX="$(BUILD_PREFIX)" \
	-DCMAKE_INSTALL_LIBDIR=lib \
	-DCMAKE_CXX_STANDARD=17 \
	-DBUILD_SHARED_LIBS=ON \
	-Dosal=cpp11 \
	-Denable-alsa=OFF \
	-Denable-aufile=OFF \
	-Denable-dbus=OFF \
	-Denable-jack=OFF \
	-Denable-ladspa=OFF \
	-Denable-libinstpatch=OFF \
	-Denable-libsndfile=ON \
	-DSndFile_DIR="$(SNDFILE_CMAKE_CONFIG_DIR)" \
	-Denable-midishare=OFF \
	-Denable-native-dls=OFF \
	-Denable-network=OFF \
	-Denable-oboe=OFF \
	-Denable-openmp=OFF \
	-Denable-opensles=OFF \
	-Denable-oss=OFF \
	-Denable-pipewire=OFF \
	-Denable-pulseaudio=OFF \
	-Denable-readline=OFF \
	-Denable-sdl3=OFF \
	-Denable-signalsmith=OFF

# Ruby configure arguments to build
RUBY_BASE_CONFIGURE_ARGS := \
	--prefix="$(RUBY_PREFIX)" \
	--enable-shared \
	--enable-static \
	--enable-install-static-library \
	--disable-install-doc \
	--disable-rubygems \
	--with-static-linked-ext \
	--without-gmp
RUBY_CONFIGURE_ARGS := $(RUBY_BASE_CONFIGURE_ARGS) \
	--with-out-ext=readline,dbm,gdbm,win32,win32ole,fiddle
RUBY_187_CONFIGURE_ARGS := $(RUBY_BASE_CONFIGURE_ARGS) \
	--without-readline --without-dbm --without-gdbm --without-win32 \
	--without-win32ole --without-fiddle --without-bigdecimal \
	--without-Win32API --without-curses --without-tk
RUBY_193_CONFIGURE_ARGS := $(RUBY_BASE_CONFIGURE_ARGS) \
	--with-out-ext=readline,dbm,gdbm,win32,win32ole,fiddle,ripper

CONFIGURE := $(CONFIGURE_ENV) ./configure $(CONFIGURE_ARGS)
CMAKE     := $(CONFIGURE_ENV) cmake .. $(CMAKE_ARGS)

# The legacy Ruby generator scripts are patched for the supported CI host Ruby.
# The Android runtimes still use the matching target headers and NDK toolchain.
SHELL := /bin/bash
HOST_TOOLS := $(BUILD_PREFIX)/host-tools
HOST_RUBY := $(HOST_TOOLS)/ruby
RUBY_187_ENV := export PATH="$(HOST_TOOLS):$(BUILD_PREFIX)/bin:$$PATH"
RUBY_193_ENV := export PATH="$(HOST_TOOLS):$(BUILD_PREFIX)/bin:$$PATH" && \
	export CC="$(CC)" && export CFLAGS="$(CFLAGS)" && \
	export LDFLAGS="$(LDFLAGS)" && export LDSHARED="$(CC) -shared"
RUBY_31_ENV := true

# Ruby 1.8.7 and 1.9.3 do not recognize Android triples; use the matching CPU family.
ifeq ($(ABI), armeabi-v7a)
	HOST_187 := arm-linux-eabi
	HOST_193 := arm-linux-eabi
else ifeq ($(ABI), arm64-v8a)
	HOST_187 := arm-linux-eabi
	HOST_193 := arm-linux-eabi
else ifeq ($(ABI), x86)
	HOST_187 := i686-linux-gnu
	HOST_193 := i686-linux-gnu
else ifeq ($(ABI), x86_64)
	HOST_187 := x86_64-linux-gnu
	HOST_193 := x86_64-linux-gnu
endif

.PHONY: all init clean cpufeatures pixman iconv openal openssl ogg-codec vorbis-codec flac-codec opus-codec soundfont-codecs libsndfile fluidsynth ruby ruby187 ruby193 bison clean-pixman clean-iconv clean-openal clean-openssl clean-ruby clean-ruby187 clean-ruby193 clean-ruby-artifacts clean-bison clean-soundfont-codecs

default: all


# -------------------- Android CPU features --------------------
cpufeatures: init $(LIBDIR)/libcpufeatures.a

$(LIBDIR)/libcpufeatures.a: $(LIBDIR)/cpu-features.o
	$(AR) rc $(LIBDIR)/libcpufeatures.a $(LIBDIR)/cpu-features.o

$(LIBDIR)/cpu-features.o:
	$(CC) -c $(NDK)/sources/android/cpufeatures/cpu-features.c -o $(LIBDIR)/cpu-features.o -Wall -Wextra -Werror

# -------------------- Pixman --------------------
pixman: init cpufeatures $(LIBDIR)/libpixman-1.a

$(LIBDIR)/libpixman-1.a: pixman/Makefile
	cd pixman; make -j$(NPROC); make install

pixman/Makefile: pixman/configure
	cd pixman; $(CONFIGURE_ENV) \
	CFLAGS="$(CFLAGS) -fno-integrated-as -I$(NDK)/sources/android/cpufeatures" \
	LDFLAGS="$(LDFLAGS) -lcpufeatures" \
	./configure $(CONFIGURE_ARGS) --enable-static=true --enable-shared=false

pixman/configure: pixman/configure.ac
	cd pixman; autoreconf -i

# -------------------- iconv --------------------
iconv: init $(LIBDIR)/libiconv.a

$(LIBDIR)/libiconv.a: libiconv/Makefile
	cd libiconv; make -j$(NPROC); make install

libiconv/Makefile: libiconv/configure
	cd libiconv; $(CONFIGURE) --enable-static=true --enable-shared=false

# -------------------- OpenAL --------------------
openal: init $(LIBDIR)/libopenal.so

$(LIBDIR)/libopenal.so: openal/cmakebuild/Makefile
	cd openal/cmakebuild; make -j$(NPROC); make install

openal/cmakebuild/Makefile: openal/CMakeLists.txt
	cd openal; mkdir cmakebuild; cd cmakebuild; \
	$(CMAKE) -DLIBTYPE=SHARED $(OPENAL_CONFIGURE_ARGS)

# -------------------- SF3 SoundFont decoder dependencies --------------------
ogg-codec: init
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" cmake -S libogg -B $(OGG_BUILD_DIR) $(OGG_CMAKE_ARGS)
	cmake --build $(OGG_BUILD_DIR) --parallel $(NPROC)
	cmake --install $(OGG_BUILD_DIR)
	test -f "$(LIBDIR)/libogg.a"

vorbis-codec: ogg-codec
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" cmake -S libvorbis -B $(VORBIS_BUILD_DIR) $(VORBIS_CMAKE_ARGS)
	cmake --build $(VORBIS_BUILD_DIR) --parallel $(NPROC)
	cmake --install $(VORBIS_BUILD_DIR)
	test -f "$(LIBDIR)/libvorbis.a"
	test -f "$(LIBDIR)/libvorbisenc.a"

flac-codec: ogg-codec
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" cmake -S flac -B $(FLAC_BUILD_DIR) $(FLAC_CMAKE_ARGS)
	cmake --build $(FLAC_BUILD_DIR) --parallel $(NPROC)
	cmake --install $(FLAC_BUILD_DIR)
	test -f "$(LIBDIR)/libFLAC.a"

opus-codec: init
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" cmake -S opus -B $(OPUS_BUILD_DIR) $(OPUS_CMAKE_ARGS)
	cmake --build $(OPUS_BUILD_DIR) --parallel $(NPROC)
	cmake --install $(OPUS_BUILD_DIR)
	test -f "$(LIBDIR)/libopus.a"

soundfont-codecs: vorbis-codec flac-codec opus-codec

libsndfile: soundfont-codecs
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" cmake -S libsndfile -B $(SNDFILE_BUILD_DIR) $(SNDFILE_CMAKE_ARGS)
	cmake --build $(SNDFILE_BUILD_DIR) --parallel $(NPROC)
	cmake --install $(SNDFILE_BUILD_DIR)
	test -f "$(LIBDIR)/libsndfile.a"
	grep -Eq '^#define HAVE_EXTERNAL_XIPH_LIBS 1$$' "$(SNDFILE_BUILD_DIR)/src/config.h" || { \
		echo "libsndfile did not enable its Ogg/Vorbis/FLAC/Opus codecs"; \
		grep -E 'HAVE_EXTERNAL_XIPH_LIBS|ENABLE_EXTERNAL_LIBS' "$(SNDFILE_BUILD_DIR)/src/config.h" "$(SNDFILE_BUILD_DIR)/CMakeCache.txt" || true; \
		exit 1; \
	}
	mkdir -p "$(SNDFILE_CMAKE_CONFIG_DIR)"
	cp cmake/SndFileConfig.cmake cmake/SndFileConfigVersion.cmake "$(SNDFILE_CMAKE_CONFIG_DIR)/"
	@echo "SF3 Vorbis decoding dependencies are enabled for $(ABI)."

# -------------------- FluidSynth --------------------
fluidsynth: init libsndfile $(LIBDIR)/libfluidsynth.so

$(LIBDIR)/libfluidsynth.so: fluidsynth/CMakeLists.txt libsndfile
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" cmake -S fluidsynth -B $(FLUIDSYNTH_BUILD_DIR) $(FLUIDSYNTH_CMAKE_ARGS)
	grep -Eq '^#define LIBSNDFILE_SUPPORT 1$$' $(FLUIDSYNTH_BUILD_DIR)/config.h || { \
		echo "FluidSynth did not detect the bundled libsndfile package"; \
		grep -E 'SndFile|SNDFILE|LIBSNDFILE' $(FLUIDSYNTH_BUILD_DIR)/CMakeCache.txt $(FLUIDSYNTH_BUILD_DIR)/config.h || true; \
		exit 1; \
	}
	grep -Eq '^#define LIBSNDFILE_HASVORBIS 1$$' $(FLUIDSYNTH_BUILD_DIR)/config.h || { \
		echo "FluidSynth detected libsndfile without Vorbis support"; \
		grep -E 'SndFile|SNDFILE|LIBSNDFILE' $(FLUIDSYNTH_BUILD_DIR)/CMakeCache.txt $(FLUIDSYNTH_BUILD_DIR)/config.h || true; \
		exit 1; \
	}
	cmake --build $(FLUIDSYNTH_BUILD_DIR) --target libfluidsynth --parallel $(NPROC)
	cmake --install $(FLUIDSYNTH_BUILD_DIR) --component fluidsynth_runtime
	test -f "$@"

# -------------------- OpenSSL --------------------
openssl: init $(LIBDIR)/libssl.a

$(LIBDIR)/libssl.a: openssl/Makefile
	cd openssl; make -j$(NPROC); make install_sw

openssl/Makefile: openssl/Configure
	cd openssl; CFLAGS="-Wno-macro-redefined" ./Configure $(OPENSSL_TARGET) \
	no-shared -D__ANDROID_API__=$(API) \
	--prefix="$(BUILD_PREFIX)" --openssldir="$(BUILD_PREFIX)"

# -------------------- Ruby --------------------
ruby: init openssl $(LIBDIR)/libruby.so.3.1.0

$(LIBDIR)/libruby.so.3.1.0: ruby/Makefile
	cd ruby; $(RUBY_31_ENV) && make -j$(NPROC); $(RUBY_31_ENV) && make install
	cd $(LIBDIR); ln -sf libruby.so.3.1.3 libruby.so; ln -sf libruby.so.3.1.3 libruby313.so

ruby/Makefile: ruby/configure
	cd ruby; DLDFLAGS="-Wl,-soname,libruby.so" \
	$(CONFIGURE) $(RUBY_CONFIGURE_ARGS)

ruby/configure: ruby/configure.ac
	cd ruby; $(RUBY_31_ENV) && autoreconf -i

# -------------------- Ruby 1.8.7 --------------------
ruby187: init openssl $(LIBDIR)/libruby187.so

$(HOST_RUBY): init
	mkdir -p "$(HOST_TOOLS)"
	host_ruby="$$(command -v ruby)"; test -n "$$host_ruby"; \
	printf '#!/bin/sh\nexec "%s" --disable-gems "$$@"\n' "$$host_ruby" > "$@"
	chmod +x "$@"

$(LIBDIR)/libruby187.so: ruby187/Makefile
	cd ruby187; $(RUBY_187_ENV) && make -j$(NPROC); $(RUBY_187_ENV) && make install
	mkdir -p "$(BUILD_PREFIX)/include/ruby-1.8.7"
	cp -f ruby187/*.h "$(BUILD_PREFIX)/include/ruby-1.8.7/"
	cd $(LIBDIR); ln -sf libruby.so.1.8.7 libruby187.so

ruby187/Makefile: ruby187/configure $(BUILD_PREFIX)/bin/bison $(HOST_RUBY)
	cd ruby187; $(RUBY_187_ENV) && DLDFLAGS="-Wl,-soname,libruby187.so" \
	CC="$(CC)" CXX="$(CXX)" AS="$(AS)" LD="$(LD)" AR="$(AR)" NM="$(NM)" \
	RANLIB="$(RANLIB)" STRIP="$(STRIP)" OBJCOPY="$(OBJCOPY)" OBJDUMP="$(OBJDUMP)" \
	CFLAGS="$(CFLAGS)" LDFLAGS="$(LDFLAGS)" CLANG_FLAGS="$(CLANG_FLAGS)" \
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" LDSHARED="$(CC) -shared" \
	./configure --host=$(HOST_187) --prefix="$(BUILD_PREFIX)" \
	ac_cv_header_pwd_h=no ac_cv_header_langinfo_h=no \
	$(RUBY_187_CONFIGURE_ARGS) --program-suffix=187

ruby187/configure: ruby187/configure.in
	cd ruby187; $(RUBY_187_ENV) && autoreconf -i

# -------------------- Ruby 1.9.3 --------------------
ruby193: init openssl $(LIBDIR)/libruby193.so

$(LIBDIR)/libruby193.so: ruby193/Makefile
	cd ruby193; $(RUBY_193_ENV) && make -j$(NPROC); $(RUBY_193_ENV) && make install
	cd $(LIBDIR); ln -sf libruby.so.1.9.1 libruby193.so

ruby193/Makefile: ruby193/configure $(BUILD_PREFIX)/bin/bison $(HOST_RUBY)
	cd ruby193; $(RUBY_193_ENV) && DLDFLAGS="-Wl,-soname,libruby193.so" \
	CC="$(CC)" CXX="$(CXX)" AS="$(AS)" LD="$(LD)" AR="$(AR)" NM="$(NM)" \
	RANLIB="$(RANLIB)" STRIP="$(STRIP)" OBJCOPY="$(OBJCOPY)" OBJDUMP="$(OBJDUMP)" \
	CFLAGS="$(CFLAGS) -U_FORTIFY_SOURCE" LDFLAGS="$(LDFLAGS)" CLANG_FLAGS="$(CLANG_FLAGS)" \
	PKG_CONFIG_LIBDIR="$(PKG_CONFIG_LIBDIR)" LDSHARED="$(CC) -shared" LIBS="-lz -lm -ldl" \
	./configure --host=$(HOST_193) --prefix="$(BUILD_PREFIX)" \
	--includedir="$(BUILD_PREFIX)/include/ruby-1.9.3" --with-baseruby=ruby \
	ac_cv_header_pwd_h=no ac_cv_header_langinfo_h=no \
	$(RUBY_193_CONFIGURE_ARGS) --program-suffix=193

ruby193/configure: ruby193/configure.in
	cd ruby193; $(RUBY_193_ENV) && autoreconf -i

# -------------------- Bison 2.x --------------------
# Bison is a host build tool used while generating Ruby sources. Do not let the
# Android target toolchain leak into its configure or build environment.
BISON_HOST_ENV := env -u HOST -u TARGET -u ABI -u CC -u CXX -u AS -u LD -u AR \
	-u NM -u RANLIB -u STRIP -u OBJCOPY -u OBJDUMP -u CPPFLAGS -u CFLAGS \
	-u CXXFLAGS -u LDFLAGS -u CLANG_FLAGS -u PKG_CONFIG_LIBDIR

bison: init $(BUILD_PREFIX)/bin/bison

$(BUILD_PREFIX)/bin/bison: bison-2.7.1/Makefile
	cd bison-2.7.1; $(BISON_HOST_ENV) make -j$(NPROC) && $(BISON_HOST_ENV) make install

bison-2.7.1/Makefile: bison-2.7.1/configure
	cd bison-2.7.1; $(BISON_HOST_ENV) ./configure \
		--build=x86_64-pc-linux-gnu --host=x86_64-pc-linux-gnu \
		--prefix="$(BUILD_PREFIX)" --disable-nls


init:
	@mkdir -p $(LIBDIR) $(INCDIR)

clean-pixman:
	-cd pixman; make clean; rm Makefile; rm configure

clean-iconv:
	-cd libiconv; make clean; rm Makefile

clean-openal:
	-cd openal; rm -rf cmakebuild

clean-openssl:
	-cd openssl; make clean; rm Makefile

clean-ruby:
	-cd ruby; make clean; rm Makefile; rm configure

clean-ruby187:
	-cd ruby187; make clean; rm Makefile; rm configure

clean-ruby193:
	-cd ruby193; make clean; rm Makefile; rm configure

# Ruby builds write ABI-specific object archives into their source trees. Remove
# leftovers before switching from arm64-v8a to armeabi-v7a.
clean-ruby-artifacts:
	-find ruby ruby187 ruby193 -type f \( -name '*.o' -o -name '*.a' -o -name '*.so' -o -name '*.bundle' \) -delete

clean-bison:
	-cd bison-2.7.1; make clean

clean-soundfont-codecs:
	-rm -rf $(OGG_BUILD_DIR) $(VORBIS_BUILD_DIR) $(FLAC_BUILD_DIR) $(OPUS_BUILD_DIR) $(SNDFILE_BUILD_DIR)

clean: clean-pixman clean-iconv clean-openal clean-openssl clean-ruby clean-ruby187 clean-ruby193 clean-ruby-artifacts clean-bison clean-soundfont-codecs

all: pixman iconv openal openssl fluidsynth ruby ruby187 ruby193 bison
