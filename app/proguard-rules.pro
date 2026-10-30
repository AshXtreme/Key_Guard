# ==============================================================================
# KeyGuard (Cybersecure Multilingual Android IME)
# Production ProGuard / R8 Obfuscation & Shrinking Configuration
# Compliance: OWASP Mobile Application Security Verification Standard (MASVS)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. AGGRESSIVE DEX OBFUSCATION & CODE FLATTENING (OWASP MASVS-RESILIENCE)
# ------------------------------------------------------------------------------
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''

# Strip debugging identifiers and normalize source file attributes
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip all Android logging calls in release builds (Log.v, Log.d, Log.i)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 2. JNI & NATIVE SYMBOL PRESERVATION (libkeyguard_audio.so)
# ------------------------------------------------------------------------------
# Invariant: Preserve all JNI native entry points required by native C++ Oboe mixer
-keepclasseswithmembernames class * {
    native <methods>;
}

# Explicitly keep KeyGuard native audio bridge symbols and method signatures
-keep class com.keyguard.ime.audio.NativeSoundBridge {
    private <methods>;
    public <methods>;
    *;
}

-keep class com.keyguard.ime.audio.NativeAudioEngine {
    private <methods>;
    public <methods>;
    *;
}

# Google Oboe Prefab native bindings
-keep class com.google.oboe.** { *; }
-dontwarn com.google.oboe.**

# ------------------------------------------------------------------------------
# 3. ANDROID OS ENTRY POINTS & SYSTEM SERVICE CONTRACTS
# ------------------------------------------------------------------------------
# InputMethodService and Activity trampoline must retain default constructors
-keep class com.keyguard.ime.KeyGuardService {
    public <init>();
}

-keep class com.keyguard.ime.permissions.PermissionTrampolineActivity {
    public <init>();
}

# ------------------------------------------------------------------------------
# 4. ROOM ORM & HARDWARE-ENCRYPTED SQLCIPHER VAULT
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# ------------------------------------------------------------------------------
# 5. RETROFIT, OKHTTP 4, & JSON SERIALIZATION (ISOLATED TRANSLATION CLIENT)
# ------------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.keyguard.ime.translation.TranslationRequest { *; }
-keep class com.keyguard.ime.translation.TranslationResponse { *; }
-keep class com.keyguard.ime.translation.TranslationApiService { *; }

-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# ------------------------------------------------------------------------------
# 6. JETPACK COMPOSE RUNTIME PRESERVATION
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# ------------------------------------------------------------------------------
# 7. AGGRESSIVE OBFUSCATION OF INTERNAL SECURITY & CRYPTOGRAPHIC LAYERS
# Note: Internal classes (SecurityAuditor, KeyStoreManager, DlpSanitizer,
# EphemeralClipboardBuffer, ContentClassifier) are NOT preserved here, ensuring
# R8 renames all classes, fields, and internal cryptographic method signatures.
# ------------------------------------------------------------------------------
