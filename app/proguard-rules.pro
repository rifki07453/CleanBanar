# ============================================================
# CleanBanar ProGuard Rules
# ============================================================

# Sembunyikan informasi source file (tidak perlu untuk debug)
-renamesourcefileattribute SourceFile

# Jaga nama class Kotlin agar reflection tetap berfungsi
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# Firebase Realtime Database — jaga model data agar tidak ter-obfuscate
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Auth
-keepclassmembers class com.google.firebase.auth.** { *; }

# Jaga semua data class di package core.data
-keep class com.example.cleanbanar.core.data.** { *; }

# Jaga enum agar tidak ter-strip
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Android Architecture Component — ViewModel & LiveData
-keep class androidx.lifecycle.** { *; }

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Jaga fragment binding (ViewBinding)
-keep class com.example.cleanbanar.databinding.** { *; }

# Jaga activity dan fragment agar tidak ter-rename
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# Hapus log di release build (keamanan: tidak ada info bocor ke logcat)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}