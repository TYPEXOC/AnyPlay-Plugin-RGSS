## Tyranor Next RGSS Runtime

Runs RPG Maker XP, VX and VX Ace games as a Tyranor Next runtime module.

### Install
1. Download **Tyranor-RGSS-Runtime.apk** below and install it (allow "Install unknown apps" if Android asks).
2. Enable the module in Tyranor Next, scan your library, and launch an RGSS game.

New versions install over the old one.

### What's new
- Old Ruby 1.8 games: more scripts are repaired automatically at start-up (`return` inside procs, old `when x:` syntax, byte/character differences, missing old methods, and more).
- TYN-owned virtual pad/editor and cheat panel are connected to the RGSS process.
- The selected TYN translation table loads at runtime and can be hot-reloaded without adding files to the game directory.
- MIDI BGM uses FluidSynth built from upstream for both Android ABIs and the bundled GMGSx SoundFont when a game has no explicit SoundFont setting; no game files are changed.
- FluidSynth now has libsndfile's Ogg/Vorbis decoder enabled, allowing games to use SF3 SoundFonts as well as SF2.
- Tyranor Next exposes the translation-file picker for games handled by this module.
- Audio scripts written for newer mkxp-z (extra position argument, `bgm_position`) no longer cause errors.
- Complete per-session diagnostics can be exported from the module; session and archive retention now have automatic age and size limits.
- Frame-speed summaries and busiest script lines are included in the private Ruby runtime log when diagnostics are enabled.

### Note
This TYN build uses package `com.tyranor.runtime.rgss`, so it installs alongside the original AnyPlay RGSS plugin. Later Tyranor runtime updates install over this module.
