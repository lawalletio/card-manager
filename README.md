# LaWallet Card Manager

Native **Kotlin + Jetpack Compose** Android app to **activate LaWallet NFC cards**
on the **ZCS Z92** POS terminal. Sign in with a device-token QR, tap a card, and it
mints a one-time **activation QR** that prints on the built-in thermal printer for
the end user to scan and claim the card. Reuses the **ZCS SmartPos** printer driver
and **NFC** stack from `../android-pos-wrapper`.

## Features

- **JWT login** — scan (or paste) a device-token QR; the token's `apiUrl` claim
  picks the instance, every call is `Bearer`-authenticated, a 401 logs out.
- **Live activation** — tap → `GET /api/cards/:id` → real status / lightning
  address / design, then mint `POST …/activation-tokens` → print the QR.
- **Real NFC + real thermal print** on the ZCS hardware; the printed QR is the
  exact same ZXing code shown on screen.
- **Settings** — instance, pubkey, scopes, **token expiry**, an **auto-print on
  tap** toggle (mint + print automatically), log out, and the app version.
- **Blank-card guidance** — a tag with no LaWallet lnurl prompts to install it
  first with the *LaWallet Card Installer* app (on zapstore).
- **Optimized release** — R8 + resource shrinking + baseline profiles (~3.7 MB),
  signed. See [`PUBLISHING.md`](PUBLISHING.md).

## Toolchain

AGP 8.7 · Gradle 8.11 · Kotlin 2.0 · compileSdk 35 · minSdk 24 · targetSdk 35.
Gradle needs **JDK 17+** (e.g. Android Studio's bundled JBR).

## Build / install / run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:installDebug       # install on the connected Z92 (adb)
adb shell am start -n io.lawallet.cardactivation/.MainActivity
```

First launch asks for `READ_PHONE_STATE` (the ZCS SDK reads device identity during
`sdkInit`). For a **signed release** build + zapstore publishing, see
[`PUBLISHING.md`](PUBLISHING.md).

## How it works

1. **Scanning** — always armed for NFC; an animated radar pulse waits for a tap.
   ("Enter manually" pastes a card URL/id for testing.) A tapped tag with no
   LaWallet lnurl shows a *blank card* prompt instead.
2. **Loading** — a Lottie animation while `GET /api/cards/:id` runs (the id is
   parsed from the tapped `…/api/cards/<id>/scan` URL).
3. **Card detail** — a 3D **floating card** (real `design.imageUrl` via Coil) with
   the card **status** and **lightning address**; **ACTIVATE** / **Cancel**.
4. **QR + print** — ACTIVATE mints `POST /api/cards/:id/activation-tokens`
   `{qrKind:"ONE_TIME"}`; the returned **`qrPayload`** shows as a thermal-ticket
   preview that slides up (~2 s) *while it really prints*, then returns to scanning.

Testing the full flow needs a valid device-token JWT and a registered card on a
reachable instance.

## Look & feel

Themed after **lawallet.io**: near-black `#0A0A0A` canvas with a soft teal glow, a
green status bar, the emerald-teal gradient `#00836D → #00A085 → #00C7A0` on buttons,
and **Inter** typography. The QR scanner is a portrait square reticle; the print
preview is an actual thermal-ticket mock with torn-paper edges. Theme + brand
helpers live in `ui/theme/{Theme,Brand}.kt`.

## Drivers (reused vendor binaries)

- `app/libs/SmartPos_1.9.4_R250117.jar` — ZCS SmartPos SDK (`com.zcs.sdk.*`).
- `app/src/main/jniLibs/{armeabi-v7a,arm64-v8a}/{libSmartPosJni,libEmvCoreJni}.so`
  — **required** native libs; without them `Sys.sdkInit()` NPEs. The Z92 is 32-bit
  (`armeabi-v7a`).

## Architecture

- `pos/ZcsPrinter.kt` — SDK init + `printActivation(url)` via
  `DriverManager.getInstance().getPrinter()` → `setPrintAppendString` /
  `setPrintAppendBitmap` (prints the same `QrBitmap` ZXing QR) / `setPrintStart()`.
- `pos/CardNfc.kt` — NDEF parse (URI + Text), preferring the LaWallet lnurl record.
- `MainActivity.kt` — Compose host + NFC foreground dispatch; emits card URLs / a
  blank-card signal to `SharedFlow`s.
- `data/{Jwt,AuthStore,Api,Settings}.kt` — JWT decode + token/prefs stores + the
  authenticated OkHttp client (`Bearer`, 401 → logout, `apiUrl`-derived base URL).
- `util/Domain.kt` — host extraction for the slip footer.
- `ui/` — `HomeScreen` (scanning → loading → detail → QR/print state machine),
  `LoginScreen` (+ `QrCaptureActivity` portrait scanner), `SettingsScreen`,
  `QrBitmap`, and `components/` (`FloatingCard`, `PrintTicket`, `NfcPulse`).
