NDK       := ${ANDROID_NDK_HOME}
TOOLCHAIN := $(NDK)/toolchains/llvm/prebuilt/${ARCH}
NPROC     := $(shell nproc)

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

# Second Ruby 3.1 build ("r18" runtime). Same source branch as "ruby", but a separate
# source tree (ruby-r18/) and a separate install prefix, so the two libruby.so
# files can never overwrite each other and can be patched differently later.
R18_BUILD_PREFIX := ${PWD}/build-r18-$(ABI)
R18_LIBDIR := $(R18_BUILD_PREFIX)/lib
ifneq (, ${MSYSTEM})
	R18_PREFIX := $(shell cygpath -m ${R18_BUILD_PREFIX})
else ifneq (, $(findstring /cygdrive/,$(PATH)))
	R18_PREFIX := $(shell cygpath -m ${R18_BUILD_PREFIX})
else
	R18_PREFIX := $(R18_BUILD_PREFIX)
endif

# Third Ruby ("r34" runtime): Ruby 3.4, optional. Only built when BUILD_R34=1 (the
# workflow input "build_r34"), so the default build is not affected by it at all.
# Own source tree (ruby-r34/) and own install prefix (build-r34-<abi>/).
R34_BUILD_PREFIX := ${PWD}/build-r34-$(ABI)
R34_LIBDIR := $(R34_BUILD_PREFIX)/lib
ifneq (, ${MSYSTEM})
	R34_PREFIX := $(shell cygpath -m ${R34_BUILD_PREFIX})
else ifneq (, $(findstring /cygdrive/,$(PATH)))
	R34_PREFIX := $(shell cygpath -m ${R34_BUILD_PREFIX})
else
	R34_PREFIX := $(R34_BUILD_PREFIX)
endif

# Files of the Ruby 1.8 compatibility layer (ruby-legacy/) and the stamp that records
# "applied to ruby-r18". Defined here because rule prerequisites expand immediately.
LEGACY_KIT   := $(wildcard ruby-legacy/mkxp_legacy.inc ruby-legacy/legacy_prelude.rb ruby-legacy/apply-legacy-patches.py)
LEGACY_STAMP := ruby-r18/.mkxp-legacy-applied

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
CFLAGS      := -O2 $(CROSSFLAGS) --sysroot=$(TOOLCHAIN)/sysroot -I$(TOOLCHAIN)/sysroot/usr/include -I$(INCDIR)
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

# Ruby configure arguments to build
RUBY_CONFIGURE_ARGS := \
	--prefix="$(RUBY_PREFIX)" \
	--enable-shared \
	--enable-static \
	--enable-install-static-library \
	--disable-install-doc \
	--disable-rubygems \
	--with-static-linked-ext \
	--with-out-ext=readline,dbm,gdbm,win32,win32ole,fiddle \
	--without-gmp

# Same options as RUBY_CONFIGURE_ARGS, only the install prefix differs.
# (A later --prefix on the configure command line overrides the earlier one.)
RUBY_R18_CONFIGURE_ARGS := $(patsubst --prefix=%,--prefix="$(R18_PREFIX)",$(RUBY_CONFIGURE_ARGS))

# Ruby 3.4 (r34): same options, own prefix. YJIT (Ruby's JIT, written in Rust) is only
# supported on 64-bit ARM, so it is only switched on for arm64-v8a, and only when
# BUILD_R34_YJIT=1. Ruby's configure refuses to cross-compile YJIT on its own, so
# --enable-yjit is forced and rustc is told which Android target to build for.
# (Needs: rustup target add aarch64-linux-android; the workflow does that.)
# RJIT is Ruby 3.4's other JIT, not wanted here.
RUBY_R34_CONFIGURE_ARGS := $(patsubst --prefix=%,--prefix="$(R34_PREFIX)",$(RUBY_CONFIGURE_ARGS)) --disable-rjit
R34_CONFIGURE_ENV :=
ifeq ($(BUILD_R34_YJIT)-$(ABI),1-arm64-v8a)
	RUBY_R34_CONFIGURE_ARGS += --enable-yjit
	R34_CONFIGURE_ENV := RUSTC="rustc --target=aarch64-linux-android"
else
	RUBY_R34_CONFIGURE_ARGS += --disable-yjit
endif

CONFIGURE := $(CONFIGURE_ENV) ./configure $(CONFIGURE_ARGS)
CMAKE     := $(CONFIGURE_ENV) cmake .. $(CMAKE_ARGS)


.PHONY: all init clean cpufeatures pixman iconv openal openssl ruby ruby-r18 ruby-r34 legacy-patches clean-pixman clean-iconv clean-openal clean-openssl clean-ruby clean-ruby-r18 clean-ruby-r34

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

# -------------------- OpenSSL --------------------
openssl: init $(LIBDIR)/libssl.a

$(LIBDIR)/libssl.a: openssl/Makefile
	cd openssl; make -j$(NPROC); make install_sw

openssl/Makefile: openssl/Configure
	cd openssl; CFLAGS="-Wno-macro-redefined" ./Configure $(OPENSSL_TARGET) \
	no-shared -D__ANDROID_API__=$(API) \
	--prefix="$(BUILD_PREFIX)" --openssldir="$(BUILD_PREFIX)"

