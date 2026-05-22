package mindurka.coreplugin

import arc.func.Boolf
import arc.func.Cons
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import buj.tl.L
import buj.tl.Tl
import mindurka.annotations.Command
import mindurka.annotations.RequiresPermission
import mindurka.annotations.Rest
import mindurka.api.Events
import mindurka.api.emit
import mindurka.api.on
import mindurka.build.CommandType
import mindurka.coreplugin.commands.metadataForCommand
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.PermLevels
import mindurka.coreplugin.messages.ServerMessage
import mindurka.coreplugin.votes.VoteFail
import mindurka.util.Async
import mindurka.util.SendMessage
import mindurka.util.UnsafeNull
import mindurka.util.newSeq
import mindurka.util.notnull
import mindurka.util.permissionLevel
import mindurka.util.stripInvisible
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import mindustry.net.Packets
import mindustry.net.ValidateException
import java.util.WeakHashMap

class LastFailedCommand(var name: String, var args: String)

internal val lastCommandArgs = WeakHashMap<Player, String>()
internal val lastFailedCommand = WeakHashMap<Player, LastFailedCommand>()
internal val carriedLastFailedCommand = WeakHashMap<Player, LastFailedCommand>()

internal fun initChat() {
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

    on<ServerMessage> { event ->
        if (!Database.linkedChannels.any { it.canHandle(event.service) }) return@on
        val service = event.service.split('@')[1].split('/')[0].lowercase()
        event.username?.let { username ->
            broadcastChatMessage(service, username, event.message, null, key = "{chat.service}")
        } ?: broadcastChatMessage(service, "", event.message, null, key = "{chat.service.unnamed}")
    }
}

// And it'll only become more complex as the time goes!
fun broadcastChatMessage(service: String?, sender: String, message: String, playerSender: Player? = null, key: String = "{generic.chat}", extras: Cons<L<*>> = {}, filter: Boolf<Player> = { true }) {
    val langCode = auto
    val playerLangCode = playerSender?.let { languageCodeFor(it.locale) }
    if (langCode == null) {
        for (recv in Groups.player) {
            if (!filter[recv]) continue
            val send = Tl.fmt(recv)
                .put("player", sender)
                .put("message", message)
                .apply { if (service != null) put("service", service) }
                .apply { extras.get(this) }
                .done(if (service == null) key else "{generic.chat.service}")
            Call.sendMessage(recv.con, send, message, playerSender)
        }
        return
    }

    val langs = ObjectMap<LanguageCode, Seq<Player>>()
    for (recv in Groups.player) {
        if (!filter[recv]) continue
        val otherCode = languageCodeFor(recv.locale)
        if (otherCode == null || otherCode === playerLangCode) {
            val send = Tl.fmt(recv)
                .put("player", sender)
                .put("message", message)
                .apply { if (service != null) put("service", service) }
                .apply { extras.get(this) }
                .done(if (service == null) key else "{generic.chat.service}")
            Call.sendMessage(recv.con, send, message, playerSender)
            continue
        }
        langs.get(otherCode, ::newSeq)?.add(recv)
    }
    for (entry in langs) {
        val lang = entry.key
        val players = entry.value
        Async.run {
            @OptIn(UnsafeNull::class)
            val message = notnull(message)
            val translation = translateFor(message, langCode, lang)
            if (translation == null) {
                for (player in players) {
                    val send = Tl.fmt(player)
                        .put("player", sender)
                        .put("message", message)
                        .apply { if (service != null) put("service", service) }
                        .apply { extras.get(this) }
                        .done(if (service == null) key else "{generic.chat.service}")
                    Call.sendMessage(player.con, send, message, playerSender)
                }
                return@run
            }
            if (translation.text.equals(message, true)) {
                for (player in players) {
                    val send = Tl.fmt(player)
                        .put("player", sender)
                        .put("message", message)
                        .apply { if (service != null) put("service", service) }
                        .apply { extras.get(this) }
                        .done(if (service == null) key else "{generic.chat.service}")
                    Call.sendMessage(player.con, send, message, playerSender)
                }
                return@run
            }
            for (player in players) {
                val send = Tl.fmt(player)
                    .put("player", sender)
                    .put("message", translation.text)
                    .apply { if (service != null) put("service", service) }
                    .apply { extras.get(this) }
                    .done(if (service == null) key else "{generic.chat.service}")
                Call.sendMessage(player.con, send, message, playerSender)
                Tl.send(player)
                    .put("lang", translation.language)
                    .put("message", message).done("{generic.chat.original}")
            }
        }
    }
}

// src/core/NetServer.java
@OptIn(UnsafeNull::class)
internal fun chatHandleMessage(player: Player?, message: String?) {
    val player = player ?: return
    if (player.con == null) return
    if (!player.con.hasConnected) return
    if (!player.isAdded) return
    if (Time.timeSinceMillis(player.con.connectTime) < 500) return

    if (!player.con.chatRate.allow(2000, Administration.Config.chatSpamLimit.num())) {
        player.con.kick(Packets.KickReason.kick)
        Vars.netServer.admins.blacklistDos(player.con.address)
    }

    var message = notnull(message ?: return).stripInvisible()
    if (message.isEmpty()) return

    if (message.length > 150) {
        throw ValidateException(player, "Player sent a message above the text limit.")
    }

    message = message.replace("\n", "")

    Events.fire(EventType.PlayerChatEvent(player, message))

    if (message.startsWith(Vars.netServer.clientCommands.prefix) && Administration.Config.logCommands.bool()) {
        Log.info("<&fi@: @&fr>", "&lk" + player.plainName(), "&lw$message")
    }

    val response = Vars.netServer.clientCommands.handleMessage(message, player)
    if (response.type == CommandHandler.ResponseType.noCommand) {
        message = Vars.netServer.admins.filterMessage(player, message) ?: return

        Log.info("&fi@: @", "&lc" + player.plainName(), "&lw$message");

        broadcastChatMessage(null, player.sessionData.fullName(), notnull(message), player)

        val msg = ServerMessage(
            Strings.stripColors(message),
            "${player.sessionData.profileId}@mindustry/${Config.i.serverName}",
            player.sessionData.userId,
            Strings.stripColors(player.sessionData.basename),
            null,
            null,
        )

        emit(msg)
    } else {
        if (response.type != CommandHandler.ResponseType.valid) {
            Vars.netServer.invalidHandler.handle(player, response)?.let(player::sendMessage)
        }
    }
}

@Command
@RequiresPermission(PermLevels.moderator)
private fun a(caller: Player, @Rest message: String) {
    val message = message.stripInvisible()
    if (message.isEmpty()) return
    broadcastChatMessage(null, caller.sessionData.fullName(), message, caller,
        key = "{generic.chat.admin} {generic.chat}") { it.permissionLevel >= 100 }
}

@Command
private fun t(caller: Player, @Rest message: String) {
    val message = message.stripInvisible()
    if (message.isEmpty()) return

    val team = caller.team()
    broadcastChatMessage(null, caller.sessionData.fullName(), message, caller,
        key = "[#${caller.team().color}]{generic.chat.team}[] {generic.chat}") { it.team() == team }

    val msg = ServerMessage(
        Strings.stripColors(message),
        "${caller.sessionData.profileId}@mindustry/${Config.i.serverName}/team#${caller.team().id}",
        caller.sessionData.userId,
        Strings.stripColors(caller.sessionData.basename),
        null,
        null,
    )

    emit(msg)
}