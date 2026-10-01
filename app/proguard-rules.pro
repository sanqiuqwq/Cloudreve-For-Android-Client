-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.cra.cloudreve.**$$serializer { *; }
-keepclassmembers class com.cra.cloudreve.** {
    *** Companion;
}
-keepclasseswithmembers class com.cra.cloudreve.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**