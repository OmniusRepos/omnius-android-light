# libtorrent4j
-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class lol.omnius.android.data.model.** { *; }
-keepclassmembers class lol.omnius.android.data.model.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keepclassmembers @kotlinx.serialization.Serializable class lol.omnius.android.data.model.** {
    *** Companion;
    *** serializer(...);
}