# -------------------- Ruby --------------------
ruby: init openssl $(LIBDIR)/libruby_r3.so

# ndk-build installs a PREBUILT library under the FILE name given in LOCAL_SRC_FILES, not
# under the module name. Two modules that both point at ".../lib/libruby.so" therefore
# collide ("overriding recipe for target libruby.so") and the engines get the wrong Ruby.
# So every Ruby gets its own file name here. The content is identical; the internal
# SONAME stays "libruby.so", which is what the engines record as NEEDED.
$(LIBDIR)/libruby_r3.so: $(LIBDIR)/libruby.so.3.1.0
	cp -L $(LIBDIR)/libruby.so.3.1.0 $@

$(LIBDIR)/libruby.so.3.1.0: ruby/Makefile
	cd ruby; make -j$(NPROC); make install

ruby/Makefile: ruby/configure
	cd ruby; DLDFLAGS="-Wl,-soname,libruby.so" \
	$(CONFIGURE) $(RUBY_CONFIGURE_ARGS)

ruby/configure: ruby/configure.ac
	cd ruby; autoreconf -i

# -------------------- Ruby 3.1 for the "r18" runtime --------------------
# Installs into build-r18-<abi>/ (headers in include/ruby-3.1.0, library in
# lib/libruby.so). Phase B: this tree gets the Ruby 1.8 compatibility layer
# (ruby-legacy/, see `legacy-patches`). The SONAME is "libruby.so" on purpose: it is the same SONAME
# for r3 and r18, and the APK keeps the two files apart by FILE NAME
# (libruby_r3.so / libruby_r18.so, see ruby.mk).
ruby-r18: init openssl $(R18_LIBDIR)/libruby_r18.so

# Own file name for ndk-build, see the comment at "ruby:" above (SONAME stays libruby.so).
$(R18_LIBDIR)/libruby_r18.so: $(R18_LIBDIR)/libruby.so.3.1.0
	cp -L $(R18_LIBDIR)/libruby.so.3.1.0 $@

$(R18_LIBDIR)/libruby.so.3.1.0: ruby-r18/Makefile $(LEGACY_STAMP)
	cd ruby-r18; make -j$(NPROC); make install

ruby-r18/Makefile: ruby-r18/configure
	cd ruby-r18; DLDFLAGS="-Wl,-soname,libruby.so" \
	$(CONFIGURE) $(RUBY_R18_CONFIGURE_ARGS)

# Ruby 1.8 compatibility layer (ruby-legacy/): added ONLY to the r18 source tree.
# Re-applied whenever a file of the kit changes (the stamp is older than the kit).
# The script is idempotent and touches vm_eval.c when something changed, so the
# normal `make` inside ruby-r18 recompiles it. No reconfigure is needed.

legacy-patches: $(LEGACY_STAMP)

$(LEGACY_STAMP): ruby-r18/configure $(LEGACY_KIT)
	python3 ruby-legacy/apply-legacy-patches.py ruby-r18
	touch $(LEGACY_STAMP)

ruby-r18/configure: ruby-r18/configure.ac
	cd ruby-r18; autoreconf -i

# -------------------- Ruby 3.4 for the "r34" runtime (optional) --------------------
# Upstream Ruby 3.4.9 (same revision as Kirin's r34), unpatched. Installs into
# build-r34-<abi>/ (headers in include/ruby-3.4.0, library lib/libruby.so). Same SONAME
# "libruby.so" as r3 and r18, the APK keeps them apart by file name (libruby_r34.so).
# Not part of `all` unless BUILD_R34=1.
ruby-r34: init openssl $(R34_LIBDIR)/libruby_r34.so

# Own file name for ndk-build, see the comment at "ruby:" above (SONAME stays libruby.so).
$(R34_LIBDIR)/libruby_r34.so: $(R34_LIBDIR)/libruby.so.3.4.0
	cp -L $(R34_LIBDIR)/libruby.so.3.4.0 $@

$(R34_LIBDIR)/libruby.so.3.4.0: ruby-r34/Makefile
	cd ruby-r34; make -j$(NPROC); make install

ruby-r34/Makefile: ruby-r34/configure
	cd ruby-r34; $(R34_CONFIGURE_ENV) DLDFLAGS="-Wl,-soname,libruby.so" \
	$(CONFIGURE) $(RUBY_R34_CONFIGURE_ARGS)

# A git checkout of Ruby has no configure script (needs autoconf and bison/gperf).
ruby-r34/configure: ruby-r34/configure.ac
	cd ruby-r34; ./autogen.sh

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

clean-ruby-r18:
	-cd ruby-r18; make clean; rm Makefile; rm configure

# r34: only does something when the ruby-r34 tree exists (BUILD_R34=1)
clean-ruby-r34:
	-if [ -d ruby-r34 ]; then cd ruby-r34 && make clean; rm -f Makefile configure; fi

clean: clean-pixman clean-iconv clean-openal clean-openssl clean-ruby clean-ruby-r18 clean-ruby-r34

all: pixman iconv openal openssl ruby ruby-r18
ifeq ($(BUILD_R34),1)
all: ruby-r34
endif
