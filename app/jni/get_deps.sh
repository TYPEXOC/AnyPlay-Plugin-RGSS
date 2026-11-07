#!/bin/bash
set -euo pipefail

# This script downloads/git clones project dependencies
# such as libogg, SDL2, Ruby, etc.

GIT_ARGS="-q -c advice.detachedHead=false --single-branch --depth 1"

apply_patch_once() {
  local patch_file="$1"
  if patch --dry-run -p1 --forward --batch < "$patch_file" >/dev/null 2>&1; then
    patch -p1 --forward --batch < "$patch_file"
  elif patch --dry-run -R -p1 --batch < "$patch_file" >/dev/null 2>&1; then
    echo "Already applied: $patch_file"
  else
    echo "Patch does not apply cleanly: $patch_file" >&2
    return 1
  fi
}

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

# OpenSSL 3.0.12 (needed by the legacy Ruby 1.9.3 build patches)
if [[ ! -d "openssl" ]]; then
  echo "Downloading OpenSSL..."
  git clone $GIT_ARGS -b openssl-3.0.12 https://github.com/openssl/openssl openssl
fi

# Bison 2.7.1 is used by the legacy Ruby source builds.
if [[ ! -d "bison-2.7.1" ]]; then
  echo "Downloading Bison 2.7.1..."
  wget -q https://ftp.gnu.org/gnu/bison/bison-2.7.1.tar.gz
  tar -xzf bison-2.7.1.tar.gz
  rm -f bison-2.7.1.tar.gz
fi
# Patch bundled gnulib for glibc 2.28+, where _IO_ftrylockfile is no longer exposed.
cd bison-2.7.1
apply_patch_once ../patches/bison-2.7.1-glibc-2.28.patch
cd ..

# Ruby 3.1.3 (patched for mkxp-z)
if [[ ! -d "ruby" ]]; then
  echo "Downloading Ruby 3.1.3..."
  git clone $GIT_ARGS -b mkxp-z-3.1.3 https://github.com/mkxp-z/ruby ruby
fi

# Ruby 1.8.7
if [[ ! -d "ruby187" ]]; then
  echo "Downloading Ruby 1.8.7..."
  git clone $GIT_ARGS -b ruby_1_8_7 https://github.com/mkxp-z/ruby ruby187
fi
cd ruby187
apply_patch_once ../patches/ruby187-remove-inline.patch
# Ruby 1.8's config generator uses TRUE, removed from the CI host Ruby.
apply_patch_once ../patches/ruby187-host-ruby-compat.patch
# Ruby 1.8 expects singleton method names as strings; Ruby 3 returns symbols.
apply_patch_once ../patches/ruby187-host-fileutils-compat.patch
# Ruby 1.8's build helpers also rely on nil paths and implicit Proc.new blocks.
apply_patch_once ../patches/ruby187-host-build-tools-compat.patch
apply_patch_once ../patches/ruby187-host-mkmf-array-compat.patch
apply_patch_once ../patches/ruby187-host-cross-compile-install.patch
cd ..

# Ruby 1.9.3
if [[ ! -d "ruby193" ]]; then
  echo "Downloading Ruby 1.9.3..."
  git clone $GIT_ARGS -b ruby_1_9_3 https://github.com/ruby/ruby ruby193
fi
cd ruby193
# Ruby 1.9.3's mkconfig generator still uses TRUE under modern host Ruby.
apply_patch_once ../patches/ruby193-host-ruby-compat.patch
python3 ../prepare-ruby193.py .

# Keep the upstream hostname-verification fix for this end-of-life Ruby branch.
RVM_PATCHSET_COMMIT=49960b3efe5635379a64511c73a26cf9516611e1
hostname_patch="$(mktemp)"
curl -fsSL "https://raw.githubusercontent.com/rvm/rvm/${RVM_PATCHSET_COMMIT}/patches/ruby/1.9.3/CVE-2015-1855-p484.patch" -o "$hostname_patch"
apply_patch_once "$hostname_patch"
rm -f "$hostname_patch"

# Use GNU's maintained host/target triplet tables instead of stale copies.
for config_tool in config.guess config.sub; do
  cp "/usr/share/misc/${config_tool}" "tool/${config_tool}"
done
cd ..

echo "Done!"
