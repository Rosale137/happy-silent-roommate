# Happy Silent Roommate

An Android app that provides gentle haptic feedback when speech volume exceeds a configurable threshold during quiet hours.

## Motivation

My roommate is very sensitive to noise, and I sometimes speak louder than I realise in the evening.

The goal of this project is to provide subtle vibration feedback whenever I exceed a configurable sound level, helping me stay aware of my volume without disturbing others.

The app is designed to be privacy-friendly:
- no cloud services
- no audio recordings
- local processing only

## Current Status

🚧 Early development

Completed:
- Android Studio project created, package `io.github.rosale137.happysilentroommate`
- Live microphone level (RMS in dBFS), computed locally, no audio stored
- Oscilloscope view of the last 30 s with the limit and the integration time dragged directly in the plot
- Test-vibration button
- HTML mockups of the planned screens in `docs/mockups` (open `index.html`)

In progress:
- Automatic vibration when the integrated level exceeds the limit

## Planned Features

Built step by step; each step is runnable on its own.

- [x] Microphone level monitoring
- [x] RMS-based volume detection (integrated over an adjustable time)
- [x] Adjustable volume limit
- [ ] Vibration feedback when the limit is exceeded
- [ ] Cooldown to prevent repeated alerts
- [ ] Foreground service, so it keeps listening with the screen off
- [ ] Whisper test as a reference line; the limit is stored relative to it, with a "limit follows whisper" switch
- [ ] Configurable quiet hours (several windows and weekdays), automatic start and stop
- [ ] Settings screen: vibration patterns (gentle, medium, strong, escalating), pause button, units
- [ ] Extras: statistics, quick-settings tile, recalibration reminder
- [ ] Later: only listen when at home (geolocation)

## Tech Stack

- Kotlin
- Android SDK
- Jetpack Compose
- Android Studio

## Installing

Sideload for now: build with `./gradlew assembleDebug` and install
`app/build/outputs/apk/debug/app-debug.apk` (for example `adb install -r`). It needs only
the microphone and vibration permissions, no Google services, so it also runs on Murena /e/OS.

## Development

This project is developed by Lena.

Planning, documentation and development support are partially assisted by Microsoft Copilot.

## License

MIT License (planned)
