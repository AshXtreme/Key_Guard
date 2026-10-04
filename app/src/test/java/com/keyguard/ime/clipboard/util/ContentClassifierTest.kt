package com.keyguard.ime.clipboard.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ContentClassifierTest
 *
 * Verifies Invariant 1: Zero Cloud / Local-Only NLP classification
 * for Code, Email, URL, Phone number, and Plain text entities.
 */
class ContentClassifierTest {

    @Test
    fun testCodeSnippetDetection() {
        val gitCommand = "git commit -m 'fix oboe latency'"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(gitCommand))

        val gitCheckout = "git checkout -b feature/stitch-ribbon"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(gitCheckout))

        val jsFunction = "function processKeys() { return true; }"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(jsFunction))

        val kotlinClass = "class KeyStoreManager"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(kotlinClass))

        val importStmt = "import androidx.compose.material3.Text"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(importStmt))

        val constDecl = "const val MAX_CAPACITY = 50"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(constDecl))

        val cppInclude = "#include <oboe/Oboe.h>\nauto stream = builder.openStream();"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(cppInclude))

        val htmlTag = "<div class=\"vault-chip\"><span /></div>"
        assertEquals(ClassifiedContentType.CODE_SNIPPET, ContentClassifier.classify(htmlTag))
    }

    @Test
    fun testEmailDetection() {
        val standardEmail = "security@keyguard.io"
        assertEquals(ClassifiedContentType.EMAIL, ContentClassifier.classify(standardEmail))

        val enterpriseEmail = "john.doe@enterprise.corp.com"
        assertEquals(ClassifiedContentType.EMAIL, ContentClassifier.classify(enterpriseEmail))

        val plusEmail = "audit+ime@secure-vault.org"
        assertEquals(ClassifiedContentType.EMAIL, ContentClassifier.classify(plusEmail))
    }

    @Test
    fun testUrlDetection() {
        val httpsUrl = "https://keyguard.dev/spec"
        assertEquals(ClassifiedContentType.URL, ContentClassifier.classify(httpsUrl))

        val httpUrl = "http://192.168.1.1:8080/dashboard"
        assertEquals(ClassifiedContentType.URL, ContentClassifier.classify(httpUrl))

        val wwwUrl = "www.github.com/AshXtreme/Key_Guard"
        assertEquals(ClassifiedContentType.URL, ContentClassifier.classify(wwwUrl))
    }

    @Test
    fun testPhoneNumberDetection() {
        val usPhone = "+1-555-867-5309"
        assertEquals(ClassifiedContentType.PHONE_NUMBER, ContentClassifier.classify(usPhone))

        val intlPhone = "+91 98765 43210"
        assertEquals(ClassifiedContentType.PHONE_NUMBER, ContentClassifier.classify(intlPhone))

        val parensPhone = "(415) 555-0199"
        assertEquals(ClassifiedContentType.PHONE_NUMBER, ContentClassifier.classify(parensPhone))
    }

    @Test
    fun testPlainTextFallback() {
        val message = "Zero-telemetry mechanical keyboard input layer"
        assertEquals(ClassifiedContentType.PLAIN_TEXT, ContentClassifier.classify(message))

        val shortWords = "Meeting tomorrow at 3pm"
        assertEquals(ClassifiedContentType.PLAIN_TEXT, ContentClassifier.classify(shortWords))

        val emptyText = ""
        assertEquals(ClassifiedContentType.PLAIN_TEXT, ContentClassifier.classify(emptyText))

        val blankText = "   "
        assertEquals(ClassifiedContentType.PLAIN_TEXT, ContentClassifier.classify(blankText))
    }
}
