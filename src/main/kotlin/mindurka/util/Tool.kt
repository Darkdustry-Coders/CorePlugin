package mindurka.util

import arc.struct.ObjectIntMap
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Strings
import arc.util.Time
import buj.tl.Script
import buj.tl.Tl
import mindurka.coreplugin.CorePlugin
import mindurka.coreplugin.sessionData
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.Posc
import mindustry.world.Block
import mindustry.world.Tile
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

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
    val data = sessionData
    if (!data.keySet && !Vars.netServer.admins.isAdmin(uuid(), usid())) return 0
    return data.permissionLevel
}

fun sha256(data: String) = sha256(data.toByteArray(Vars.charset))
fun sha256(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(data)
    val hash = StringBuilder(digest.size)
    for (x in digest) {
        val most = x.div(16)
        val least = x.rem(16)

        hash.append((if (most >= 10) most + 'a'.code - 10 else most + '0'.code).toChar())
        hash.append((if (least >= 10) least + 'a'.code - 10 else least + '0'.code).toChar())
    }
    return hash.toString()
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
        val years = floor(time / yearsT).toInt()
        time -= yearsT * years
        builder.append(years).append("y")
    }

    if (time >= weeksT) {
        val weeks = floor(time / weeksT).toInt()
        time -= weeksT * weeks
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(weeks).append("w")
    }

    if (time >= daysT) {
        val days = floor(time / daysT).toInt()
        time -= daysT * days
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(days).append("d")
    }

    if (time >= 3600) {
        val hours = floor(time / 3600).toInt()
        time -= 3600f * hours
        if (!builder.isEmpty()) builder.append(" ")
        builder.append(hours).append("h")
    }

    if (time >= 60) {
        val minutes = floor(time / 60).toInt()
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
        } else if (time <= 0f) builder.append("0s") else builder.append("~0s")
    }

    return if (neg) "-$builder" else builder.toString()
}
fun durationToTlString(time: Float): Script {
    if (time.isInfinite()) return Tl.parse(if (time > 0) "{generic.duration.inf}" else "-{generic.duration.inf}")

    var time = time

    val neg = time < 0
    if (neg) time *= -1

    val str = StringBuilder()
    var partsAdded = 0

    // An approximation ofc.
    if (time >= yearsT) {
        val years = floor(time / yearsT).toInt()
        time %= yearsT
        str.append("$years {generic.duration.year${if (years == 1) "" else "s"}}")
        partsAdded++
    }

    if (time >= weeksT) {
        val weeks = floor(time / weeksT).toInt()
        time %= weeksT
        if (!str.isEmpty()) str.append(" ")
        str.append("$weeks {generic.duration.week${if (weeks == 1) "" else "s"}}")
        partsAdded++
    }

    if (time >= daysT) {
        val days = floor(time / daysT).toInt()
        time %= daysT
        if (!str.isEmpty()) str.append(" ")
        str.append("$days {generic.duration.day${if (days == 1) "" else "s"}}")
        partsAdded++
    }

    if (time >= 3600) {
        val hours = floor(time / 3600).toInt()
        time %= 3600
        if (partsAdded < 3) {
            if (!str.isEmpty()) str.append(" ")
            str.append("$hours {generic.duration.hour${if (hours == 1) "" else "s"}}")
        }
        partsAdded++
    }

    if (time >= 60) {
        val minutes = floor(time / 60).toInt()
        time %= 60
        if (partsAdded < 3) {
            if (!str.isEmpty()) str.append(" ")
            str.append("$minutes {generic.duration.minute${if (minutes == 1) "" else "s"}}")
        }
        partsAdded++
    }

    if (time >= 0.5f) {
        val seconds = ceil(time).toInt()
        time %= 1
        if (partsAdded < 3) {
            if (!str.isEmpty()) str.append(" ")
            str.append("$seconds {generic.duration.second${if (seconds == 1) "" else "s"}}")
        }
        partsAdded++
    }

    if (partsAdded >= 1) return Tl.parse("${if (neg) "-" else ""}$str")
    return Tl.parse(if (time >= 0.001f) {
        time.times(1000).toInt()
        val ms = ceil(time).toInt()

        "${if (neg) "-" else ""}$ms {generic.duration.milli${if (ms == 1) "" else "s"}}"
    } else "~0 {generic.duration.seconds}")
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
    if (arg.isBlank()) return null

    if (arg.startsWith('#')) arg.substring(1).toIntOrNull()?.let { id ->
        Groups.player.find { it.id == id }?.let { return it }
    }

    if (checkUuid) {
        for (player in Groups.player) {
            if (arg == player.con.uuid) return player
        }
        for (player in Groups.player) {
            if (arg == player.con.usid) return player
        }
    }

    arg.toLongOrNull()?.let { shortId ->
        for (player in Groups.player)
            if (player.sessionData.shortId == shortId)
                return player
    }

    for (player in Groups.player) {
        val id = player.sessionData.profileId
        if (id.endsWith(arg)) return player
    }

    for (player in Groups.player) {
        if (player.plainName().lowercase().contains(arg.lowercase())) return player
    }

    return null
}

