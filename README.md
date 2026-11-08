# 🎮 Any Play - RGSS Plugin

A small companion app for **[Any Play](https://001-lovely-singh.github.io/)**. It runs **RPG Maker XP, VX and VX Ace** games (the "RGSS" / Ruby ones) on Android. It has no icon of its own: you add the game in AnyPlay, press play, and AnyPlay opens this plugin for you.

It is built on [mkxp-z](https://github.com/mkxp-z/mkxp-z), using the Android port [BookerRues9/mkxp-z-android-reworked](https://github.com/BookerRues9/mkxp-z-android-reworked) as the starting point.

> This plugin is only distributed here, through the **Releases** page of this repository.

## 📥 Install

1. Open the [latest release](../../releases/latest) and download **`AnyPlay-RGSS-Plugin.apk`**
   (direct link: `https://github.com/001-lovely-singh/AnyPlay-Plugin-RGSS/releases/latest/download/AnyPlay-RGSS-Plugin.apk`).
2. Open the file and allow "Install unknown apps" for your browser or file manager if Android asks.
3. Open AnyPlay, add an RPG Maker game (a folder, or a `.zip`), and press play.

New versions install over the old one. Nothing needs to be uninstalled.

## 📦What it includes

- One APK for 64-bit and 32-bit phones (arm64-v8a and armeabi-v7a).
- Touch controls: tap the map to walk, on-screen pad and buttons.
- Cheat menu (opened from the AnyPlay overlay) for XP / VX / VX Ace and for Pokémon Essentials games (v17, v19, v21 style).
- Fixes for old scripts. Many games were written for Ruby 1.8; the plugin repairs the common problems automatically when the game starts, so the game does not need to be edited.
- `AnyPlayLog.txt`, a problem report written into the game folder (see below).

## 🧩 Which Ruby does a game get?

The plugin has two runtimes: **r3** (clean Ruby 3.1) and **r18** (Ruby 3.1 with a Ruby 1.8 compatibility layer; game scripts see Ruby 1.8.7). The runtime is chosen when the game starts, in this order:

1. The `runtime_mode` that AnyPlay sends (`r3` or `r18`; `auto` or nothing means "decide below").
2. A file called **`anyplay_runtime.txt`** inside the game folder, with one word: `r3`, `r18` or `auto`. Use this to force a runtime for one game.
3. **Automatic detection.** First the RPG Maker generation, using the same signals as the engine: `mkxp.json` `rgssVersion`, then `Game.ini` `Scripts=` (`.rxdata`/`.rvdata`/`.rvdata2`), then `Game.ini` `Library=` (`RGSS1xx`/`RGSS2xx`/`RGSS3xx`), then `Data/Scripts.*`, then `.rgssad`/`.rgss2a`/`.rgss3a`.
   - **VX and VX Ace games** get the modern runtime (r3).
   - **RPG Maker XP (RGSS1) games** share one file format with very old (Ruby 1.8) and very new (Ruby 3) games, so they are looked at more closely (`RuntimeProbe.java`):
     1. the **Essentials version** written inside `Data/Scripts.rxdata` (`ESSENTIALS_VERSION = "16.2"`, `Essentials::VERSION = "21.1"`): version 18 and older = Ruby 1.8 era = **r18**, version 19 and newer = modern = r3;
     2. otherwise the **`ProductVersion` of the game's `.exe`**, the same rule the Kirin reference app uses: `1`, `1.x` or `1,x` = **r18**, any other value = r3;
     3. otherwise r3.
4. If nothing can be recognised the game gets r3.

`AnyPlayLog.txt` shows the result and the evidence, for example `runtime=r18 pick=auto: RGSS1 via Game.ini Scripts=...; Essentials 16.2 in scripts (v18 and older = Ruby 1.8 era)`. A game that is detected wrongly can be fixed for good with `anyplay_runtime.txt`. "Modern" games get `MODERN_AUTO_RUNTIME` in `MainActivity.java` (r3 by default; see the r34 section).

The detection can be tested on a PC with a JDK: `sh tests/runtime-detect/run.sh`.

### Optional third runtime: r34 (Ruby 3.4, optional YJIT)

`r34` is Ruby 3.4.9. It is **not** in a normal build and, by default, **never picked automatically**: you choose it per game with `runtime_mode=r34` or `anyplay_runtime.txt` containing `r34`. (Kirin works the same way. To send every game that does not need Ruby 1.8 to r34, set `MODERN_AUTO_RUNTIME = RT_R34` in `MainActivity.java`. Games that need Ruby 1.8 behaviour still get r18.) If the APK was built without it, the game simply runs on r3 and `AnyPlayLog.txt` says so (`pick=wanted r34 ... does not contain it, using r3`).

To build it, start the GitHub workflow by hand and tick **build_r34** (the build gets slower). Tick **r34_yjit** as well to build Ruby 3.4 with **YJIT**, Ruby's JIT compiler. YJIT only exists on 64-bit ARM, so only the `arm64-v8a` build gets it. It is switched on at the first `Graphics.update`, after all scripts have loaded; `AnyPlayLog.txt` then shows `yjit=on`. Tag pushes never build r34.

If you build r34 from the command line: `BUILD_R34=1 BUILD_R34_YJIT=1 make ...` in `app/jni` (needs `rustup target add aarch64-linux-android` for YJIT).

### Other runtime tuning

- **r3** starts with `RUBY_THREAD_MACHINE_STACK_SIZE=16MB` (big games overflowed Ruby 3.1's small default). Set the variable yourself and yours wins.

## 🎯 If a game does not work

When something goes wrong, the plugin writes a file called **`AnyPlayLog.txt`** into the game's folder. The file is created fresh every time the game is started, and only if there is something to report. It lists script errors, the error that stopped the game (with the script name and line), and, if the game runs slowly, which part is slow.

Please report it on **[Discord](https://discord.gg/Y9Yjsn5yDP)** and attach that file, together with the game name and your phone model.

## 👨‍🍳 Build it yourself

The APK is built by GitHub Actions (`.github/workflows/android.yml`) whenever a tag like `v1.0.41` is pushed. The result is attached to the release as `AnyPlay-RGSS-Plugin.apk`.

The release is signed with a key that is kept in the repository secrets (`ANYPLAY_KEYSTORE_BASE64`, `ANYPLAY_STORE_PASSWORD`, `ANYPLAY_KEY_ALIAS`, `ANYPLAY_KEY_PASSWORD`). If you fork this repository without those secrets, the build still works and is signed with a temporary debug key.

## 📄 Credits and license

- mkxp-z and its contributors.
- [BookerRues9](https://github.com/BookerRues9) for the Android port this project started from.
- Ruby, SDL2, OpenAL Soft and the other libraries bundled in the build keep their own licenses.

This project is licensed under the **GNU General Public License v2** (see [`app/jni/mkxp-z/COPYING`](app/jni/mkxp-z/COPYING)). The complete source of every release is this repository, at the release's tag.
