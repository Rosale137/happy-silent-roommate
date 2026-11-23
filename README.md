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
- Automatic vibration when the integrated level exceeds the limit; while you stay too loud the
  reminders come faster (adjustable range) and stronger (soft tap, double tap, long buzz)
- Urgency fades slowly once you are quieter, so getting loud again soon continues where it was
- Adjustable hysteresis, shown as a dotted line in the plot
- The screen flashes with every buzz, plus a buzz counter (handy on the emulator)
- Settings are saved on the phone
- Test-vibration button that plays the three patterns in turn
- Listening in the background (foreground service): keeps going with the screen off, started and
  stopped in the app; the notification shows the buzz count, turns coral while too loud and has a
  Stop button
- The app ignores the microphone while the phone vibrates, so its own buzz never triggers the next one
- Tested on a Pixel 8 with Murena /e/OS (Android 15). With Do Not Disturb on, the phone does not vibrate
- Quiet time: listening starts and stops on its own (one daily window, may run past midnight).
  Two modes to choose in the app: standby (waits in the background with a small notification and
  starts by itself) or reminder (a notification at the start time, one tap starts listening).
  After a phone restart, standby needs one tap on a notification
- App icon: an elephant that may not trumpet (mockups of the other ideas in `docs/mockups/08-app-icon.html`)
- HTML mockups of the planned screens in `docs/mockups` (open `index.html`)

Next:
- Several quiet windows and weekdays

## Planned Features

Built step by step; each step is runnable on its own.

- [x] Microphone level monitoring
- [x] RMS-based volume detection (integrated over an adjustable time)
- [x] Adjustable volume limit
- [x] Vibration feedback when the limit is exceeded
- [x] Reminders that speed up and get stronger while you stay too loud, with hysteresis
- [x] Foreground service, so it keeps listening with the screen off
- [x] Quiet time with automatic start and stop (one daily window)
- [ ] Several quiet windows and weekdays
- [ ] Settings screen: choice of vibration patterns, pause button, units
- [ ] Extras: statistics, quick-settings tile
- [ ] Later: only listen when at home (geolocation)

### Optional features to come

- Whisper test: whisper for a few seconds to set a reference line, and keep the limit relative
  to it, so it adapts when the phone lies in a different place. Left out for now because the
  limit can be dragged directly in the plot and is saved; worth adding if the phone placement
  varies a lot. Would come with a recalibration reminder.

- Animated mascot: the app's animal (from the app icon) is shown on the main screen and reacts
  to the level, calm and content while you are quiet, more and more alarmed as you get close to
  or above the limit.

- Fitness band or smartwatch: forward the "too loud" buzz to a band on the wrist, for example
  via [Gadgetbridge](https://gadgetbridge.org/) (works without Google services, so also on
  Murena /e/OS). Useful when the phone is not close enough to feel it.

## Tech Stack

- Kotlin
- Android SDK
- Jetpack Compose
- Android Studio

## Installing

Sideload for now: build with `./gradlew assembleDebug` and install
`app/build/outputs/apk/debug/app-debug.apk` (for example `adb install -r`). It needs only
the microphone, vibration and notification permissions, no Google services, so it also runs on Murena /e/OS.

## Development

This project is developed by Lena.

Planning, documentation and development support are partially assisted by Microsoft Copilot.

## License

MIT License (planned)
