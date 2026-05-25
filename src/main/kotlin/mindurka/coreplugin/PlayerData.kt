package mindurka.coreplugin

import arc.struct.Seq
import arc.util.Log
import mindurka.annotations.Command
import mindurka.annotations.PublicAPI
import mindurka.annotations.Rest
import mindurka.api.Gamemode
import mindurka.api.interval
import mindurka.coreplugin.database.Database
import mindurka.ui.openMenu
import mindurka.ui.openText
import mindurka.util.Async
import mindurka.util.K
import mindurka.util.newSeq
import mindustry.gen.KickCallPacket
import mindustry.gen.KickCallPacket2
import mindurka.coreplugin.mindurkacompat.Version as MdcVersion
import mindustry.gen.Player
import mindustry.net.Packets
import mjson.Json
import net.buj.surreal.Query
import java.lang.ref.WeakReference
import java.security.PublicKey
import java.util.WeakHashMap
import kotlin.time.TimeSource

enum class TranslatorStyle(@JvmField val shortName: String) {
    /** Translator is disabled. */
    Disabled("none"),
    /**
     * Multiline style.
     *
     * ```
     * [player]: Hello
     * Original (en): Hello
     * ```
     */
    Multiline("multiline"),
    /**
     * Shorter style hiding original translation.
     *
     * ```
     * [player] (en): Hello
     * ```
     */
    Short("short"),
}

/** Player session data. */
class PlayerData private constructor(player: Player) {
    companion object {
        internal const val defaultString = ""
        private val cache = WeakHashMap<Player, PlayerData>()
        private class GcEntry(@JvmField val player: WeakReference<Player>, @JvmField val data: PlayerData)
        private val gc = newSeq<GcEntry>(ordered = false)

        init {
            val removed = newSeq<GcEntry>(ordered = false)
            interval(15f) {
                gc.removeAll {
                    if (it.player.get() == null) {
                        if (it.data.exitHandledCorrectly) true
                        else {
                            removed.add(it)
                            true
                        }
                    } else false
                }
                if (!removed.isEmpty) {
                    val entries = removed.copy()
                    Log.err("${entries.size} entr${if (entries.size == 1) "y" else "ies"} were not handled correctly!")
                    removed.clear()
                    Async.run {
                        for (entry in entries) {
                            entry.data.releaseLocks()
                        }
                    }
                }
            }
        }

        /**
         * Obtain session data for player.
         *
         * If none exists, new data will be created.
         */
        @PublicAPI
        @JvmStatic
        fun of(player: Player): PlayerData = cache.getOrPut(player) { PlayerData(player) }

        /**
         * Obtain session data for player.
         *
         * If none exists, returns `null`.
         */
        @PublicAPI
        @JvmStatic
        fun ofOrNull(player: Player): PlayerData? = cache[player]
    }

    init {
        gc.add(GcEntry(WeakReference(player), this))
    }

    private var exitHandledCorrectly = false

    @JvmField
    val basename = player.coloredName()
    /** A name set with /nick. Overrides [basename]. */
    var customname: String? = null
        set(value) {
            field = value
            updateUsername()
        }
    @JvmField
    val player = WeakReference(player)

    @JvmField
    var mindurkaCompatVersion = 0
    @JvmField
    var publicKey: PublicKey? = null

    @JvmField
    var keySet = false

    var permissionLevel = 0
        private set
    suspend fun setPermissionLevel(level: Int) {
        Database.setPermissionLevel(profileId, level)
        permissionLevel = level
        player.get()?.admin = level >= 100
    }
    /**
     * Set permission level without updating the database.
     */
    internal fun `unsafe$rawSetPermissionLevel`(level: Int) {
        permissionLevel = level
    }

    @JvmField var extraBlocksPlaced = 0
    @JvmField var extraBlocksBroken = 0
    @JvmField var extraGamesPlayed = 0
    @JvmField var extraWaves = 0
    @JvmField var extraWins = 0
    var shortId: Long? = null
        set(value) {
            field = value
            updateUsername()
        }
    var profileId: String = defaultString
        set(value) {
            field = value
            updateUsername()
        }
    @JvmField
    var userId: String = defaultString

