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

object Completion

object DialogsInternal {
    val data = WeakHashMap<Player, OpenDialogs>()

    fun newId(player: Player): Int {
        val dialogs = data.getOrPut(player) { OpenDialogs() }
        val id = dialogs.id
        dialogs.id = id + 1
        return id
    }

    fun setOpenDialog(player: Player, dialog: Dialog?) {
        val dialogs = data.getOrPut(player) { OpenDialogs() }
        dialogs.mainDialog = dialog
    }

    operator fun get(player: Player) = data[player]
}

interface Dialog {}

data class OpenDialogs (
    var id: Int = 0,
    var mainDialog: Dialog? = null,
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
    val mainDialog = dialogs.mainDialog

    if (mainDialog is TextDialog) {
        if (mainDialog.menuId != event.textInputId) return
        dialogs.mainDialog = null
        mainDialog.handleEvent(event)
    }
}
fun handleUiEvent(event: MenuOptionChooseEvent) {
    val dialogs = DialogsInternal[event.player] ?: return
    val mainDialog = dialogs.mainDialog

    if (mainDialog is MenuDialog<*>) {
        if (mainDialog.menuId != event.menuId) return
        dialogs.mainDialog = null
        mainDialog.handleEvent(event)
    }
}
fun handleUiEvent(event: PlayerLeave) {
    val dialogs = DialogsInternal[event.player] ?: return
    val mainDialog = dialogs.mainDialog

    if (mainDialog is MenuDialog<*>) {
        dialogs.mainDialog = null
        mainDialog.handleEvent(event)
    }
    else if (mainDialog is TextDialog) {
        dialogs.mainDialog = null
        mainDialog.handleEvent(event)
    }
}
