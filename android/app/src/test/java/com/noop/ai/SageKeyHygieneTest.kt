package com.noop.ai

import com.noop.data.BackupSettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I-05 (R10a): Sage's API key never leaves the encrypted store. It is not in the `.noopbak` settings whitelist, Android
 * backup and device transfer exclude every preference file, and a provider's error text has key-shaped tokens cut out
 * before it is shown (and so before it can reach a screenshot, a copied message or a bug report).
 */
class SageKeyHygieneTest {

    @Test
    fun providerErrorsNeverShowAKey() {
        val openAi = "Incorrect API key provided: sk-proj-********************************abcd. You can find your API key at https://platform.openai.com/account/api-keys."
        val red = AiCoach.redactSecrets(openAi)
        assertFalse(red, "sk-proj" in red)
        assertTrue(red, "[key hidden]" in red)
        assertTrue("the rest of the sentence stays", "You can find your API key at" in red)

        assertEquals("bad key [key hidden] rejected", AiCoach.redactSecrets("bad key sk-ant-api03-AbCdEf123456 rejected"))
        assertEquals("API key not valid: [key hidden]", AiCoach.redactSecrets("API key not valid: AIzaSyD-1234567890abcdef"))
        assertEquals("header was Authorization: [key hidden]", AiCoach.redactSecrets("header was Authorization: Bearer abcdefgh12345678"))
        assertEquals("x-api-key: [key hidden]", AiCoach.redactSecrets("x-api-key: localtoken-12345678"))
        // The stored key itself, whatever its shape, when the caller passes it.
        assertEquals("gateway refused [key hidden]", AiCoach.redactSecrets("gateway refused my-gateway-token-42", key = "my-gateway-token-42"))
        // Ordinary text is untouched.
        assertEquals("Rate limit reached, retry in 20s.", AiCoach.redactSecrets("Rate limit reached, retry in 20s."))
    }

    @Test
    fun theKeyIsNotInTheBackupWhitelist() {
        for (key in BackupSettingsCodec.WHITELIST.keys) {
            assertFalse(key, Regex("(?i)api|secret|token|coach\\.key|ai\\.").containsMatchIn(key))
        }
    }

    @Test
    fun androidBackupAndTransferExcludePreferences() {
        val rules = listOf("src/main/res/xml/data_extraction_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml")
            .map(::File).first { it.exists() }.readText()
        assertEquals(2, Regex("""<exclude domain="sharedpref" />""").findAll(rules).count())
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }.readText()
        assertTrue("android:allowBackup=\"false\"" in manifest)
    }
}
