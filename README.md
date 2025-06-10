# 🛠️ Minecraft Modpack Updater

Keep your Minecraft modpacks up to date—automatically.

---

## 🧩 The Problem

Managing modded Minecraft servers can be tedious. Constant file changes mean manually syncing updates across all your friends’ modpack instances. I had to do this and it was awful, usually leading to giving them a completely new updated instance which discards important personal folders like screenshots and map files.

## ✅ The Solution

This application uses an Amazon S3 bucket to host the latest version of your modpack and a manifest to track changes. It provides a centralized version for everyone to sync to, eliminating manual work. All your friends need to do is click **“Update”**.

This app keeps consistent modpack versions across all clients by:
- Connecting to an Amazon S3 bucket containing modpack data
- Downloading and applying updates locally
- Tracking modpack changes over time
<img width="1112" alt="Screenshot 2025-06-10 at 11 32 38 AM" src="https://github.com/user-attachments/assets/db32657d-8d6b-49d9-afe8-498cf750cc41" />

## Features

- Configurable client S3 Bucket settings
- Ability to migrate old modpacks to a server modpack to start tracking
- Ability to create a brand new local modpack and update to any server modpack
- Git-style tracking of changes
- Ability to update existing added modpacks to latest version
- Ability to view changelogs of the modpacks
- Full admin panel equiped with update pushes, new folder tracking declarations, and new server modpack additions

## How to track a local modpack

<img width="612" alt="Screenshot 2025-06-10 at 12 34 34 PM" src="https://github.com/user-attachments/assets/0ca73ca6-daf4-4c14-8124-3d4c8babaf37" />

This entire application assumes the client is using Curseforge: https://www.curseforge.com, to run minecraft profile instances.

### Brand New local modpack

1. Open this application and view which server modpack you would like to add.
2. Make a new profile instance on curseforge with the server's Minecraft version, and Modloader version
3. Click on "Migrate Old Modpack" and pass in the root directory of this new modpack.
4. Click on which server modpack you would like to download.
5. Click ok

### Existing modpack

This only works if you have an existing modpack that is already on the server but never locally tracked.

1. Click on "Migrate Old Modpacks" and pass in the root directory of your local modpack
2. When you choose which server modpack you want make sure it is the correct version as the local one

For example: I have ServerV3 modpack locally but never tracked it. ServerV3 is on the server and able for me to start tracking. I pass in the root of my local ServerV3 modpack and click the ServerV3 modpack on the server.

## Important Settings

In order for this to work you NEED a server URL and correct credentials. If you do not have these then nothing will happen. Get the Application Key and KeyID from your admin and place them in settings.

Additionally, you NEED a curseforge instances path. This is the root directory for every curseforge instance profile. NOTHING will work if you do not have this setting. It is typically located in Users/user/<directory>/curseforge/minecraft/instances.

<img width="1112" alt="Screenshot 2025-06-10 at 11 33 52 AM" src="https://github.com/user-attachments/assets/c5f0712b-3f06-4766-bc44-0f5339f6753f" />

## Admin Controls

### Pushing a new version

You must pass in a local and server modpack to compare differences. These MUST be the same modpack server/local or this will not work and you will screw everything up. 
Changes will be displayed on the card list on the bottom, click the changes you want to push for the version. You then need to stage them before pushing to the server. Add a new version number and then a description, then push to the server.

There are 3 operations, ADD, DELETE, and MODIFY.

<img width="1312" alt="Screenshot 2025-06-10 at 11 34 22 AM" src="https://github.com/user-attachments/assets/a06daa21-547a-404c-8c95-eadcb740e267" />

### Adding a new folder to track

The folder section will display all of the lcoal folders on your modpack, if the folders are checked then that means they are being tracked. To add a folder, just keep it clicked when you push a new version.

### Adding a new server modpack

Click the "Upload New Modpack" button and then pass in the ROOT of your local new modpack. Next give it a display name and fill in the minecraft version, modloader name/version, creator, name, description, and folders to track. Then press push, the modpack is now on the server and can be used to track local ones.


