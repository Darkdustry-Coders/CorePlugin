package mindurka.util

import arc.math.Mathf
import arc.struct.ObjectIntMap
import arc.util.Strings
import arc.util.Time
import buj.tl.Tl
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.database.Database
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@JvmOverloads
fun unreachable(message: String = "Unreachable reached!"): Nothing = throw UnreachableException(message)
class UnreachableException(message: String = "Unreachable reached!"): RuntimeException(message)

// TODO: Make spectator team configurable
val Team.isServiceTeam get() = this == Team.derelict || this == Team.all[69]

private val commandCooldowns = WeakHashMap<Player, ObjectIntMap<String>>()
fun Player.cooldownDuration(name: String): Float = commandCooldowns[this]?.get(name)?.let { max(it - (Time.millis() - CorePlugin.epoch).toInt(), 0) / 1000f } ?: 0f
fun Player.onCooldown(name: String): Boolean = cooldownDuration(name) > 0.0001f
fun Player.setCooldown(name: String, cooldown: Float) = commandCooldowns.getOrPut(this) { ObjectIntMap() }.put(name, (Time.millis() - CorePlugin.epoch).toInt() + cooldown.times(1000).toInt())
fun Player.checkOnCooldown(name: String): Boolean {
    if (!onCooldown(name)) return false

    Tl.send(this).done("{generic.checks.cooldown}")

    return true
}
val Player.permissionLevel: Int get() {
    val data = Database.localPlayerData(this)
    if (!data.keySet && !Vars.netServer.admins.isAdmin(uuid(), usid())) return 0
    return data.permissionLevel
}

fun minutes(time: Float) = time * 60f
fun hours(time: Float) = time * 60f * 60f
fun days(time: Float) = time * 60f * 60f * 24f
fun years(time: Float) = time * yearsT

private const val yearsT = 60 * 60 * 24 * 365.25f
private const val daysT = 60 * 60 * 24f
/** Return duration as string. */
fun durationToString(time: Float): String {
    val builder = StringBuilder()
    var time = time

    val neg = time < 0
    if (neg) time *= -1

    // An approximation ofc.
    if (time >= yearsT) {
        val years = floor(time % yearsT).toInt()
        time -= yearsT * years
        builder.append(years).append("y")
    }

    if (time >= daysT) {
        val days = floor(time % daysT).toInt()
        time -= daysT * days
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(days).append("d")
    }

    if (time >= 3600) {
        val hours = floor(time % 3600).toInt()
        time -= 3600f * hours
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(hours).append("h")
    }

    if (time >= 60) {
        val minutes = floor(time % 60).toInt()
        time -= 60f * minutes
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(minutes).append("m")
    }

    if (time >= 0.5f) {
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(ceil(time).toInt()).append("s")
    }

    if (builder.isEmpty()) {
        if (time >= 0.001f) {
            time.times(1000).toInt()
            builder.append(ceil(time).toInt()).append("ms")
        } else if (time <= 0f) builder.append("0s") else builder.append("~0").append("s")
    }

    return if (neg) "-$builder" else builder.toString()
}

fun findPlayer(arg: String, checkUuid: Boolean): Player? {
    if (Strings.canParseInt(arg)) {
        val shortId = Strings.parseInt(arg)
        for (player in Groups.player)
            if (Database.localPlayerData(player).shortId == shortId)
                return player
    }

    for (player in Groups.player) {
        val id = Database.localPlayerData(player).id
        if (id.endsWith(arg)) return player
    }

    for (player in Groups.player) {
        val id = Database.localPlayerData(player).id
        if (id.contains(arg)) return player
    }

    for (player in Groups.player) {
        if (player.plainName().lowercase().contains(arg.lowercase())) return player
    }

    return null
}