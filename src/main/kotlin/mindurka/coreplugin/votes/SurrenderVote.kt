package mindurka.coreplugin.votes

import arc.math.Mathf
import buj.tl.L
import mindurka.api.timer
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.sessionData
import mindurka.util.SendMessage
import mindustry.game.Team
import mindustry.gen.Player

class SurrenderVote(initiator: Player, val player: Player, val reason: String): SimpleVote("commands.votekick.vote", initiator, initiator.team()) {
    override fun formatParams(l: L<*>) {
        l.put("player", initiator.sessionData.simpleName())
            .put("reason", reason)
            .put("target", player.sessionData.simpleName())
    }
    override fun done() {
        team!!.cores().each { it.kill() }
        team.data().buildings.each {
            if (it.block.privileged) return@each
            if (Mathf.random() > 0.7f) it.kill()
            else it.team(Team.derelict)
        }
        team.data().units.each {
            if (!it.killable()) return@each
            timer(Mathf.random() * 5f) { it.kill() }
        }
    }

    override fun playerLeft(send: SendMessage, player: Player) {
        finished = true
        CorePlugin.teamVotes.remove(team!!.id)

        done()
    }

    override fun filter(player: Player): Boolean = player !== this.player

    override val cancelsIfRoundChanged = false
}