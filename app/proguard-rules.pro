-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp / Okio optional platform providers (not on the class path of a plain Android app)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 loads the HLS / DASH sources by reflection: R8 must not strip or rename them
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
