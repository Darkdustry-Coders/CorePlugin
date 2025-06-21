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
    /** Load this map. */
    fun rtv()
    /** Set this as the next map. */
    fun setNext()
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

    /** Load the next map. */
    fun rtv()
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
    protected companion object {
        var nextSave: Fi? = null
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
        override fun rtv() {
            mindustry.server.ServerControl.instance.play { Vars.world.loadMap(map) }
        }
        override fun setNext() {
            Vars.maps.setNextMapOverride(map)
        }
    }

    open class DefaultSaveHandle(val fi: Fi, val meta: SaveMeta, val flags: Seq<MapFlags>) : MapHandle {
        override fun name(): String = meta.map.name()
        override fun description(): String = meta.map.description()
        override fun author(): String = meta.map.author()
        override fun width(): Int = meta.map.width
        override fun height(): Int = meta.map.height
        override fun flags(): Iterator<MapFlags> = flags.iterator()
        override fun rtv() {
            mindustry.server.ServerControl.instance.play { SaveIO.load(fi) }
        }
        override fun setNext() {
            nextSave = fi
        }
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
            } catch (_: Exception) {}
        }
    }

    init {
        reloadSaves()
    }

    override fun current(): MapHandle = DefaultMapHandle(Vars.state.map)
    override fun maps(): Iterator<MapHandle> =
        Vars.maps.customMaps().iterator().map { DefaultMapHandle(it) }
    override fun saves(): Iterator<MapHandle> = saves.iterator()
    override fun hasSaves(): Boolean = true
    override fun rtv() {
        if (nextSave != null) {
            val save = nextSave ?: unreachable()
            mindustry.server.ServerControl.instance.play { SaveIO.load(save) }
            nextSave = null
            return
        }
        val map = Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map)
        mindustry.server.ServerControl.instance.play { Vars.world.loadMap(map) }
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
