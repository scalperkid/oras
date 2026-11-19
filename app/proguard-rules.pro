-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.jefflim.oras.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.jefflim.oras.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
