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
-keepattributes Signature
-keepattributes Exceptions

# Retrofit: Keep service interfaces and their generic type information
-keep interface com.flintr.tv.data.api.GitHubApiService { *; }
-keep interface com.flintr.tv.data.api.XtreamApiService { *; }

# Retrofit: Keep Response class and its generic type parameters
-keep class retrofit2.Response { *; }
-keep class retrofit2.Response$* { *; }

# Retrofit: Keep Call and its generic type parameters
-keep interface retrofit2.Call { *; }
-keep class retrofit2.Call$* { *; }

# Retrofit: Keep all Retrofit annotations
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keep class retrofit2.http.** { *; }

# Moshi: Keep data model classes used by Retrofit/Moshi
# All models in com.flintr.tv.data.model package need to be preserved
-keep class com.flintr.tv.data.model.** { *; }

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

# Moshi: Keep generic signatures for Kotlin data classes
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}