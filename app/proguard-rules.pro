# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.raviga.app.**$$serializer { *; }
-keepclassmembers class com.raviga.app.** { *** Companion; }
-keepclasseswithmembers class com.raviga.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit / OkHttp
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }

# PDFBox (on-device PDF text): optional JPEG 2000 codec is not bundled.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# Contract v0.9: no advertising id. play-services-ads-identifier is excluded on purpose; Analytics only
# references it on a path turned off by google_analytics_adid_collection_enabled=false.
-dontwarn com.google.android.gms.ads.identifier.**
