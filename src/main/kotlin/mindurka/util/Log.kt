package mindurka.util

import arc.Core
import arc.util.Log
import mindurka.coreplugin.CorePlugin

/**
 * Print a debug message.
 *
 * This function is mainly for languages that support string interpolation
 * to not allocate an extra string if it won't be ever used.
 *
 * If not on main thread, will use `Core.app.run`.
 */
inline fun debug(msg: () -> String) {
    if (Log.level.ordinal > Log.LogLevel.debug.ordinal) return
    if (Thread.currentThread() !== CorePlugin.mainThread) Core.app.run {
        Log.debug(msg())
    } else Log.debug(msg())
}

/**
 * Print a debug message.
 *
 * If not on main thread, will use `Core.app.run`.
 */
fun debug(msg: String, vararg objs: Any) {
    if (Log.level.ordinal > Log.LogLevel.debug.ordinal) return
    if (Thread.currentThread() !== CorePlugin.mainThread) Core.app.run {
        Log.debug(msg, objs)
    } else Log.debug(msg, objs)
}

/**
 * Print a debug message.
 *
 * If not on main thread, will use `Core.app.run`.
 */
fun debug(msg: Any) {
    if (Log.level.ordinal > Log.LogLevel.debug.ordinal) return
    if (Thread.currentThread() !== CorePlugin.mainThread) Core.app.run {
        Log.debug(msg)
    } else Log.debug(msg)
}
