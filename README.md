# LaWallet Card Manager

Native **Kotlin + Jetpack Compose** Android app to **activate LaWallet NFC cards**
on the **ZCS Z92** POS terminal. Sign in with a device-token QR, tap a card, and it
mints a one-time **activation QR** that prints on the built-in thermal printer for
the end user to scan and claim the card. For an owner who lost their key, tapping
their card mints a one-time **recovery QR** instead. Reuses the **ZCS SmartPos**
printer driver and **NFC** stack from `../android-pos-wrapper`.

## Features

- **JWT login** — scan (or paste) a device-token QR; the token's `apiUrl` claim
  picks the instance, every call is `Bearer`-authenticated, a 401 logs out.
- **Live activation** — tap → `GET /api/cards/:id` → real status / lightning
  address / design, then mint `POST …/activation-tokens` → print the QR.
- **Account recovery** — an owned card shows its owner (primary + other lightning
  addresses, wallet balance) and a **RECOVER** button. It mints a ONE_TIME
  `…?tag=recover` QR from the tap's SUN proof. Whoever scans it with a new key
  takes over the account. RECOVER is disabled, with the reason shown, when the
  owner turned recovery off, the card is paused, or there was no fresh tap.
- **Real NFC + real thermal print** on the ZCS hardware; the printed QR is the
  exact same ZXing code shown on screen.
- **Settings** — instance, pubkey, scopes, **token expiry**, an **auto-print on
  tap** toggle (mint + print automatically, unowned cards only), log out, and the
  app version.
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

**No NFC (emulator)?** Debug builds accept a simulated tap and plain-http
local servers. Grab a signed tap URL from the admin *Card emulator* (Simple
tap shows the exact `lnurlw://` the card emits), forward the port, and send it:

```bash
adb reverse tcp:3305 tcp:3305
adb shell "am start -n io.lawallet.cardactivation/.MainActivity --es sim_tap '<lnurlw://…/scan?p=…&c=…>'"
```

First launch asks for `READ_PHONE_STATE` (the ZCS SDK reads device identity during
`sdkInit`). For a **signed release** build + zapstore publishing, see
[`PUBLISHING.md`](PUBLISHING.md).

## How it works

1. **Scanning** — always armed for NFC; an animated radar pulse waits for a tap.
   ("Enter manually" pastes a full tap URL for testing — a card id alone is
   refused.) A tapped tag with no LaWallet lnurl shows a *blank card* prompt instead.
2. **Loading** — a Lottie animation while the tap is checked and the card loads.
   The id and the SUN proof (`p`/`c`) come from the tapped
   `…/api/cards/<id>/scan?p=…&c=…` URL. `POST /api/cards/:id/verify-tap` checks
   the proof the way a payment does (genuine card, unedited, counter not already
   spent) without spending it; an edited or replayed URL stops here with the
   server's reason. Then `GET /api/cards/:id`, and for an owned card
   `GET /api/cards/:id/owner`.
3. **Card detail** — a 3D **floating card** (real `design.imageUrl` via Coil) with
   the card **status** and **lightning address**; **ACTIVATE** / **Cancel**.
   With *auto-print* on, an unowned card skips this screen and prints right away.
   An owned card always stops here and shows an **Owner** block: the owner's
   primary lightning address (from the server), any other addresses, and the
   balance ("12,345 sats", or *Balance unavailable*). Its actions are **RECOVER**,
   **ACTIVATE** and **Cancel**.
4. **QR + print** — ACTIVATE mints `POST /api/cards/:id/activation-tokens`
   `{qrKind:"ONE_TIME"}`; the returned **`qrPayload`** shows as a thermal-ticket
   preview that slides up (~2 s) *while it really prints*, then returns to scanning.
   RECOVER mints `POST /api/cards/:id/recovery-tokens` `{p, c}` instead and prints
   a *Recover your account* slip. A SUN proof works once, so a failed or repeated
   recovery needs a new tap.

### Endpoints

All calls send `Authorization: Bearer <device token>` and JSON.

| Call | Purpose |
| --- | --- |
| `GET /api/cards/:id` | Card design, owner `username`/`pubkey`, `disabled` (paused). |
| `GET /api/cards/:id/owner` | Owner summary: `primaryAddress`, `lightningAddresses`, `cardRecoveryEnabled`, `balanceSats` (null if the wallet is unreachable). A 404 means an older backend, so the balance and RECOVER are hidden. |
| `POST /api/cards/:id/activation-tokens` | `{qrKind:"ONE_TIME"}` → activation `qrPayload`. |
| `POST /api/cards/:id/verify-tap` | `{p, c}` from the tap → 200 if genuine and unspent (doesn't spend it). 400 = forged or edited, 409 = already used. Needs a backend with card recovery (lawallet-nwc PR #373). |
| `POST /api/cards/:id/recovery-tokens` | `{p, c}` from the tap → `…/wallet/activate/<id>?tag=recover` `qrPayload`. Errors show the server's `error.message`: 400 = SUN invalid; 403 = owner turned recovery off; 409 = no owner, card blocked or paused, or tap already used. |

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
  authenticated OkHttp client (`Bearer`, 401 → logout, `apiUrl`-derived base URL),
  SUN (`p`/`c`) parsing, owner summary and activation/recovery minting.
- `util/Domain.kt` — host extraction for the slip footer.
- `ui/` — `HomeScreen` (scanning → loading → detail → activate/recover → QR/print
  state machine),
  `LoginScreen` (+ `QrCaptureActivity` portrait scanner), `SettingsScreen`,
  `QrBitmap`, and `components/` (`FloatingCard`, `PrintTicket`, `NfcPulse`).
