# MG4 Task Manager

[Italiano](README.md) · **English**

Task manager for the **MG4** infotainment (Android Automotive OS 9): it shows the open apps with their RAM usage and lets you really close them (`am force-stop`), one at a time or all at once. It opens from the launcher icon or with a **steering-wheel button**.

The app also runs without root. It picks the best way available: see "The three modes".

## Features

- List of open apps, with the foreground app highlighted
- **Close** a single app or **Close all**
- **Exclude and hide system apps** (radio, navigation, car apps): on by default
- **Apps excluded by choice** from "Close all" (e.g. DiPlay/CarPlay), from Settings or by long-pressing an app
- Configurable **steering-wheel button** ("Set button…" then press the button); "long-press only" option
- **Automatic updates** from this repository's Releases, even when the app is closed
- Bilingual: Italian or English, following the system language

## Which edition to download

The [Releases](../../releases/latest) contain two files. **Install only one.**

- **`MG4-TaskManager-system.apk` (recommended):** instant close and the full app list **without ADB and with no permissions to enable**. It works because the MG4 infotainment is signed with AOSP's public platform key, so this edition runs as a **system app**. See below what that means.
- **`MG4-TaskManager.apk`:** normal edition, no privileges. Instant close only with ADB active, otherwise basic mode via accessibility.

### What the system edition means

- It runs as a **system app** on the infotainment: it has broad permissions (force-stop apps, read processes). Install it only if you trust the source, i.e. this repository.
- It is signed with a **public** key: anyone can build an APK signed the same way. So download the APK **only** from this official Releases page, never from third parties.
- Updates **always require a tap** on "Install": the app never installs itself silently. (On your own car you can enable silent install via ADB if you want.)

## Installation (from a USB stick, no PC)

1. Download **one** of the two editions from the [Releases](../../releases/latest) and copy it to a USB stick
2. Insert the stick into the car and install the APK like any other app
3. Open **Task Manager**. With the system edition everything is ready. With the normal edition and no ADB, follow the "Finish setup" card (Accessibility + Usage data access).

## The three modes

The app chooses by itself, each time it opens:

| | System | Full (ADB) | Basic |
|---|---|---|---|
| When | platform-signed edition | ADB answers on `127.0.0.1:5555` | all other cases |
| App list | real processes, with RAM | real processes, with RAM | apps opened since power-on |
| Closing | instant (`forceStopPackage`) | instant (`am force-stop`) | "Force stop" via accessibility |
| Permissions | automatic (signature) | automatic (ADB) | enable once |

With the normal edition and ADB, on the first connection the car may ask "Allow debugging?": check "Always allow".

## Automatic updates

At car power-on and then every 6 hours (needs an internet connection) the app checks this repository's latest Release, even when it isn't open.

- **With ADB:** it downloads and installs the update by itself.
- **Without ADB:** it downloads it and shows the "Task Manager vX ready" notification; one tap opens the Android "Install" screen. The first time, Android may ask to allow installing apps from "Task Manager".

In **Settings → Updates** you can turn the check off, choose whether to install automatically, or force a check ("Check now").

## Releases (for the developer)

1. Push your changes to `main` (update `VERSION` too)
2. On GitHub: **Releases → Draft a new release**, create the tag (e.g. `v1.2.1`), write the notes and press **Publish release**
3. GitHub Actions builds and signs the APKs and attaches them to the Release in a couple of minutes: cars receive them at the next check

The version number comes from the tag (`v1.2.3` → versionCode `10203`).

**Signing:** updates install only if signed with the same key. The GitHub build takes it from the repository secrets:

- `KEYSTORE_B64`: the `tm.keystore` file in base64 (`base64 -w0 tm.keystore`)
- `KEYSTORE_PASS`: the keystore password

The system edition is signed with AOSP's **public** platform key, versioned in `signing/` (`platform.pk8` + `platform.x509.pem`): it is not a secret and the GitHub build uses it directly.

Never commit `tm.keystore` to the repository and keep a safe backup: if you lose it, you'll have to uninstall the app to update it.

## Building locally

No Android Studio needed: just a JDK, Python 3 with Pillow, `android.jar` for API 28 and `d8` (build-tools).

```sh
ANDROID_JAR=$ANDROID_HOME/platforms/android-28/android.jar \
D8=$ANDROID_HOME/build-tools/34.0.0/d8 \
KEYSTORE=tm.keystore KEYSTORE_PASS=taskmanager \
sh build.sh
```

`build_apk.py` generates the binary Android manifest and resources without `aapt`.

## Layout

| File | What it does |
|---|---|
| `AdbClient.java` | ADB protocol client (RSA auth, shell commands) |
| `Tasks.java` | Reads processes and closes apps (system / ADB / basic) |
| `SysPriv.java` | System mode: list and force-stop with platform signature |
| `MainActivity.java` | UI and settings |
| `KeyService.java` | Accessibility service for the steering-wheel button |
| `KeyMonitorService.java` | Raw key listening (`getevent`) for keys Android doesn't see |
| `Updater.java` | Automatic updates from GitHub Releases |
| `Lang.java` | Italian/English strings by system language |
