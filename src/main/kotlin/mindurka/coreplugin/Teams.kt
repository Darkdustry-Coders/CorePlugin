package mindurka.coreplugin

import arc.Core
import arc.struct.ObjectIntMap
import arc.struct.ObjectSet
import arc.util.Log
import arc.util.Strings
import mindurka.annotations.Command
import mindurka.annotations.EnabledIf
import mindurka.api.Gamemode
import mindurka.api.Lifetime
import mindurka.api.PlayerTeamAssign
import mindurka.api.RoundEndEvent
import mindurka.api.emit
import mindurka.api.on
import mindurka.api.timer
import mindurka.coreplugin.commands.SpectateEnabled
import mindurka.util.BoolRef
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import kotlin.uuid.ExperimentalUuidApi

private val assignDerelict = BoolRef(false)

fun teamAssigned(player: Player) = !assignDerelict.r &&
    (!Gamemode.enableSpectate || !Gamemode.spectate[player])

@OptIn(ExperimentalUuidApi::class)
fun initTeams() {
    val vanillaTeamAssigner = Vars.netServer.assigner
    var restoreTeams = ObjectIntMap<String>()
    var restoreSpectator: ObjectSet<Player>? = null

    on<EventType.PlayEvent> { restoreTeams = ObjectIntMap<String>() }
    on<RoundEndEvent> { // IDEA cannot predict that 'restoreSpectator' is ever modified otherwise lmao
        val s = ObjectSet<Player>()
        for (player in Groups.player) {
            if (Gamemode.enableSpectate && Gamemode.spectate[player]) s.add(player)
        }
        restoreSpectator = s
    }
    on<EventType.PlayerLeave> {
        restoreSpectator?.remove(it.player)
        if (!teamAssigned(it.player)) return@on
        restoreTeams.put(it.player.sessionData.profileId, it.player.team().id)
    }

    on<EventType.PlayEvent> {
        if (!Gamemode.randomizeTeams) return@on
        if (!Vars.state.rules.pvp) return@on

        assignDerelict.r = true
        timer(3f, lifetime = Lifetime.Round) {
            assignDerelict.r = false
            val vec = Groups.player.copy()
            vec.shuffle()
            for (player in vec) {
                Log.info("[DEBUG] Shuffling ")
                if (player.team() != Team.derelict) {
                    Log.info("[DEBUG] Skipping team reassignment for ${Strings.stripColors(player.sessionData.simpleName())}")
                    continue
                }
                val event = PlayerTeamAssign(
                    player,
                    Groups.player,
                    vanillaTeamAssigner.assign(player, Groups.player)
                )
                emit(event)
                player.team(event.team)
            }
        }
    }

    Vars.netServer.admins.addActionFilter { action -> action.type != Administration.ActionType.respawn || teamAssigned(action.player) }

    Vars.netServer.assigner = NetServer.TeamAssigner { player, players ->
        if (restoreSpectator?.remove(player) == true) return@TeamAssigner Gamemode.spectate.spectateRestore(player, null)
        if (assignDerelict.r) return@TeamAssigner Team.derelict

        if (!Vars.state.isPaused && Gamemode.restoreTeams) restoreTeams.get(player.sessionData.profileId, -1).let { id ->
            if (id == -1) return@let
            val team = Team.all[id]
            if (!team.data().isAlive) return@let
            return@TeamAssigner team
        }

        val event = PlayerTeamAssign(
            player,
            players,
            vanillaTeamAssigner.assign(player, players)
        )
        emit(event)
        event.team
    }
}

@Command
@EnabledIf(SpectateEnabled::class)
private fun spectate(caller: Player) {
    Gamemode.spectate[caller] = !Gamemode.spectate[caller]
}

// TODO: Automatic spectator
// TODO: /rejoin