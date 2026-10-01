# GroidPack Updater

Keeps your Minecraft modpack folders identical to the server. Worlds, screenshots and settings are never touched.

## Install

Download the zip for your platform from Releases and unzip it anywhere. Needs Java 17 or newer from [adoptium.net](https://adoptium.net).

Double-click `PackUpdater.jar`.

On macOS the first launch is blocked. Right-click the jar, choose Open, then Open again. Only needed once.

## Setup

Open Settings and fill in two things:

1. Your CurseForge instances folder. Press Detect.
2. The key ID and application key from your admin. Press Test connection.

## Adding a modpack

Click Add modpack, pick a pack, then either:

- **Set up for me** creates the CurseForge profile and downloads everything.
- **Use my folder** points at a profile you already made.

## Update and Re-check

**Update** appears when the server has a newer version.

**Re-check** compares every tracked file against the server and repairs anything that drifted. It downloads what is missing, replaces what differs, and deletes files added inside tracked folders. Saves, screenshots and resource packs are left alone.

Both show the full list and wait for confirmation.

## Admin

- **Compare** a local and server pack, tick the changes, set a higher version, add a changelog line, then **Publish**.
- **New modpack** uploads a local instance as a new server pack.
- **Set template** publishes the profile so players can use Set up for me.
- **Tracked content** picks which folders and files are synced.

## Build

```bash
./gradlew createLaunchers
```

Zips land in `build/launchers/`. For in-app updates, attach `PackUpdater.jar` to a release tagged `v<version>`.
