package mindurka.coreplugin.votes

import buj.tl.L
import mindurka.util.SendMessage
import mindustry.game.Team
import mindustry.gen.Player

abstract class SimpleVote(protected val category: String, initiator: Player, team: Team?): Vote(initiator, team) {
    protected abstract fun formatParams(l: L<*>)
    protected abstract fun done()

    override fun sendUpdateMessage(send: SendMessage) {
        send.send().put("player", initiator.coloredName())
            .put("votes", votesForNumber.toString())
            .put("threshold", totalPlayers.toString())
            .apply(::formatParams)
            .done("{$category.update}\n{generic.vote.${if (team == null) "global" else "team"}}")
    }

    override fun cancelled(send: SendMessage) {
        send.send().apply(::formatParams).done("{$category.cancel}")
    }

    override fun commit(send: SendMessage) {
        send.send().apply(::formatParams).done("{$category.commit}")
        done()
    }

    override fun voted(send: SendMessage, player: Player, fo: Boolean) {
        send.send().put("player", player.coloredName())
            .put("votes", votesForNumber.toString())
            .put("threshold", totalPlayers.toString())
            .apply(::formatParams)
            .done("{$category.${if (fo) "for" else "against"}}\n{generic.vote.${if (team == null) "global" else "local"}}")
    }
}