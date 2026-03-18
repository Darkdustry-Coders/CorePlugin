package mindurka.coreplugin

import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import buj.tl.Tl
import mindurka.annotations.Command
import mindurka.annotations.RequiresPermission
import mindurka.annotations.Rest
import mindurka.api.emit
import mindurka.api.on
import mindurka.build.CommandType
import mindurka.coreplugin.commands.metadataForCommand
import mindurka.coreplugin.database.PermLevels
import mindurka.coreplugin.messages.ServerMessage
import mindurka.coreplugin.votes.VoteFail
import mindurka.util.SendMessage
import mindurka.util.filter
import mindurka.util.permissionLevel
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import java.util.Locale.getDefault
import java.util.WeakHashMap

class LastFailedCommand(var name: String, var args: String)

internal val lastCommandArgs = WeakHashMap<Player, String>()
internal val lastFailedCommand = WeakHashMap<Player, LastFailedCommand>()
internal val carriedLastFailedCommand = WeakHashMap<Player, LastFailedCommand>()

internal fun chatInit() {
    Vars.netServer.admins.addChatFilter chat@{ player, text ->
        if (CorePlugin.currentGlobalVote != null) {
            if (text == "y" || text == "n") {
                when (CorePlugin.currentGlobalVote!!.vote(SendMessage.All, player, text == "y")) {
                    VoteFail.Ok -> {}
                    VoteFail.SameVote -> Tl.send(player).done("{generic.checks.same-vote}")
                    VoteFail.Filtered -> Tl.send(player).done("{generic.checks.vote-filter}")
                }
                return@chat null
            }

            if (text == "c") {
                if (!player.admin) Tl.send(player).done("{generic.checks.admin-action-permission}")
                else {
                    Tl.broadcast().put("admin", player.coloredName()).done("{generic.vote.global.cancelled}")
                    CorePlugin.currentGlobalVote!!.finished = true
                    CorePlugin.currentGlobalVote = null
                }
                return@chat null
            }
        }

        text
    }

    Vars.netServer.invalidHandler = { player, response ->
        if (response.type == CommandHandler.ResponseType.manyArguments) {
            Tl.fmt(player)
                .put("cause", Tl.parse("{generic.command.too-many-arguments}"))
                .put("command", response.command.text)
                .done("{invalid-arguments}")
        } else if (response.type == CommandHandler.ResponseType.fewArguments) {
            Tl.fmt(player)
                .put("cause", Tl.parse("{generic.command.end-of-input}"))
                .put("command", response.command.text)
                .done("{invalid-arguments}")
        } else {
            var minDst = -1
            var command: CommandHandler.Command? = null

            for (x in Vars.netServer.clientCommands.commandList) {
                val iter = metadataForCommand(x.text, CommandType.Player)
                if (iter.hasNext()) {
                    val permLevel = player.permissionLevel
                    var good = false
                    for (meta in iter) {
                        if (meta.hidden) continue
                        if (meta.minPermissionLevel > permLevel) continue

                        good = true
                        break
                    }
                    if (!good) continue
                }

                val newDst = Strings.levenshtein(x.text, response.runCommand)
                if (newDst >= 3) continue
                if (newDst <= minDst) continue

                minDst = newDst
                command = x
            }

            command?.let { command ->
                lastFailedCommand.getOrPut(player) { LastFailedCommand("", "") }.let { last ->
                    last.name = command.text
                    last.args = lastCommandArgs[player] ?: ""
                } }

            Tl.fmt(player)
                .put("suggestion", if (command == null) "" else command.text)
                .put("command", response.runCommand)
                .done("{generic.checks.unknown-command}")
        }
    }

    Vars.netServer.chatFormatter = formatter@{ player, text ->
        for (recv in Groups.player) {
            Call.sendMessage(recv.con, Tl.fmt(recv)
                .put("player", player.sessionData.fullName()).put("message", text).done("{generic.chat}"), text, player)
        }

        val msg = ServerMessage(
            Strings.stripColors(text),
            "${player.sessionData.profileId}@mindustry",
            player.sessionData.userId,
            Strings.stripColors(player.sessionData.basename),
            null
        )

        emit(msg)

        null
    }

    on<ServerMessage> { event ->
        val service = event.service.split('@')[1].uppercase(getDefault())
        Call.sendMessage("[$service | ${event.username}]: ${event.message}")
    }
}

@Command
@RequiresPermission(PermLevels.moderator)
private fun a(caller: Player, @Rest message: String) {
    for (player in Groups.player) {
        if (player.permissionLevel < 100) continue
        Tl.send(player).put("player", caller.sessionData.fullName()).put("message", message).done("{generic.chat.admin} {generic.chat}")
    }
}

@Command
private fun t(caller: Player, @Rest message: String) {
    for (player in Groups.player) {
        if (player.team() !== caller.team()) continue
        Tl.send(player).put("player", caller.sessionData.fullName()).put("message", message).done("[#${caller.team().color}]{generic.chat.team}[] {generic.chat}")

        val msg = ServerMessage(
            Strings.stripColors(message),
            "${player.sessionData.profileId}@mindustry+team#${player.team().id}",
            player.sessionData.userId,
            Strings.stripColors(player.sessionData.basename),
            null
        )

        emit(msg)
    }
}