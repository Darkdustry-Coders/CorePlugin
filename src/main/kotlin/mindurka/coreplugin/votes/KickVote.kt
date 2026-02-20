package mindurka.coreplugin.votes

import buj.tl.L
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.sessionData
import mindurka.util.Async
import mindurka.util.SendMessage
import mindustry.gen.Player

class KickVote(initiator: Player, val player: Player, val reason: String): SimpleVote("commands.votekick.vote", initiator, initiator.team()) {
    override fun formatParams(l: L<*>) {
        l.put("player", initiator.sessionData.simpleName())
            .put("reason", reason)
            .put("target", player.sessionData.simpleName())
    }
    override fun done() {
        Async.run {
            Database.votekick(
                player,
                initiator,
                votesFor,
                reason
            )
        }
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