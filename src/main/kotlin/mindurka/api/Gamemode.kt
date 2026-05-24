package mindurka.api

import arc.files.Fi
import arc.func.Prov
import arc.struct.Seq
import buj.tl.Tl
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.teamAssigned
import mindurka.util.SafeFilename
import mindurka.util.child
import mindurka.util.map
import mindustry.Vars
import mindustry.game.MapObjectives.FlagObjective
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.io.SaveIO
import mindustry.io.SaveMeta
import java.util.WeakHashMap

@PublicAPI
enum class MapFlags {
    /** Forbid /1va (one vs all). Only has effect if gamemode allows /1va. */
    No1va,
}

@PublicAPI
interface MapHandle {
    /** Get the name of the specified map. */
    fun name(): String
    /** Get the description of the specified map. */
    fun description(): String
    /** Get the author of the specified map. */
    fun author(): String
    /** Get the width of the specified map. */
    fun width(): Int
    /** Get the height of the specified map. */
    fun height(): Int
    /** Obtain flags for the specified map. */
    fun flags(): Iterator<MapFlags>
    /**
     * Load this map.
     *
     * Must be executed immediately.
     */
    fun rtv()
}

@PublicAPI
interface MapManager {
    /** Current map. */
    fun current(): MapHandle
    /** All available maps. */
    fun maps(): Iterator<MapHandle>
    /** All available saves. */
    fun saves(): Iterator<MapHandle>

    /**
     * Whether this gamemode support saves.
     *
     * Setting this to `false` disables /votesave, /voteload, etc.
     */
    fun hasSaves(): Boolean

    /** Get the next map. */
    fun next(): MapHandle

    /** Refresh maps. */
    fun setNext(map: MapHandle)

    /**
     * Make a save with the provided name.
     *
     * Caller must prove that the filename is valid.
     */
    fun save(name: SafeFilename)

    /** Refresh maps. */
    fun refresh()
}

/**
 * Default map handler that simply wraps over Mindustry's API.
 */
@PublicAPI
open class DefaultMapManager : MapManager {
    private val mapHandles = WeakHashMap<mindustry.maps.Map, MapHandle>()
    private fun mapHandleFor(map: mindustry.maps.Map): MapHandle {
        val handle = mapHandles[map]
        if (handle != null) return handle
        val newHandle = DefaultMapHandle(map)
        mapHandles[map] = newHandle
        return newHandle
    }

    protected companion object {
        var nextMap: MapHandle? = null
    }

    open class DefaultMapHandle(val map: mindustry.maps.Map) : MapHandle {
        val flags = Seq<MapFlags>()

        init {
            for (obj in map.rules().objectives) {
                if (obj is FlagObjective) {
                    if (obj.details == "no1va" || obj.flag == "no1va" || obj.text() == "no1va") {
                        flags.addUnique(MapFlags.No1va)
                        continue
                    }
                }
            }
        }

        override fun name(): String = map.name()
        override fun description(): String = map.description()
        override fun author(): String = map.author()
        override fun width(): Int = map.width
        override fun height(): Int = map.height
        override fun flags(): Iterator<MapFlags> = flags.iterator()
        override fun rtv() { Vars.world.loadMap(map) }
    }

    open class DefaultSaveHandle(val fi: Fi, val meta: SaveMeta, val flags: Seq<MapFlags>) : MapHandle {
        override fun name(): String = meta.map.name()
        override fun description(): String = meta.map.description()
        override fun author(): String = meta.map.author()
        override fun width(): Int = meta.map.width
        override fun height(): Int = meta.map.height
        override fun flags(): Iterator<MapFlags> = flags.iterator()
        override fun rtv() { SaveIO.load(fi) }
    }

    protected val saves = Seq<DefaultSaveHandle>()

    protected fun reloadSaves() {
        val dir = Vars.dataDirectory.child("saves")
        if (!dir.exists()) return
        saves.clear()
        for (save in dir.list()) {
            try {
                val meta = SaveIO.getMeta(save)
                val flags = Seq<MapFlags>()
                for (obj in meta.map.rules().objectives) {
                    if (obj is FlagObjective) {
                        if (obj.details == "no1va" || obj.flag == "no1va" || obj.text() == "no1va") {
                            flags.addUnique(MapFlags.No1va)
                            continue
                        }
                    }
                }
                saves.add(DefaultSaveHandle(save, meta, flags))
            } catch (_: Exception) {}
        }
    }

    init {
        reloadSaves()
    }

    override fun current(): MapHandle = mapHandleFor(Vars.state.map)
    override fun maps(): Iterator<MapHandle> =
        Vars.maps.customMaps().iterator().map { mapHandleFor(it) }
    override fun saves(): Iterator<MapHandle> = saves.iterator()
    override fun hasSaves(): Boolean = true
    override fun next(): MapHandle {
        val nextMap = nextMap
        if (nextMap != null) {
            DefaultMapManager.nextMap = null
            return nextMap
        }
        val map = Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map)
        return mapHandleFor(map)
    }

    override fun setNext(map: MapHandle) {
        nextMap = map
    }
    override fun save(name: SafeFilename) {
        Vars.dataDirectory.child("saves").mkdirs()
        SaveIO.write(Vars.dataDirectory.child("saves").child(name))
    }
    override fun refresh() {
        Vars.maps.reload()
    }
}

