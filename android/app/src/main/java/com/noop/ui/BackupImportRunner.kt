package com.noop.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.noop.R
import com.noop.data.DataBackup
import com.noop.data.ImportProgress
import com.noop.data.ImportProgressSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Runs a `.noopbak` import and publishes where it is, for the progress dialog ([BackupImportHost]).
 *
 * The import runs on the app's own scope, not a screen's: leaving the screen must not abandon a restore halfway, and a
 * finished restore must restart the app whichever screen started it ([restartAfterRestore]). So the runner, not the
 * caller, does that; the caller only hears about a failure or a file that needs confirming. Every screen starts an import
 * here, never through `DataBackup.importFrom` directly (a source-scan test enforces it).
 */
internal object BackupImportRunner {

    /** What the dialog draws: the import's progress, when its current stage began, and whether a cancel is on its way. */
    data class State(val progress: ImportProgress, val stageStartedAtMs: Long, val cancelling: Boolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    @Volatile private var cancelRequested = false

    val running: Boolean get() = _state.value != null

    /** Ask the running import to stop at its next safe point. Harmless when none is running or it can no longer stop. */
    fun cancel() {
        cancelRequested = true
        _state.value = _state.value?.copy(cancelling = true)
    }

    /**
     * Start importing [uri]. Ignored while another import runs. A restored database restarts the app (after [beforeRestart]);
     * anything else, a failure, a cancel, or a file that needs the size override, goes to [onResult] on the main thread.
     */
    fun start(
        context: Context,
        uri: Uri,
        allowOversize: Boolean = false,
        beforeRestart: () -> Unit = {},
        onResult: (DataBackup.ImportResult) -> Unit,
    ) {
        if (running) return
        val appContext = context.applicationContext
        cancelRequested = false
        _state.value = State(ImportProgress(com.noop.data.ImportStage.READING, 1, 6), System.currentTimeMillis(), false)
        scope.launch {
            val sink = object : ImportProgressSink {
                override fun report(progress: ImportProgress) {
                    val old = _state.value
                    val startedAt = if (old == null || old.progress.stage != progress.stage) System.currentTimeMillis() else old.stageStartedAtMs
                    _state.value = State(progress, startedAt, cancelRequested)
                }
                override fun isCancelled(): Boolean = cancelRequested
            }
            val result = runCatching { DataBackup.importFrom(appContext, uri, allowOversize, sink) }
                .getOrElse { DataBackup.ImportResult.Failed("The backup could not be imported. (${it.message})") }
            if (result is DataBackup.ImportResult.NeedsRestart) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, appContext.getString(R.string.backup_restored_restarting), Toast.LENGTH_LONG).show()
                    beforeRestart()
                }
                restartAfterRestore(appContext) // never returns; the dialog stays up until the process is gone
            } else {
                _state.value = null
                withContext(Dispatchers.Main) { onResult(result) }
            }
        }
    }
}

/**
 * Seconds left in the current stage, from how far it has got and how long that took; null when it is too early to say
 * (under two seconds in, or under 3% done) or the stage has no measurable length.
 */
internal fun importSecondsLeft(elapsedMs: Long, done: Long, total: Long): Int? {
    if (total <= 0L || done <= 0L || elapsedMs < 2_000L) return null
    val fraction = done.toDouble() / total
    if (fraction < 0.03) return null
    return ceil(elapsedMs / 1000.0 * (1.0 - fraction) / fraction).toInt().coerceAtLeast(0)
}
