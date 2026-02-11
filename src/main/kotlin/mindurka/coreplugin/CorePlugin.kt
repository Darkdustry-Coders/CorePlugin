package mindurka.coreplugin

// Keeping those unwrapped for my own sanity.
import arc.Core
import arc.func.Cons
import arc.math.Mathf
import arc.struct.IntMap
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import buj.tl.Tl
import kotlinx.coroutines.future.await
import kotlinx.serialization.ExperimentalSerializationApi
import mindurka.annotations.Command
import mindurka.annotations.ConsoleCommand
import mindurka.annotations.PublicAPI
import mindurka.annotations.RequiresPermission
import mindurka.annotations.Rest
import mindurka.api.BuildEvent
import mindurka.api.BuildEventPost
import mindurka.api.Consts
import mindurka.api.Events
import mindurka.api.Gamemode
import mindurka.api.Lifetime
import mindurka.api.MapHandle
import mindurka.api.PlayerTeamAssign
import mindurka.api.Priority
import mindurka.api.RoundEndEvent
import mindurka.api.SpecialSettings
import mindurka.api.emit
import mindurka.api.interval
import mindurka.api.on
import mindurka.api.timer
import mindurka.build.CommandImpl
import mindurka.build.CommandType
import mindurka.config.SharedConfig
import mindurka.coreplugin.commands.metadataForCommand
import mindurka.coreplugin.commands.registerCommand
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.ok
import mindurka.coreplugin.messages.ServerDown
import mindurka.coreplugin.messages.ServerInfo
import mindurka.coreplugin.messages.ServersRefresh
import mindurka.coreplugin.votes.KickVote
import mindurka.coreplugin.votes.NextMapVote
import mindurka.coreplugin.votes.RtvVote
import mindurka.coreplugin.votes.Vote
import mindurka.coreplugin.votes.VoteFail
import mindurka.ui.handleUiEvent
import mindurka.ui.openMenu
import mindurka.util.Async
import mindurka.util.K
import mindurka.util.ModifyWorld
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
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import net.buj.surreal.Query
import sun.net.www.content.text.plain
import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.roundToInt

object CorePlugin {
    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun init(loader: ClassLoader) {
        Tl.init(loader)

        (loader.getResourceAsStream("META-INF/mindurka.coreplugin.commands")?.readAllBytes()?.toString(charset("UTF-8")) ?: "")
            .split("\n")
            .listIterator()
            .map { it.trim() }
            .filter { !it.isEmpty() }
            .forEach {
                @Suppress("UNCHECKED_CAST")
                val klass = loader.loadClass(it) as Class<CommandImpl>
                val init = klass.constructors.first()
                val cmd = init.newInstance() as CommandImpl
                registerCommand(cmd)
            }
    }
    @JvmStatic
    fun init(klass: Class<mindustry.mod.Plugin>) {
        init(klass.classLoader)
    }

    private fun serverInfo(): ServerInfo? = run {
        if (!Vars.net.active()) return null

        ServerInfo(
            name = Administration.Config.serverName.get() as String,
            motd = Administration.Config.desc.get() as String,
            gamemode = Vars.state.rules.modeName,
            map = Vars.state.map.name(),
            players = Groups.player.size(),
            maxPlayers = Vars.netServer.admins.playerLimit,
            wave = if (Vars.state.rules.waves) -1 else Vars.state.wave,
            maxWaves = if (Vars.state.rules.winWave > 0) Vars.state.rules.winWave else -1,
            ip = "${(+SharedConfig).serverIp}:${Administration.Config.port.get()}",
        )
    }

    @JvmField val epoch = Time.millis()
    @JvmField val protocol: Protocol
    @JvmField var currentGlobalVote: Vote? = null
    @JvmField val teamVotes = IntMap<Vote>()

