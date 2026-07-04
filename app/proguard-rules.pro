# PJSIP / pjsua2 JNI bindings are called reflectively from native code and must
# not be renamed or stripped.
-keep class org.pjsip.** { *; }
-keepclassmembers class org.pjsip.** { *; }
-dontwarn org.pjsip.**

# Room generated code.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep our Telecom service entry points (referenced from the manifest / system).
-keep class com.magick.gsm2sip.telecom.** { *; }
