# MG4 Task Manager

[Italiano](README.md) · **English**

Task manager for the **MG4** infotainment (Android Automotive OS 9): it shows the open apps with their RAM usage and lets you really close them, one at a time or all at once. It opens from the launcher icon or with a **steering-wheel button**.

No root needed.

[![Buy me a coffee with PayPal](https://img.shields.io/badge/Buy%20me%20a%20coffee-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white)](https://www.paypal.com/pool/9tv7fQ36nA?sr=wccr)

## Video

[![Watch the video on YouTube](https://img.youtube.com/vi/fYtzuTELNVg/hqdefault.jpg)](https://youtu.be/fYtzuTELNVg)

▶️ [Watch the video on YouTube](https://youtu.be/fYtzuTELNVg)

## Features

- List of open apps, with the foreground app highlighted and RAM usage
- **Close** a single app or **Close all**
- **Uninstall** the apps you installed (never system apps), see below
- **Exclude and hide system apps** (radio, navigation, car apps): on by default
- **Apps excluded by choice** from "Close all" (e.g. DiPlay/CarPlay), from Settings or by long-pressing an app
- Configurable **steering-wheel button** ("Set button…" then press the button); "long-press only" option
- **6 languages:** Italian, English, Spanish, Portuguese, French, German
- **Fast:** when reopened the list shows up at once, and closed apps disappear instantly
- **Automatic updates**, even when the app is closed

## Languages

The app uses the car's **system language** (italiano, English, español, português, français or deutsch; English for any other language). To pick a different one: **Settings → Language**. You can go back to "Automatic" at any time.

## Uninstalling an app

Cards of apps you installed have a **🗑** button; **Settings → Uninstall apps** lists all the apps you installed, closed ones included. Task Manager always asks for confirmation before removing an app. System apps aren't listed and can't be uninstalled.

- **System edition or with ADB:** the app is removed right after you confirm.
- **Normal edition without ADB:** Android's screen opens, where you confirm with "OK".

## Which edition to download

The [Releases](../../releases/latest) contain two files. **Install only one.**

- **`MG4-TaskManager-system.apk` (recommended):** instant close and the full app list, **with no permissions to enable**. It runs as a **system app** on the infotainment.
- **`MG4-TaskManager.apk`:** normal edition, no privileges. Instant close only with ADB active, otherwise via accessibility.

To switch from one edition to the other, uninstall the one you have first.

### What the system edition means

- It has broad permissions (force-stop apps, read processes): install it only if you trust the source, i.e. this repository.
- Download the APK **only** from this official Releases page, never from third parties.
- Updates **always require a tap** on "Install": the app never installs itself.

## Installation (from a USB stick, no PC)

1. Download **one** of the two editions from the [Releases](../../releases/latest) and copy it to a USB stick
2. Plug the stick into the car and install the APK the way you install other apps
3. Open **Task Manager**. With the system edition everything is ready. With the normal one, if needed, follow the "Finish setup" card (Accessibility + Usage access).

## The three modes

The app picks by itself every time it opens:

| | System | Full (ADB) | Basic |
|---|---|---|---|
| When | system edition | normal edition with ADB active | normal edition without ADB |
| App list | all open apps, with RAM | all open apps, with RAM | apps opened since power-on |
| Closing | instant | instant | by tapping "Force stop" automatically |
| Permissions | none to enable | none to enable | enable once |

With the normal edition and ADB, the first time the car may ask "Allow debugging?": check "Always allow".

## Automatic updates

At power-on and then every 6 hours (Internet needed) the app checks for a new version, even when it isn't open. Each edition downloads its own update.

- **With ADB:** it downloads and installs it by itself.
- **Without ADB:** it downloads it and shows the notification "Task Manager vX ready"; a tap opens Android's screen with "Install".

In **Settings → Updates** you can turn checking off, choose whether to install automatically, or check right away ("Check now").

> **Have the normal 1.2?** Install 1.2.1 by hand once, with the USB stick. From 1.2.1 on, updates are automatic.

## Support the project

MG4 Task Manager is and will stay **free**. If you find it useful and would like to contribute to the hours spent
developing it and testing it in the car, you can leave a small donation: it's completely **optional**, and a star on
the repository or a bug report help a lot too. Thank you!

[![Buy me a coffee with PayPal](https://img.shields.io/badge/Buy%20me%20a%20coffee-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white)](https://www.paypal.com/pool/9tv7fQ36nA?sr=wccr)