fun encodeURIComponent(text: String): String = URLEncoder.encode(text, Vars.charset)
inline fun <T, Y> ObjectMap<T, Y>.getOrPut(key: T, supplier: () -> Y): Y = run {
    if (containsKey(key)) this[key]
    else {
        val x = supplier()
        put(key, x)
        x
    }
}

@OptIn(UnsafeNull::class)
inline fun <reified T> newSeq(capacity: Int = 16, ordered: Boolean = true): Seq<T> {
    return Seq.createUnsafe(Array<T>(capacity) { nodecl() }, 0, ordered)
}
inline fun <reified T> seqOf(vararg objs: T): Seq<T> = Seq<T>(objs)
inline fun <reified T> seqBy(size: Int, initfun: (Int) -> T): Seq<T> {
    val seq = newSeq<T>(size)
    for (i in 0..<size) seq.add(initfun(i))
    return seq
}

fun keyHasHeadByte(key: String, head: String): Boolean {
    val end = head.length
    if (!key.startsWith(head)) return false
    return when (key.length - head.length) {
        1 -> ('1'..'9').contains(key[end])
        2 -> ('1'..'9').contains(key[end]) && ('0'..'9').contains(key[end + 1])
        3 -> ('1'..'2').contains(key[end]) && (
            ('0'..'4').contains(key[end + 1]) && ('0'..'9').contains(key[end + 2]) ||
                key[end + 1] == '5' && ('0'..'4').contains(key[end + 2])
            )
        else -> false
    }
}
fun keyHeadByte(key: String, head: String): Int = Strings.parseInt(key, 10, 0, head.length, key.length)

inline val Block.blockOffset: Int get() = -(size / 2)
inline fun Block.eachBlockOffset(cb: (Int, Int) -> Unit) = eachBlockOffset(0, 0, cb)
inline fun Block.eachBlockOffset(from: Tile, cb: (Int, Int) -> Unit) = eachBlockOffset(from.x.toInt(), from.y.toInt(), cb)
inline fun Block.eachBlockOffset(from: Posc, cb: (Int, Int) -> Unit) = eachBlockOffset(from.tileX(), from.tileY(), cb)
inline fun Block.eachBlockOffset(startX: Int, startY: Int, cb: (Int, Int) -> Unit) {
    for (x in 0..<size) for (y in 0..<size) cb(startX + blockOffset + x, startY + blockOffset + y)
}

inline fun Player.sendBinaryPacket(packet: String, data: ByteArray, reliable: Boolean = true) = if (reliable) Call.clientBinaryPacketReliable(con, packet, data)
                                                                                                else Call.clientBinaryPacketUnreliable(con, packet, data)
inline fun Player.sendPacket(packet: String, data: String, reliable: Boolean = true) = if (reliable) Call.clientPacketReliable(con, packet, data)
                                                                                       else Call.clientPacketUnreliable(con, packet, data)
inline fun Player.copyClipboard(data: String) = Call.copyToClipboard(con, data)

inline val Player.ip: String get() = if (con.address.contains('/')) con.address.substring(con.address.lastIndexOf('/') + 1) else con.address