package mindurka.ui

import mindurka.annotations.PublicAPI
import mindustry.gen.Player
import arc.func.Cons
import java.util.WeakHashMap
import mindustry.gen.Call
import arc.math.Mathf
import buj.tl.Tl
import buj.tl.LCtx
import buj.tl.Ls
import arc.struct.Seq
import mindustry.util.nodecl
import mindustry.game.EventType.MenuOptionChooseEvent
import mindurka.util.map
import mindurka.util.collect
import arc.util.Log
import arc.util.Timer

// TODO: Refactor this whole thing to use CompletableFuture.

private val possiblyOpenDialogs = WeakHashMap<Player, UiDialog>()
private val nextMenuId = WeakHashMap<Player, Int>()

/**
 * A form.
 *
 * A wrapper class for class-based UI dialogs.
 */
@PublicAPI
abstract class Form(protected val player: Player) {
    abstract fun build(builder: Builder)

    fun open() {
        val dialog = UiDialog()
        val builder: Array<Builder> = arrayOf(nodecl())
        builder[0] = Builder(dialog, player) {
            dialog.clear()
            build(builder[0])
            dialog.write(player)
        }
        build(builder[0])
        dialog.write(player)
    }
}

@PublicAPI
data class CloseFollowUpDialog(private val player: Player): Runnable {
    var id: Int? = null
    var signal: Boolean = false

    operator fun invoke() {
        if (signal) return
        signal = true
        val id = id ?: return
        Call.hideFollowUpMenu(player.con, id)
        
    }

    override fun run() {
        if (signal) return
        signal = true
        val id = id ?: return
        Call.hideFollowUpMenu(player.con, id)
    }
}

/**
 * Button
 */
data class UiButton (
    val text: String,
    val fn: Runnable,
    val ctx: LCtx? = null,
)

/**
 * Dialog.
 *
 * A wrapper over Mindustry's dialogs.
 */
class UiDialog {
    var title: String = ""
    var message: String = ""

    var titleCtx: LCtx? = null
    var messageCtx: LCtx? = null

    val options = Seq<Seq<UiButton>>()
    var closeHandle: Runnable? = null
    var closeFollowup: CloseFollowUpDialog? = null

    var menuId: Int? = null

    /**
     * Clear all UI elements.
     */
    fun clear() {
        title = ""
        message = ""
        titleCtx = null
        messageCtx = null
        closeHandle = null
        options.clear()
    }

    fun write(player: Player) {
        val id = nextMenuId.getOrDefault(player, 0)
        nextMenuId.set(player, id + 1)
        possiblyOpenDialogs[player] = this
        val followup = closeFollowup
        if (followup == null) Call.menu(
            player.con,
            id,
            if (titleCtx == null) title else Ls(player.locale, titleCtx!!).done(title),
            if (messageCtx == null) message else Ls(player.locale, messageCtx!!).done(message),
            options.iterator().map {
                it.iterator().map {
                    if (it.ctx == null) it.text else Ls(player.locale, it.ctx).done(it.text)
                }.collect(ArrayList<String>()).toTypedArray()
            }.collect(ArrayList<Array<String>>()).toTypedArray())
        else {
            if (followup.signal) return
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
            followup.id = id
        }
        menuId = id
    }
}

@PublicAPI
class GroupBuilder(private val group: Seq<UiButton>) {
    @JvmOverloads
    fun optionText(text: String, cb: Runnable = object : Runnable { override fun run() {} }) {
        group.add(UiButton(text, cb))
    }
    @JvmOverloads
    fun option(text: String, cb: Runnable = object : Runnable { override fun run() {} }): LCtx {
        val ctx = LCtx()
        group.add(UiButton(text, cb, ctx))
        return ctx
    }
}

@PublicAPI
class FollowUpBuilder(private val dialog: UiDialog, private val player: Player, private val close: CloseFollowUpDialog, private val executeAgain: Runnable) {
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

    @JvmOverloads
    fun optionText(text: String, cb: Runnable = object : Runnable { override fun run() {} }) {
        dialog.options.add(Seq.with(UiButton(text, cb)))
    }
    @JvmOverloads
    fun option(text: String, cb: Runnable = object : Runnable { override fun run() {} }): LCtx {
        val ctx = LCtx()
        dialog.options.add(Seq.with(UiButton(text, cb, ctx)))
        return ctx
    }

    fun group(config: GroupBuilder.() -> kotlin.Unit) {
        val group = Seq<UiButton>()
        config(GroupBuilder(group))
        dialog.options.add(group)
    }
    fun group(config: Cons<GroupBuilder>) {
        val group = Seq<UiButton>()
        config[GroupBuilder(group)]
        dialog.options.add(group)
    }

    fun closeDialog() {
        close()
    }

    fun exit(callback: Runnable) {
        dialog.closeHandle = callback
    }

    fun rerenderDialog() {
        executeAgain.run()
    }
}

@PublicAPI
class Builder(private val dialog: UiDialog, private val player: Player, private val executeAgain: Runnable) {
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

    @JvmOverloads
    fun optionText(text: String, cb: Runnable = object : Runnable { override fun run() {} }) {
        dialog.options.add(Seq.with(UiButton(text, cb)))
    }
    @JvmOverloads
    fun option(text: String, cb: Runnable = object : Runnable { override fun run() {} }): LCtx {
        val ctx = LCtx()
        dialog.options.add(Seq.with(UiButton(text, cb, ctx)))
        return ctx
    }

    fun group(config: GroupBuilder.() -> kotlin.Unit) {
        val group = Seq<UiButton>()
        config(GroupBuilder(group))
        dialog.options.add(group)
    }
    fun group(config: Cons<GroupBuilder>) {
        val group = Seq<UiButton>()
        config[GroupBuilder(group)]
        dialog.options.add(group)
    }

    fun exit(callback: Runnable) {
        dialog.closeHandle = callback
    }

    fun rerenderDialog() {
        executeAgain.run()
    }
}

@PublicAPI
fun Player.openDialog(dialogFun: Builder.() -> kotlin.Unit) {
    val dialog = UiDialog()
    val builder = arrayOf(nodecl<Builder>())
    builder[0] = Builder(dialog, this) {
        dialog.clear()
        dialogFun(builder[0])
        dialog.write(this)
    }
    dialogFun(builder[0])
    dialog.write(this)
}

@PublicAPI
object Dialogs {
    @JvmStatic
    fun open(player: Player, dialog: Cons<Builder>) {
        val d = UiDialog()
        val builder = arrayOf(nodecl<Builder>())
        builder[0] = Builder(d, player) {
            d.clear()
            dialog[builder[0]]
            d.write(player)
        }
        dialog[builder[0]]
        d.write(player)
    }

    @JvmStatic
    fun openFollowUp(player: Player, dialog: Cons<FollowUpBuilder>) {
        val d = UiDialog()
        val close = CloseFollowUpDialog(player)
        val builder = arrayOf(nodecl<FollowUpBuilder>())
        builder[0] = FollowUpBuilder(d, player, close) {
            d.clear()
            dialog[builder[0]]
            d.write(player)
        }
        dialog[builder[0]]
        d.write(player)
    }
}

fun handleUiEvent(event: MenuOptionChooseEvent) {
    val menu = possiblyOpenDialogs[event.player] ?: return
    if (menu.menuId != event.menuId) return
    possiblyOpenDialogs[event.player] = null
    if (event.option == -1) {
        menu.closeHandle?.run()
        return
    }
    var optionIdx = 0
    r@for (group in menu.options)
        for (option in group)
            if (optionIdx++ == event.option) {
                option.fn.run()
                break@r
            }
}
