package mindurka.coreplugin.votes

import buj.tl.L
import mindurka.coreplugin.CorePlugin
import mindurka.util.SendMessage
import mindustry.gen.Player

class KickVote(initiator: Player, val player: Player, val reason: String): SimpleVote("commands.votekick.vote", initiator, initiator.team()) {
    override fun formatParams(l: L<*>) {
        l.put("player", initiator.coloredName())
            .put("reason", reason)
            .put("target", player.coloredName())
    }
    override fun done() {
        // TODO!
    }

    override fun playerLeft(send: SendMessage, player: Player) {
        finished = true
        if (team == null) CorePlugin.currentGlobalVote = null
        else CorePlugin.teamVotes.remove(team.id)

        done()
    }

    override fun filter(player: Player): Boolean = player !== this.player

    override val cancelsIfRoundChanged = false
}