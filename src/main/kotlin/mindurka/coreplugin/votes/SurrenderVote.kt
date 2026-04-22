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
        // team.data().buildings.each {
        //     if (it.block.privileged) return@each
        //     if (Mathf.random() > 0.7f) it.kill()
        //     else it.team(Team.derelict)
        // }
        // team.data().units.each {
        //     if (!it.killable()) return@each
        //     timer(Mathf.random() * 5f) { it.kill() }
        // }
    }

    override fun playerLeft(send: SendMessage, player: Player) {
        finished = true
        CorePlugin.teamVotes.remove(team!!.id)

        done()
    }

    override val cancelsIfRoundChanged = true
}