# GroidPack Updater

Keep your Minecraft modpacks identical to the server, automatically.

---

## The problem

Running a modded server means constant file changes, and every change has to reach everyone's
instance. Doing that by hand usually ends with handing people a whole fresh instance, which throws
away their screenshots, worlds and settings.

## The solution

The modpack lives in a Backblaze B2 bucket with a manifest describing what belongs in each version.
Everyone syncs against that. Your friends click **Update** and they are done.

- Connects to an S3-compatible bucket holding modpack data
- Downloads and applies changes locally
- Tracks modpack changes over time, with a changelog per version
- Never touches worlds, screenshots, or anything outside the tracked folders

## Features

- Configurable S3 bucket settings with a connection test
- Add an existing or brand new CurseForge instance and start tracking it
- Only instances that match a server modpack appear, so personal profiles stay out of the way
- **Re-check**: compare every tracked file against the server and repair drift
- Git-style tracking of changes with a per-version changelog
- Admin panel for publishing versions, picking tracked folders, and reviewing diffs
- In-app software updates

## Requirements

Java 17 or newer, and CurseForge for your Minecraft profiles.

Get Java from [adoptium.net](https://adoptium.net) if `java -version` fails.

## Installing

Download the zip for your platform from the releases page and unzip it anywhere.

**Windows** — double-click `PackUpdater.jar`. If Windows opens it with an archive tool instead,
use `Launch PackUpdater.bat`.

**macOS** — double-click `PackUpdater.jar`. The first time, macOS will say the file is from an
unidentified developer. Right-click (or Control-click) it, choose **Open**, then **Open** again.
That is only needed once. If double-clicking does nothing, use `Launch PackUpdater.command`.

Each zip ships a `FIRST-TIME-SETUP.txt` with the same instructions.

`settings.json` is written next to the jar, so keep the unzipped folder somewhere you can write to.
If that folder is read-only, settings fall back to `~/.packupdater/settings.json`.

## First run

Two settings are required before anything works. Both are in **Settings**.

1. **CurseForge instances folder.** Press **Detect** and it will usually find it. Otherwise, in
   CurseForge click the three dots next to the search bar, choose *Open modding folder*, then go
   into `minecraft` and pick `Instances`.
2. **Key ID and application key.** Ask your admin. The server URL and bucket are normally already
   correct. Press **Test connection** to confirm.

## Adding a modpack

1. Look at the pack under **On the server** and note its Minecraft and mod loader versions.
2. In CurseForge, create a new profile with exactly those versions.
3. Back here, click **Add modpack**, pick the pack, and choose that new instance folder.

Folder picking uses the operating system's own browser, opened at your instances folder, so the new
profile is normally one click away.

This also works for an instance you already have that matches a server pack but was never tracked.

## Update vs Re-check

**Update** appears when the server has a newer version. It shows exactly what will change, then
downloads it.

**Re-check** compares every tracked file against the server and repairs anything that drifted. Use
it when the game crashes, a mod went missing, or files were added by accident. It:

- downloads files that are missing
- replaces files that differ from the server
- deletes files inside tracked folders that the server does not have
- leaves saves, screenshots, resource packs, and everything outside tracked folders alone

Both show a full list of changes and wait for confirmation before touching a single file.

## Admin controls

### Publishing a version

Pick the matching local and server modpack. These must be the same modpack or you will publish
nonsense. Press **Compare**, review the diff, untick anything you do not want, set a version number
higher than the current one, write a changelog line, then **Publish version**.

Operations are recorded as Added, Modified, and Deleted.

### Choosing what is tracked

The **Tracked content** tab lists the folders and loose files in your local instance. Whatever is
ticked when you publish becomes the tracked set for that version. Anything the server tracks but
that is missing locally is shown in red.

## Building

```bash
./gradlew createLaunchers
```

Produces `build/launchers/GroidPack Updater-Windows.zip` and `GroidPack Updater-MacOS.zip`. The
macOS zip stores the Unix executable bit on `.command` so the launcher is runnable after unzipping.

For a plain jar:

```bash
./gradlew shadowJar
```

## Releasing

Bump `version` in `build.gradle`, build, and attach `PackUpdater.jar` to a GitHub release tagged
`v<version>`. The in-app updater looks for an asset named `PackUpdater.jar`, verifies the manifest
version inside it is newer, stages it alongside the current jar, and swaps it in on restart. The
running jar is never deleted before the replacement is verified.
