// TODO: Documentation

package mindurka.ui

import mindustry.game.EventType.MenuOptionChooseEvent
import mindustry.game.EventType.PlayerLeave
import mindustry.gen.Player
import mindustry.gen.Call
import mindurka.annotations.PublicAPI
import mindurka.util.map
import mindurka.util.collect
import mindurka.util.nodecl
import buj.tl.LCtx
import buj.tl.Ls
import arc.func.Cons
import arc.func.Prov
import arc.util.Timer
import arc.struct.Seq
import java.util.concurrent.CompletableFuture

/**
 * Button.
 */
data class MenuUiButton<T> (
    val text: String,
    val fn: Prov<T?>?,
    val ctx: LCtx? = null,
)

/**
 * A menu dialog.
 *
 * A wrapper over Mindustry's menu dialogs.
 *
 * Once `future` returns, instance of this class must not be used again.
 */
class MenuDialog<T>: Dialog {
    var title: String = ""
    var message: String = ""

    var titleCtx: LCtx? = null
    var messageCtx: LCtx? = null

    val options = Seq<Seq<MenuUiButton<T>>>()
    var closeHandle: Prov<T?>? = null
    var exitHandle: Prov<T?>? = null

    var menuId: Int? = null

    val future: CompletableFuture<T?> = CompletableFuture()
    var returnValue: T? = null

    var rerender: Runnable? = null
    var handlingButton = false

    /**
     * Clear all UI elements.
     */
    fun clear() {
        title = ""
        message = ""
        titleCtx = null
        messageCtx = null
        closeHandle = null
        exitHandle = null
        returnValue = null
        options.clear()
    }

    fun write(player: Player) {
        val id = menuId ?: DialogsInternal.newId(player)
        DialogsInternal.openDialog(player, this)
        Call.followUpMenu(
            player.con,
            id,
            if (titleCtx == null) title else Ls(player.locale, titleCtx!!).done(title),
            if (messageCtx == null) message else Ls(player.locale, messageCtx!!).done(message),
            options.iterator().map {
                it.iterator().map {
                    if (it.ctx == null) it.text else Ls(player.locale, it.ctx).done(it.text)
                }.collect(ArrayList<String>()).toTypedArray()
            }.collect(ArrayList<Array<String>>()).toTypedArray())
        menuId = id
    }

    fun handleEvent(event: MenuOptionChooseEvent) {
        if (event.option == -1) {
            val value = closeHandle?.get()
            val r = rerender
            menuId = null
            if (r != null) {
                rerender = null
                r.run()
            } else {
                DialogsInternal.closeDialog(event.player, this)
                future.complete(value)
            }
            return
        }
        var optionIdx = 0
        r@for (group in options)
            for (option in group)
                if (optionIdx++ == event.option) {
                    val handler = option.fn ?: break@r
                    handlingButton = true
                    val value = handler.get()
                    handlingButton = false
                    val r = rerender
                    val id = menuId
                    if (r != null) {
                        rerender = null
                        r.run()
                    } else {
                        menuId = null
                        if (id != null) Timer.schedule({ Call.hideFollowUpMenu(event.player.con, id) }, TIMER_CLOSE_TIME)
                        DialogsInternal.closeDialog(event.player, this)
                        future.complete(value)
                    }
                    
                    break@r
                }
    }
    fun handleEvent(event: PlayerLeave) {
        val value = exitHandle?.get()
    }
}

/**
 * A menu group builder.
 */
@PublicAPI
class MenuGroupBuilder<T>(private val group: Seq<MenuUiButton<T>>) {
    @JvmName("optionText")
    fun optionText(text: String) {
        optionText(text, null)
    }
    @JvmName("optionText")
    fun optionText(text: String, cb: Prov<T?>?) {
        group.add(MenuUiButton(text, cb))
    }
    @JvmName("option")
    fun option(text: String) {
        option(text, null)
    }
    @JvmName("option")
    fun option(text: String, cb: Prov<T?>?): LCtx {
        val ctx = LCtx()
        group.add(MenuUiButton(text, cb, ctx))
        return ctx
    }
}

/**
 * A menu builder.
 */
@PublicAPI
class MenuBuilder<T>(private val dialog: MenuDialog<T>, private val player: Player, private val executeAgain: Runnable) {
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

    @JvmName("optionText")
    fun optionText(text: String) = optionText(text, null)
    @JvmName("optionText")
    fun optionText(text: String, cb: Prov<T?>?) {
        dialog.options.add(Seq.with(MenuUiButton(text, cb)))
    }
    @JvmName("option")
    fun option(text: String): LCtx = option(text, null)
    @JvmName("option")
    fun option(text: String, cb: Prov<T?>?): LCtx {
        val ctx = LCtx()
        dialog.options.add(Seq.with(MenuUiButton(text, cb, ctx)))
        return ctx
    }

    fun group(config: MenuGroupBuilder<T>.() -> kotlin.Unit) {
        val group = Seq<MenuUiButton<T>>()
        config(MenuGroupBuilder<T>(group))
        dialog.options.add(group)
    }
    @JvmName("group")
    fun group(config: Cons<MenuGroupBuilder<T>>) {
        val group = Seq<MenuUiButton<T>>()
        config[MenuGroupBuilder<T>(group)]
        dialog.options.add(group)
    }

    fun onInit(callback: Runnable) {
        if (dialog.menuId == null) dialog.rerender = callback;
    }
    fun onClose(callback: Prov<T?>) {
        dialog.closeHandle = callback
    }
    fun onExit(callback: Prov<T?>) {
        dialog.exitHandle = callback
    }

    @JvmOverloads
    fun closeDialog(value: T? = null): T? {
        if (dialog.handlingButton) throw IllegalStateException("TODO! 'closeDialog()' cannot be used in a callback.")

        val id = dialog.menuId ?: throw IllegalStateException("Calling 'closeDialog()' on a dialog that is not being displayed!")

        dialog.menuId = null
        executeAgain.run()
        Timer.schedule({ Call.hideFollowUpMenu(player.con, id) }, TIMER_CLOSE_TIME)
        DialogsInternal.closeDialog(player, dialog)
        dialog.future.complete(value)

        return null
    }

    fun rerenderDialog(): T? {
        dialog.menuId ?: throw IllegalStateException("Calling 'rerenderDialog()' on a dialog that is not being displayed!")

        if (dialog.handlingButton) dialog.rerender = executeAgain
        else executeAgain.run()

        return null
    }
}

@PublicAPI
fun <T> Player.openMenu(dialogFun: MenuBuilder<T>.() -> kotlin.Unit): CompletableFuture<T?> {
    val dialog = MenuDialog<T>()
    val builder = arrayOf(nodecl<MenuBuilder<T>>())
    builder[0] = MenuBuilder(dialog, this) {
        dialog.clear()
        dialogFun(builder[0])
        dialog.write(this)
    }
    dialogFun(builder[0])
    dialog.rerender?.run()
    dialog.rerender = null
    dialog.write(this)
    return dialog.future
}
