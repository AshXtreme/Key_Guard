package com.keyguard.ime.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * SecurityAuditReport
 *
 * Immutable data structure containing the comprehensive outcome of client-side
 * anti-tamper, root detection, and anti-debug inspections (OWASP MASVS-RESILIENCE).
 */
data class SecurityAuditReport(
    val isRooted: Boolean,
    val isDebuggerAttached: Boolean,
    val isDebuggableBuild: Boolean,
    val detectedRootIndicators: List<String> = emptyList(),
    val detectedDebugIndicators: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Flag indicating whether the host execution environment is compromised
     * by either active root privilege or debugger interception.
     */
    val isEnvironmentCompromised: Boolean
        get() = isRooted || isDebuggerAttached
}

/**
 * SecurityAuditor
 *
 * Lightweight, non-crashing anti-tamper security inspection module.
 *
 * Enforces OWASP MASVS (v2 MASVS-RESILIENCE) compliance:
 *  1. Root Detection:
 *     - Inspects well-known 'su' binary filesystem paths
 *     - Detects modern Magisk, KernelSU, and APatch framework directories
 *     - Scans for custom firmware 'test-keys' build tags
 *     - Identifies installed root manager package signatures
 *  2. Anti-Debug & Anti-Instrumentation:
 *     - Inspects Linux kernel /proc/self/status TracerPid (ptrace detection)
 *     - Queries Android Framework Debug.isDebuggerConnected() and waitingForDebugger()
 *     - Checks ApplicationInfo.FLAG_DEBUGGABLE manifest flags
 *
 * Invariant:
 *  All inspections are non-crashing, execute strictly in-memory, and return structured
 *  verifiable reports without destabilizing the IME process.
 */
object SecurityAuditor {

    private const val TAG = "SecurityAuditor"

