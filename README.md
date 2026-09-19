# ALMEER MUSIC
A dark Android music player foundation with real local MediaStore scanning, offline playback through Media3, search, and an offline DJ workspace UI.

## Build
Open in Android Studio or push to GitHub. The included GitHub Actions workflow builds `app-debug.apk` as an artifact.

## API note
Official services such as YouTube/YouTube Music, Apple Music, and Boomplay have different API, playback, download, and licensing restrictions. This project deliberately does not scrape, bypass DRM, or download protected streams. Provider integrations should use official APIs/SDKs and their permitted playback/download mechanisms.
