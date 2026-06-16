package mindurka.coreplugin.extern

import mindurka.api.SpecialSettings
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.hasMindurkaCompat
import mindurka.coreplugin.sessionData
import mindurka.util.Async
import mindustry.game.Team
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

    override fun hasMindurkaCompat(player: Player?): Boolean = player?.hasMindurkaCompat ?: false
    override fun onMapLoad(): Boolean {
        if (CorePlugin.restarting) {
            CorePlugin.actuallyDoARestart()
        }
        return !CorePlugin.restarting
    }
    override fun countAlive(team: Team): Boolean =
        !SpecialSettings.currentMap().teams[team].pvpTeamDeathRequired
}