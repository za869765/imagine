# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.za869765.imagine.**$$serializer { *; }
-keepclassmembers class com.za869765.imagine.** {
    *** Companion;
}
-keepclasseswithmembers class com.za869765.imagine.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OkHttp 4
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose
-keep class androidx.compose.runtime.** { *; }

# Media3 (ExoPlayer / PlayerView 透過 AndroidView 嵌進來，R8 靜態看不到引用)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil 3 (AsyncImage 反射載入 fetcher)
-keep class coil3.** { *; }
-dontwarn coil3.**
