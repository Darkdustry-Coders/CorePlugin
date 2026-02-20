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
import mindurka.api.Lifetime
import mindurka.util.Ref
import mindurka.util.UnsafeNull
import java.util.concurrent.CompletableFuture

/**
 * Button.
 */
@UiInternals
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
@UiInternals
class MenuDialog<T>(val lifetime: Lifetime): Dialog {
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
                lifetime.cancel()
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
                        lifetime.cancel()
                        future.complete(value)
                    }
                    
                    break@r
                }
    }
    fun handleEvent(event: PlayerLeave) {
        val value = exitHandle?.get()
        future.complete(value)
        lifetime.cancel()
    }
}

/**
 * A menu group builder.
 */
@PublicAPI
@OptIn(UiInternals::class)
class MenuGroupBuilder<T>(private val group: Seq<MenuUiButton<T>>, val lifetime: Lifetime) {
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
@OptIn(UiInternals::class)
class MenuBuilder<T>(
    private val dialog: MenuDialog<T>,
    private val player: Player,
    val lifetime: Lifetime,
    private val executeAgain: Runnable) {
    /**
     * Get or set raw title.
     *
     * If you want to use [buj.tl.Tl], use [title(String)] instead.
     */
    @PublicAPI
    var title: String
        get() = if (dialog.titleCtx == null) dialog.title else Ls(player.locale, dialog.titleCtx!!).done(dialog.title)
        set(value) {
            dialog.title = value
            dialog.titleCtx = null
        }
    /**
     * Set localized title.
     *
     * This will call into [buj.tl.Tl] subsystem. If you want to set raw text, use [val title] instead.
     */
    @PublicAPI
    fun title(title: String): LCtx {
        dialog.title = title
        val ctx = LCtx()
        dialog.titleCtx = ctx
        return ctx
    }

    /**
     * Get or set raw message.
     *
     * If [message(String)] was previously used,
     *
     * If you want to use [buj.tl.Tl], use [title(String)] instead.
     */
    @PublicAPI
    var message: String
        get() = if (dialog.messageCtx == null) dialog.message else Ls(player.locale, dialog.messageCtx!!).done(dialog.message)
        set(value) {
            dialog.message = value
            dialog.messageCtx = null
        }
    /**
     * Set localized message.
     *
     * This will call into [buj.tl.Tl] subsystem. If you want to set raw text, use [val title] instead.
     */
    @PublicAPI
    fun message(message: String): LCtx {
        dialog.message = message
        val ctx = LCtx()
        dialog.messageCtx = ctx
        return ctx
    }

    /**
     * Add a raw text option.
     *
     * If no callback is added, pressing the button will do nothing. If callback
     * returns a value, the dialog will close.
     *
     * If you want to use [buj.tl.Tl], use [option] instead.
     */
    @PublicAPI
    @JvmName("optionText")
    @JvmOverloads
    fun optionText(text: String, cb: Prov<T?>? = null) {
        dialog.options.add(Seq.with(MenuUiButton(text, cb)))
    }
    /**
     * Add a text option.
     *
     * If no callback is added, pressing the button will do nothing. If callback
     * returns a value, the dialog will close.
     *
     * If you don't want text to be localized, use [optionText] instead.
     */
    @PublicAPI
    @JvmName("option")
    @JvmOverloads
    fun option(text: String, cb: Prov<T?>? = null): LCtx {
        val ctx = LCtx()
        dialog.options.add(Seq.with(MenuUiButton(text, cb, ctx)))
        return ctx
    }

    /**
     * Add a button group.
     *
     * This is a Kotlin-only overload for extra convenience.
     */
    @PublicAPI
    inline fun group(config: MenuGroupBuilder<T>.() -> kotlin.Unit) {
        val group = Seq<MenuUiButton<T>>()
        config(MenuGroupBuilder<T>(group, lifetime))
        `unsafe$getInnerDialog`().options.add(group)
    }
    /**
     * Add a button group.
     */
    @PublicAPI
    @JvmName("group")
    fun group(config: Cons<MenuGroupBuilder<T>>) {
        val group = Seq<MenuUiButton<T>>()
        config[MenuGroupBuilder<T>(group, lifetime)]
        dialog.options.add(group)
    }

    /**
     * Set a function to run on dialog initialization.
     */
    @PublicAPI
    fun onInit(callback: Runnable) {
        if (dialog.menuId == null) dialog.rerender = callback;
    }
    /**
     * Set a function to run when dialog is closed.
     */
    @PublicAPI
    fun onClose(callback: Prov<T?>) {
        dialog.closeHandle = callback
    }
    /**
     * Set a function to run when the player has left the game.
     */
    @PublicAPI
    fun onExit(callback: Prov<T?>) {
        dialog.exitHandle = callback
    }

    /**
     * Close the dialog.
     *
     * While it can be called in button callbacks, it's not necessary.
     */
    @PublicAPI
    @JvmOverloads
    fun closeDialog(value: T? = null): T? {
        if (dialog.handlingButton) return value

        val id = dialog.menuId ?: throw IllegalStateException("Calling 'closeDialog()' on a dialog that is not being displayed!")

        dialog.menuId = null
        executeAgain.run()
        Timer.schedule({ Call.hideFollowUpMenu(player.con, id) }, TIMER_CLOSE_TIME)
        DialogsInternal.closeDialog(player, dialog)
        dialog.future.complete(value)

        return null
    }

    /**
     * Rerender the dialog
     *
     * This will work inside and outside the dialog.
     */
    @PublicAPI
    fun rerenderDialog(): T? {
        dialog.menuId ?: throw IllegalStateException("Calling 'rerenderDialog()' on a dialog that is not being displayed!")

        if (dialog.handlingButton) dialog.rerender = executeAgain
        else executeAgain.run()

        return null
    }

    /**
     * Do not use this unless you REALLY know what you're doing.
     *
     * You don't? Then don't touch it.
     */
    @UiInternals
    fun `unsafe$getInnerDialog`(): MenuDialog<T> {
        return dialog;
    }
}

@PublicAPI
@OptIn(UiInternals::class, UnsafeNull::class)
fun <T> Player.openMenu(dialogFun: MenuBuilder<T>.() -> kotlin.Unit): CompletableFuture<T?> {
    val dialog = Ref(nodecl<MenuDialog<T>>())
    val lifetime = object : Lifetime() {
        override fun uponEnd() {
            if (dialog.r.menuId == null) return
            dialog.r.menuId = null

            Timer.schedule({ Call.hideFollowUpMenu(con, id) }, TIMER_CLOSE_TIME)
            DialogsInternal.closeDialog(this@openMenu, dialog.r)
            dialog.r.future.complete(null)
        }
    }

    dialog.r = MenuDialog(lifetime)
    val builder = Ref(nodecl<MenuBuilder<T>>())
    builder.r = MenuBuilder(dialog.r, this, lifetime) {
        dialog.r.clear()
        dialogFun(builder.r)
        dialog.r.write(this)
    }
    dialogFun(builder.r)
    dialog.r.rerender?.run()
    dialog.r.rerender = null
    dialog.r.write(this)
    return dialog.r.future
}