    init {
        Log.info("Starting CorePlugin")
        Time.mark()

        Database.load()
        Overrides.load()

        val vanillaTeamAssigner = Vars.netServer.assigner
        Vars.netServer.assigner = NetServer.TeamAssigner { player, players ->
            val event = PlayerTeamAssign(
                player,
                players,
                vanillaTeamAssigner.assign(player, players)
            )
            Events.fire(event)
            event.team
        }

        on<EventType.WorldLoadEndEvent>(priority = Priority.Before) {
            SpecialSettings.`coreplugin$loadSettings`()
            if (Gamemode.unlockSpecialBlocks) {
                Vars.state.rules.revealedBlocks.addAll(Blocks.heatReactor, Blocks.slagCentrifuge,
                    Blocks.scrapWallHuge, Blocks.scrapWallLarge, Blocks.scrapWallGigantic, Blocks.thruster, Blocks.scrapWall)
            }
        }
        on<EventType.PlayerLeave> {
            handleUiEvent(it)

            val player = it.player

            Core.app.post {
                if (currentGlobalVote != null)
                    currentGlobalVote!!.playerLeft(SendMessage.All, player)
                if (teamVotes[player.team().id] != null)
                    teamVotes[player.team().id]!!.playerLeft(SendMessage.Multi(player.team()), player)
            }
        }
        on<EventType.MenuOptionChooseEvent>(listener = ::handleUiEvent)
        on<EventType.TextInputEvent>(listener = ::handleUiEvent)

        on<EventType.PlayerJoin> {
            if (currentGlobalVote != null)
                currentGlobalVote!!.updateStatus(SendMessage.One(it.player))
            if (teamVotes[it.player.team().id] != null)
                teamVotes[it.player.team().id]!!.updateStatus(SendMessage.One(it.player))
        }

        on<ServersRefresh> { serverInfo()?.let { info -> RabbitMQ.reply(it, info) } }
        interval(30f) { serverInfo()?.let(::emit)  }

        on<EventType.BlockBuildEndEvent>(priority = Priority.After) {
            if (it.breaking) return@on

            emit(BuildEvent(it.unit, it.tile))
        }
        on<BuildEvent>(priority = Priority.After) {
            val block = it.replacementBlock
            val overlay = it.replacementOverlay
            val floor = it.replacementFloor
            if (block != null) it.tile.setBlock(block, it.replacementTeam, it.replacementRotation)
            if (it.replacementHealth != null && it.tile.build != null) {
                it.tile.build.health = it.replacementHealth as Float
                Vars.indexer.notifyHealthChanged(it.tile.build)
            }
            if (overlay != null) it.tile.setOverlay(overlay)
            if (floor != null) it.tile.setFloor(floor)
            it.replacementCallback?.run()
            timer(0.05f) {
                if (block != null) {
                    ModifyWorld.netBlock(it.tile, Blocks.worldMessage, Team.derelict, 0)
                    ModifyWorld.netBlock(it.tile, it.tile.block(), it.tile.team(), it.tile.build?.rotation ?: 0)
                    val build = it.tile.build
                    if (build != null) ModifyWorld.syncBuild(build)
                }
                if (overlay != null && floor == null)
                    ModifyWorld.netOverlay(it.tile, it.tile.overlay())
                else if (overlay != null || floor != null)
                    ModifyWorld.netFloor(it.tile, it.tile.floor(), it.tile.overlay())
                if (it.replacementHealth != null && it.tile.build != null)
                    Vars.netServer.buildHealthUpdate(it.tile.build)
            }

            BuildEventPost.tile = it.tile
            BuildEventPost.unit = it.unit

            emit(BuildEventPost)
        }

        Vars.netServer.admins.addChatFilter chat@{ player, text ->
            if (currentGlobalVote != null) {
                if (text == "y" || text == "n") {
                    when (currentGlobalVote!!.vote(SendMessage.All, player, text == "y")) {
                        VoteFail.Ok -> {}
                        VoteFail.SameVote -> Tl.send(player).done("{generic.checks.same-vote}")
                        VoteFail.Filtered -> Tl.send(player).done("{generic.checks.vote-filter}")
                    }
                    return@chat null
                }
            }

            text
        }
        Vars.netServer.chatFormatter = formatter@{ player, text ->
            // TODO: Translator.

            for (recv in Groups.player) {
                Call.sendMessage(recv.con, Tl.fmt(recv)
                    .put("player", player.coloredName()).put("message", text).done("{generic.chat}"), text, player)
            }

            null
        }

        modActionsInit()

        Consts.serverControl.gameOverListener = Cons { event ->
            val map = Gamemode.maps.next()
            val key = "{generic.gameover.${if (Vars.state.rules.pvp)
                                               if (event.winner == Team.derelict) "tie"
                                               else "pvp"
                                           else if (Vars.state.rules.infiniteResources) "unexpected"
                                           else if (Vars.state.rules.waves) "waves"
                                           else if (event.winner == Vars.state.rules.defaultTeam) "attackWin"
                                           else "attackLose"}}"
            Log.info(Tl.fmt("c")
                .put("team", event.winner.toString())
                .put("wave", Vars.state.wave.toString())
                .put("map", map.name())
                .done(key)
                .replace(Regex("\n+"), "\n")
                .replace(Regex("\n"), " "))
            for (player in Groups.player) {
                Call.infoMessage(player.con, Tl.fmt(player)
                    .put("team", event.winner.toString())
                    .put("wave", Vars.state.wave.toString())
                    .put("map", map.name())
                    .done(key))
            }
            Vars.state.gameOver = true
            Call.updateGameOver(event.winner)
            Log.info("Selected next map to be ${map.name()}.")
            Consts.serverControl.play {
                emit(RoundEndEvent)
                map.rtv()
            }
        }

        setupTerminalInput()

        RabbitMQ.noop()
        Runtime.getRuntime().addShutdownHook(Thread {
            emit(ServerDown)
            for (player in Groups.player) {
                player.kick("Server closed", 0L)
            }
            Vars.net.closeServer()
            RabbitMQ.flush()
        })

        protocol = Protocol()

        Log.info("CorePlugin loaded in ${Time.elapsed()} ms.");
    }

    /**
     * Try set new global vote.
     *
     * @return `true` on success, `false` otherwise.
     */
    @PublicAPI
    fun startVote(player: Player, vote: Vote): Boolean {
        if (vote.team == null) {
            if (currentGlobalVote != null) return false
            currentGlobalVote = vote
            timer(30f, lifetime = if (vote.cancelsIfRoundChanged) Lifetime.Round else Lifetime.Forever) {
                if (vote.finished) return@timer
                currentGlobalVote = null
                vote.cancelled(SendMessage.All)
            }
            vote.refresh()
            if (!vote.finished) vote.sendUpdateMessage(SendMessage.All)
        } else {
            if (teamVotes[vote.team.id] != null) return false
            teamVotes.put(vote.team.id, vote)
            timer(30f, lifetime = if (vote.cancelsIfRoundChanged) Lifetime.Round else Lifetime.Forever) {
                if (vote.finished) return@timer
                teamVotes.remove(vote.team.id)
                vote.cancelled(SendMessage.Multi(vote.team))
            }
            vote.refresh()
            if (!vote.finished) vote.sendUpdateMessage(SendMessage.Multi(vote.team))
        }
        return true
    }
}

