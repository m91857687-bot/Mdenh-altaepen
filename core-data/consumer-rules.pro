# Keep kotlinx.serialization DTOs & companion serializers used by :core-data and :core-city
-keepattributes *Annotation*,InnerClasses,Signature
-keep class com.example.core.data.supabase.** { *; }
-keepclassmembers class com.example.core.data.supabase.** {
    *** Companion;
    *** serializer(...);
}
-keep class com.example.cityengine.model.** { *; }
