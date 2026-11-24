package com.noop.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I-03 (R10a): "Preview what Sage sends" is only honest if the send paths build their text the same way. Both send paths
 * and the preview go through `AiCoach.groundingContext`, and nothing else assembles the daily table into a request.
 * I-02 (S0-2): saving a key still contacts nobody (#288); only the explicit Test key does.
 */
class SagePreviewHonestyTest {

    private fun source(path: String) = listOf("src/main/java/com/noop/$path", "app/src/main/java/com/noop/$path")
        .map(::File).first { it.exists() }.readText()

    @Test
    fun everySendAndThePreviewShareOneBuilder() {
        val coach = source("ai/AiCoach.kt")
        assertEquals("two send paths", 2, Regex("""injectContext\(history, groundingContext\(ctx, consent, includeSignals\)\)""").findAll(coach).count())
        // buildContext(days) is called from groundingContext only.
        assertEquals(1, Regex("""append\(buildContext\(days\)\)""").findAll(coach).count())
        val vm = source("ui/CoachViewModel.kt")
        assertTrue(vm.contains("aiCoach.groundingContext(appCtx, consentNow, consentNow && NoopPrefs.coachSignals(appCtx))"))
        // The send path decides signals the same way the preview does.
        assertTrue(vm.contains("includeSignals = consentNow && NoopPrefs.coachSignals(appCtx)"))
    }

    @Test
    fun savingAKeyContactsNobody() {
        val vm = source("ui/CoachViewModel.kt")
        val save = vm.substringAfter("fun saveKey(").substringBefore("\n    }\n")
        assertTrue("saveKey must not fetch", "fetchModels" !in save && "refreshModels" !in save && "testKey" !in save)
    }
}
