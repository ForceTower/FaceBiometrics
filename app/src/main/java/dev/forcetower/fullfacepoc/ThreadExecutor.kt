package dev.forcetower.fullfacepoc

import android.os.Handler
import java.util.concurrent.Executor

open class ThreadExecutor(private val handler: Handler) : Executor {
    override fun execute(runnable: Runnable) {
        handler.post(runnable)
    }
}