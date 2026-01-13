package mindurka.api

import mindurka.annotations.PublicAPI
import mindurka.coreplugin.Config
import mindustry.Vars
import mindustry.game.EventType
import mindustry.maps.Map
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
class SpecialSettingsLoad(val settings: SpecialSettings)

@PublicAPI
class SpecialSettings internal constructor(val map: Map) {
    companion object {
        private var settings: SpecialSettings? = null;

        internal fun `coreplugin$loadSettings`() {
            settings = of(Vars.state.map)
        }

        /**
         * Get special settings for current map.
         */
        @PublicAPI
        fun currentMap(): SpecialSettings = Objects.requireNonNull(settings)!!

        /**
         * Get special settings for a map.
         *
         * @throws NotMindurkaMap If map is not valid.
         */
        @PublicAPI
        fun of(map: Map) = SpecialSettings(map)
    }

    /**
     * String name of the gamemode.
     */
    @PublicAPI
    val gamemode: String

    init {
        val tags = map.rules().tags;

        var formatVersion: String =
            tags.get("mdrk.format") ?: throw NotMindurkaMap("Not a Mindurka map. Consider specifying a gamemode using MindurkaCompat")
        if (formatVersion != "1") throw NotMindurkaMap("Unsupported format version $formatVersion")

        gamemode = tags.get("mdrk.gamemode") ?: throw NotMindurkaMap("Not a Mindurka map. Consider specifying a gamemode using MindurkaCompat")
        if (gamemode != Config.i().gamemode) throw NotMindurkaMap("This map was made for a different gamemode ($gamemode vs ${Config.i().gamemode})")

        emit(SpecialSettingsLoad(this))
    }
}