# Changelog

All notable user-visible changes to Daily List are recorded here. Update the
`Unreleased` section whenever the app changes, then move those entries into a
versioned section when a new APK is released.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the version numbers match the Android app's `versionName`.

## [Unreleased]

## [1.6.1] - 2026-09-04

### Fixed

- Stopped older incomplete tasks from being suggested again after a carried
  copy was completed on a later day.

## [1.6] - 2026-09-02

### Added

- Displayed the human-readable app version and Android version code in a subtle
  footer below the Add task button.

## [1.5.1] - 2026-09-02

### Changed

- Improved long-press reordering with a live insertion preview. Nearby tasks
  animate aside to show the destination slot while the selected task follows
  the drag.
- Parent tasks and their subtasks now preview-move together as one group.

## [1.5] - 2026-09-02

### Added

- Reordered tasks by holding a task and dragging it vertically.
- Created a subtask by holding a task and dragging it to the right; dragging a
  subtask left promotes it back to a normal task.
- Carried a task group forward with its unfinished subtasks while leaving
  completed subtasks behind.

### Changed

- Deleting a parent promotes its subtasks to normal tasks, with Undo restoring
  the original relationship.

### Removed

- Removed long-press deletion. A quick left swipe remains the delete gesture.

## [1.4] - 2026-09-02

### Changed

- Replaced the task-editing dialog with inline editing in the list. Changes save
  when the keyboard's Done action is used, another part of the app is tapped,
  or the app is left.

## [1.3] - 2026-09-02

### Added

- Opened an edit dialog by tapping a task's text, with the existing text ready
  to update.
- Deleted tasks by swiping them to the left, with a visible delete affordance.
- Added a temporary bottom undo bar that restores the most recently deleted
  task for a few seconds.

## [1.2] - 2026-09-01

### Changed

- Centred the page heading and date between the previous- and next-day controls.
- Reworked the interface around the reference blue (`#23588C`), with coordinated
  pale-blue backgrounds, buttons, checkboxes, dividers, and navigation elements.
- Added clear selected and unselected styling to the Today and Tomorrow buttons.

## [1.1] - 2026-09-01

### Fixed

- Kept the header and add-task control clear of Android's status and navigation
  bars by applying the device's system-bar insets.
- Improved APK compatibility with current Android devices and made the release
  installable as an update without clearing existing task data.

## [1.0] - 2026-09-01

### Added

- Created daily to-do lists with previous- and next-day navigation.
- Added tasks to either the current day or the following day.
- Marked tasks complete with checkboxes.
- Displayed unfinished tasks from earlier days as grey carry-over suggestions
  that can be added to the current day.
- Stored tasks and completion state locally on the device.
