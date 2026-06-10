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
import mindurka.util.UnsafeNull
import mindurka.util.notnull
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import kotlin.uuid.ExperimentalUuidApi

private var assignDerelict = false
private val vanillaTeamAssigner = Vars.netServer.assigner
private var restoreTeams = ObjectIntMap<String>()
private val restoreSpectator: ObjectSet<String> = ObjectSet()

fun teamAssigned(player: Player) = !assignDerelict &&
    (!Gamemode.enableSpectate || !Gamemode.spectate[player])

@OptIn(ExperimentalUuidApi::class)
fun initTeams() {

    on<EventType.PlayEvent> { restoreTeams = ObjectIntMap<String>() }
    on<RoundEndEvent> {
        for (player in Groups.player) {
            if (Gamemode.enableSpectate && Gamemode.spectate[player]) restoreSpectator.add(player.sessionData.profileId)
        }
    }
    on<EventType.PlayerLeave> {
        val profileId = it.player.sessionData.profileId
        restoreSpectator.remove(profileId)
        if (!teamAssigned(it.player)) return@on
        restoreTeams.put(profileId, it.player.team().id)
    }

    on<EventType.PlayEvent> {
        if (!Gamemode.randomizeTeams) return@on
        if (!Vars.state.rules.pvp) return@on

        assignDerelict = true
        timer(3f, lifetime = Lifetime.Round) {
            assignDerelict = false
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
        Log.info("[DEBUG/Team] Assigning some team to ${Strings.stripColors(player.name)}")

        if (restoreSpectator.remove(player.sessionData.profileId)) {
            Log.info("[DEBUG/Team] Assigned spectator")
            return@TeamAssigner Gamemode.spectate.spectateRestore(player, null)
        }
        if (assignDerelict) {
            Log.info("[DEBUG/Team] Assigning derelict team")
            return@TeamAssigner Team.derelict
        }

        if (!Vars.state.isPaused && Gamemode.restoreTeams) restoreTeams.get(player.sessionData.profileId, -1).let { id ->
            Log.info("[DEBUG/Team] Trying to restore team")
            if (id == -1) return@let
            val team = Team.all[id]
            if (!team.data().isAlive) return@let
            if (team == Team.derelict) return@let
            return@TeamAssigner team
        }

        Log.info("[DEBUG/Team] No shortcut hit")
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
    val setSpectate = !Gamemode.spectate[caller]
    Gamemode.spectate[caller] = setSpectate

    if (setSpectate) restoreSpectator.add(caller.sessionData.profileId)
    else restoreSpectator.remove(caller.sessionData.profileId)
}

// TODO: Automatic spectator
// TODO: /rejoin