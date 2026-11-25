# Ryzn SA-MP R8 / ProGuard Rules

-optimizationpasses 2
-allowaccessmodification
-repackageclasses 'o'

# Native & Engine Entry Points (JNI callbacks from libGTASA.so & libryzn_samp.so)
-keep class com.nvidia.devtech.** { *; }
-keep class com.wardrumstudios.** { *; }
-keep class com.rockstargames.** { *; }
-keep class com.ryzn.game.** { *; }

# Keep native methods everywhere
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Launcher models / Gson / XML reflection
-keep class com.ryzn.launcher.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.stream.** { *; }

# Third-party transport / INI libraries
-dontwarn retrofit.**
-keep class retrofit.** { *; }
-keepattributes Signature,Exceptions,*Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn java.nio.file.*
-dontwarn org.ini4j.**
-keep class org.ini4j.** { *; }
