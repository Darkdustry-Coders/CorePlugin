package mindurka.coreplugin

// Keeping those unwrapped for my own sanity.
import arc.func.Cons
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import buj.tl.Tl
import kotlinx.coroutines.future.await
import kotlinx.serialization.ExperimentalSerializationApi
import mindurka.annotations.Command
import mindurka.api.BuildEvent
import mindurka.api.BuildEventPost
import mindurka.api.Consts
import mindurka.api.Events
import mindurka.api.Gamemode
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
import mindurka.coreplugin.messages.ServerDown
import mindurka.coreplugin.messages.ServerInfo
import mindurka.coreplugin.messages.ServersRefresh
import mindurka.ui.handleUiEvent
import mindurka.ui.openMenu
import mindurka.util.Async
import mindurka.util.K
import mindurka.util.ModifyWorld
import mindurka.util.UnreachableException
import mindurka.util.all
import mindurka.util.collect
import mindurka.util.filter
import mindurka.util.join
import mindurka.util.map
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

    @JvmField val protocol: Protocol

    init {
        Log.info("Starting CorePlugin")
        Time.mark()

        Overrides.load();

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
        on<EventType.PlayerLeave>(listener = ::handleUiEvent)
        on<EventType.MenuOptionChooseEvent>(listener = ::handleUiEvent)
        on<EventType.TextInputEvent>(listener = ::handleUiEvent)

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
}

/** List commands */
@Command
private fun help(caller: Player, pageInit: UInt?) = Async.run {
    abstract class Page
    data class HelpMenu(var page: UInt) : Page() {}
    val SelectPage = object : Page() {}
    
    val commands = Vars.netServer.clientCommands.commandList.iterator()
        .filter {
            val commands = metadataForCommand(it.text, CommandType.Player).collect(Seq())
            commands.isEmpty || !commands.iterator().all { it.hidden }
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
    Tl.send(caller).put("command", command).done("{commands.help.title-cmd}")
}

/** List maps. */
@Command
private fun maps(caller: Player) {
    caller.sendMessage("De maps:")
    for (map in Gamemode.maps.maps()) {
        caller.sendMessage(map.name())
    }
}
