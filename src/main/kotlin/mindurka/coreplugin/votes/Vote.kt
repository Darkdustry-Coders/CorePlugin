package mindurka.coreplugin.votes

import arc.func.Boolf
import arc.struct.Seq
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.sessionData
import mindurka.util.SendMessage
import mindurka.util.isServiceTeam
import mindurka.util.unreachable
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player

enum class VoteFail {
    Ok,
    SameVote,
    Filtered,
}

abstract class Vote(val initiator: Player, val team: Team?) {
    val votesFor: Seq<Player> = Seq.with(initiator)
    val votesAgainst = Seq<Player>()

    var finished = false
    var totalPlayers: Int = 0
    var votesForNumber: Int = 0

    open fun playerLeft(send: SendMessage, player: Player) {
        if (finished) return

        votesFor.remove(player)
        votesAgainst.remove(player)

        refresh(false)
        send.send().put("player", player.sessionData.simpleName())
            .put("votes", votesForNumber.toString())
            .put("threshold", totalPlayers.toString())
            .done("{generic.vote.left}\n{generic.vote.${if (team == null) "global" else "team"}}")
        if (finished) {
            if (team == null) CorePlugin.currentGlobalVote = null
            else CorePlugin.teamVotes.remove(team.id)
            commit(SendMessage.All)
        }
    }

    /**
     * Try to add a vote.
     *
     * @return `true` on success, `false` otherwise.
     */
    fun vote(send: SendMessage, player: Player, fo: Boolean): VoteFail {
        if (finished) unreachable()
        if (!filter(player)) return VoteFail.Filtered

        val remove = if (fo) votesAgainst else votesFor
        val add = if (!fo) votesAgainst else votesFor

        if (add.contains(player)) return VoteFail.SameVote

        remove.remove(player)
        add.addUnique(player)

        refresh(false)
        voted(send, player, fo)
        if (finished) {
            if (team == null) CorePlugin.currentGlobalVote = null
            else CorePlugin.teamVotes.remove(team.id)
            commit(SendMessage.All)
        }

        return VoteFail.Ok
    }

    fun refresh(commit: Boolean = true) {
        if (finished) return

        val selector = Boolf<Player> { filter(it) && if (team == null) (!it.team().isServiceTeam || votesFor.contains(it) || votesAgainst.contains(it)) else it.team() == team }
        totalPlayers = Groups.player.count(selector)
        votesForNumber = votesFor.count(selector) - votesAgainst.count(selector)

        if (votesForNumber >= totalPlayers / 2 + 1) {
            finished = true
            if (commit) {
                if (team == null) CorePlugin.currentGlobalVote = null
                else CorePlugin.teamVotes.remove(team.id)
                commit(SendMessage.All)
            }
        }
    }

    fun updateStatus(send: SendMessage) {
        if (finished) return
        sendUpdateMessage(send)
    }

    open fun filter(player: Player): Boolean = true
    abstract fun sendUpdateMessage(send: SendMessage)
    abstract fun cancelled(send: SendMessage)
    abstract fun commit(send: SendMessage)
    abstract fun voted(send: SendMessage, player: Player, fo: Boolean)
    abstract val cancelsIfRoundChanged: Boolean
}