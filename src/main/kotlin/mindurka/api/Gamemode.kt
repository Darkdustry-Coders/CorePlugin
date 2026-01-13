package mindurka.api

import arc.files.Fi
import arc.struct.Seq
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.CorePlugin
import mindurka.util.SafeFilename
import mindurka.util.child
import mindurka.util.map
import mindurka.util.unreachable
import mindustry.Vars
import mindustry.game.MapObjectives.FlagObjective
import mindustry.game.Team
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

/**
 * Called when player is assigned a team.
 *
 * This event is synchronous. If a value is set after the event is completed, it will have no
 * effect.
 */
@PublicAPI
data class PlayerAssignTeamEvent(
    val player: Player,
    val players: Iterable<Player>,
    var team: Team,
) {
    @PublicAPI fun team(): Team = team
    @PublicAPI
    fun team(team: Team) {
        this.team = team
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

    /**
     * Whether secret blocks need to be unblocked.
     *
     * Only has an effect at the start of the round.
     */
    @JvmField
    var unlockSpecialBlocks = true
    /**
     * Whether teams should be restored upon rejoin.
     */
    @JvmField
    var restoreTeams = true
    /** Enable /rtv. */
    @JvmField
    var enableRtv = true

    /** Initialize CorePlugin. */
    @JvmStatic
    fun init(loader: ClassLoader) {
        CorePlugin.init(loader)
    }
    /** Initialize CorePlugin. */
    @JvmStatic
    fun init(cls: Class<*>) {
        init(cls.classLoader)
    }
}
