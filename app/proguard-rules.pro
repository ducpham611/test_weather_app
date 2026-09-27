# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.vnweather.app.**$$serializer { *; }
-keepclassmembers class com.vnweather.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.vnweather.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit / Conscrypt
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepclassmembers,allowshrinking,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
