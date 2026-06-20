# LaWallet Card Activation — native Android (ZCS Z92 / CS30Pro)

Native **Kotlin + Jetpack Compose** rewrite of the card-activation app for the
**ZCS Z92** POS terminal. Tap a LaWallet card → mint a one-time activation code
→ show/print the activation QR. Reuses the **ZCS SmartPos printer driver** and
**NFC** from `../android-pos-wrapper`.

## Toolchain

AGP 8.7 · Gradle 8.11 · Kotlin 2.0 · compileSdk 35 · minSdk 24 · targetSdk 35.
Gradle needs **JDK 17+** (the machine default may be older), e.g. Android
Studio's bundled JBR.

## Build / install / run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug      # build the APK
./gradlew :app:installDebug       # install on the connected Z92 (adb)
adb shell am start -n io.lawallet.cardactivation/.MainActivity
```

The first launch asks for `READ_PHONE_STATE` (the ZCS SDK reads device identity
during `sdkInit`). Pre-grant for automated runs:
`adb shell pm grant io.lawallet.cardactivation android.permission.READ_PHONE_STATE`.

## Login + flow (live endpoints, real NFC + real print)

**Sign in** by scanning a **device-token QR** (a bare JWT; its optional `apiUrl`
claim sets the instance URL, else `https://beta.lawallet.io`). The token is saved
and every call sends `Authorization: Bearer <jwt>`; a 401 logs out. A paste
fallback is available. Mirrors `card-installer`'s LaWallet provider.

1. **Scanning** — always armed for NFC; an animated radar/NFC pulse waits for a
   tap. ("Ingresar manualmente" lets you paste a card URL/id for testing.)
2. **Loading** — a Lottie animation (`res/raw/loading.json`) while
   `GET /api/cards/:id` runs (the id is parsed from the tapped
   `…/api/cards/<id>/scan` URL).
3. **Card detail** — a 3D **floating card** (real `design.imageUrl` via Coil)
   with the card **status** and **lightning address**; **ACTIVAR** / **Cancelar**.
4. **QR + print** — ACTIVAR mints `POST /api/cards/:id/activation-tokens`
   `{qrKind:"ONE_TIME"}`; the returned **`qrPayload`** is shown as a thermal-ticket
   preview that slides up *while it really prints* on the ZCS printer (~2.7s feed),
   then returns to scanning. The end user scans that QR to claim the card.

Auth + API live in `data/{Jwt,AuthStore,Api}.kt`; login UI in `ui/LoginScreen.kt`
(ZXing camera scan). Testing the full flow needs a valid device-token JWT and a
registered card on a reachable instance.

Extra deps: `com.airbnb.android:lottie-compose` (loading), `io.coil-kt:coil-compose`
(card image), `com.journeyapps:zxing-android-embedded` (QR scan).

## Look & feel

Themed after **lawallet.io**: near-black `#0A0A0A` canvas with a soft teal glow,
the emerald-teal brand gradient `#00836D → #00A085 → #00C7A0` on buttons (with a
teal glow), and **Inter** typography (`res/font/inter_variable.ttf`). The card
detail shows a large image that floats and rotates in real 3D perspective; the
print step shows the actual **thermal-ticket preview** (full width − a 60px side
gutter) that slides up (~2.7s) as it really prints, while the physical print fires
immediately. Theme + brand helpers live in `ui/theme/{Theme,Brand}.kt`.

## Drivers (reused vendor binaries)

- `app/libs/SmartPos_1.9.4_R250117.jar` — ZCS SmartPos SDK (`com.zcs.sdk.*`).
- `app/src/main/jniLibs/{armeabi-v7a,arm64-v8a}/libSmartPosJni.so, libEmvCoreJni.so`
  — **required** native libs; without them `Sys.sdkInit()` NPEs (the JNI bridge
  can't load). The Z92 is 32-bit, so `armeabi-v7a` is the one it uses.

## Architecture

- `pos/ZcsPrinter.kt` — SDK init + `printTest()` / `printActivation(url)` via
  `DriverManager.getInstance().getPrinter()` → `setPrintAppendString` /
  `setPrintAppendBitmap` (prints the same `QrBitmap` ZXing QR shown on screen, so
  the on-screen and printed codes match) / `setPrintStart()`, on `Dispatchers.IO`.
- `pos/CardNfc.kt` — NDEF parse (URI + Text records) → `lnurlw://…/scan?p=…&c=…`.
- `MainActivity.kt` — Compose host + NFC foreground dispatch (`onNewIntent`),
  emitting card URLs to a `SharedFlow`.
- `data/{Jwt,AuthStore,Api}.kt` — JWT decode + token store + the authenticated
  OkHttp client (`GET /api/cards/:id`, `POST …/activation-tokens`); `Bearer` auth,
  401 → logout, `apiUrl`-derived base URL.
- `util/{Domain,…}.kt` — small helpers (e.g. `cardDomain` for the slip footer).
- `ui/` — `HomeScreen` (scanning → loading → detail → QR/print state machine),
  `LoginScreen` (+ `QrCaptureActivity` portrait QR scanner), `QrBitmap` (ZXing),
  and `components/` (`FloatingCard`, `PrintTicket`, `NfcPulse`, `LottieLoader`).

## Verified on-device (ZCS Z92, Android 13)

Build → install → **PRINT** prints a real slip with a QR (`setPrintStart`
returns `SDK_OK`, UI shows "Printed ✓"). The activation flow needs a physical
card tap (and the instance reachable over the network) to complete end-to-end.
