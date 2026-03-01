package mindurka.coreplugin

// Keeping those unwrapped for my own sanity.
import arc.Core
import arc.func.Cons
import arc.struct.IntMap
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
import mindurka.coreplugin.votes.VoteFail
import mindurka.ui.handleUiEvent
import mindurka.util.ModifyWorld
import mindurka.util.SendMessage
import mindurka.util.filter
import mindurka.util.map
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration

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

            player.sessionData.releaseLocks()

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