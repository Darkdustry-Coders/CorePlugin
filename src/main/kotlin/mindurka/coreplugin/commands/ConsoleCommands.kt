package mindurka.coreplugin.commands

import arc.util.Log
import arc.util.Strings
import buj.tl.Tl
import mindurka.annotations.ConsoleCommand
import mindurka.annotations.Rest
import mindurka.api.Consts
import mindurka.api.OfflinePlayer
import mindurka.build.CommandType
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.ok
import mindurka.coreplugin.sessionData
import mindurka.coreplugin.Build
import mindurka.util.Async
import mindustry.gen.Player
import net.buj.surreal.Query
import java.util.Arrays
import kotlin.time.Duration

/** Display command list or get help for a specific command */
@ConsoleCommand
private fun help(command: String?) {
    if (command == null) {
        Log.info("Commands:")

        for (cmd in Consts.serverControl.handler.commandList) {
            val prefix = "  &b&lb ${cmd.text}"
            val iter = metadataForCommand(cmd.text, CommandType.Console)
            if (iter.hasNext()) {
                for (meta in iter)
                    Log.info("$prefix${if (meta.usage.isBlank()) "" else "&lc&fi ${meta.usage}"}&fr -&lw ${meta.doc.lines().first()}")
            } else
                Log.info("$prefix${if (cmd.paramText.isBlank()) "" else "&lc&fi ${cmd.paramText}"}&fr -&lw ${cmd.description}")
        }
    } else {
        for (cmd in Consts.serverControl.handler.commandList) {
            if (!cmd.text.equals(command, true)) continue

            Log.info("Help for&b&lb ${cmd.text}&fr:")

            val iter = metadataForCommand(cmd.text, CommandType.Console)
            if (iter.hasNext()) for (meta in iter) {
                Log.info("")
                Log.info("Params:&lc&fi ${meta.usage}")
                Log.info(meta.doc)
            } else {
                Log.info("")
                Log.info("Params:&lc&fi ${cmd.paramText}")
                Log.info(cmd.description)
            }

            return
        }

        Log.err("Command &b&lb\"${command}\"&fr not found!")
    }
}

/** Pardon a player */
@ConsoleCommand
private fun pardon(player: OfflinePlayer) = Async.run {
    if (Database.pardon(player.userId)) Log.info("Pardoned player ${Strings.stripColors(player.lastName)}")
    else Log.info("Player ${Strings.stripColors(player.lastName)} was not kicked")
}

/** Kick a player */
@ConsoleCommand
private fun kick(player: OfflinePlayer, duration: Duration?, reason: String) = Async.run {
    Database.kick(player, null, duration, reason)
    player.player?.let { player ->
        player.sessionData.playerLeft(player)
    }
    Log.info("Kicked player ${player.lastName}")
}

/** Unban a player */
@ConsoleCommand
private fun unban(player: OfflinePlayer) = Async.run {
    if (Database.unban(player.userId)) Log.info("Unbanned player ${Strings.stripColors(player.lastName)}")
    else Log.info("Player ${Strings.stripColors(player.lastName)} was not banned")
}

/** Ban a player */
@ConsoleCommand
private fun ban(player: OfflinePlayer, duration: Duration?, reason: String) = Async.run {
    Database.ban(player, null, duration, reason)
    player.player?.let { player ->
        player.sessionData.playerLeft(player)
    }
    Log.info("Banned player ${player.lastName}")
}

/** Execute a SurrealQL query */
@ConsoleCommand
private fun sql(@Rest query: String) = Async.run {
    try {
        for (r in Arrays.stream(Database.abstractQuery(Query(query)).ok())) {
            Log.info(r.result.toString())
        }
    } catch (e: Exception) {
        Log.err(e)
    }
}

/** Localize a string */
@ConsoleCommand
private fun tl(locale: String, @Rest query: String) {
    try {
        Log.info(Tl.fmt(locale).done(query))
    } catch (e: Exception) {
        Log.err(e)
    }
}

/** Set permission */
@ConsoleCommand
private fun setpermlevel(player: Player, level: Int) = Async.run {
    val data = player.sessionData
    data.setPermissionLevel(level)
    Log.info("Set permission level of ${player.plainName()} to ${data.permissionLevel}")
}

/** Display CorePlugin version */
@ConsoleCommand
private fun version() = Async.run {
    Log.info("CorePlugin: ${Build.gitCommit}")
}
