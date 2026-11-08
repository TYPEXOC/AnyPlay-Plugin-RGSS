# Tyranor Next RGSS Runtime

An installable runtime module for **Tyranor Next** that runs **RPG Maker XP, VX and VX Ace** (RGSS) games on Android. Tyranor Next discovers the module, hands it the selected game, and provides the selected translation file and virtual-pad profile when configured.

The runtime is based on [mkxp-z](https://github.com/mkxp-z/mkxp-z) and the Android port [BookerRues9/mkxp-z-android-reworked](https://github.com/BookerRues9/mkxp-z-android-reworked), adapted from [AnyPlay-Plugin-RGSS](https://github.com/001-lovely-singh/AnyPlay-Plugin-RGSS).

The TYN module uses its own APK process. It declares translation support to Tyranor Next, and includes a TYN virtual pad and editor, a TYN cheat panel, and a Ruby translation bridge that loads the selected JSON table at runtime and checks for updates while the game is running. The bridge and generated table are kept in the module's private session directory; the game folder is not patched with plugin or translation files.

## 📥 Install

1. Download **`Tyranor-RGSS-Runtime.apk`** from the `rgss-module` branch's GitHub Actions artifact, or from a tagged release.
2. Install it and allow "Install unknown apps" for your browser or file manager if Android asks.
3. In Tyranor Next, rescan the game library and launch an XP, VX, or VX Ace game detected as RPG Maker. The module is managed from TYN's RPG Maker engine entry; it does not appear in the generic runtime-module manager.

The APK also appears in Android's app drawer as **TYN RGSS**. Opening it shows the runtime version, usage note, and source link; game launches still come from Tyranor Next.

New versions install over the old one. Nothing needs to be uninstalled.

## 📦What it includes

- One APK for 64-bit and 32-bit phones (arm64-v8a and armeabi-v7a).
- TYN's configurable on-screen pad and pad-layout editor; virtual keys are sent to SDL in the runtime process.
- TYN cheat panel backed by Ruby-side RGSS control code for supported XP / VX / VX Ace games and Pokémon Essentials variants.
- Runtime loading of the translation file selected in Tyranor Next, with polling-based hot reload. The module reads the JSON through a temporary content URI grant and does not copy the bridge into the game folder.
- Three separately built Ruby VMs: 1.8.7, 1.9.3, and 3.1.3. Tyranor Next selects the effective global or per-game choice before loading native libraries. Auto uses the detected RGSS generation; explicitly selected versions override that mapping.
- Tyranor Next's RPG Maker runtime options are applied to the private mkxp session: automatic Ruby selection uses 1.8.7 for XP/VX and 1.9.3 for VX Ace (unknown generations fall back to 3.1.3); rendering and frame pacing, path caches, fast resource enumeration, resolution and screen alignment, speed-up toggle, font scale/custom/CJK fonts, dialogue copy, cheat panel, debug mode, and configured postload scripts are wired to the runtime. Options can have global defaults and per-game overrides. The dialogue-copy action covers standard RGSS message events; custom plugin drawing paths may not expose their text.
- Fixes for old scripts. Many games were written for Ruby 1.8; the plugin repairs the common problems automatically when the game starts, so the game does not need to be edited.
- Per-session diagnostics record the local/UTC launch time, elapsed time, device and selected Ruby/RGSS runtime, setup stages, translation loading, activity lifecycle, first rendered frame, native engine messages, and Java/Ruby errors. Ruby logs also record the update loop and scene/map transitions. Verbose logs identify Win32API functions handled by TYN's compatibility layer and Windows functions that fall back to generic stub results. Logs stay in the module's private session folders and do not modify the game folder.

## 🎯 If a game does not work

When something goes wrong, open the in-game TYN panel and tap **Logs** in its header. If the game has already closed, open **TYN RGSS** from Android's app drawer and tap **Export all runtime logs**. The ZIP includes every saved game session, each session's startup timeline, native engine log, and Ruby error log when available, so you can test several XP/VX/VX Ace games and send one complete archive. It identifies sessions by game title and launch time, but redacts the game and private session roots and does not copy game assets/translations.

Imported RTP files stay in the existing shared `/JoiPlay/RTP` directory so previously imported assets remain available. TYN passes its managed RTP root to the module; the runtime accepts RTP paths only from the game folder or that managed root. Missing Ruby resources include a hint to check the game files or import the matching RTP in TYN's RPG Maker settings. Native diagnostics record which mkxp configuration files were read and the effective RTP paths.

Please report it on **[Discord](https://discord.gg/Y9Yjsn5yDP)** and attach that file, together with the game name and your phone model.

## 👨‍🍳 Build it yourself

The APK is built by GitHub Actions (`.github/workflows/android.yml`) when a `rgss-module` branch update is pushed or a tagged build is requested. Branch builds upload `Tyranor-RGSS-Runtime.apk` and a separate `Tyranor-RGSS-Debug.apk` artifact and do not publish a Release. The Debug APK enables verbose startup logs by default, including Ruby bootstrap, preload scripts, script-pack decoding, compatibility checks, and the name/index of each game script as it executes. It does not force the game's own DEBUG or TEST mode. Install the Debug APK to reproduce a startup issue, then export all runtime logs from its launcher screen. A manual build can also publish a Release when a release tag is supplied.

The release is signed with a key that is kept in the repository secrets (`ANYPLAY_KEYSTORE_BASE64`, `ANYPLAY_STORE_PASSWORD`, `ANYPLAY_KEY_ALIAS`, `ANYPLAY_KEY_PASSWORD`). If you fork this repository without those secrets, the build still works and is signed with a temporary debug key.

## 📄 Credits and license

- mkxp-z and its contributors.
- [BookerRues9](https://github.com/BookerRues9) for the Android port this project started from.
- [001-lovely-singh](https://github.com/001-lovely-singh/AnyPlay-Plugin-RGSS) for the AnyPlay RGSS base adapted by this module branch.
- Ruby 1.8.7 and 1.9.3 sources from their respective upstream projects; parser sources are generated during the build with GNU Bison.
- The Ruby 1.9.3 hostname-verification backport from the pinned RVM patch set.
- Ruby, SDL2, OpenAL Soft and the other libraries bundled in the build keep their own licenses.

This project is licensed under the **GNU General Public License v2** (see [`app/jni/mkxp-z/COPYING`](app/jni/mkxp-z/COPYING)). The complete source of every release is this repository, at the release's tag.
