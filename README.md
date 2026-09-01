# Daily List for Android

A small, private, offline Android day planner. It uses only Android platform APIs and stores its data in the app's private on-device preferences.

- Check off tasks for any day.
- Tap a task's text to edit it directly in the list.
- Swipe a task left to delete it; use the temporary bottom bar to undo.
- Add a task to the displayed day or its following day.
- Unfinished tasks from earlier days appear greyed out on Today; tap one to copy it into Today.
- Long-press a normal task for a confirmation before deleting it.
- Data stays on the device in the app's private storage.

Requires Android 8.0 or newer.

## Build

Open the repository in Android Studio, allow Gradle to sync, and run the `app` configuration.

From a terminal with Android SDK 35 and JDK 17 installed:

```bash
gradle :app:assembleDebug
```

The generated debug APK is placed under `app/build/outputs/apk/debug/`.

## Privacy

The app requests no network permission and sends no data anywhere.
