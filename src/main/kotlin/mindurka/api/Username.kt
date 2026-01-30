package mindurka.api

import mindurka.annotations.PublicAPI
import mindustry.gen.Player
import java.lang.ref.WeakReference
import java.util.WeakHashMap

class Username(player: Player) {
    companion object {
        private val objects = WeakHashMap<Player, Username>()
        @PublicAPI
        @JvmStatic
        fun of(player: Player): Username = objects.getOrElse(player) { Username(player) }
    }

    val player = WeakReference(player)
    var basename: String = player.name
        set(value) {
            field = value
            updateUsername()
        }
    var id: String = ""
        set(value) {
            field = value
            updateUsername()
        }

    override fun toString() = "$basename [#dadada][[${id.takeLast(6)}]"

    fun updateUsername() {
        player.get()?.name = toString()
    }
}