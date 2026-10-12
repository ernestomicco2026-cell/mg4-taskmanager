# MG4 Task Manager

[Italiano](README.md) · **English**

Task manager for the **MG4** infotainment (Android Automotive OS 9): it shows the open apps with their RAM usage and lets you really close them (`am force-stop`), one at a time or all at once. It opens from the launcher icon or with a **steering-wheel button**.

No root needed: the system edition uses the permissions of the car's platform signature, the normal one connects to the **car's ADB** locally (`127.0.0.1:5555`) or uses accessibility.

## Features

- List of open apps, with the foreground app highlighted and RAM usage
- **Close** a single app or **Close all**
- **Exclude and hide system apps** (radio, navigation, car apps): on by default
- **Apps excluded by choice** from "Close all" (e.g. DiPlay/CarPlay), from Settings or by long-pressing an app
- Configurable **steering-wheel button** ("Set button…" then press the button); "long-press only" option
- **6 languages:** Italian, English, Spanish, Portuguese, French, German (see below)
- **Automatic updates** from this repository's Releases, even when the app is closed

## Languages

By default the app follows the car's **system language**: italiano, English, español, português, français or deutsch; any other language falls back to English.

To pick a different one: **Settings → Language**. You can go back to "Automatic (system language)" at any time. The choice applies to the whole app, notifications included. The service name on the car's Accessibility page always follows the system language, because Android shows it.

Update notes also arrive in the chosen language.

## Which edition to download

The [Releases](../../releases/latest) contain two files. **Install only one.**

- **`MG4-TaskManager-system.apk` (recommended):** instant close and the full app list **without ADB and with no permissions to enable**. It works because the MG4 infotainment is signed with AOSP's public platform key, so this edition runs as a **system app**. See below what that means.
- **`MG4-TaskManager.apk`:** normal edition, no privileges. Instant close only with ADB active, otherwise basic mode via accessibility.

The two editions have different signatures: to switch from one to the other, uninstall the one you have first.

### What the system edition means

- It runs as a **system app** on the infotainment: it has broad permissions (force-stop apps, read processes). Install it only if you trust the source, i.e. this repository.
- It is signed with a **public** key: anyone can build an APK signed the same way. So download the APK **only** from this official Releases page, never from third parties.
- Updates **always require a tap** on "Install": the app never installs itself silently. (On your own car you can enable silent install via ADB if you want.)

## Installation (from a USB stick, no PC)

1. Download **one** of the two editions from the [Releases](../../releases/latest) and copy it to a USB stick
2. Plug the stick into the car and install the APK the way you install other apps
3. Open **Task Manager**. With the system edition everything is ready. With the normal one and no ADB, follow the "Finish setup" card (Accessibility + Usage access).

## The three modes

The app picks by itself every time it opens:

| | System | Full (ADB) | Basic |
|---|---|---|---|
| When | platform-signed edition | ADB answers on `127.0.0.1:5555` | otherwise |
| App list | real processes, with RAM | real processes, with RAM | apps opened since power-on |
| Closing | instant (`forceStopPackage`) | instant (`am force-stop`) | "Force stop" via accessibility |
| Permissions | automatic (signature) | automatic (ADB) | enable once |

With the normal edition and ADB, on the first connection the car may ask "Allow debugging?": check "Always allow".

## Speed

- When you reopen it, the last list shows up at once and then refreshes by itself
- In system mode the list arrives first and RAM a moment later, read only for the apps shown
- App icons, names and info are kept in memory: they're read only once
- Closed apps disappear from the list right away, without waiting for a reload

## Automatic updates

At power-on and then every 6 hours (an Internet connection is needed) the app checks this repository's latest Release, even when it isn't open. Each edition downloads **its own** APK (normal or `-system`), so the signature always matches.

- **With ADB:** it downloads and installs the update by itself.
- **Without ADB:** it downloads it and shows the notification "Task Manager vX ready"; a tap opens Android's screen with "Install". The first time Android may ask to allow installing apps from "Task Manager".

In **Settings → Updates** you can turn checking off, choose whether to install automatically, or force a check ("Check now").

> **From 1.2 to 1.2.1 with the normal edition:** 1.2 always downloaded the first APK of the Release (the `-system` one), which doesn't install over the normal edition. If you have the normal 1.2, install 1.2.1 by hand once (same USB procedure as above). From 1.2.1 on, updates pick the right edition.

## Releases (for the developer)

1. Push your changes to `main` and update `VERSION` and `RELEASE_NOTES.md`. On every push the check build (**Actions → Build**) compiles and verifies both editions; test APKs are among the run's *Artifacts*
2. On GitHub: **Releases → Draft a new release**, create the tag (e.g. `v1.2.1`) on `main`, any title
3. **Description:** leave it empty to use `RELEASE_NOTES.md` automatically, or write your own
4. Press **Publish release**: GitHub Actions builds, verifies and signs both editions and attaches them to the Release in a couple of minutes. Cars get them at the next check

In a Release the version number comes from the tag (`v1.2.3` → versionCode `10203`); `VERSION` is used by local and check builds, keep it equal to the tag.

**Multilingual notes:** in `RELEASE_NOTES.md` each language starts with `<!-- lang:xx -->` (`it`, `en`, `es`, `pt`, `fr`, `de`). GitHub shows all of them; the app shows only the user's language (or English).

**Signing:** updates install only if signed with the same key. The GitHub build takes it from the repository secrets:

- `KEYSTORE_B64`: the `tm.keystore` file in base64 (`base64 -w0 tm.keystore`)
- `KEYSTORE_PASS`: the keystore password

The system edition is signed with AOSP's **public** platform key, versioned in `signing/` (`platform.pk8` + `platform.x509.pem`): it isn't a secret and the GitHub build uses it directly.

Never commit `tm.keystore` to the repository and keep a backup: if you lose it, updating the normal edition will require uninstalling it.

## Building locally

No Android Studio needed: just a JDK, Python 3 with Pillow, API 28 `android.jar` and `d8` (build-tools).

```sh
ANDROID_JAR=$ANDROID_HOME/platforms/android-28/android.jar \
D8=$ANDROID_HOME/build-tools/34.0.0/d8 \
KEYSTORE=tm.keystore KEYSTORE_PASS=taskmanager \
sh build.sh

# optional: check the APKs with aapt2 and apksigner
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0 sh verify.sh v1.2.1
```

`build_apk.py` generates the manifest and resources (translated strings included) in Android binary format without `aapt`.

## Translating

Every UI text is written in the code as `Lang.tr(it, en, es, pt, fr, de)`: the compiler reports an error if a language is missing. The accessibility service texts are in `A11Y_STRINGS` inside `build_apk.py`.

## Structure

| File | What it does |
|---|---|
| `MainActivity.java` | UI and settings (language included) |
| `Lang.java` | Languages: automatic or chosen, translated texts, per-language release notes |
| `Tasks.java` | Listing and closing apps, icon/name/package caches |
| `SysPriv.java` | System mode: list via ActivityManager, RAM in a second pass, `forceStopPackage` |
| `AdbClient.java` / `Adb.java` | ADB protocol client (RSA auth, shell commands) and preferences |
| `KeyService.java` | Accessibility service: steering-wheel button and "Force stop" in basic mode |
| `KeyMonitorService.java` / `KeyHub.java` | Raw keys (`getevent`) and button learning |
| `Updater.java` / `UpdateJobService.java` | Automatic updates from GitHub Releases, per edition |
| `build.sh` / `build_apk.py` / `verify.sh` | Building, packaging and verifying the APKs |
