# ==============================================================================
# KeyGuard (Cybersecure Multilingual Android IME)
# Production R8 Full Mode & ProGuard Rules Specification (Phase 5 - Step 5.2)
# Compliance: OWASP Mobile Application Security Verification Standard (MASVS)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. AGGRESSIVE DEX OBFUSCATION & R8 FULL MODE SHRINKING
# ------------------------------------------------------------------------------
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# Repackage all non-kept classes into a single internal package
-repackageclasses 'com.keyguard.ime.internal'

# ------------------------------------------------------------------------------
# 2. INFORMATION LEAK PREVENTION (OWASP MASVS)
# Strip source line numbers, source file attributes, and local variable tables
# ------------------------------------------------------------------------------
-renamesourcefileattribute ''
# Intentionally exclude SourceFile, LineNumberTable, and LocalVariableTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip all android.util.Log logging calls (v, d, i, w, e) in production release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
}

# ------------------------------------------------------------------------------
# 3. JNI & NATIVE SYMBOL PRESERVATION (libkeyguard_audio.so)
# Invariant: Strictly seal JNI boundary; keep only required JNI entry points
# ------------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve KeyGuard native audio bridge JNI entry points mapped to C++ Oboe mixer
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

# Sherpa-ONNX offline speech engine JNI bindings
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ------------------------------------------------------------------------------
# 4. HARDWARE-ENCRYPTED SQLCIPHER VAULT & ROOM ORM
# ------------------------------------------------------------------------------
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration

# ------------------------------------------------------------------------------
# 5. ANDROID OS SYSTEM CONTRACTS & ENTRY POINTS
# ------------------------------------------------------------------------------
-keep class com.keyguard.ime.KeyGuardService {
    public <init>();
}

-keep class com.keyguard.ime.permissions.PermissionTrampolineActivity {
    public <init>();
}

# ------------------------------------------------------------------------------
# 6. JETPACK COMPOSE RUNTIME PRESERVATION
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# ------------------------------------------------------------------------------
# 7. ISOLATED TRANSLATION CLIENT (RETROFIT 2, OKHTTP 4, GSON)
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
# 8. OBFUSCATION OF INTERNAL SECURITY & DLP SANITIZER LAYERS
# Invariant: No blanket keep rules for com.keyguard.ime.**
# Internal cryptographic, auditing, and DLP modules must undergo maximum
# identifier mangling and class flattening into 'com.keyguard.ime.internal'.
# ------------------------------------------------------------------------------
-keepclassmembers,allowobfuscation,allowshrinking,allowoptimization class com.keyguard.ime.security.DlpSanitizer { *; }
-keepclassmembers,allowobfuscation,allowshrinking,allowoptimization class com.keyguard.ime.security.SecurityAuditor { *; }
-keepclassmembers,allowobfuscation,allowshrinking,allowoptimization class com.keyguard.ime.security.KeyStoreManager { *; }
-keepclassmembers,allowobfuscation,allowshrinking,allowoptimization class com.keyguard.ime.clipboard.EphemeralClipboardBuffer { *; }
-keepclassmembers,allowobfuscation,allowshrinking,allowoptimization class com.keyguard.ime.clipboard.util.ContentClassifier { *; }
