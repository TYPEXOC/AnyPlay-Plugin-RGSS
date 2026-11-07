#!/bin/bash

# This script downloads/git clones project dependencies
# such as libogg, SDL2, Ruby, etc.

GIT_ARGS="-q -c advice.detachedHead=false --single-branch --depth 1"

# Xiph libogg
if [[ ! -d "libogg" ]]; then
  echo "Downloading libogg..."
  git clone $GIT_ARGS -b v1.3.5 https://github.com/xiph/ogg libogg
fi

# Xiph libvorbis
if [[ ! -d "libvorbis" ]]; then
  echo "Downloading libvorbis..."
  git clone $GIT_ARGS -b v1.3.7 https://github.com/xiph/vorbis libvorbis
fi

# Xiph libtheora
if [[ ! -d "libtheora" ]]; then
  echo "Downloading libtheora..."
  wget -q https://ftp.osuosl.org/pub/xiph/releases/theora/libtheora-1.1.1.tar.gz
  tar -xzf libtheora-1.1.1.tar.gz
  mv libtheora-1.1.1 libtheora
  rm -f libtheora-1.1.1.tar.gz
fi

# GNU libiconv
if [[ ! -d "libiconv" ]]; then
  echo "Downloading libiconv..."
  wget -q https://ftp.gnu.org/pub/gnu/libiconv/libiconv-1.17.tar.gz
  tar -xzf libiconv-1.17.tar.gz
  mv libiconv-1.17 libiconv
  rm -f libiconv-1.17.tar.gz
fi

# Freedesktop uchardet
if [[ ! -d "uchardet" ]]; then
  echo "Downloading uchardet..."
  git clone $GIT_ARGS -b v0.0.8 https://gitlab.freedesktop.org/uchardet/uchardet.git/ uchardet
fi

# Freedesktop Pixman
if [[ ! -d "pixman" ]]; then
  echo "Downloading Pixman..."
  git clone $GIT_ARGS -b pixman-0.42.2 https://gitlab.freedesktop.org/pixman/pixman.git/ pixman
fi

# PhysicsFS
if [[ ! -d "physfs" ]]; then
  echo "Downloading PhysicsFS..."
  git clone $GIT_ARGS -b release-3.2.0 https://github.com/icculus/physfs physfs
fi

# OpenAL Soft 1.23.0
if [[ ! -d "openal" ]]; then
  echo "Downloading OpenAL Soft..."
  git clone $GIT_ARGS -b 1.23.0 https://github.com/kcat/openal-soft openal
fi

# SDL2
if [[ ! -d "SDL2" ]]; then
  echo "Downloading SDL2..."
  git clone $GIT_ARGS -b release-2.26.3 https://github.com/libsdl-org/SDL SDL2
fi

# SDL2_image
if [[ ! -d "SDL2_image" ]]; then
  echo "Downloading SDL2_image..."
  git clone $GIT_ARGS --recurse-submodules -b release-2.6.3 https://github.com/libsdl-org/SDL_image SDL2_image
fi

# SDL2_ttf
if [[ ! -d "SDL2_ttf" ]]; then
  echo "Downloading SDL2_ttf..."
  git clone $GIT_ARGS --recurse-submodules -b release-2.20.2 https://github.com/libsdl-org/SDL_ttf SDL2_ttf
fi

# SDL2_sound
if [[ ! -d "SDL2_sound" ]]; then
  echo "Downloading SDL2_sound..."
  git clone $GIT_ARGS -b v2.0.1 https://github.com/icculus/SDL_sound SDL2_sound
fi

# OpenSSL 1.1.1t
if [[ ! -d "openssl" ]]; then
  echo "Downloading OpenSSL..."
  git clone $GIT_ARGS -b OpenSSL_1_1_1t https://github.com/openssl/openssl openssl
fi

# Ruby 3.1.0 (patched for mkxp-z)
if [[ ! -d "ruby" ]]; then
  echo "Downloading Ruby..."
  git clone $GIT_ARGS -b mkxp-z-3.1 https://github.com/mkxp-z/ruby ruby
fi

# Second, independent copy of the same Ruby 3.1 source for the "r18" runtime.
# It is built into its own prefix (build-r18-<abi>) and is the ONLY tree that gets
# the Ruby 1.8 compatibility layer (ruby-legacy/, applied by `make ruby-r18`),
# so the clean "r3" Ruby above is never touched.
if [[ ! -d "ruby-r18" ]]; then
  echo "Downloading Ruby (r18 copy)..."
  git clone $GIT_ARGS -b mkxp-z-3.1 https://github.com/mkxp-z/ruby ruby-r18
fi

# Ruby 3.4.9 for the optional "r34" runtime (BUILD_R34=1). Plain upstream Ruby 3.4.9
# (revision 76cca827ab, the same one Kirin's r34 reports).
# The official release tarball is used because it already contains configure, the
# generated parser and the bundled gems (a git checkout would need autoconf, bison,
# gperf and downloads gems during "make install"). Falls back to the git tag if the
# download fails.
if [[ "${BUILD_R34:-0}" == "1" && ! -d "ruby-r34" ]]; then
  echo "Downloading Ruby 3.4.9 (r34)..."
  if curl -fsSL --retry 3 -o ruby-3.4.9.tar.xz https://cache.ruby-lang.org/pub/ruby/3.4/ruby-3.4.9.tar.xz \
     && tar -xf ruby-3.4.9.tar.xz && [[ -f ruby-3.4.9/configure ]]; then
    mv ruby-3.4.9 ruby-r34
    touch ruby-r34/configure   # never let make think configure is older than configure.ac
    rm -f ruby-3.4.9.tar.xz
  else
    echo "Tarball download failed, using the git tag instead..."
    rm -rf ruby-3.4.9 ruby-3.4.9.tar.xz
    git clone $GIT_ARGS -b v3_4_9 https://github.com/ruby/ruby ruby-r34
  fi
fi

echo "Done!"