/** List commands */
@Command
private fun help(caller: Player, pageInit: UInt?) = Async.run {
    abstract class Page
    data class HelpMenu(var page: UInt) : Page() {}
    val SelectPage = object : Page() {}

    val permissionLevel = Database.localPlayerData(caller).permissionLevel

    val commands = Vars.netServer.clientCommands.commandList.iterator()
        .filter {
            val commands = metadataForCommand(it.text, CommandType.Player).collect(Seq())
            commands.isEmpty || !commands.iterator().all { it.hidden || it.minPermissionLevel > permissionLevel }
        }
        .map { Tl.fmt(caller).put("command", it.text).done("{commands.help.command}") }
        .collect(Seq())
    val maxPage = ceil(commands.size.toFloat().div(5)).roundToInt().toUInt()
    var page: Page = HelpMenu(run {
        var page = (pageInit ?: 1U)
        if (page == 0U) page = 1U
        if (page > maxPage) page = maxPage
        page
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
    caller.openMenu {
        title("{commands.help.man.title}")
        message("{commands.help.man.message}").put("command", (if (command.startsWith("/") && !command.startsWith("//")) command.substring(1) else command).lowercase())

        option("{generic.close}") { K }
    }
}

/** List maps. */
@Command
private fun maps(caller: Player, pageInit: UInt?) = Async.run {
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
        var page = (pageInit ?: 1U)
        if (page == 0U) page = 1U
        if (page > maxPage) page = maxPage
        page
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

    if (Database.localPlayerData(caller).keySet) {
        Tl.send(caller).done("{commands.setkey.error}")
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

@Command
private fun vote(caller: Player, vote: String) {
    if (vote != "y" && vote != "n") {
        Tl.send(caller).done("{commands.vote.invalid-vote}")
        return
    }

    if (!CorePlugin.teamVotes.containsKey(caller.team().id)) {
        Tl.send(caller).done("{commands.vote.no-vote}")
        return
    }

    CorePlugin.teamVotes[caller.team().id].vote(SendMessage.Multi(caller.team()), caller, vote == "y")
}

@Command
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
@RequiresPermission(100)
private fun a(caller: Player, @Rest message: String) {
    for (player in Groups.player) {
        if (player.permissionLevel < 100) continue
        Tl.send(player).put("player", caller.name).put("message", message).done("{generic.chat.admin} {generic.chat}")
    }
}

@Command
private fun t(caller: Player, @Rest message: String) {
    for (player in Groups.player) {
        if (player.team() !== caller.team()) continue
        Tl.send(player).put("player", caller.name).put("message", message).done("[#${caller.team().color}]{generic.chat.team}[] {generic.chat}")
    }
}

@Command
@RequiresPermission(200)
private fun artv(caller: Player, @Rest map: MapHandle?) {
    val map = map ?: Gamemode.maps.next()
    Consts.serverControl.play(false, map::rtv)
}

@Command
private fun votekick(caller: Player, player: Player, @Rest reason: String) {
    if (caller.checkOnCooldown("/votekick")) return

    if (player === caller) {
        Tl.send(caller).done(if (Mathf.random() < 0.8f) "{commands.votekick.errors.self}"
            else "{commands.votekick.errors.self.special${Mathf.random(0, 2)}}")
        return
    }
    if (player.admin) {
        Tl.send(caller).done("{commands.votekick.errors.admin}")
        return
    }

    if (!CorePlugin.startVote(caller, KickVote(caller, player, reason))) {
        Tl.send(caller).done("{generic.checks.vote}")
        return
    }
    caller.setCooldown("/votekick", 60f)
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
private fun tl(locale: String, @Rest query: String) = Async.run {
    try {
        Log.info(Tl.fmt(locale).done(query))
    } catch (e: Exception) {
        Log.err(e)
    }
}

/** Set permission */
@ConsoleCommand
private fun setpermlevel(player: Player, level: Int) = Async.run {
    val data = Database.localPlayerData(player)
    data.setPermissionLevel(player, level)
    Log.info("Set permission level of ${player.plainName()} to ${data.permissionLevel}")
}
