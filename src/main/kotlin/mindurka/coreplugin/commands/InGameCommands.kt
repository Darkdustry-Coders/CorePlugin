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
import mindurka.api.OfflinePlayer
import mindurka.api.RoundEndEvent
import mindurka.api.emit
import mindurka.build.CommandType
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.carriedLastFailedCommand
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.DatabaseScripts
import mindurka.coreplugin.database.PermLevels
import mindurka.coreplugin.database.ok
import mindurka.coreplugin.hasMindurkaCompat
import mindurka.coreplugin.sessionData
import mindurka.coreplugin.votes.KickVote
import mindurka.coreplugin.votes.NextMapVote
import mindurka.coreplugin.votes.RtvVote
import mindurka.coreplugin.votes.SurrenderVote
import mindurka.ui.openMenu
import mindurka.util.Async
import mindurka.util.K
import mindurka.util.SendMessage
import mindurka.util.UnreachableException
import mindurka.util.all
import mindurka.util.checkOnCooldown
import mindurka.util.collect
import mindurka.util.copyClipboard
import mindurka.util.durationToTlString
import mindurka.util.filter
import mindurka.util.join
import mindurka.util.map
import mindurka.util.minutes
import mindurka.util.setCooldown
import mindurka.util.skip
import mindurka.util.take
import mindurka.util.unreachable
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.Call
import net.buj.surreal.Query
import java.util.Arrays
import kotlin.collections.iterator
import kotlin.math.ceil
import kotlin.math.roundToInt

@Command
private fun stats(caller: Player, @Rest target: OfflinePlayer?) {
    val target = target ?: OfflinePlayer.of(caller)

    Async.run {
        target.player?.sessionData?.flush()

        val resp = Database.abstractQuery(Query(DatabaseScripts.statsScript).x("profile", target.profileId)).ok().run { this[size - 1] }

        // 0 - main page
        // 1..(1+n) - info for nerds
        var page = 0U

        caller.openMenu {
            if (page == 0U) {
                title("{commands.stats.dialog-title}")
                message("{commands.stats.dialog-content}").run {
                    put("player", target.lastName)
                    put("playtime", durationToTlString(resp.result.at("play_time").asLong().toFloat()))
                    put("user-id", target.userId)
                    put("profile-id", target.profileId)
                    put("blocks-placed", resp.result.at("blocks_placed").asLong().toString())
                    put("blocks-broken", resp.result.at("blocks_broken").asLong().toString())
                    put("games-played", resp.result.at("games_played").asLong().toString())
                    put("waves", resp.result.at("waves").asLong().toString())
                    put("gamemode-stats", buildString {
                        for (gamemode in resp.result.at("gamemodes").asJsonList()) {
                            if (!isEmpty()) append("\n")
                            append(Tl.fmt(caller)
                                .put("name", if (gamemode.at("name").isNull) "???" else gamemode.at("name").asString())
                                .put("color", if (gamemode.at("color").isNull) "red" else gamemode.at("color").asString())
                                .put("emoji", if (gamemode.at("emoji").isNull) "\uE815" else gamemode.at("emoji").asString())
                                .put("wins", gamemode.at("wins").asLong().toString())
                                .put("ovas", gamemode.at("ovas").asLong().toString())
                                .put("color", gamemode.at("color").asString())
                                .done("{commands.stats.dialog-gamemode-stats}"))
                        }

                        if (isEmpty()) append(Tl.fmt(caller).done("{commands.stats.whoops}"))
                    })
                }

                group {
                    option("{commands.stats.dialog-profile-id}") {
                        caller.copyClipboard(target.profileId)
                        K
                    }
                    option("{commands.stats.dialog-user-id}") {
                        caller.copyClipboard(target.userId)
                        K
                    }
                }

                if (resp.result.at("gamemodes").asJsonList().isNotEmpty())
                    option("{commands.stats.dialog-more-data}") {
                        page = 1U
                        rerenderDialog()
                    }

                option("{generic.close}") { K }
            } else {
                val entries = resp.result.at("gamemodes").asJsonList()
                val entry = entries[page.toInt() - 1]

                title("{commands.stats.dialog-data-title}").apply {
                    put("name", entry.at("name").asString())
                    put("color", entry.at("color").asString())
                    put("emoji", entry.at("emoji").asString())
                }
                message = buildString {
                    val wins = entry.at("wins").asLong()
                    val ovas = entry.at("ovas").asLong()
                    append(Tl.fmt(caller).put("wins", wins.toString()).put("ovas", ovas.toString()).done("{commands.stats.dialog-data-wins}"))

                    val blocksPlaced = entry.at("blocks_placed").asLong()
                    append(Tl.fmt(caller).put("blocks-placed", blocksPlaced.toString()).done("\n{commands.stats.dialog-data-blocks-placed}"))

                    val blocksBroken = entry.at("blocks_broken").asLong()
                    append(Tl.fmt(caller).put("blocks-broken", blocksBroken.toString()).done("\n{commands.stats.dialog-data-blocks-broken}"))

                    val games = entry.at("games").asLong()
                    append(Tl.fmt(caller).put("games", games.toString()).done("\n{commands.stats.dialog-data-games}"))

                    val waves = entry.at("waves").asLong()
                    if (waves != 0L) append(Tl.fmt(caller).put("waves", waves.toString()).done("\n{commands.stats.dialog-data-waves}"))

                    append(Tl.fmt(caller).put("playtime", durationToTlString(entry.at("play_time").asInteger().toFloat())).done("\n\n{commands.stats.dialog-data-playtime}"))

                    // TODO: Rank.
                }

                if (entries.size != 1) {
                    option("[gray]<<<") {
                        page--
                        if (page == 0U) page = entries.size.toUInt()
                        rerenderDialog()
                    }
                    option("[gray]>>>") {
                        page++
                        if (page > entries.size.toUInt()) page = 1U
                        rerenderDialog()
                    }
                }

                option("{generic.close}") {
                    page = 0U
                    rerenderDialog()
                }
            }
        }
    }
}

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
            commands.isEmpty || !commands.iterator().all { it.hidden || it.minPermissionLevel > permissionLevel ||
                Arrays.stream(it.constraints).anyMatch { !(it as CommandConstraint).enabled(caller) }
            }
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
@EnabledIf(SurrenderEnabled::class)
private fun surrender(caller: Player) {
    if (caller.checkOnCooldown("/surrender")) return

    if (!CorePlugin.startVote(caller, SurrenderVote(caller))) {
        Tl.send(caller).done("{generic.checks.vote}")
        return
    }
    caller.setCooldown("/surrender", minutes(5f))
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
    Async.run {
        val map = map ?: Gamemode.maps.next()

        if (caller.openMenu<Boolean?> {
            title("{commands.artv.dialog.title}")
            message("{commands.artv.dialog.contents}").put("map", map.name())

            group {
                option("{generic.ok}") { true }
                option("{generic.cancel}") { false }
            }
        }.await() != true) return@run

        if (!caller.admin) return@run

        Consts.serverControl.play(false) {
            emit(RoundEndEvent)
            map.rtv()
        }
    }
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