    val usid = player.usid()
    val uuid = player.uuid()

    fun idString() = shortId?.toString() ?: profileId.takeLast(6)
    /** Just the name. */
    val name: String get() = customname ?: basename
    /** Full username of a player as displayed in chat and tab. */
    fun fullName() = "$name [#dadada][[${idString()}]"
    /** Simple username of a player as displayed in kick messages. */
    fun simpleName() = "$name [#dadada][[${idString()}]"

    fun updateUsername() {
        player.get()?.name = fullName()
    }

    /** Metadata sent with the `schemesize.available` packet.
     *
     * Right now the packet just sends a 0, so it's reserved for future use.
     * */
    @JvmField
    var schemeSizeMetadata: K? = null
    /**
     * Scheme size subtitle.
     *
     * `schemesize.available` may not get sent, so it's outside of [schemeSizeMetadata].
     */
    @JvmField
    var schemeSizeSubtitle: String? = null
    /**
     * Translator style.
     */
    @JvmField
    var translatorStyle = TranslatorStyle.Multiline

    /** Per-player banned SS tools. Combined with gamemode flags at send time. */
    @JvmField
    var ssBannedTools: java.util.EnumSet<SSTool> = java.util.EnumSet.noneOf(SSTool::class.java)

    private val locks = newSeq<RabbitMQLock>()
    suspend fun addLock(lock: RabbitMQLock) {
        if (exitHandledCorrectly) {
            lock.release()
            return
        }

        locks.add(lock)
    }
    suspend fun releaseLocks() {
        val locks = locks.copy()
        this.locks.clear()
        for (lock in locks) {
            lock.release()
        }
    }

    internal fun handleUpdateOutput(out: Json) {
        var updateUsername = false

        if (out.has("new_short_id") && !out.at("new_short_id").isNull) {
            shortId = out.at("new_short_id").asLong()
            updateUsername = true
        }

        if (updateUsername) updateUsername()
    }

    private var lastPushed = TimeSource.Monotonic.markNow()
    suspend fun flush() {
        if (profileId.isEmpty()) return
        if (userId.isEmpty()) return

        val now = TimeSource.Monotonic.markNow()
        val elapsed = now - lastPushed
        lastPushed = now

        val query = StringBuilder($$"let $profile_t = update only type::record(\"mindustry_profile\", <uuid> $profile) set ")
        query.append("total_play_time += duration::from_millis(${if (player.get()?.let { Gamemode.spectate[it] } == true) 0 else elapsed.inWholeMilliseconds})")
        query.append(", play_time += duration::from_millis(${elapsed.inWholeMilliseconds})")
        if (Gamemode.hasStats) {
            if (extraBlocksPlaced != 0) query.append(", blocks_placed += $extraBlocksPlaced")
            if (extraBlocksBroken != 0) query.append(", blocks_broken += $extraBlocksBroken")
            if (extraGamesPlayed != 0) query.append(", games_played += $extraGamesPlayed")
            if (extraWaves != 0) query.append(", waves += $extraWaves")
        }
        query.append(";\n")
        if (Gamemode.hasStats) {
            query.append($$"upsert only type::record(\"mindustry_profile_gamemode\", [$profile_t.id, type::record(\"mindustry_gamemode\", <string> $gamemode)]) set ")
            query.append("play_time += duration::from_millis(${elapsed.inWholeMilliseconds})")
            if (extraGamesPlayed != 0) query.append(", games += $extraGamesPlayed")
            if (extraWins != 0) query.append(", wins += $extraWins")
            if (extraWaves != 0) query.append(", waves += $extraWaves")
            if (extraBlocksPlaced != 0) query.append(", blocks_placed += $extraBlocksPlaced")
            if (extraBlocksBroken != 0) query.append(", blocks_broken += $extraBlocksBroken")
            query.append(";\n")
        }
        query.append($$"return fn::mindustry_update_profile(type::record(\"mindustry_profile\", <uuid> $profile));")

        extraGamesPlayed = 0
        extraWins = 0
        extraWaves = 0
        extraBlocksPlaced = 0
        extraBlocksBroken = 0

        handleUpdateOutput(Database.abstractQuerySingle(Query(query.toString())
            .x("profile", profileId).x("gamemode", Config.i.gamemode)).ok().result)
    }

