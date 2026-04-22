package mindurka.coreplugin.extern

import mindurka.coreplugin.sessionData
import mindurka.util.Async
import mindustry.gen.Player

/**
 * Frontend for custom Mindustry server to interact with CorePlugin.
 */
object HeaderI: Header() {
    /** Return this object because apparently 'INSTANCE' doesn't work. */
    @JvmStatic
    fun self() = HeaderI

    override fun onDisconnected(player: Player) {
        Async.run {
            player.sessionData.playerLeft(player)
        }
    }
}