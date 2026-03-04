package mindurka.coreplugin

import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import buj.tl.Tl
import mindurka.build.CommandType
import mindurka.coreplugin.commands.metadataForCommand
import mindurka.coreplugin.votes.VoteFail
import mindurka.util.SendMessage
import mindurka.util.permissionLevel
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
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
        // TODO: Translator.

        for (recv in Groups.player) {
            Call.sendMessage(recv.con, Tl.fmt(recv)
                .put("player", player.coloredName()).put("message", text).done("{generic.chat}"), text, player)
        }

        null
    }
}