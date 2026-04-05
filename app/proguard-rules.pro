# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Kotlin ---
-dontwarn kotlin.**
-keepclassmembers class **\$WhenMappings { *; }

# --- OkHttp / Okio (транзитивные предупреждения) ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Retrofit 2 ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Kotlinx Serialization (Retrofit converter + модели в com.rocketlauncher.data.dto) ---
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer { *; }
-keep,includedescriptorclasses class **$serializer { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# --- Hilt / Dagger (плагин добавляет часть правил; оставляем базовые) ---
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}

# --- Jitsi Meet / React Native (часть классов из транзитивных AAR) ---
-keep class org.jitsi.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn com.facebook.react.**

# При включённом R8: убрать вызовы Log.d/v в байткоде (дополнение к AppLog в исходниках)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
