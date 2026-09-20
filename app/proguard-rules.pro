# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Added alongside enabling isMinifyEnabled for release builds ---
# (see app/build.gradle.kts) to shrink unused code/resources, including
# the many unused icons pulled in by material-icons-extended, which was
# one of the two largest contributors found when investigating why the
# APK was ~87MB (the other being onnxruntime-android bundling native
# libraries for all 4 Android ABIs -- see the ndk.abiFilters fix in
# build.gradle.kts for that one). These -keep rules are NOT verified end
# to end on a real device from this environment (no Android runtime is
# available here to build/run and confirm) -- they follow each library's
# own officially documented/commonly-required R8 rules, but please
# smoke-test a release build on a real device (in particular: opening
# and running actual OCR detection/recognition, not just launching the
# app) before shipping it, since a wrong or missing keep rule for JNI
# reflection (ONNX Runtime) or reflection-based (de)serialization (Room,
# Moshi) code is a common way minification silently breaks an app that
# otherwise runs fine unminified.

# ONNX Runtime: uses JNI to call into native code and reflection for its
# OrtException/tensor type resolution; stripping/renaming those classes
# breaks native-to-Java calls with no compile-time warning.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Room: generated DAO/database implementations and entities are
# referenced by generated code in ways R8 can't always trace; annotation
# metadata must also survive for KSP-generated code to keep working.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Moshi: uses reflection (or generated adapters) keyed off of class/field
# names, both of which R8 would otherwise be free to rename or strip.
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-dontwarn com.squareup.moshi.**

# Retrofit/OkHttp: keep annotations and generic signatures that Retrofit's
# reflection-based request building relies on.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn retrofit2.**
-dontwarn okhttp3.**
