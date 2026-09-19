# ALMEER MUSIC 3.0

A dark Android music player and offline DJ mixer.

## Live online music

ALMEER MUSIC uses the official Jamendo API for online discovery. Search returns live Jamendo catalog results with artwork and streaming URLs. Tracks marked by Jamendo as downloadable expose the Download button; tracks where the artist has disabled downloads do not.

The source currently contains Jamendo's documented test read-only client ID. For a real release, register your own Jamendo application and replace `JAMENDO_CLIENT_ID` in `MainActivity.kt`.

## Local / offline music

- Scans the phone's music library.
- Plays local files with AndroidX Media3 ExoPlayer.
- Downloaded Jamendo tracks are stored in the app's music directory and appear in the local library.

## DJ Studio

- Two independent Media3 ExoPlayer decks.
- Load/play/pause controls.
- Independent BPM controls.
- Pitch/speed controls.
- BPM sync from Deck A to Deck B.
- Crossfader with real deck volume changes.
- Jog/scratch surface that moves each deck's playhead.
- Android Equalizer + BassBoost effects when supported by the device/audio session.
- Cue buttons.

This is a real mobile mixer implementation, not placeholder buttons. Professional vinyl emulation, beat-grid detection, waveform analysis, time-stretch algorithms and advanced DSP would require a dedicated native DSP engine beyond Android's standard audio effects.

## API / licensing

Jamendo's API provides catalog search, stream URLs, artwork and download URLs only when `audiodownload_allowed` is true. The app does not scrape or bypass services that prohibit downloading.

For mainstream commercial catalogs, use their official SDK/API and follow their content rules. For example, Apple Music's MusicKit terms do not generally permit users to download or modify MusicKit content.
