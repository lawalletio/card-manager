# Releasing & publishing

How to cut a signed release build and publish it to
[zapstore](https://zapstore.dev/docs/publish).

## 1. Signing key (one-time)

A release keystore signs every build. The **same key must sign every future
update**, and zapstore links its certificate to your Nostr identity — so back it
up and never lose it.

This repo is already wired for signing via `keystore.properties` (gitignored):

```properties
storeFile=release.keystore
storePassword=…
keyAlias=lawallet
keyPassword=…
```

`app/build.gradle.kts` reads it and signs the `release` build type. If
`keystore.properties` is missing, the release build falls back to the debug key
(so a fresh checkout still builds, but that APK is not publishable).

> Current `release.keystore` cert SHA-256:
> `96:E0:2E:34:05:78:51:3F:96:9A:A1:C0:13:CE:17:23:A3:B7:0E:32:BF:15:61:C8:2B:5A:04:07:7F:10:D3:84`
>
> ⚠️ Back up `release.keystore` + the passwords somewhere safe. To create your
> own instead:
> ```bash
> keytool -genkeypair -v -keystore release.keystore -alias lawallet \
>   -keyalg RSA -keysize 2048 -validity 10000
> ```

## 2. Build the signed release APK

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # JDK 17+
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk  (~3.7 MB)
```

The release build is optimized: **R8** minify + obfuscation, **resource
shrinking**, the dependency-metadata blob stripped, and Compose **baseline
profiles** merged in for faster startup. ProGuard keep rules live in
`app/proguard-rules.pro` (ZCS SDK / JNI, ZXing views, enums).

Verify the signature:

```bash
"$HOME/Library/Android/sdk/build-tools/35.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Install on the device (uninstall a debug build first — different signature):

```bash
adb uninstall io.lawallet.cardactivation
adb install app/build/outputs/apk/release/app-release.apk
```

## 3. Bumping versions for updates

In `app/build.gradle.kts` raise both before each release:

```kotlin
versionCode = 2          // must increase every release
versionName = "1.0.1"
```

## 4. Publish to zapstore

Install the `zsp` CLI:

```bash
go install github.com/zapstore/zsp@latest   # or grab a binary from the zsp releases
```

Set your Nostr `pubkey` (npub) in [`zapstore.yaml`](zapstore.yaml), then publish
(the private key signs the Nostr event — never commit it):

```bash
SIGN_WITH=nsec1...  zsp publish
# first time, the interactive wizard is handy:
SIGN_WITH=nsec1...  zsp publish --wizard
```

`SIGN_WITH` accepts an `nsec…`, a hex key, a `bunker://…` (NIP-46), or `browser`
(NIP-07 extension).

**First publish only:** link the APK signing certificate (`release.keystore`) to
your Nostr identity when prompted (NIP-C1 proof), and commit `zapstore.yaml` so
the relay can verify + whitelist you.

The APK is taken from `release_source` in `zapstore.yaml` (the local release APK
by default — switch it to a GitHub release asset or HTTPS URL if you prefer).
