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
- Private runtime diagnostics with the selected Ruby/RGSS versions, device ABI, game marker checks, and Ruby error details. Logs stay in the module's private session folder and do not modify the game folder.

## 🎯 If a game does not work

When something goes wrong, open the in-game TYN panel and tap **Logs** in its header. If the game has already closed, open **TYN RGSS** from Android's app drawer and tap **Export the latest runtime log**. Share the generated text file with the report. It includes the selected Ruby runtime, detected RGSS generation, device ABI, expected game-file markers, script parse errors, and Ruby stack traces. It does not include the full game folder path or copy game files.

Please report it on **[Discord](https://discord.gg/Y9Yjsn5yDP)** and attach that file, together with the game name and your phone model.

## 👨‍🍳 Build it yourself

The APK is built by GitHub Actions (`.github/workflows/android.yml`) when a `rgss-module` branch update is pushed or a tagged build is requested. Branch builds upload a `Tyranor-RGSS-Runtime.apk` artifact and do not publish a Release. A manual build can also publish a Release when a release tag is supplied.

The release is signed with a key that is kept in the repository secrets (`ANYPLAY_KEYSTORE_BASE64`, `ANYPLAY_STORE_PASSWORD`, `ANYPLAY_KEY_ALIAS`, `ANYPLAY_KEY_PASSWORD`). If you fork this repository without those secrets, the build still works and is signed with a temporary debug key.

## 📄 Credits and license

- mkxp-z and its contributors.
- [BookerRues9](https://github.com/BookerRues9) for the Android port this project started from.
- [001-lovely-singh](https://github.com/001-lovely-singh/AnyPlay-Plugin-RGSS) for the AnyPlay RGSS base adapted by this module branch.
- [zths/mkxp-z-android-mtool](https://github.com/zths/mkxp-z-android-mtool) for the three-Ruby build layout and legacy Ruby compatibility patches adapted by this branch.
- Ruby, SDL2, OpenAL Soft and the other libraries bundled in the build keep their own licenses.

This project is licensed under the **GNU General Public License v2** (see [`app/jni/mkxp-z/COPYING`](app/jni/mkxp-z/COPYING)). The complete source of every release is this repository, at the release's tag.