    // Common SU binary candidate locations across Android distributions
    val KNOWN_SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/system/usr/we-need-root/su",
        "/system/xbin/daemonsu",
        "/su/bin/su"
    )

    // Known root framework signatures (Magisk, KernelSU, APatch)
    val KNOWN_ROOT_FRAMEWORK_PATHS = listOf(
        "/sbin/.magisk",
        "/system/addon.d/99-magisk.sh",
        "/data/adb/magisk",
        "/data/adb/ksu",
        "/data/adb/apatch",
        "/data/adb/modules"
    )

    // Known root manager and hooking package identifiers
    val KNOWN_ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "io.github.a13e300.ksu",
        "org.meowcat.edxposed.manager",
        "de.robv.android.xposed.installer",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "eu.chainfire.supersu"
    )

    /**
     * Performs a full non-crashing security audit of the runtime environment.
     *
     * @param context Optional Android Context for package manager and application flag inspection.
     * @return [SecurityAuditReport] detailing findings.
     */
    fun performAudit(context: Context? = null): SecurityAuditReport {
        val rootIndicators = mutableListOf<String>()
        val debugIndicators = mutableListOf<String>()

        try {
            // 1. Check for SU binaries
            val suPath = findSuBinary()
            if (suPath != null) {
                rootIndicators.add("SU_BINARY_FOUND: $suPath")
            }

            // 2. Check for Magisk / KernelSU / APatch directories
            val frameworkPath = findRootFrameworkPath()
            if (frameworkPath != null) {
                rootIndicators.add("ROOT_FRAMEWORK_FOUND: $frameworkPath")
            }

            // 3. Check for OS test-keys build tags
            if (isTestKeysBuild()) {
                rootIndicators.add("TEST_KEYS_BUILD_TAG")
            }

            // 4. Check for known root manager packages (if context provided)
            context?.let { ctx ->
                val rootPkg = findInstalledRootPackage(ctx)
                if (rootPkg != null) {
                    rootIndicators.add("ROOT_PACKAGE_INSTALLED: $rootPkg")
                }
            }

            // 5. Ptrace debugger check (/proc/self/status TracerPid > 0)
            val tracerPid = readTracerPid()
            if (tracerPid > 0) {
                debugIndicators.add("PTRACE_TRACER_ATTACHED: TracerPid=$tracerPid")
            }

            // 6. Android framework debugger check
            if (isDebuggerConnected()) {
                debugIndicators.add("FRAMEWORK_DEBUGGER_CONNECTED")
            }

            // 7. Check if application has android:debuggable enabled
            val isDebuggable = isAppDebuggable(context)
            if (isDebuggable) {
                debugIndicators.add("FLAG_DEBUGGABLE_ENABLED")
            }

        } catch (e: Throwable) {
            safeLogE("Error executing security audit check: ${e.message}")
        }

        val isRooted = rootIndicators.isNotEmpty()
        val isDebuggerAttached = debugIndicators.any { it.startsWith("PTRACE_") || it == "FRAMEWORK_DEBUGGER_CONNECTED" }
        val isDebuggableBuild = debugIndicators.contains("FLAG_DEBUGGABLE_ENABLED")

        if (isRooted || isDebuggerAttached) {
            safeLogW("Security audit detected environment anomalies: Rooted=$isRooted, Debugger=$isDebuggerAttached")
        } else {
            safeLogI("Security audit clean. Zero root or debugger anomalies detected.")
        }

        return SecurityAuditReport(
            isRooted = isRooted,
            isDebuggerAttached = isDebuggerAttached,
            isDebuggableBuild = isDebuggableBuild,
            detectedRootIndicators = rootIndicators,
            detectedDebugIndicators = debugIndicators
        )
    }

    /**
     * Checks if any common 'su' binary exists on the filesystem.
     *
     * @param customPaths Optional list of paths for testing overrides.
     * @return Path of detected binary or null if none found.
     */
    fun findSuBinary(customPaths: List<String> = KNOWN_SU_PATHS): String? {
        return try {
            for (path in customPaths) {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    return path
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks for modern root framework directories (Magisk, KernelSU, APatch).
     *
     * @param customPaths Optional list of paths for testing overrides.
     * @return Path of detected framework directory or null.
     */
    fun findRootFrameworkPath(customPaths: List<String> = KNOWN_ROOT_FRAMEWORK_PATHS): String? {
        return try {
            for (path in customPaths) {
                val file = File(path)
                if (file.exists()) {
                    return path
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Evaluates Android Build.TAGS for custom or uncertified ROM builds ('test-keys').
     *
     * @param tagsOverride Optional string for unit test verification.
     */
    fun isTestKeysBuild(tagsOverride: String? = null): Boolean {
        val tags = tagsOverride ?: Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    /**
     * Checks if any known root manager or hooking package is installed on the device.
     */
    fun findInstalledRootPackage(
        context: Context,
        packagesToCheck: List<String> = KNOWN_ROOT_PACKAGES
    ): String? {
        val pm = context.packageManager ?: return null
        for (pkg in packagesToCheck) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // Expected when package is not installed
            } catch (e: Exception) {
                safeLogW("Error checking package $pkg: ${e.message}")
            }
        }
        return null
    }

    /**
     * Inspects /proc/self/status for ptrace debugger attachment.
     *
     * When a debugger (GDB, LLDB, Frida, ptrace) attaches to an Android process,
     * the kernel sets 'TracerPid' in /proc/self/status to the PID of the tracing process.
     * Under normal execution, TracerPid is 0.
     *
     * @param statusReader Optional lambda to supply lines for unit testing.
     * @return The TracerPid integer (0 if no debugger attached, >0 if tracing).
     */
    fun readTracerPid(statusReader: (() -> List<String>)? = null): Int {
        return try {
            val lines = if (statusReader != null) {
                statusReader()
            } else {
                val statusFile = File("/proc/self/status")
                if (!statusFile.exists() || !statusFile.canRead()) {
                    return 0
                }
                BufferedReader(FileReader(statusFile)).use { reader ->
                    reader.readLines()
                }
            }

            for (line in lines) {
                if (line.startsWith("TracerPid:", ignoreCase = true)) {
                    val parts = line.split(":")
                    if (parts.size >= 2) {
                        return parts[1].trim().toIntOrNull() ?: 0
                    }
                }
            }
            0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Checks if an Android framework debugger is actively connected or waiting.
     */
    fun isDebuggerConnected(): Boolean {
        return try {
            Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the application package has android:debuggable enabled in the manifest.
     */
    fun isAppDebuggable(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val flags = context.applicationInfo.flags
            (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun safeLogI(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {}
    }

    private fun safeLogW(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {}
    }

    private fun safeLogE(message: String) {
        try {
            Log.e(TAG, message)
        } catch (_: Throwable) {}
    }
}
