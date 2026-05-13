package mindurka.coreplugin.votes

import buj.tl.L
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.sessionData
import mindurka.util.SendMessage
import mindustry.gen.Player

class SurrenderVote(initiator: Player): SimpleVote("commands.surrender.vote", initiator, initiator.team()) {
    override fun formatParams(l: L<*>) {
        l.put("player", initiator.sessionData.simpleName())
    }
    override fun done() {
        team!!.cores().copy().each { it.kill() }
    }

    override fun playerLeft(send: SendMessage, player: Player) {
        finished = true
        CorePlugin.teamVotes.remove(team!!.id)
    }

    override val cancelsIfRoundChanged = true
}