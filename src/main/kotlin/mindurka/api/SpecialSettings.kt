package mindurka.api

import arc.struct.Seq
import arc.util.Strings
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.Config
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Rules
import mindustry.maps.Map
import mindustry.world.Block
import mindustry.world.Tiles
import java.util.Objects

/**
 * This map was not created for Mindurka.
 */
@PublicAPI
class NotMindurkaMap(message: String): Exception(message)

/**
 * Special settings began loading.
 *
 * This is where gamemodes should try loading their own
 * settings and throw [NotMindurkaMap] if they are not
 * valid.
 */
@PublicAPI
class SpecialSettingsLoad(val rc: RulesContext, val currentMap: Boolean)

@PublicAPI
data class RulesContext(
    val rules: Rules,
    val specialSettings: SpecialSettings,
    val mapWidth: Int,
    val mapHeight: Int,

    val warnings: Seq<String> = Seq.with(),
) {
    // TODO: Enforce validation.

    fun r(key: String, de: String): String = rules.tags.get(key, de)
    fun r(key: String, de: Int): Int = rules.tags.getInt(key, de)
    fun r(key: String, de: Float): Float = rules.tags.getFloat(key, de)
    fun r(key: String, de: Boolean): Boolean = rules.tags.get(key)?.let { it == "true" } ?: de
    fun r(key: String, de: Block): Block = rules.tags.get(key)?.let(Vars.content::block) ?: de

    fun warn(text: String) {
        warnings.addUnique(text)
    }
}

@PublicAPI
class SpecialSettings internal constructor(rules: Rules, mapWidth: Int, mapHeight: Int) {
    companion object {
        private var settings: SpecialSettings? = null;

        internal fun `coreplugin$loadSettings`() {
            settings = of(Vars.state.rules, Vars.world.tiles)
        }

        /**
         * Get special settings for current map.
         */
        @PublicAPI
        @JvmStatic
        fun currentMap(): SpecialSettings = Objects.requireNonNull(settings)!!

        /**
         * Get special settings for a map.
         *
         * @throws NotMindurkaMap If map is not valid.
         */
        @PublicAPI
        @JvmStatic
        fun of(rules: Rules, tiles: Tiles) = SpecialSettings(rules, tiles.width, tiles.height)

        /**
         * Get special settings for a map.
         *
         * @throws NotMindurkaMap If map is not valid.
         */
        @PublicAPI
        @JvmStatic
        fun of(map: Map) = SpecialSettings(map.rules(), map.width, map.height)

        @JvmStatic @PublicAPI val PREFIX = "mdrk"
        @JvmStatic @PublicAPI val FORMAT = "$PREFIX.format"
        @JvmStatic @PublicAPI val FORMAT_VER = "1"
        @JvmStatic @PublicAPI val PATCH = "$PREFIX.patch"
        @JvmStatic @PublicAPI val GAMEMODE = "$PREFIX.gamemode"
        @JvmStatic @PublicAPI val GAMEMODE_LEGACY = "mindurkaGamemode"
        @JvmStatic @PublicAPI val OVERDRIVE_IGNORES_CHEAT = "$PREFIX.overdriveIgnoresCheat"
    }

    /** String name of the gamemode. */
    @JvmField @PublicAPI val gamemode: String
    /** Patch version. Used by gamemodes to apply updates to older maps */
    @PublicAPI val patch: Int
    /** Context for rules parsing. */
    @JvmField @PublicAPI val rc: RulesContext = RulesContext(rules, this, mapWidth, mapHeight)
    @JvmField @PublicAPI var overdriveIgnoresCheat = rc.r(OVERDRIVE_IGNORES_CHEAT, false)

    init {
        val tags = rules.tags;

        rc.r(FORMAT, "0").let { version ->
            if (version == "0") throw NotMindurkaMap("Not a Mindurka map. Consider specifying a gamemode using MindurkaCompat")
            if (version != FORMAT_VER) throw NotMindurkaMap("Unknown format version $version. Is gamemode not specified?")
        }

        gamemode = tags.get(GAMEMODE)
            ?: tags.get(GAMEMODE_LEGACY)?.let {
                rc.warn("Using legacy gamemode key. Consider updating the map.")
                it
            }
            ?: throw NotMindurkaMap("Format 1 requires gamemode to be specified!")
        if (gamemode != Config.i().gamemode) throw NotMindurkaMap("This map was made for a different gamemode ('$gamemode' != '${Config.i().gamemode}')")

        patch = rc.r(PATCH, 0)

        emit(SpecialSettingsLoad(rc, rules === Vars.state.rules))
    }
}