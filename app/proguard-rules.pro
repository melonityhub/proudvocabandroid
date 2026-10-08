# ProudVocab Android — ProGuard / R8 rules
# (Release builds currently ship without minification so that a fresh clone
#  is guaranteed to build; these rules are kept for when shrinking is enabled.)

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
-keep class androidx.room.** { *; }

# kotlinx.serialization
-keepclassmembers class com.proudvocab.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.proudvocab.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.proudvocab.android.**$$serializer { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
