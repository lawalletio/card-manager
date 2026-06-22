# ── ZCS SmartPos SDK (vendored jar; JNI + reflection) ─────────────────────────
# The SDK resolves classes/methods from native code (libSmartPosJni.so), so keep
# everything under com.zcs and never rename it.
-keep class com.zcs.** { *; }
-dontwarn com.zcs.**

# Keep all JNI native methods (the bridge resolves them by name).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep enum values()/valueOf() — used reflectively (e.g. SdkResult, PrnTextFont).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── ZXing scanner (zxing-android-embedded + core) ─────────────────────────────
# DecoratedBarcodeView/BarcodeView/ViewfinderView are inflated from layout XML
# (custom_barcode_scanner.xml) by name, so they must survive R8.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Our custom scanner overlay is referenced by FQN in custom_barcode_scanner.xml.
-keep class io.lawallet.cardactivation.ui.SquareFrameView { *; }

# ── OkHttp / Okio (ship their own rules; silence optional/transitive deps) ─────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Lottie / coroutines (consumer rules bundled; just silence warnings) ────────
-dontwarn com.airbnb.lottie.**
-dontwarn kotlinx.coroutines.**

# Preserve generic signatures, inner-class info and annotations.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
