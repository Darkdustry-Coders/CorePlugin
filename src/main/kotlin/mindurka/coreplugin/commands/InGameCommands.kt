package mindurka.coreplugin.commands

import arc.math.Mathf
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import buj.tl.Tl
import kotlinx.coroutines.future.await
import mindurka.annotations.Command
import mindurka.annotations.EnabledIf
import mindurka.annotations.Hidden
import mindurka.annotations.RequiresPermission
import mindurka.annotations.Rest
import mindurka.api.Consts
import mindurka.api.Gamemode
import mindurka.api.MapHandle
import mindurka.build.CommandType
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.carriedLastFailedCommand
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.DatabaseScripts
import mindurka.coreplugin.database.PermLevels
import mindurka.coreplugin.database.ok
import mindurka.coreplugin.hasMindurkaCompat
import mindurka.coreplugin.lastFailedCommand
import mindurka.coreplugin.sessionData
import mindurka.coreplugin.votes.KickVote
import mindurka.coreplugin.votes.NextMapVote
import mindurka.coreplugin.votes.RtvVote
import mindurka.ui.openMenu
import mindurka.util.Async
import mindurka.util.AsyncCall
import mindurka.util.K
import mindurka.util.SendMessage
import mindurka.util.UnreachableException
import mindurka.util.all
import mindurka.util.checkOnCooldown
import mindurka.util.collect
import mindurka.util.filter
import mindurka.util.join
import mindurka.util.map
import mindurka.util.minutes
import mindurka.util.permissionLevel
import mindurka.util.setCooldown
import mindurka.util.skip
import mindurka.util.take
import mindurka.util.unreachable
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import net.buj.surreal.Query
import kotlin.collections.iterator
import kotlin.math.ceil
import kotlin.math.roundToInt

/** List commands */
@Command
private fun help(caller: Player, pageInit: Int?) = Async.run async@{
    abstract class Page
    data class HelpMenu(var page: UInt) : Page() {}
    val SelectPage = object : Page() {}

    val permissionLevel = caller.sessionData.permissionLevel

    val commands = Vars.netServer.clientCommands.commandList.iterator()
        .filter {
            val commands = metadataForCommand(it.text, CommandType.Player).collect(Seq())
            commands.isEmpty || !commands.iterator().all { it.hidden || it.minPermissionLevel > permissionLevel }
        }
        .map { Tl.fmt(caller).put("command", it.text).done("{commands.help.command}") }
        .collect(Seq())
    val maxPage = ceil(commands.size.toFloat().div(5)).roundToInt().toUInt()
    var page: Page = HelpMenu(run {
        var page = (pageInit ?: 1)
        if (page == 0) page = 1

        if (page > maxPage.toInt()) {
            Tl.send(caller).done("{commands.help.invalid-page}")
            return@async
        }
        if (page < 0) {
            Tl.send(caller).done("{commands.help.negative-page}")
            return@async
        }

        page.toUInt()
    })

    caller.openMenu {
        when (val currentPage = page) {
            is HelpMenu -> {
                title("{commands.help.title-page}")
                message = commands.iterator().skip((currentPage.page - 1U) * 5U).take(5U).join("\n\n")

                group {
                    optionText("") {
                        if (currentPage.page != 1U) currentPage.page--
                        else currentPage.page = maxPage
                        rerenderDialog()
                    }
                    optionText("[white]${currentPage.page}/$maxPage") {
                        page = SelectPage
                        rerenderDialog()
                    }
                    optionText("") {
                        if (currentPage.page != maxPage) currentPage.page++
                        else currentPage.page = 1U
                        rerenderDialog()
                    }
                }

                option("{generic.close}") { K }
            }
            SelectPage -> {
                title("{commands.help.select-page.title}")

                var i = 0U
                while (i <= maxPage) {
                    group {
                        for (o in 0..<3) {
                            i++
                            val switchTo = i
                            if (i <= maxPage) optionText("$i") {
                                page = HelpMenu(switchTo)
                                rerenderDialog()
                            }
                            else optionText("")
                        }
                    }
                }

                option("{generic.close}") { K }
            }
            else -> throw UnreachableException()
        }
    }.await()
}

/** List commands. */
@Command
private fun help(caller: Player, command: String) {
    if (Vars.netServer.clientCommands.commandList.none { it.text == command }) {
        Tl.send(caller)
            .put("command", command)
            .done("{commands.help.unknown-command}")
        return
    }

    caller.openMenu {
        title("{commands.help.man.title}")
        message("{commands.help.man.message}").put(
            "command",
            (if (command.startsWith("/") && !command.startsWith("//")) command.substring(1) else command).lowercase()
        )

        option("{generic.close}") { K }
    }
}

