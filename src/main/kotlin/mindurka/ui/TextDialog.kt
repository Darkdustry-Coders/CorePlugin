// TODO: Documentation

package mindurka.ui

import mindustry.gen.Player
import mindurka.annotations.PublicAPI
import mindustry.gen.Call
import buj.tl.LCtx
import buj.tl.Ls
import mindurka.util.nodecl
import java.util.concurrent.CompletableFuture
import mindustry.game.EventType.PlayerLeave
import mindustry.game.EventType.TextInputEvent
import arc.func.Func
import arc.func.Prov

class TextDialog: Dialog {
    var title: String = ""
    var message: String = ""
    var default: String = ""

    var titleCtx: LCtx? = null
    var messageCtx: LCtx? = null
    var defaultCtx: LCtx? = null

    var onOverrun: Func<String, String?>? = null
    var onComplete: Func<String, String?>? = null
    var onExit: Prov<String?>? = null
    var onClose: Prov<String?>? = null

    var textLength = 128
    var numeric = false

    var future: CompletableFuture<String?> = CompletableFuture()
    var menuId = 0
    var rerender: Runnable? = null

    fun write(player: Player) {
        menuId = DialogsInternal.newId(player)
        DialogsInternal.openDialog(player, this)
        Call.textInput(
            player.con,
            menuId,
            if (titleCtx == null) title else Ls(player.locale, titleCtx!!).done(title),
            if (messageCtx == null) message else Ls(player.locale, messageCtx!!).done(message),
            textLength,
            if (defaultCtx == null) default else Ls(player.locale, defaultCtx!!).done(default),
            numeric,
        )
    }

    fun handleEvent(event: TextInputEvent) {
        var value = event.text
        if (event.text == null) {
            val onClose = onClose
            value = if (onClose != null) onClose.get() else event.text
        } else if (event.text.length > textLength) {
            val onOverrun = onOverrun
            value = if (onOverrun != null) onOverrun[event.text] else event.text
        } else {
            val onComplete = onComplete
            value = if (onComplete != null) onComplete[event.text] else event.text
        }

        var rerender = rerender
        if (rerender == null) {
            future.complete(value)
        } else {
            rerender.run()
            this.rerender = null
        }
    }
    fun handleEvent(event: PlayerLeave) {
        future.complete(onExit?.get())
    }
}

class TextDialogBuilder(private val dialog: TextDialog, private val player: Player, private val executeAgain: Runnable) {
    var title: String
        get() = if (dialog.titleCtx == null) dialog.title else Ls(player.locale, dialog.titleCtx!!).done(dialog.title)
        set(value) {
            dialog.title = value
            dialog.titleCtx = null
        }
    fun title(title: String): LCtx {
        dialog.title = title
        val ctx = LCtx()
        dialog.titleCtx = ctx
        return ctx
    }

    var message: String
        get() = if (dialog.messageCtx == null) dialog.message else Ls(player.locale, dialog.messageCtx!!).done(dialog.message)
        set(value) {
            dialog.message = value
            dialog.messageCtx = null
        }
    fun message(message: String): LCtx {
        dialog.message = message
        val ctx = LCtx()
        dialog.messageCtx = ctx
        return ctx
    }

    var default: String
        get() = if (dialog.defaultCtx == null) dialog.default else Ls(player.locale, dialog.defaultCtx!!).done(dialog.default)
        set(value) {
            dialog.default = value
            dialog.defaultCtx = null
        }
    fun default(default: String): LCtx {
        dialog.default = default
        val ctx = LCtx()
        dialog.defaultCtx = ctx
        return ctx
    }

    var textLength: Int
        get() = dialog.textLength
        set(v) { dialog.textLength = v }
    var numeric: Boolean
        get() = dialog.numeric
        set(v) { dialog.numeric = v }

    fun onClose(callback: Prov<String?>) {
        dialog.onClose = callback
    }
    fun onExit(callback: Prov<String?>) {
        dialog.onExit = callback
    }
    fun onComplete(callback: Func<String, String?>) {
        dialog.onComplete = callback
    }
    fun onOverrun(callback: Func<String, String?>) {
        dialog.onOverrun = callback
    }

    fun rerenderDialog(): String? {
        dialog.rerender = executeAgain
        return null
    }
}

@PublicAPI
fun Player.openText(builder: TextDialogBuilder.() -> Unit): CompletableFuture<String?> {
    val d = TextDialog()
    val b: Array<TextDialogBuilder> = arrayOf(nodecl())
    b[0] = TextDialogBuilder(d, this) {
        builder(b[0])
        d.write(this)
    }

    builder(b[0])
    d.write(this)

    return d.future
}
