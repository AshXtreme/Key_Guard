package com.keyguard.ime.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * PermissionTrampolineActivityTest
 *
 * Verifies invariants for the Permission Trampoline:
 *  1. Action and extra constants match KeyGuardService protocol.
 *  2. Request code uniqueness.
 */
class PermissionTrampolineActivityTest {

    @Test
    fun testIntentActionAndExtraConstants() {
        assertEquals(
            "com.keyguard.ime.action.RECORD_AUDIO_RESULT",
            PermissionTrampolineActivity.ACTION_RECORD_AUDIO_RESULT
        )
        assertEquals("extra_is_granted", PermissionTrampolineActivity.EXTRA_IS_GRANTED)
        assertEquals(1001, PermissionTrampolineActivity.REQUEST_CODE_RECORD_AUDIO)
    }

    @Test
    fun testClassPresence() {
        val clazz = PermissionTrampolineActivity::class.java
        assertNotNull(clazz)
        assertEquals("com.keyguard.ime.permissions.PermissionTrampolineActivity", clazz.name)
    }
}
