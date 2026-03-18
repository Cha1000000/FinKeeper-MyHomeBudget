# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class ru.homebudget.finkeeper.**$$serializer { *; }
-keepclassmembers class ru.homebudget.finkeeper.** {
    *** Companion;
}
-keepclasseswithmembers class ru.homebudget.finkeeper.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-dontwarn com.google.errorprone.annotations.**
