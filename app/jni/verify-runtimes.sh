#!/bin/bash
# Checks the native libraries of a built APK (or of an unzipped lib/ folder).
#
#   ./verify-runtimes.sh AnyPlay-RGSS-Plugin.apk
#   ./verify-runtimes.sh path/to/folder-that-contains-lib/
#
# For every ABI it verifies, per runtime:
#   * libruby_r3.so / libruby_r18.so exist and have the internal SONAME "libruby.so"
#   * the engine (libmkxp-z-r3.so / libmkxp-z-r18.so) has NEEDED libruby.so
#   * the engine exports ONLY SDL_main
#   * Phase B: libruby_r18.so exports the mkxp_legacy_* API, libruby_r3.so does not,
#     the r18 engine imports it, the r3 engine does not
#   * Phase D: if the APK contains r34 (Ruby 3.4), the same checks as r3 apply to it
#     (libruby_r34.so SONAME libruby.so, engine NEEDED libruby.so / exports only SDL_main,
#     no mkxp_legacy_*). r34 is optional; nothing is reported when it is absent.
#   * there is NO file called libruby.so, libmkxp-z.so, libruby18.so or
#     libmkxp-z-ruby18.so (old names; the real Ruby 1.8.7 build was removed)
# Exit code 1 if anything is wrong. Tool: llvm-readelf from the NDK, else readelf.

set -u

SRC="${1:-}"
if [ -z "$SRC" ] || [ ! -e "$SRC" ]; then
  echo "usage: $0 <apk | folder containing lib/>" >&2
  exit 2
fi

READELF="${READELF:-}"
if [ -z "$READELF" ]; then
  for c in "${ANDROID_NDK_HOME:-/nonexistent}"/toolchains/llvm/prebuilt/*/bin/llvm-readelf; do
    [ -x "$c" ] && READELF="$c" && break
  done
fi
[ -z "$READELF" ] && READELF="$(command -v llvm-readelf || command -v readelf || true)"
if [ -z "$READELF" ]; then echo "no readelf found" >&2; exit 2; fi
echo "using: $READELF"

if [ -d "$SRC" ]; then
  ROOT="$SRC"
else
  ROOT="$(mktemp -d)"
  trap 'rm -rf "$ROOT"' EXIT
  unzip -q -o "$SRC" 'lib/*' -d "$ROOT" || { echo "cannot unzip lib/ from $SRC" >&2; exit 2; }
fi

FAIL=0
fail() { echo "  FAIL: $*"; FAIL=1; }
ok()   { echo "  ok:   $*"; }

soname_of()  { "$READELF" -d -W "$1" 2>/dev/null | sed -n 's/.*(SONAME).*\[\(.*\)\].*/\1/p'; }
needed_of()  { "$READELF" -d -W "$1" 2>/dev/null | sed -n 's/.*(NEEDED).*\[\(.*\)\].*/\1/p'; }
# defined, global/weak dynamic symbols (what other libraries can see)
exports_of() {
  "$READELF" --dyn-syms -W "$1" 2>/dev/null \
    | awk '$1 ~ /^[0-9]+:$/ && $7 != "UND" && ($5 == "GLOBAL" || $5 == "WEAK") { n=$8; sub(/@.*/, "", n); print n }' \
    | sort -u
}

# undefined (imported) dynamic symbols
imports_of() {
  "$READELF" --dyn-syms -W "$1" 2>/dev/null \
    | awk '$1 ~ /^[0-9]+:$/ && $7 == "UND" { n=$8; sub(/@.*/, "", n); print n }' \
    | sort -u
}

LEGACY_API="mkxp_legacy_set_enabled mkxp_legacy_enabled_p mkxp_legacy_prepare_next_eval mkxp_legacy_cancel_next_eval"

check_legacy_ruby() {   # abi_dir file expect(yes|no)
  local f="$1/$2" want="$3" ex missing=""
  [ -f "$f" ] || return
  ex="$(exports_of "$f")"
  if [ "$want" = yes ]; then
    for sym in $LEGACY_API; do echo "$ex" | grep -qx "$sym" || missing="$missing $sym"; done
    if [ -z "$missing" ]; then ok "$2 exports the mkxp_legacy_* API"; else fail "$2 does not export:$missing (legacy patch not built in?)"; fi
  else
    if echo "$ex" | grep -q '^mkxp_legacy_'; then fail "$2 exports mkxp_legacy_* but r3 must stay unpatched"; else ok "$2 has no mkxp_legacy_* (clean r3)"; fi
  fi
}