/** List maps. */
@Command
private fun maps(caller: Player, pageInit: Int?) = Async.run async@{
    abstract class Page
    data class MapsMenu(var page: UInt) : Page() {}
    val SelectPage = object : Page() {}

    val source = Gamemode.maps.maps()
    val maps = Seq<String>()
    var id = 1
    for (map in source) {
        maps.add(
            Tl.fmt(caller)
                .put("id", (id++).toString())
                .put("map", map.name())
                .put("author", map.author())
                .put("width", map.width().toString())
                .put("height", map.height().toString())
                .done("{commands.maps.map}")
        )
    }

    val maxPage = ceil(maps.size.toFloat().div(5)).roundToInt().toUInt()

    var page: Page = MapsMenu(run {
        var page = (pageInit ?: 1)
        if (page == 0) page = 1

        if (page > maxPage.toInt()) {
            Tl.send(caller).done("{commands.help.invalid-page}")
            return@async
        }
        if (page < 0) {
            Tl.send(caller).done("{commands.help.negative-page}")
            return@async
        }

        page.toUInt()
    })

    caller.openMenu {
        when (val currentPage = page) {
            is MapsMenu -> {
                title("{commands.maps.title-page}")
                message = maps.iterator().skip((currentPage.page - 1U) * 5U).take(5U).join("\n\n")

                group {
                    optionText("") {
                        if (currentPage.page != 1U) currentPage.page--
                        else currentPage.page = maxPage
                        rerenderDialog()
                    }
                    optionText("[white]${currentPage.page}/$maxPage") {
                        page = SelectPage
                        rerenderDialog()
                    }
                    optionText("") {
                        if (currentPage.page != maxPage) currentPage.page++
                        else currentPage.page = 1U
                        rerenderDialog()
                    }
                }

                option("{generic.close}") { K }
            }
            SelectPage -> {
                title("{commands.maps.select-page.title}")

                var i = 0U
                while (i <= maxPage) {
                    group {
                        for (o in 0..<3) {
                            i++
                            val switchTo = i
                            if (i <= maxPage) optionText("$i") {
                                page = MapsMenu(switchTo)
                                rerenderDialog()
                            }
                            else optionText("")
                        }
                    }
                }

                option("{generic.close}") { K }
            }
            else -> throw UnreachableException()
        }
    }.await()
}

@Command
private fun setkey(caller: Player) = Async.run {
    if (!caller.hasMindurkaCompat) {
        Tl.send(caller).done("{commands.setkey.no-mdc}")
        return@run
    }

    if (caller.sessionData.keySet) {
        Tl.send(caller).done("{commands.setkey.error}")
        return@run
    }

    if (caller.sessionData.permissionLevel > 0 && !caller.admin) {
        Tl.send(caller).done("{commands.setkey.admin-protection}")
        return@run
    }

    if (caller.openMenu {
        title("{commands.setkey.title}")
        message("{commands.setkey.message}")

        group {
            option("{generic.cancel}") { false }
            option("{generic.ok}") { true }
        }
    }.await() != true) return@run

    try {
        Database.setKey(caller)
        Tl.send(caller).done("{commands.setkey.ok}")
    } catch (t: Throwable) {
        caller.sendMessage("[scarlet]An error has occurred while processing command.")
        Log.err(t)
    }
}

@Hidden
@Command
private fun fuck(caller: Player) {
    val command = carriedLastFailedCommand[caller] ?: run {
        Tl.send(caller).done("{commands.fuck.no-command-stored}")
        return
    }
    carriedLastFailedCommand.remove(caller)

    val ru = "/${command.name} ${command.args}"

    Log.info(">>> $ru")

    val resp = Vars.netServer.clientCommands.handleMessage(ru, caller)
    when (resp.type) {
        CommandHandler.ResponseType.valid -> {}
        CommandHandler.ResponseType.noCommand -> unreachable()
        CommandHandler.ResponseType.unknownCommand -> Tl.send(caller).done("{commands.fuck.whoops}")
        CommandHandler.ResponseType.fewArguments -> Tl.send(caller).put("cause", Tl.parse("{generic.commands.end-of-input}")).done("{generic.checks.invalid-arguments}")
        CommandHandler.ResponseType.manyArguments -> Tl.send(caller).put("cause", Tl.parse("{generic.commands.too-many-arguments}")).done("{generic.checks.invalid-arguments}")
    }
}

