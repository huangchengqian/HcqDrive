# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.SerializationKt

# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# kotlinx.serialization
-keep,includedescriptorclasses class com.hcqdrive.**$$serializer { *; }
-keepclassmembers class com.hcqdrive.** {
    *** Companion;
}
-keepclasseswithmembers class com.hcqdrive.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Apache Commons Compress
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# androidx.exifinterface
-keep class androidx.exifinterface.** { *; }
