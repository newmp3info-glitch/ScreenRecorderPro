# Screen Recorder Pro

GitHub-ready Android Studio project for testing a native screen recorder.

## Current build
- Android 10+ (minSdk 29)
- MediaProjection screen recording
- Device default / 1080p / 1440p / 4K selection
- 30/60 FPS selection
- Microphone audio option
- Floating `REC` overlay with Stop action
- MP4/H.264 output
- GitHub Actions APK build

## Important limitations
Android controls internal playback capture. This starter uses the microphone audio path; it does not claim that every app's internal audio or headphone output can be captured. A production internal-audio implementation must use Android's AudioPlaybackCapture APIs and handle source-app opt-out/protected content and synchronized audio/video muxing.

Recordings are written to the app's Movies/Recordings directory for this test build.

## GitHub
Push the whole repository. GitHub Actions runs `assembleDebug` and publishes the APK as an artifact.