    /**
     * Handle player existing.
     *
     * This MUST be called.
     */
    suspend fun playerLeft(player: Player) {
        if (exitHandledCorrectly) return
        exitHandledCorrectly = true
        flush()
        releaseLocks()
    }

    suspend fun kickSilent(player: Player, reason: String) {
        val packet = KickCallPacket()
        packet.reason = reason
        player.con.send(packet, true)
        player.con.close()
        playerLeft(player)
    }

    suspend fun kick(player: Player, reason: String) {
        if (player.isAdded) player.kick(reason)
        else player.con.kick(reason)
        playerLeft(player)
    }

    suspend fun kickSilent(player: Player, reason: Packets.KickReason) {
        val packet = KickCallPacket2()
        packet.reason = reason
        player.con.send(packet, true)
        player.con.close()
        playerLeft(player)
    }

    suspend fun kick(player: Player, reason: Packets.KickReason) {
        if (player.isAdded) player.kick(reason)
        else player.con.kick(reason)
        playerLeft(player)
    }
}

/**
 * Get player data.
 */
@PublicAPI val Player.sessionData get() = PlayerData.of(this)
/**
 * Check if player has MindurkaCompat installed.
 */
@PublicAPI val Player.hasMindurkaCompat get() = sessionData.mindurkaCompatVersion != 0
/**
 * Obtain raw number for MindurkaCompat version.
 */
@PublicAPI val Player.mindurkaCompatVersion get() = sessionData.mindurkaCompatVersion
/**
 * Obtain MindurkaCompat version.
 */
@PublicAPI val Player.mindurkaCompat get() = MdcVersion.of(sessionData.mindurkaCompatVersion)
@PublicAPI suspend fun Player.ckick(reason: String) = PlayerData.of(this).kick(this, reason)
@PublicAPI suspend fun Player.ckick(reason: Packets.KickReason) = PlayerData.of(this).kick(this, reason)

@Command
private fun settings(caller: Player, @Rest path: Seq<String>?) = Async.run async@{
    path?.let { path ->
        for (x in path) Log.info(x)
        return@async
    }

    open class State(val openText: Boolean)
    val MainMenu = State(false)
    val TranslatorMenu = State(false)

    var state = MainMenu

    // TODO: MindurkaCompat extra UI components.
    while (true) {
        if (state.openText) {
            val text = caller.openText {

            } ?: break

        }
        else state = caller.openMenu {
            when (state) {
                MainMenu -> {
                    title("{commands.settings.maindialog.title}")

                    option("{commands.settings.maindialog.translator}") { state = TranslatorMenu; rerenderDialog() }
                    option("{generic.close}") { null }
                }
                TranslatorMenu -> {
                    title("{commands.settings.translatordialog.title}")
                    // TODO: Alignment, potentially?
                    message("{commands.settings.translatordialog.message}").apply {
                        put("style", caller.sessionData.translatorStyle.shortName)
                        put("player", caller.sessionData.fullName())
                    }

                    group {
                        option("{commands.settings.translator.none.name}") {
                            val sessionData = caller.sessionData
                            sessionData.translatorStyle = TranslatorStyle.Disabled
                            Async.run { Database.setTranslationPreference(sessionData.profileId, sessionData.translatorStyle, newSeq()) }
                            rerenderDialog()
                        }
                        option("{commands.settings.translator.multiline.name}") {
                            val sessionData = caller.sessionData
                            sessionData.translatorStyle = TranslatorStyle.Multiline
                            Async.run { Database.setTranslationPreference(sessionData.profileId, sessionData.translatorStyle, newSeq()) }
                            rerenderDialog()
                        }
                        option("{commands.settings.translator.short.name}") {
                            val sessionData = caller.sessionData
                            sessionData.translatorStyle = TranslatorStyle.Short
                            Async.run { Database.setTranslationPreference(sessionData.profileId, sessionData.translatorStyle, newSeq()) }
                            rerenderDialog()
                        }
                    }

                    option("{generic.close}") { state = MainMenu; rerenderDialog() }
                }
            }
        } ?: break
    }
}
