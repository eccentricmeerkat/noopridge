package com.noop.data

import java.io.IOException

/** One stage of a backup import, in the order they run. The screen turns each into a sentence. */
enum class ImportStage { READING, UNPACKING, CHECKING, SAFETY_COPY, REPLACING, MERGING, VERIFYING, FINISHING }

/**
 * Where an import is: stage [step] of [steps], and how far through that stage ([doneBytes] of [totalBytes]; both 0 when the
 * stage has no measurable length, such as SQLite's integrity check). For [ImportStage.MERGING] the two count tables, not
 * bytes. [cancellable] is false from the moment the live data starts being replaced or merged: nothing before that point
 * has changed anything, so only up to there can the import be abandoned.
 */
data class ImportProgress(
    val stage: ImportStage,
    val step: Int,
    val steps: Int,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    val cancellable: Boolean = true,
)

/** Receives progress from [DataBackup.importFrom] and tells it to stop at the next safe point. */
interface ImportProgressSink {
    fun report(progress: ImportProgress)
    fun isCancelled(): Boolean
}

/** Thrown from inside a copy loop when the user cancelled; [DataBackup.importFrom] turns it into a failed, unchanged result. */
class ImportCancelled : IOException("The import was cancelled.")

/** The sentence a cancelled import returns, and what the screen shows. */
const val IMPORT_CANCELLED_MESSAGE = "Import cancelled. Nothing was changed."
