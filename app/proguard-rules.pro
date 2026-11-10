-dontoptimize
-dontobfuscate
-keepattributes *
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.**

# Preservar integralmente todas as classes do app, DTOs, ViewModels e repositórios
-keep class br.com.raevan.treinoria.** { *; }
-keep interface br.com.raevan.treinoria.** { *; }
-keep enum br.com.raevan.treinoria.** { *; }

# Preservar Kotlinx Serialization e Datetime
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.* <fields>;
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.datetime.** { *; }

# Preservar Supabase, Ktor, Settings, Biometric, Fragment e Lifecycle
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keep class com.russhwolf.settings.** { *; }
-keep class org.kotlincrypto.** { *; }
-keep class androidx.biometric.** { *; }
-keep class androidx.fragment.app.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.startup.** { *; }