@Command
private fun vote(caller: Player, vote: String) {
    if (!CorePlugin.teamVotes.containsKey(caller.team().id)) {
        Tl.send(caller).done("{commands.vote.no-vote}")
        return
    }

    if (vote == "c") {
        if (!caller.admin) Tl.send(caller).done("{generic.checks.admin-action-permission}")
        else {
            for (x in Groups.player.iterator().filter { it.team() == caller.team() }) {
                Tl.send(x).put("admin", caller.coloredName()).done("{generic.vote.team.cancelled}")
            }
            CorePlugin.teamVotes[caller.team().id].finished = true
            CorePlugin.teamVotes.remove(caller.team().id)
        }
        return
    }

    if (vote != "y" && vote != "n") {
        Tl.send(caller).done("{commands.vote.invalid-vote}")
        return
    }

    CorePlugin.teamVotes[caller.team().id].vote(SendMessage.Multi(caller.team()), caller, vote == "y")
}

@Command
@EnabledIf(RtvEnabled::class)
private fun rtv(caller: Player, @Rest map: MapHandle?) {
    if (caller.checkOnCooldown("/rtv")) return

    val map = map ?: Gamemode.maps.next()

    if (!CorePlugin.startVote(caller, RtvVote(map, caller))) {
        Tl.send(caller).done("{generic.checks.vote}")
        return
    }
    caller.setCooldown("/rtv", minutes(5f))
}

@Command
@EnabledIf(RtvEnabled::class)
private fun vnm(caller: Player, @Rest map: MapHandle?) {
    if (caller.checkOnCooldown("/vnm")) return

    val map = map ?: Gamemode.maps.next()

    if (!CorePlugin.startVote(caller, NextMapVote(map, caller))) {
        Tl.send(caller).done("{generic.checks.vote}")
        return
    }
    caller.setCooldown("/vnm", minutes(5f))
}

@Command
@RequiresPermission(PermLevels.admin)
private fun artv(caller: Player, @Rest map: MapHandle?) {
    val map = map ?: Gamemode.maps.next()
    Consts.serverControl.play(false, map::rtv)
}

@Command
private fun votekick(caller: Player, player: Player, @Rest reason: String) {
    if (caller.checkOnCooldown("/votekick")) return

    if (player.team() !== caller.team()) {
        Tl.send(caller).done("{commands.votekick.errors.team}")
        return
    }
    if (player === caller) {
        Tl.send(caller).done(if (Mathf.random() < 0.8f) "{commands.votekick.errors.self}"
        else "{commands.votekick.errors.self.special${Mathf.random(0, 2)}}")
        return
    }
    if (player.admin) {
        Tl.send(caller).done("{commands.votekick.errors.admin}")
        return
    }
    if (player.team().data().players.size < 3) {
        Tl.send(caller).done("{commands.votekick.errors.players}")
        return
    }

    if (!CorePlugin.startVote(caller, KickVote(caller, player, reason))) {
        Tl.send(caller).done("{generic.checks.vote}")
        return
    }
    caller.setCooldown("/votekick", 60f)
}

@Command
private fun nick(caller: Player, @Rest name: String?) = Async.run {
    if (caller.checkOnCooldown("/nick")) return@run

    val data = caller.sessionData

    if (name != null) {
        if (Strings.stripColors(name).length > 32) {
            Tl.send(caller).done("{commands.nick.errors.too-long}")
            return@run
        }
        if (name.length > 128) {
            Tl.send(caller).done("{commands.nick.errors.colors-too-long}")
            return@run
        }
        if (name == data.customname) {
            Tl.send(caller).done("{commands.nick.warns.same-name}")
            return@run
        }
    } else {
        if (data.customname == null) {
            Tl.send(caller).done("{commands.nick.warns.nothing-reset}")
            return@run
        }
    }

    data.customname = name
    data.updateUsername()
    Database.abstractQuery(Query(DatabaseScripts.playerSetNick)
        .x("profile", data.profileId)
        .apply { if (name != null) x("name", name) }).ok()

    caller.setCooldown("/nick", 3f)
}

@Command
private fun hub(caller: Player) = Async.run {
    if (caller.checkOnCooldown("/hub")) return@run

    for (server in CorePlugin.hubServers.copy()) {
        val i = server.ip.indexOf(':')
        val host = server.ip.substring(0, i)
        val port = server.ip.substring(i + 1).toInt()

        if (AsyncCall.connect(caller, host, port)) return@run
    }

    Tl.send(caller).done("{commands.hub.errors.hub-down}")

    caller.setCooldown("/hub", 10f)
}
