# Oras

A kids mode for one shared family phone. Each child has a code, a turn, and their own
home screen with only the apps I picked for them. Turns rotate. When the day's total
runs out, the phone locks until morning.

"Oras" is Filipino for time.

## Why I built it

A lot of families here share one phone between the kids. Most screen-time apps assume
one child per device, and many quietly stop working overnight on the budget Android
phones sold here. I wanted something a young child can understand: you get your turn,
then it is your sibling's turn, and when the day is used up, it is used up.

## How the time works

Two numbers, set once, in Rules:

- **One turn**: 20 minutes by default. What each child gets when they enter their code.
- **Whole day**: 2 hours by default. The total the children share between them.

When a turn ends, that child goes to the back of the queue, so they cannot start again
until everyone else has had theirs. **The rotation is the break.** There is no separate
cooldown to explain and no per-child settings to keep in sync.

When the shared daily total runs out, the phone locks until the reset hour.

## Kids mode

Oras is set as the phone's Home app. That does the heavy lifting:

- During a child's turn, **Home is their home screen**: their name, their countdown,
  their apps. Nothing is being blocked. Oras simply is Home.
- When no child is using the phone, Home hands straight over to your normal launcher,
  so your own phone feels unchanged.
- Apps outside their list are caught by the accessibility service and sent Home, which
  lands them back on their own grid instead of a dead end.

The app list is set per child, inside the part of the app gated by the parent code. A
child never sees the picker, only the result. An empty list means no limit.

## Setup

First run asks for the parent code, then walks every permission in one pass:

1. **Home screen**, so kids mode works at all
2. **Draw over other apps**, for the lock
3. **Unrestricted battery**, so the timer survives
4. **Notifications**, for the countdown

App blocking (accessibility) stays optional and is offered later. A parent who says no
to it still gets a working kids mode through the launcher.

On Xiaomi, Realme, Oppo, Vivo and Infinix, Oras detects the brand and shows the
autostart step. **Skipping it is the most common reason a screen-time app quietly stops
overnight on phones sold here.**

## Codes

Child codes are 4 digits and the parent code is 6. Both are stored as salted
PBKDF2-HMAC-SHA256, never in the clear. Five wrong codes freeze the keypad for a minute,
doubling up to a fifteen minute cap.

Because a parent typing six digits passes through four, the keypad checks at four
without spending a try. Backup and device transfer are disabled, so a cloud restore
cannot bring back an old code. There is no code recovery. Losing the parent code means
clearing the app's data, which also clears the children.

## What it cannot do

It is a sideloaded app, not a managed device profile. Solid against a young child, not
against a determined teenager with a laptop.

- **Safe mode** disables third-party apps, including this one.
- **ADB or a factory reset** removes it.
- **Recents** can still reach a backgrounded app. The accessibility service is what
  catches that, so kids mode is a lot stronger with it on.
- A child can change the default Home app in Settings. The accessibility service sends
  them Home when Settings is opened during a lock, which covers the realistic case.

## Stack

- Kotlin 2.0 and Jetpack Compose (Material 3)
- Android Gradle Plugin 8.7, Gradle 8.11.1 wrapper
- DataStore for settings, kotlinx.serialization for the models
- minSdk 26, targetSdk 35
- Manrope and IBM Plex Mono bundled as fonts

## Build and run

You need JDK 17 or newer (Android Studio's bundled JDK works) and the Android SDK with
platform 35. Create `local.properties` with `sdk.dir=<path to your Android SDK>`, or
open the project in Android Studio and it writes that for you.

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Out of the box the release build is signed with your debug key, so it installs on your
own phone straight away. To sign with your own release key instead:

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/your.jks \
  -Pandroid.injected.signing.store.password=... \
  -Pandroid.injected.signing.key.alias=... \
  -Pandroid.injected.signing.key.password=...
```

Keep that key. A build signed with a different key will not install over the old one.

Unit tests:

```bash
./gradlew testDebugUnitTest
```

If a build on Windows fails with a file lock on `lint-cache`, run `./gradlew --stop`
and delete `app/build/intermediates/lint-cache`.

## Installing

Play Protect blocks the installer for any sideloaded app that declares an accessibility
service. ADB gets around it:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Android also blocks accessibility for sideloaded apps. Before turning on App blocking,
go to Settings, Apps, Oras, open the three-dot menu, and tap **Allow restricted
settings**.

## Going to the Play Store

Two things to handle before submitting:

- **Accessibility** is allowed for apps that are not accessibility tools, but it needs a
  declaration form in Play Console, an in-app disclosure and explicit consent. Do not
  set `isAccessibilityTool=true`. That is for disability tools and would fail review.
- **Uninstall protection was removed** and should stay out. Parental control apps get
  rejected under the Deceptive Behavior policy when the app cannot be easily removed.
  Boomerang was blocked from Play more than once for exactly that.

## Layout

```
app/src/main/java/com/jefflim/oras/
  core/Engine.kt              turns, the shared pool, the queue, unlocking, recovery
  core/TimeRules.kt           day boundaries, bedtime windows, formatting
  data/                       models, storage, code hashing
  launcher/HomeActivity       Oras as Home; kid grid during a turn, handoff otherwise
  ui/kid/KidHome.kt           the child's home screen
  service/GuardService        foreground service, ticking, notification, lock
  service/StrongLockService   optional app-blocking backstop
  overlay/OverlayHost.kt      hosts Compose in a service-owned window
  ui/lock/                    the lock screen
  ui/parent/                  dashboard, children, rules, history
  ui/setup/                   first run and the one-pass permission walk
  ui/theme/                   colour and type tokens, converted from OKLCH
```

## Status

Version 1.0, sideload only. Not on the Play Store. 28 unit tests cover the
turns, the shared pool, the queue, restarts, the lock and code hashing.

## License

MIT. See `LICENSE`.
