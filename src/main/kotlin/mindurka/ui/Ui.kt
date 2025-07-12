package mindurka.ui

import mindurka.annotations.PublicAPI
import mindustry.gen.Player
import java.util.WeakHashMap
import arc.func.Cons
import mindustry.game.EventType.MenuOptionChooseEvent
import java.util.concurrent.CompletableFuture
import mindustry.game.EventType.PlayerLeave
import mindustry.game.EventType.TextInputEvent
import arc.util.Log
import mindustry.gen.Call
import arc.struct.Seq

/** Approximate time that is necessary to prevent flashing when opening a dialog. */
internal val TIMER_CLOSE_TIME = 0.031f

object DialogsInternal {
    val data = WeakHashMap<Player, OpenDialogs>()

    fun newId(player: Player): Int {
        val dialogs = data.getOrPut(player) { OpenDialogs() }
        val id = dialogs.id
        dialogs.id = id + 1
        return id
    }

    fun openDialog(player: Player, dialog: Dialog) {
        val dialogs = data.getOrPut(player) { OpenDialogs() }
        dialogs.openedDialogs.addUnique(dialog)
    }

    fun closeDialog(player: Player, dialog: Dialog) {
        val dialogs = data.getOrPut(player) { OpenDialogs() }
        dialogs.openedDialogs.removeAll { it == dialog }
    }

    operator fun get(player: Player) = data[player]
}

interface Dialog {}

data class OpenDialogs (
    var id: Int = 0,
    var openedDialogs: Seq<Dialog> = Seq(),
)

@PublicAPI
object Dialogs {
    @JvmStatic
    @JvmName("menu")
    fun <T> menu(player: Player, dialog: Cons<MenuBuilder<T>>): CompletableFuture<T?> = player.openMenu<T> { dialog.get(this) }
    @JvmStatic
    fun text(player: Player, dialog: Cons<TextDialogBuilder>): CompletableFuture<String?> = player.openText { dialog.get(this) }
}

fun handleUiEvent(event: TextInputEvent) {
    val dialogs = DialogsInternal[event.player] ?: return

    for (dialog in dialogs.openedDialogs) {
        if (dialog is TextDialog) {
            if (dialog.menuId != event.textInputId) return
            dialog.handleEvent(event)
            break
        }
    }
}
fun handleUiEvent(event: MenuOptionChooseEvent) {
    val dialogs = DialogsInternal[event.player] ?: return

    for (dialog in dialogs.openedDialogs) {
        if (dialog is MenuDialog<*>) {
            if (dialog.menuId != event.menuId) return
            dialog.handleEvent(event)
            break
        }
    }
}
fun handleUiEvent(event: PlayerLeave) {
    val dialogs = DialogsInternal[event.player] ?: return

    for (dialog in dialogs.openedDialogs) {
        if (dialog is MenuDialog<*>) {
            dialog.handleEvent(event)
            break
        }
        else if (dialog is TextDialog) {
            dialog.handleEvent(event)
            break
        }
    }
}

fun Player.openURI(uri: String) {
    Call.openURI(con, uri)
}
