#
# Ryzn SA-MP ProGuard Rules
#

-dontoptimize
-keep public class com.nvidia.devtech.* { *; }
-keep public class com.wardrumstudios.utils.* { *; }
-keep public class com.ryzn.game.gui.* { *; }
-keep public class com.ryzn.game.gui.util.* { *; }
-keep public class com.ryzn.game.core.* { *; }
-keep public class com.ryzn.launcher.activity.* { *; }
-keep public class com.ryzn.launcher.adapter.* { *; }
-keep public class com.ryzn.launcher.fragment.* { *; }
-keep public class com.ryzn.launcher.model.* { *; }
-keep public class com.ryzn.launcher.other.* { *; }
-keep public class com.ryzn.launcher.Registration.* { *; }

-keep class com.google.gson.stream.** { *; }

-dontwarn retrofit.**
-keep class retrofit.** { *; }
-keepattributes Signature
-keepattributes Exceptions

-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn java.nio.file.*