check_legacy_engine() {   # abi_dir file expect(yes|no)
  local f="$1/$2" want="$3" im
  [ -f "$f" ] || return
  im="$(imports_of "$f")"
  if [ "$want" = yes ]; then
    if echo "$im" | grep -qx mkxp_legacy_set_enabled; then ok "$2 imports mkxp_legacy_set_enabled"; else fail "$2 does not call the legacy layer (built without MKXPZ_RUBY18_COMPAT?)"; fi
  else
    if echo "$im" | grep -q '^mkxp_legacy_'; then fail "$2 imports mkxp_legacy_* but r3 has no such functions (it would not load)"; else ok "$2 has no mkxp_legacy_* imports"; fi
  fi
}

check_engine() {   # abi_dir engine_file expected_needed forbidden_needed
  local dir="$1" eng="$2" want="$3" forbid="$4"
  local f="$dir/$eng"
  if [ ! -f "$f" ]; then fail "$eng is missing"; return; fi
  local needed; needed="$(needed_of "$f")"
  if echo "$needed" | grep -qx "$want"; then ok "$eng NEEDED $want"; else fail "$eng does not NEED $want (needs: $(echo $needed))"; fi
  if [ -n "$forbid" ] && echo "$needed" | grep -qx "$forbid"; then fail "$eng NEEDS $forbid (wrong Ruby)"; fi
  if echo "$needed" | grep -Eq '^libruby_r[0-9]+\.so$'; then fail "$eng NEEDS a renamed Ruby file instead of its SONAME"; fi
  local ex; ex="$(exports_of "$f" | tr '\n' ' ' | sed 's/ $//')"
  if [ "$ex" = "SDL_main" ]; then ok "$eng exports only SDL_main"; else fail "$eng exports: [$ex] (expected only SDL_main)"; fi
}

check_ruby() {     # abi_dir file expected_soname
  local dir="$1" file="$2" want="$3"
  local f="$dir/$file"
  if [ ! -f "$f" ]; then fail "$file is missing"; return; fi
  local so; so="$(soname_of "$f")"
  if [ "$so" = "$want" ]; then ok "$file SONAME $so"; else fail "$file SONAME is '$so' (expected $want)"; fi
}

FOUND=0
for dir in "$ROOT"/lib/*/; do
  [ -d "$dir" ] || continue
  dir="${dir%/}"
  abi="$(basename "$dir")"
  FOUND=1
  echo "== $abi"

  for old in libruby.so libmkxp-z.so libruby18.so libmkxp-z-ruby18.so; do
    [ -e "$dir/$old" ] && fail "$old must not be in the APK (old name; risks loading the wrong runtime)"
  done

  check_ruby "$dir" libruby_r3.so  libruby.so
  check_ruby "$dir" libruby_r18.so libruby.so
  # Phase D: r34 (Ruby 3.4) is optional. It is only checked when the APK contains it.
  if [ -f "$dir/libruby_r34.so" ] || [ -f "$dir/libmkxp-z-r34.so" ]; then
    check_ruby "$dir" libruby_r34.so libruby.so
  fi

  check_engine "$dir" libmkxp-z-r3.so      libruby.so   ""
  check_engine "$dir" libmkxp-z-r18.so     libruby.so   ""

  if [ -f "$dir/libruby_r34.so" ] || [ -f "$dir/libmkxp-z-r34.so" ]; then
    check_engine "$dir" libmkxp-z-r34.so libruby.so ""
    check_legacy_ruby   "$dir" libruby_r34.so  no
    check_legacy_engine "$dir" libmkxp-z-r34.so no
    if [ -f "$dir/libruby_r34.so" ]; then
      for other in libruby_r3.so libruby_r18.so; do
        if [ -f "$dir/$other" ] && cmp -s "$dir/libruby_r34.so" "$dir/$other"; then fail "libruby_r34.so is identical to $other"; fi
      done
    fi
  fi
  check_legacy_ruby   "$dir" libruby_r18.so yes
  check_legacy_ruby   "$dir" libruby_r3.so  no
  check_legacy_engine "$dir" libmkxp-z-r18.so yes
  check_legacy_engine "$dir" libmkxp-z-r3.so  no

  # r3 and r18 are separate files and, since Phase B, different builds.
  if [ -f "$dir/libruby_r3.so" ] && [ -f "$dir/libruby_r18.so" ]; then
    if cmp -s "$dir/libruby_r3.so" "$dir/libruby_r18.so"; then fail "libruby_r3.so and libruby_r18.so are identical (patch missing)"; else ok "libruby_r3.so and libruby_r18.so differ"; fi
  fi
done

if [ "$FOUND" = 0 ]; then echo "no lib/<abi>/ folders found" >&2; exit 2; fi

if [ "$FAIL" = 0 ]; then echo "ALL RUNTIME CHECKS PASSED"; else echo "RUNTIME CHECKS FAILED"; fi
exit "$FAIL"
