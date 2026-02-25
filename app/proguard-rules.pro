# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the default proguard-android-optimize.txt file.

# Keep data model classes intact (serialization / reflection)
-keep class com.drumtrainer.model.** { *; }

# Keep Room entity annotations
-keep class androidx.room.** { *; }
