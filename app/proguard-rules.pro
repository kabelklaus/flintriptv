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

# Preserve line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep attributes needed for Retrofit's generic type reflection
# Signature is CRITICAL - Retrofit needs this to extract generic type parameters (Response<T>)
-keepattributes Signature
-keepattributes Exceptions
# Runtime annotations are needed for Retrofit's @GET, @POST, @Query annotations
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit: Keep service interfaces with their method signatures
# The interfaces must retain generic type information for Retrofit's reflection
# Using -keepclassmembers ensures methods (including suspend functions) are preserved
-keep interface com.flintr.tv.data.api.** { *; }
-keepclassmembers interface com.flintr.tv.data.api.** {
    <methods>;
}

# Moshi: Keep data model classes used by Retrofit/Moshi
# All models in com.flintr.tv.data.model package need to be preserved
# Including interfaces in case any models implement interfaces
-keep class com.flintr.tv.data.model.** { *; }
-keep interface com.flintr.tv.data.model.** { *; }

# Moshi: Keep custom adapter and factory (uses reflection)
-keep class com.flintr.tv.data.model.SeriesInfoResponseAdapter { *; }
-keep class com.flintr.tv.data.model.SeriesInfoResponseAdapterFactory { *; }

# Moshi: Keep all generated JsonAdapters
-keep class com.flintr.tv.data.model.*JsonAdapter { *; }

# Moshi: Keep reflection classes needed for custom SeriesInfoResponseAdapterFactory
# The factory uses Types.newParameterizedType() and moshi.adapter() at runtime
-keep class com.squareup.moshi.Types { *; }
-keep class com.squareup.moshi.Moshi { *; }
-keep class com.squareup.moshi.JsonAdapter$Factory { *; }
-keep @com.squareup.moshi.JsonQualifier interface *

# Retrofit: Suppress warnings (Retrofit handles its own obfuscation)
-dontwarn retrofit2.**

# Moshi: Keep generic signatures for Kotlin data classes
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Strip verbose/info/debug logging from release builds (konzept.md 1.4).
# Keep Log.w/e/wtf so error diagnostics survive in Crashlytics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}