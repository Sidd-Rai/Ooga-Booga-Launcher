# Ooga Booga Launcher

Ooga Booga Launcher is a minimalist, offline-first Android home screen focused on privacy and intentional app use.

## Features

- Searchable, text-based app drawer
- Configurable home-screen apps and two optional bottom shortcuts
- Hidden-app drawer with launch, search, bulk editing, and app actions
- Standard Android widgets on the home screen
- Optional left and right widget screens
- Direct widget adding, moving, edge/corner resizing, and drag-to-remove
- Optional home-screen clock
- Custom light or dark appearance with foreground/background colour controls, system fonts, and font sizing
- Solid system wallpaper matching the selected background colour
- Distracting-app timers with 1, 5, 10, 15, or custom minute limits
- Foreground-only timer counting, notification deep-link preservation, three optional extensions, and automatic exit when time expires
- ShutUp list for dismissing notifications from selected apps
- Browser-assisted update checks without granting the launcher Internet access
- No ads, analytics, accounts, cloud services, or Internet permission

## Privacy

The launcher stores configuration only on the device and cannot access the Internet. See the full [Privacy Policy](PrivacyPolicy.md).

## Required access

Ooga Booga Launcher uses only access needed for its enabled features:

- **Installed apps:** displays and launches installed applications.
- **Accessibility:** enforces distracting-app timers and displays the expiry prompt.
- **Notification access:** powers the optional ShutUp list.
- **Wallpaper:** applies the selected solid background colour as the system wallpaper.
- **Home role:** allows the app to act as the device launcher.

Accessibility and Notification access are optional and must be enabled manually in Android Settings. Android may require **Allow restricted settings** under App Info for sideloaded installations.

## Build

Requirements:

- Android SDK 36
- JDK 17 or newer
- Gradle 8.13 via the included wrapper

Build a debug APK:

```sh
./gradlew assembleDebug
```

Build a minified release APK:

```sh
./gradlew assembleRelease
```

Release APKs require your own signing configuration before public distribution.

## Installation

1. Install the APK.
2. Open Ooga Booga Launcher or press Home.
3. Select it as the default Home app.
4. Configure optional Accessibility and Notification access from App Settings.

## Platform notes

- Android controls default-launcher selection; the app cannot silently make itself the default.
- Some ongoing or system notifications cannot be dismissed by third-party notification listeners.
- Widget previews and configuration options depend on what each widget provider exposes.

## License

Ooga Booga Launcher is available under the [MIT License](LICENSE).
