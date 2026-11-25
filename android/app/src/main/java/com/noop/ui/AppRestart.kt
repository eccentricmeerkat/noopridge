package com.noop.ui

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Restart the app process after a backup restore swapped or merged the database underneath it.
 *
 * Every screen, the repository and the strap link hold the database they opened at launch. After a restore they would
 * keep reading the old, now-closed one (empty scores and a "scores are building" note over a full history), and a strap
 * sync could write into it, or acknowledge history it could not store (#57). Relaunching the process opens the restored
 * file. Done automatically, rather than asking the user to close and reopen the app, which is how #57 happened and how
 * a restore from Settings left Today empty.
 *
 * NonCancellable: the caller's scope is cancelled the moment the user navigates away, and the restart is a data-safety
 * guarantee, not a courtesy. Never returns normally.
 */
internal suspend fun restartAfterRestore(context: Context) {
    withContext(NonCancellable) {
        delay(800) // let the toast render before the process dies
        val ctx = context.applicationContext
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?.let { ctx.startActivity(it) }
        Runtime.getRuntime().exit(0)
    }
}
