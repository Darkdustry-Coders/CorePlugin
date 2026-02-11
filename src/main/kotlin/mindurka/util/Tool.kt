package mindurka.util

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
import java.net.URLEncoder
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
private const val weeksT = 60 * 60 * 24f * 7
private const val daysT = 60 * 60 * 24f
/** Return duration as string. */
fun durationToString(time: Float): String {
    if (time.isInfinite()) return if (time > 0) "inf" else "-inf"

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

    if (time >= weeksT) {
        val weeks = floor(time % weeksT).toInt()
        time -= weeksT * weeks
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(weeks).append("w")
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
@Throws(FormatException::class)
fun stringToDuration(duration: String): Float {
    if (duration == "inf") return Float.POSITIVE_INFINITY
    if (duration == "-inf") return Float.NEGATIVE_INFINITY
    if (duration == "0") return 0f

    val duration = duration.trim()
    var neg = false
    var value = 0f
    var ptr = 0

    if (duration.isEmpty()) throw FormatException("Duration cannot be empty")
    if (duration.startsWith("-")) {
        ptr++
        neg = true
    }

    while (ptr < duration.length) {
        while (ptr < duration.length && duration[ptr].isWhitespace()) ptr++

        if (ptr >= duration.length) unreachable("Reached the end of a duration string")
        if (duration[ptr] == '~') {
            if (++ptr >= duration.length || duration[ptr] != '0') throw FormatException("Sub-ms value must be '~0s'")
            if (++ptr >= duration.length || duration[ptr] != 's') throw FormatException("Sub-ms value must be '~0s'")
            ptr++
            continue
        }

        val start = ptr
        while (ptr < duration.length && (duration[ptr] in '0'..'9')) ptr++
        if (ptr < duration.length && (duration[ptr] == '.')) {
            ptr++
            while (ptr < duration.length && (duration[ptr] in '0'..'9')) ptr++
        }

        val s = duration.substring(start, ptr)
        if (!Strings.canParseFloat(s)) throw FormatException("'$s' is not a valid number")
        val sv = Strings.parseFloat(s)

        while (ptr < duration.length && duration[ptr].isWhitespace()) ptr++
        if (ptr >= duration.length) throw FormatException("Missing time unit")
        when (val timeUnit = duration[ptr++]) {
            's' -> value += sv
            'm' -> if (ptr < duration.length && duration[ptr] == 's') {
                ptr++
                value += sv / 1000
            } else value += sv * 60
            'h' -> value += sv * 60 * 60
            'd' -> value += sv * daysT
            'w' -> value += sv * weeksT
            'y' -> value += sv * yearsT
            else -> throw FormatException("Invalid time unit: $timeUnit")
        }
    }

    return value * if (neg) -1 else 1
}

fun findPlayer(arg: String, checkUuid: Boolean): Player? {
    if (arg.length > 1 && arg.startsWith('#') && Strings.canParseInt(arg.substring(1))) {
        val id = Strings.parseInt(arg.substring(1))
        Groups.player.find { it.id() == id }?.let { return it }
    }

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

fun encodeURIComponent(text: String): String = URLEncoder.encode(text, Vars.charset)