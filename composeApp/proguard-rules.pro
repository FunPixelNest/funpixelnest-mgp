# R8 rules for the release build (code shrinking + resource shrinking).
# Libraries (Unity, kotlinx.serialization, Ktor, Supabase, Firebase) bring their own rules.

# Native Unity games: the Unity SDK calls FpnUnityBridge by name through JNI, and HybridGameLauncher
# creates UnityGameLauncher by name (Class.forName) so the app also builds without the Unity library.
-keep class com.funpixelnest.mgp.unity.** { *; }

# WebGL games call the app through window.FPNNative (@JavascriptInterface methods).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Readable crash reports (CrashReport "Copy details").
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optional dependencies referenced by Ktor / OkHttp / Supabase that aren't used on Android.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.utils.io.jvm.nio.**
