# InstaTools – Android
An Android-first Arabic Instagram companion app.

Native Android Java / Gradle, compatible Android 7.0+.
Open Instagram account, posts, reels and stories in an in-app web browser, use logged-in Instagram permission; no bypass of private content.
Save Instagram links and personal notes locally, copy visible comment text from Instagram UI where DOM allows, paste/copy comment text, and download direct .mp4/.jpg media URLs to Downloads/InstaTools.

## Build
Install JDK 17, Android SDK platform 35 and build tools 35, Gradle 8.9.
Run `gradle assembleDebug`; APK is `app/build/outputs/apk/debug/app-debug.apk`.
The GitHub Actions workflow automatically builds a debug APK and attaches it to the run.
The debug APK uses Android's debug signing and is installable on Android; it is not a Play Store release build.
