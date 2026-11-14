# Tyranor Next RGSS Runtime

An installable runtime module for **Tyranor Next** that runs **RPG Maker XP, VX and VX Ace** (RGSS) games on Android. Tyranor Next discovers the module, hands it the selected game, and provides a normalized translation-map session and virtual-pad profile when configured.

The runtime is based on [mkxp-z](https://github.com/mkxp-z/mkxp-z) and the Android port [BookerRues9/mkxp-z-android-reworked](https://github.com/BookerRues9/mkxp-z-android-reworked), adapted from [AnyPlay-Plugin-RGSS](https://github.com/001-lovely-singh/AnyPlay-Plugin-RGSS).

The TYN module uses its own APK process. Its manifest declares the host translation rule `mtool-json`; Tyranor Next discovers the source file and serves the parsed, normalized JSON map through a read-only session URI. The module includes a TYN virtual pad and editor, virtual mouse controls, a TYN cheat panel, and a Ruby translation bridge that loads and polls that map while the game is running. The bridge and generated table are kept in the module's private session directory; the game folder is not patched with plugin or translation files.

## 📥 Install

1. Download **`Tyranor-RGSS-Runtime.apk`** from the `rgss-module` branch's GitHub Actions artifact, or from a tagged release.
2. Install it and allow "Install unknown apps" for your browser or file manager if Android asks.
3. In Tyranor Next, rescan the game library and launch an XP, VX, or VX Ace game detected as RPG Maker. The module is managed from TYN's RPG Maker engine entry; it does not appear in the generic runtime-module manager.

The APK also appears in Android's app drawer as **TYN RGSS**. Opening it shows the runtime version, usage note, and source link; game launches still come from Tyranor Next.

New versions install over the old one. Nothing needs to be uninstalled.

## 📦What it includes

- One APK for 64-bit and 32-bit phones (arm64-v8a and armeabi-v7a).
- TYN's configurable on-screen pad and pad-layout editor; virtual keys are sent to SDL in the runtime process. A matching three-button overlay toggles the pad, switches SDL virtual-mouse mode, and opens the cheat panel. In mouse mode, dragging moves the pointer, tapping clicks, and long-pressing sends a right click; taps on pad controls continue to work.
- TYN cheat panel backed by Ruby-side RGSS control code for supported XP / VX / VX Ace games and Pokémon Essentials variants.
- Its minimap shows one cell per game tile, fits the full map by default, and supports zooming, teleporting to a tile, and opening that tile's event interpreter.
- Runtime loading of the normalized translation map selected in Tyranor Next, with polling-based hot reload. The module reads the host-parsed JSON through a temporary read-only content URI grant and does not inspect source formats or copy the bridge into the game folder.
- MIDI playback through upstream FluidSynth 2.6.2. Each Android ABI builds the synthesizer with libsndfile 1.2.2 and Ogg/Vorbis decoding enabled, so SF2 and SF3 SoundFonts can be loaded. The static decoder libraries are linked into FluidSynth; no additional runtime libraries are required. When a game's `mkxp.json` does not name a SoundFont, the module copies the bundled `GMGSx.SF2` into its private session and passes that path to mkxp-z. MIDI PCM stays on mkxp-z's existing OpenAL audio path.
- Three separately built Ruby VMs: 1.8.7, 1.9.3, and 3.1.3. Tyranor Next selects the effective global or per-game choice before loading native libraries. Auto uses the detected RGSS generation; explicitly selected versions override that mapping.
- Tyranor Next's RPG Maker runtime options are applied to the private mkxp session: automatic Ruby selection uses 1.8.7 for XP/VX and 1.9.3 for VX Ace (unknown generations fall back to 3.1.3); rendering and frame pacing, path caches, fast resource enumeration, resolution and screen alignment, speed-up toggle, font scale/custom/CJK fonts, dialogue copy, cheat panel, debug mode, configured postload scripts, and autotile animation are wired to the runtime. Options can have global defaults and per-game overrides. Autotile animation is enabled by default and can be disabled for both XP and VX/VX Ace map rendering. The dialogue-copy action covers standard RGSS message events; custom plugin drawing paths may not expose their text.
- Pokémon Essentials compatibility uses TYN's native input-update bridge and `TilemapLoader` mega-surface fallback, with a `Graphics.haveresizescreen` shim backed by mkxp-z's `Graphics.resize_screen`. JoiPlay-only patches are not loaded wholesale because they depend on JoiPlay APIs or alter game features; the separate `CustomTilemap` rewrite still needs game-specific validation.
- Fixes for old scripts. Many games were written for Ruby 1.8; the plugin repairs the common problems automatically when the game starts, so the game does not need to be edited.
- Per-session diagnostics record the local/UTC launch time, elapsed time, device and selected Ruby/RGSS runtime, setup stages, translation loading, activity lifecycle, first rendered frame, native engine messages, and Java/Ruby errors. Ruby logs also record the update loop and scene/map transitions. Verbose logs identify Win32API functions handled by TYN's compatibility layer and Windows functions that fall back to generic stub results. Logs stay in the module's private session folders and do not modify the game folder. Each session is limited to 8 MiB; its individual timeline/native/Ruby logs are capped, and older entries are trimmed with a marker.

## 🎯 If a game does not work

When something goes wrong, open the in-game TYN panel and tap **Logs** in its header. If the game has already closed, open **TYN RGSS** from Android's app drawer and tap **Export all runtime logs**. The ZIP includes retained game sessions, each session's startup timeline, native engine log, and Ruby error log when available, so you can test several XP/VX/VX Ace games and send one archive. Each session is limited to 8 MiB. The module automatically removes sessions older than 30 days and prunes the oldest sessions to keep at most 40 sessions and 128 MiB of diagnostic logs in total, including the module error log. It keeps up to three exported ZIPs in the temporary cache (up to 64 MiB total). The archive identifies sessions by game title and launch time, but redacts the game and private session roots and does not copy game assets/translations.

RTP and custom fonts are managed by Tyranor Next in the user-visible shared-storage folder `/TyranorNext/RTP` and `/TyranorNext/fonts`, separate from JoiPlay's resource folders. TYN passes its managed RTP root to the module; the runtime accepts RTP paths only from the game folder or that managed root. Missing Ruby resources include a hint to check the game files or import the matching RTP in TYN's RPG Maker settings. Native diagnostics record which mkxp configuration files were read and the effective RTP paths.

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
- [FluidSynth](https://github.com/FluidSynth/fluidsynth) 2.6.2, licensed under LGPL-2.1; its shared library is built from the pinned upstream tag for both Android ABIs.
- [libsndfile](https://github.com/libsndfile/libsndfile) 1.2.2, licensed under LGPL-2.1; it and pinned Ogg, Vorbis, FLAC, and Opus dependencies are statically linked into FluidSynth to enable SF3 decoding.
- `GMGSx.SF2` was supplied by the project maintainer. Its embedded SoundFont INFO metadata labels it “Public Domain”.
- Ruby, SDL2, OpenAL Soft and the other libraries bundled in the build keep their own licenses.

This project is licensed under the **GNU General Public License v2** (see [`app/jni/mkxp-z/COPYING`](app/jni/mkxp-z/COPYING)). The complete source of every release is this repository, at the release's tag.
