# Daily List for Android

A small, private, offline Android day planner. It uses only Android platform APIs and stores its data in the app's private on-device preferences.

- Check off tasks for any day.
- Long task text wraps onto additional lines while its checkbox stays centred.
- Tap a task's text to edit it directly in the list.
- Hold a task and drag vertically to reorder it; nearby tasks animate aside to
  show exactly where it will be placed.
- Hold and drag a task right to make it a subtask; drag a subtask left to
  promote it back to a normal task.
- Swipe a task left to reveal Delete, then tap the button to confirm; use the
  temporary bottom bar to undo.
- Swipe a task right to reveal Tomorrow, then tap the button to move it to the
  following day. Parent tasks bring their subtasks with them.
- Add a task to the displayed day or its following day.
- Unfinished tasks from earlier days appear greyed out on Today. Carrying a
  parent forward also carries only its unfinished subtasks.
- Data stays on the device in the app's private storage.
- The installed app version and Android build code appear in a subtle footer.

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