/** Manager for spectator mode. */
interface SpectateManager {
    /** Check if a player is spectating. */
    operator fun get(player: Player): Boolean
    /** Set spectator mode for a player. */
    operator fun set(player: Player, spectating: Boolean)
    /** Update spectator status for a player. */
    fun playerTeamChanged(player: Player, previous: Team)
    /**
     * Check if a team is a spectator team.
     *
     * Spectator teams are considered to be service teams, thus players can't normally join them.
     */
    fun isSpectatorTeam(team: Team): Boolean

    /**
     * Restore spectator status for a player.
     */
    fun spectateRestore(player: Player, ogTeam: Team?): Team
    /** Reset all round data. */
    fun reset()
}

class DefaultSpectateManager: SpectateManager {
    val spectatorTeam: Team = Team.all[69]
    val ogTeams = WeakHashMap<Player, Team>()

    override fun get(player: Player): Boolean = player.team() == spectatorTeam
    override fun set(player: Player, spectating: Boolean) {
        val rn = get(player)
        if (rn == spectating) return

        if (spectating) {
            if (teamAssigned(player)) ogTeams[player] = player.team()
            player.clearUnit()
            player.team(spectatorTeam)
            Tl.send(player).done("{generic.spectating}")
        } else {
            player.team(ogTeams[player]?.let { team ->
                if (team.data().isAlive) team else null
            } ?: Vars.netServer.assignTeam(player))
            Tl.send(player).done("{generic.no-longer-spectating}")
        }
    }

    override fun isSpectatorTeam(team: Team): Boolean = team == spectatorTeam
    override fun spectateRestore(player: Player, ogTeam: Team?): Team {
        ogTeam?.let { ogTeams[player] = it }
        return spectatorTeam
    }
    override fun reset() {
        ogTeams.clear()
    }

    override fun playerTeamChanged(player: Player, previous: Team) {
        if ((player.team() == spectatorTeam) == (previous == spectatorTeam)) return

        if (previous == spectatorTeam) Tl.send(player).done("{generic.no-longer-spectating}")
        else Tl.send(player).done("{generic.spectating}")
    }
}

/** Gamemode info. */
@PublicAPI
object Gamemode {
    /** Whether 1va is enabled. */
    @JvmStatic
    var allow1va: Boolean = false
    /** Map manager. */
    @JvmStatic
    var maps: MapManager = DefaultMapManager()
    /** Manager for spectating. */
    @JvmStatic
    var spectate: SpectateManager = DefaultSpectateManager()

    /** Whether admin commands like /unit are enabled. */
    @JvmField
    var adminCommands: Boolean = true
    /**
     * Whether secret blocks need to be unblocked.
     *
     * Only has an effect at the start of the round.
     */
    /** Whether playing on the server updates statistics. */
    @JvmField
    var hasStats = true
    @JvmField
    var unlockSpecialBlocks = true
    /**
     * Whether teams should be restored upon rejoin.
     */
    @JvmField
    var restoreTeams = true
    /**
     * Whether teams should be properly randomized when a round starts.
     *
     * Will only actually do this is there are more than one team a player could be in.
     */
    @JvmField
    var randomizeTeams = true
    /** Enable /rtv and /vnm. */
    @JvmField
    var enableRtv = true
    /** Enable /vnw. */
    @JvmField
    var enableVnw = false
    /**
     * Enable /surrender.
     *
     * This only takes effect if the server is launched in pvp mode.
     */
    @JvmField
    var enableSurrender = true
    /** Enable /spectate. */
    @JvmField
    var enableSpectate = true
    /** Send players to hub on shutdown. */
    @JvmField
    var sendHub = true
    /** Emit a packet to make players rejoin the server once it has restarted. */
    @JvmField
    var sendBringBackPacket = true

    @JvmField
    var defaultPatch: Prov<String>? = null

    /** Tools available to regular players (whitelist). Default: all. */
    @JvmField
    var unbannedTools: java.util.EnumSet<mindurka.coreplugin.SSTool> = java.util.EnumSet.allOf(mindurka.coreplugin.SSTool::class.java)

    /** Tools banned for everyone regardless of permissions (blacklist). */
    @JvmField
    var bannedTools: java.util.EnumSet<mindurka.coreplugin.SSTool> = java.util.EnumSet.noneOf(mindurka.coreplugin.SSTool::class.java)

    /** Initialize CorePlugin. */
    @JvmStatic
    fun init(loader: ClassLoader) {
        CorePlugin.init(loader)
    }
    /** Initialize CorePlugin. */
    @JvmStatic
    @Deprecated("Provide a prefixed ClassLoader instead")
    fun init(cls: Class<*>) {
        init(cls.classLoader)
    }
}
