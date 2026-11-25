package com.noop.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A finished restore (`ImportResult.NeedsRestart`) swapped or merged the database underneath a running app. Every screen
 * and the repository still hold the one they opened at launch, so the app shows an empty history ("scores are building"
 * over a full database) until the process restarts. A restore from Settings once only asked the user to close and reopen
 * the app, and left Today empty.
 *
 * So no screen runs an import itself: they start it on [BackupImportRunner], which shows the progress dialog and restarts
 * the app when the restore finishes. This pins both halves.
 */
class RestoreRestartsTheAppTest {

    private val uiDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/noop/ui").takeIf { d -> d.isDirectory } ?: File(it, "src/main/java/com/noop/ui") }
        .first { it.isDirectory }

    private fun screens() = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }

    @Test
    fun noScreenRunsAnImportItself() {
        val offenders = screens()
            .filter { it.name != "BackupImportRunner.kt" }
            .filter { Regex("""DataBackup\.importFrom\(""").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertTrue("these call DataBackup.importFrom directly instead of BackupImportRunner.start: $offenders", offenders.isEmpty())
    }

    @Test
    fun theRunnerRestartsTheAppWhenARestoreFinishes() {
        val runner = File(uiDir, "BackupImportRunner.kt").readText()
        assertTrue("the runner must handle NeedsRestart", runner.contains("ImportResult.NeedsRestart"))
        assertTrue("the runner must call restartAfterRestore", runner.contains("restartAfterRestore("))
    }

    @Test
    fun theScreensThatImportUseTheRunner() {
        val users = screens().count { it.readText().contains("BackupImportRunner.start(") }
        assertTrue("expected Settings (twice), Your data and the wizard to start imports on the runner, saw $users files", users >= 3)
    }

    @Test
    fun theProgressDialogIsHostedAtTheRoot() {
        assertTrue("BackupImportHost() must be in the root composable", File(uiDir, "MainActivity.kt").readText().contains("BackupImportHost()"))
    }
}
