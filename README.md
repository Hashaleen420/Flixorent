🎬 Flixorent (Android)
A modern, native Android application for streaming movies and TV shows.

Flixorent is a lightweight, "plug-and-play" alternative to complex streaming setups. Built entirely in native Kotlin, it leverages the TMDB API for rich metadata and integrates directly with Torrentio to provide high-quality streams without the need for external add-ons or configuration.

Whether you are a power user with a Debrid account or a free user looking for a solid torrenting experience, Flixtorrent handles it all with a unified, Netflix-style interface.

✨ Key Features

⚡ Core Streaming Engine
Zero-Setup Scraping: Built-in integration with Torrentio. No external add-ons, repositories, or configuration required—just click and play.

Hybrid "Free & Premium" System:

Premium Mode: Unlocks high-speed, buffer-free streaming using Debrid services.

Free Mode: Seamlessly bridges with Amnis Torrent Player for reliable peer-to-peer streaming if no Debrid service is detected.

Multi-Provider Support: Secured locally on user device
Native API integration for:

Real-Debrid

AllDebrid

Premiumize

TorBox

Smart Source Sorting: Automatically ranks links by resolution (4K > 1080p > 720p) and seed count.

📺 Android TV Experience (Native)
Leanback Support: Fully compatible with the Android TV Home Screen (Leanback Launcher).

Custom Banners: High-resolution (720p) TV banner assets designed to look crisp on 4K TVs.

Remote-Optimized Navigation:

D-Pad Support: Full navigation using only directional keys.

Visual Focus States: "Glowing" borders on posters and buttons so you always know what is selected.

Search Optimization: Auto-focus logic prevents "Focus Traps" common in other Android apps.

⏯️ Player & Playback
Advanced Internal Player: Built on Media3 (ExoPlayer) with an integrated FFmpeg decoder to support high-quality audio formats (AC3, EAC3, DTS) often found in 4K torrents.

Google Cast Support: Cast your favorite content directly to Chromecast or Android TV devices (Premium links only).
Need to cast to tv before selecting stream.

Smart Resume: Local database tracks your watch progress.

Pop-up: "Resume from 45:20 or Start Over?"

External Player Choice: Option to "Play External" (VLC, MX Player, JustPlayer) for specific codec needs.

🛠️ User Interface & Metadata
Material Design UI: A sleek, dark-themed interface designed for OLED screens, featuring high-res backdrops, posters, and plot summaries.

Smart Recommendations: Dynamically generates a "Recommended for You" row based on your viewing history.

Immersive Viewing: Transparent system bars for an edge-to-edge cinematic experience.

Smart Error Handling: Context-aware messages (e.g., "Please login to TorBox") instead of generic API errors.

⚙️ Technical Highlights
100% Native Kotlin: Fast, lightweight, and battery-efficient (unlike web-wrapper apps like Stremio).

Local History Database: Uses Room Database to store watch history locally on your device.

Privacy Focused: No tracking, no ads, and no mandatory account sign-up.

🚀 Setup & Requirements

📱 Requirements
Android Version: Android 8.0 (Oreo) or higher.

TV Support: Compatible with FireStick, Google TV, and Shield TV.

🔑 For Best Experience (Premium)
Link your Debrid account in Settings for instant 4K/1080p streaming without waiting for seeds.

Supported: Real-Debrid, AllDebrid, Premiumize, TorBox.

🆓 For Free Users
If you do not use a Debrid service, you must install Amnis Torrent Player from the Google Play Store. Flixtorrent uses Amnis as the engine for free-mode peer-to-peer streaming.

🛣️ Roadmap
Trakt.tv Sync: Cloud syncing for watch history.

Auto-Play Next Episode: Seamless binge-watching support.

In-App Subtitles: OpenSubtitles integration for the internal player (currently supported via Amnis only).

🤝 Credits & Disclaimer
Data: Metadata provided by TMDB.

Player: Built using AndroidX Media3 & FFmpeg.

Disclaimer: This application is for educational purposes only. The developer does not host any content. The app acts solely as a client to scrape publicly available data.
