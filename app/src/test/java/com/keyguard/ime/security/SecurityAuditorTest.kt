package com.keyguard.ime.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * SecurityAuditorTest
 *
 * Comprehensive unit test suite for SecurityAuditor anti-tamper and anti-debug inspection module:
 *  1. Anti-Tamper & Root Detection (SU binaries, root frameworks, test-keys build tags).
 *  2. Anti-Debug Inspection (/proc/self/status TracerPid, ptrace tracer detection).
 *  3. Non-crashing resilience and SecurityAuditReport state model.
 */
class SecurityAuditorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // 1. TEST-KEYS BUILD TAG INSPECTION
    // =========================================================================

    @Test
    fun `isTestKeysBuild returns true when tags contains test-keys`() {
        assertTrue(SecurityAuditor.isTestKeysBuild("test-keys"))
        assertTrue(SecurityAuditor.isTestKeysBuild("custom-rom,test-keys,dev"))
    }

    @Test
    fun `isTestKeysBuild returns false for production release-keys or null`() {
        assertFalse(SecurityAuditor.isTestKeysBuild("release-keys"))
        assertFalse(SecurityAuditor.isTestKeysBuild("user,release-keys"))
        assertFalse(SecurityAuditor.isTestKeysBuild(null))
    }

    // =========================================================================
    // 2. PTRACE & TRACERPID ANTI-DEBUGGING INSPECTION
    // =========================================================================

    @Test
    fun `readTracerPid returns zero when no debugger is attached`() {
        val normalProcStatus = listOf(
            "Name:\tcom.keyguard.ime",
            "State:\tS (sleeping)",
            "Tgid:\t1234",
            "Pid:\t1234",
            "PPid:\t567",
            "TracerPid:\t0",
            "Uid:\t10001\t10001\t10001\t10001"
        )

        val tracerPid = SecurityAuditor.readTracerPid { normalProcStatus }
        assertEquals(0, tracerPid)
    }

    @Test
    fun `readTracerPid detects active ptrace debugger attachment`() {
        // GDB/Frida/LLDB actively tracing PID 4321
        val debuggerProcStatus = listOf(
            "Name:\tcom.keyguard.ime",
            "State:\tt (tracing stop)",
            "Tgid:\t1234",
            "Pid:\t1234",
            "PPid:\t567",
            "TracerPid:\t4321",
            "Uid:\t10001\t10001\t10001\t10001"
        )

        val tracerPid = SecurityAuditor.readTracerPid { debuggerProcStatus }
        assertEquals(4321, tracerPid)
    }

    @Test
    fun `readTracerPid handles missing or malformed status entries safely`() {
        assertEquals(0, SecurityAuditor.readTracerPid { emptyList() })
        assertEquals(0, SecurityAuditor.readTracerPid { listOf("TracerPid: not-a-number") })
        assertEquals(0, SecurityAuditor.readTracerPid { listOf("UnrelatedField:\t123") })
    }

    // =========================================================================
    // 3. SU BINARY & ROOT FRAMEWORK INSPECTION
    // =========================================================================

    @Test
    fun `findSuBinary returns null when candidate paths do not exist`() {
        val nonExistentPaths = listOf(
            "/non_existent_dir/su",
            "/another_fake_dir/su"
        )

        val detected = SecurityAuditor.findSuBinary(nonExistentPaths)
        assertNull(detected)
    }

    @Test
    fun `findSuBinary detects executable binary when present`() {
        val fakeBinDir = tempFolder.newFolder("bin")
        val fakeSu = File(fakeBinDir, "su").apply {
            createNewFile()
            setExecutable(true)
        }

        val detected = SecurityAuditor.findSuBinary(listOf(fakeSu.absolutePath))
        assertEquals(fakeSu.absolutePath, detected)
    }

    @Test
    fun `findRootFrameworkPath detects Magisk or KernelSU directories`() {
        val fakeAdbDir = tempFolder.newFolder("adb")
        val fakeMagiskDir = File(fakeAdbDir, "magisk").apply {
            mkdir()
        }

        val detected = SecurityAuditor.findRootFrameworkPath(listOf(fakeMagiskDir.absolutePath))
        assertEquals(fakeMagiskDir.absolutePath, detected)
    }

    @Test
    fun `findRootFrameworkPath returns null when directories are absent`() {
        val absentPaths = listOf(
            "/non_existent/magisk",
            "/non_existent/ksu"
        )

        val detected = SecurityAuditor.findRootFrameworkPath(absentPaths)
        assertNull(detected)
    }

    // =========================================================================
    // 4. SECURITY AUDIT REPORT MODEL INTEGRITY
    // =========================================================================

    @Test
    fun `SecurityAuditReport correctly flags environment compromise`() {
        val cleanReport = SecurityAuditReport(
            isRooted = false,
            isDebuggerAttached = false,
            isDebuggableBuild = false
        )
        assertFalse(cleanReport.isEnvironmentCompromised)

        val rootedReport = SecurityAuditReport(
            isRooted = true,
            isDebuggerAttached = false,
            isDebuggableBuild = false,
            detectedRootIndicators = listOf("SU_BINARY_FOUND")
        )
        assertTrue(rootedReport.isEnvironmentCompromised)

        val debugReport = SecurityAuditReport(
            isRooted = false,
            isDebuggerAttached = true,
            isDebuggableBuild = false,
            detectedDebugIndicators = listOf("PTRACE_TRACER_ATTACHED")
        )
        assertTrue(debugReport.isEnvironmentCompromised)
    }

    @Test
    fun `performAudit runs without crashing and produces valid report`() {
        val report = SecurityAuditor.performAudit(null)
        assertNotNull(report)
        assertTrue(report.timestampMs > 0)
    }
}

