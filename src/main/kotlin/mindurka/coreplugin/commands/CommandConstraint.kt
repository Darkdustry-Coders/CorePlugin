package mindurka.coreplugin.commands

import mindurka.annotations.BaseCommandConstraint
import mindurka.api.Gamemode
import mindustry.Vars
import mindustry.gen.Player

/**
 * A constraint on a command.
 *
 * If a constraint fails, the command will not be available.
 *
 * Any object extending this interface must contain a static `obtain(): CommandConstraint` method.
 */
interface CommandConstraint: BaseCommandConstraint {
    fun enabled(player: Player): Boolean
}

/**
 * Whether /rtv and /surrender are enabled.
 */
object RtvEnabled: CommandConstraint {
    @JvmStatic
    fun obtain(): CommandConstraint = RtvEnabled

    override fun enabled(player: Player): Boolean = Gamemode.enableRtv
}

/**
 * Whether /vnw is enabled.
 */
object VnwEnabled: CommandConstraint {
    @JvmStatic
    fun obtain(): CommandConstraint = RtvEnabled

    override fun enabled(player: Player): Boolean = Gamemode.enableVnw
}

/**
 * Whether /surrender is enabled.
 */
object SurrenderEnabled: CommandConstraint {
    @JvmStatic
    fun obtain(): CommandConstraint = RtvEnabled

    override fun enabled(player: Player): Boolean = Gamemode.enableSurrender && Vars.state.rules.pvp
}
