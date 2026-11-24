package com.noop.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A string defined twice in one `strings.xml` passes a debug build from a tree that happens to be clean but fails the
 * release resource merge ("Found item String/x more than one time"). This catches it before a release build does.
 */
class StringResourcesTest {
    @Test
    fun noStringNameIsDefinedTwiceInOneFile() {
        val res = File("src/main/res")
        val files = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }.filter { it.exists() }
        assertTrue("no strings.xml found under ${res.absolutePath}", files.isNotEmpty())
        val dupes = files.flatMap { f ->
            Regex("""<string\s+name="([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }
                .groupingBy { it }.eachCount().filter { it.value > 1 }.keys.map { "${f.parentFile.name}: $it" }
        }
        assertTrue("Duplicate string names: $dupes", dupes.isEmpty())
    }
}
