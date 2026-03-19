package mindurka.coreplugin

// Keeping those unwrapped for my own sanity.
import arc.func.Cons
import arc.math.Mathf
import arc.struct.IntMap
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import buj.tl.Tl
import kotlinx.serialization.ExperimentalSerializationApi
import mindurka.annotations.PublicAPI
import mindurka.api.BuildEvent
import mindurka.api.BuildEventPost
import mindurka.api.Consts
import mindurka.api.Events
import mindurka.api.Gamemode
import mindurka.api.Lifetime
import mindurka.api.PlayerTeamAssign
import mindurka.api.Priority
import mindurka.api.RoundEndEvent
import mindurka.api.SpecialSettings
import mindurka.api.emit
import mindurka.api.interval
import mindurka.api.on
import mindurka.api.timer
import mindurka.build.CommandImpl
import mindurka.config.SharedConfig
import mindurka.coreplugin.commands.registerCommand
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.messages.ServerDown
import mindurka.coreplugin.messages.ServerInfo
import mindurka.coreplugin.messages.ServersRefresh
import mindurka.coreplugin.votes.Vote
import mindurka.ui.handleUiEvent
import mindurka.util.Async
import mindurka.util.ModifyWorld
import mindurka.util.SendMessage
import mindurka.util.filter
import mindurka.util.map
import mindurka.util.random
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.SetTileCallPacket
import mindustry.net.Administration
import mindustry.world.Block
import mindustry.world.blocks.environment.StaticWall
import kotlin.math.min

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
    @JvmField val hubServers = Seq<HubServer>()

    class HubServer(val name: String, var ip: String) {
        var lastRecvd: Long = System.nanoTime()
    }

    private var fakeBlockKind: Block? = null
    private class FakeBlock(
        var x: Int,
        var y: Int,
    )
    private val fakeBlockPos = ObjectMap<Player, FakeBlock>()

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

        on<ServerInfo> { msg ->
            val name = RabbitMQ.sentBy(msg)!!
            if (msg.gamemode == "Hub") {
                hubServers.find { it.name == name }
                    ?.apply {
                        ip = msg.ip
                        lastRecvd = System.nanoTime()
                    }
                    ?: run {
                        hubServers.add(HubServer(name, msg.ip))
                        null
                    }
            }
        }
        on<ServerDown> { msg ->
            hubServers.removeAll { it.name == RabbitMQ.sentBy(msg) }
        }
        interval(60f) {
            val time = System.nanoTime()
            hubServers.removeAll { time - it.lastRecvd > 30_000_000_000 }
        }

        on<EventType.WorldLoadEvent>(priority = Priority.Lowest) {
            fakeBlockPos.clear()

            if (Vars.state.patcher.patches.size > 0 && Vars.state.patcher.patches[0].name == "Mindurka Default Patch") {
                Vars.state.patcher.patches.remove(0)
            }

            fakeBlockKind = run {
                for (shift in 0..min(Vars.world.width(), Vars.world.height()) / 2) {
                    for (x in shift..<(Vars.world.width() - shift)) {
                        val tile = Vars.world.tile(x, shift)
                        if (tile.block() !is StaticWall) continue
                        if (tile.block().size != 1) continue
                        return@run tile.block()
                    }
                    for (x in shift..<(Vars.world.width() - shift)) {
                        val tile = Vars.world.tile(x, Vars.world.height() - shift - 1)
                        if (tile.block() !is StaticWall) continue
                        if (tile.block().size != 1) continue
                        return@run tile.block()
                    }

                    for (y in shift..<(Vars.world.height() - shift)) {
                        val tile = Vars.world.tile(shift, y)
                        if (tile.block() !is StaticWall) continue
                        if (tile.block().size != 1) continue
                        return@run tile.block()
                    }
                    for (y in shift..<(Vars.world.height() - shift)) {
                        val tile = Vars.world.tile(Vars.world.width() - shift - 1, y)
                        if (tile.block() !is StaticWall) continue
                        if (tile.block().size != 1) continue
                        return@run tile.block()
                    }
                }

                null
            }

            if (fakeBlockKind != null) interval(0.125f, lifetime = Lifetime.Round) {
                Groups.player.each {
                    val tileX = Mathf.floor(it.mouseX / Vars.tilesize)
                    val tileY = Mathf.floor(it.mouseY / Vars.tilesize)

                    val fakePos = fakeBlockPos[it] ?: return@each
                    val dst = Mathf.dst(fakePos.x.toFloat(), fakePos.y.toFloat(), tileX.toFloat(), tileY.toFloat())
                    if (dst > 16) return@each

                    val packet = SetTileCallPacket()
                    packet.tile = Vars.world.tile(fakePos.x, fakePos.y)
                    packet.block = fakeBlockKind
                    packet.team = Team.derelict
                    packet.rotation = 0
                    it.con.send(packet, true)

                    val newTile = Vars.world.tiles.iterator()
                        .filter { it.block() == fakeBlockKind }
                        .random() ?: run {
                            fakeBlockPos.remove(it)
                            return@each
                    }

                    val packet2 = SetTileCallPacket()
                    packet2.tile = newTile
                    packet2.block = Vars.content.block("legacy-mech-pad")
                    packet2.team = Team.derelict
                    packet2.rotation = 0
                    it.con.send(packet2, true)

                    fakePos.x = newTile.x.toInt()
                    fakePos.y = newTile.y.toInt()
                }
            }

            Vars.state.patcher.apply(Vars.state.patcher.patches.map { it.patch }.apply { insert(0, run {
                val patch = StringBuilder()

                patch.append("name: Mindurka Default Patch\n")
                Gamemode.defaultPatch?.let { patch.append(it.get()).append('\n') }
                fakeBlockKind?.let { real ->
                    patch.append("block.legacy-mech-pad: {\n")
                    patch.append("    region: block-${real.name}-full\n")
                    patch.append("    uiIcon: block-${real.name}-ui\n")
                    patch.append("    localizedName: ${real.name.replace(Regex("-[a-z]")) {
                        " ${it.value[1].uppercase()}"
                    }.replaceFirstChar { it.uppercase() }}\n")
                    patch.append("    drawTeamOverlay: false\n")
                    patch.append("    placeablePlayer: false\n")
                    patch.append("    replaceable: false\n")
                    patch.append("    consumesPower: false\n")
                    patch.append("    connectedPower: false\n")
                    patch.append("    unloadable: false\n")
                    patch.append("    acceptsItems: false\n")
                    patch.append("    rotateDraw: false\n")
                    patch.append("    rebuildable: false\n")
                    patch.append("    canOverdrive: false\n")
                    patch.append("    inlineDescription: false\n")
                    patch.append("    targetable: false\n")
                    patch.append("    hideDatabase: true\n")
                    patch.append("    solid: true\n")
                    patch.append("    forceDark: true\n")
                    patch.append("    privileged: true\n")
                    patch.append("}\n")
                }

                Log.info("$patch")

                patch.toString()
            }) })
        }
        Vars.netServer.admins.addActionFilter { act ->
            if (!(act.type == Administration.ActionType.breakBlock && act.block == fakeBlockKind) || fakeBlockKind == null) return@addActionFilter true

            val packet = SetTileCallPacket()
            packet.tile = act.tile
            packet.block = fakeBlockKind
            packet.team = Team.derelict
            packet.rotation = 0
            act.player.con.send(packet, true)

            fakeBlockKind?.let { real ->
                val tile = Vars.world.tiles.iterator()
                    .filter { it.block() == real }
                    .random() ?: run {
                        fakeBlockPos.remove(act.player)
                        return@let
                }
                val packet = SetTileCallPacket()
                packet.tile = tile
                packet.block = Vars.content.block("legacy-mech-pad")
                packet.team = Team.derelict
                packet.rotation = 0
                val blockPos = fakeBlockPos[act.player] ?: run {
                    val x = FakeBlock(tile.x.toInt(), tile.y.toInt())
                    fakeBlockPos.put(act.player, x)
                    x
                }
                blockPos.x = tile.x.toInt()
                blockPos.y = tile.y.toInt()
                act.player.con.send(packet, true)
            }

            false
        }

        on<EventType.PlayerConnectionConfirmed> { event ->
            fakeBlockKind?.let { real ->
                val tile = Vars.world.tiles.iterator()
                    .filter { it.block() == real }
                    .random() ?: return@let
                val packet = SetTileCallPacket()
                packet.tile = tile
                packet.block = Vars.content.block("legacy-mech-pad")
                packet.team = Team.derelict
                packet.rotation = 0
                val blockPos = FakeBlock(
                    x = tile.x.toInt(),
                    y = tile.y.toInt()
                )
                fakeBlockPos.put(event.player, blockPos)
                event.player.con.send(packet, true)
            }
        }

        on<EventType.PlayerLeave> {
            fakeBlockPos.remove(it.player)

            handleUiEvent(it)

            val player = it.player

            Async.run {
                if (currentGlobalVote != null)
                    currentGlobalVote!!.playerLeft(SendMessage.All, player)
                if (teamVotes[player.team().id] != null)
                    teamVotes[player.team().id]!!.playerLeft(SendMessage.Multi(player.team()), player)
                player.sessionData.releaseLocks()
            }
        }
        on<EventType.MenuOptionChooseEvent>(listener = ::handleUiEvent)
        on<EventType.TextInputEvent>(listener = ::handleUiEvent)

        on<EventType.PlayerJoin> {
            if (currentGlobalVote != null)
                currentGlobalVote!!.updateStatus(SendMessage.One(it.player))
            if (teamVotes[it.player.team().id] != null)
                teamVotes[it.player.team().id]!!.updateStatus(SendMessage.One(it.player))

            Call.menu(it.player.con, Int.MAX_VALUE,
                Tl.fmt(it.player).done("{generic.welcome-message-title}"),
                Tl.fmt(it.player).done("{generic.welcome-message}"),
                arrayOf(arrayOf(Tl.fmt(it.player).done("{generic.close}"))))
        }

        on<ServersRefresh> { serverInfo()?.let { info -> Async.run { RabbitMQ.reply(it, info) } } }
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

        modActionsInit()
        chatInit()

        interval(30f) { Async.run {
            for (player in Groups.player) {
                player.sessionData.flush()
            }
        } }

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

        protocol = Protocol()

        RabbitMQ.noop()
        Runtime.getRuntime().addShutdownHook(Thread {
            emit(ServerDown)
            for (player in Groups.player) {
                player.kick("Server closed", 0L)
            }
            Vars.net.closeServer()

            RabbitMQ.flush()
        })

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
