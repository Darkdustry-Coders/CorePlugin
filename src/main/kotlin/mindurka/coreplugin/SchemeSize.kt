package mindurka.coreplugin

import arc.math.geom.Geometry
import arc.util.Log
import arc.util.Strings
import arc.util.serialization.JsonReader
import arc.util.serialization.JsonValue
import buj.tl.Tl
import mindurka.annotations.Command
import mindurka.annotations.EnabledIf
import mindurka.annotations.RequiresPermission
import mindurka.annotations.Rest
import mindurka.api.Gamemode
import mindurka.api.on
import mindurka.coreplugin.commands.AdminCommandsEnabled
import mindurka.coreplugin.database.PermLevels
import mindurka.util.K
import mindurka.util.ModifyWorld
import mindurka.util.permissionLevel
import mindurka.util.sendBinaryPacket
import mindurka.util.sendPacket
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.Unit
import mindustry.io.JsonIO
import mindustry.type.Item
import mindustry.type.StatusEffect
import mindustry.type.UnitType
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.storage.CoreBlock
import kotlin.math.min

enum class SSTool(val flag: Int, val minPermLevel: Int) {
    FLUSH(1, PermLevels.admin),
    FILL(1 shl 1, PermLevels.admin),
    BRUSH(1 shl 2, PermLevels.admin),
    RULESETTER(1 shl 3, PermLevels.admin),
    DESPAWN(1 shl 4, PermLevels.admin),
    TELEPORT(1 shl 5, PermLevels.admin),
    SPAWN(1 shl 6, PermLevels.admin),
    EFFECT(1 shl 7, PermLevels.admin),
    ITEM(1 shl 8, PermLevels.admin),
    TEAM(1 shl 9, PermLevels.admin),
    CORE(1 shl 10, PermLevels.admin);

    companion object {
        fun mask(tools: Collection<SSTool>): Int = tools.fold(0) { acc, t -> acc or t.flag }
        fun mask(vararg tools: SSTool): Int = tools.fold(0) { acc, t -> acc or t.flag }
        fun all(): Int = entries.fold(0) { acc, t -> acc or t.flag }
    }
}

fun disabledToolsFor(player: Player): Int {
    val perm = player.permissionLevel
    var disabled = 0

    for (tool in SSTool.entries) {
        if (perm < tool.minPermLevel) disabled = disabled or tool.flag
    }

    disabled = disabled or SSTool.mask(Gamemode.bannedTools)
    disabled = disabled or SSTool.mask(player.sessionData.ssBannedTools)

    return disabled
}

fun sendDisabledTools(player: Player) {
    val flags = disabledToolsFor(player)
    player.sendBinaryPacket("schemesize.available", byteArrayOf(
        (flags shr 8).toByte(),
        (flags and 0xFF).toByte()
    ))
}

internal fun initSchemeSize() {
    Vars.netServer.addBinaryPacketHandler("schemesize.available") { player, _ ->
        player.sessionData.schemeSizeMetadata = K
        sendDisabledTools(player)
    }

    val jsonRegex = Regex("[\"\\\\\n\t\r]", RegexOption.MULTILINE)

    fun escapeJson(s: String) = s.replace(jsonRegex) { when (it.value) {
        "\"" -> "\\\""
        "\\" -> "\\\\"
        "\n" -> "\\n"
        "\t" -> "\\t"
        "\r" -> "\\r"
        else -> ""
    } as CharSequence }

    fun subtitleJson(id: Int, subtitle: String) = "{$id:\"${escapeJson(subtitle)}\"}"

    Vars.netServer.addPacketHandler("MySubtitle") { player, text ->
        player.sessionData.schemeSizeSubtitle = text
        Call.clientPacketReliable("Subtitles", subtitleJson(player.id, text))

        val obj = StringBuilder("{")
        for (x in Groups.player) {
            val sub = x.sessionData.schemeSizeSubtitle ?: continue
            if (x == player) continue
            obj.append("${x.id}:\"${escapeJson(sub)}\",")
        }
<<<<<<< HEAD
        Call.clientPacketReliable("Subtitles", "$obj}")
=======
        obj.append("}")
        if (obj.length > 2) player.sendPacket("Subtitles", obj.toString())
>>>>>>> refs/remotes/origin/master
    }

    on<EventType.PlayerJoin> { event ->
        // This packet is supposed to send the player ID of the host.
        // If I'm ever adding lobbies, this could be very, very interesting.
        event.player.sendPacket("SendMeSubtitle", null)
    }

    on<EventType.PlayerLeave> { event ->
        if (event.player.sessionData.schemeSizeSubtitle != null) {
            Call.clientPacketReliable("Subtitles", subtitleJson(event.player.id, ""))
        }
    }

    // Different packet name since in the packet sheet `fill` packet didn't have `overlay`.
    Vars.netServer.addPacketHandler("schemesize.fill") { player, data ->
        if (!Gamemode.adminCommands) return@addPacketHandler
        if (player.permissionLevel < 200) {
            Tl.send(player).done("{generic.checks.admin-action-permission}")
            return@addPacketHandler
        }
        val data = data.split(" ")
        if (data.size != 9) return@addPacketHandler
        if (!data[8].isEmpty()) return@addPacketHandler

        val block: Block? = Vars.content.block(data[0].toIntOrNull() ?: -1)
        val rotation: Int = data[1].toIntOrNull() ?: return@addPacketHandler
        val floor: Floor? = Vars.content.block(data[2].toIntOrNull() ?: -1)?.asFloor()
        val overlay: Floor? = Vars.content.block(data[3].toIntOrNull() ?: -1)?.asFloor()
        val sx: Int = data[4].toIntOrNull() ?: return@addPacketHandler
        val sy: Int = data[5].toIntOrNull() ?: return@addPacketHandler
        val w: Int = data[6].toIntOrNull() ?: return@addPacketHandler
        val h: Int = data[7].toIntOrNull() ?: return@addPacketHandler

        // For some reason width and height can be 0.
        if (w < 0 || h < 0) return@addPacketHandler

        val shiftx = -min(0, sx)
        val shifty = -min(0, sy)

        if (shiftx > w) return@addPacketHandler
        if (shifty > h) return@addPacketHandler

        for (x1 in 0..min(w - shiftx, Vars.world.width() - sx)) for (y1 in 0..min(h - shifty, Vars.world.height() - sy)) {
            val tile = Vars.world.tile(x1 + sx, y1 + sy) ?: continue
            tile.setFloorNet(floor ?: tile.floor(), overlay ?: tile.overlay())
        }
        block?.let { block ->
            for (x1 in 0..min(w - shiftx, Vars.world.width().minus(sx)).div(block.size)) for (y1 in 0..min(h - shifty, Vars.world.height().minus(sy)).div(block.size)) {
                val tile = Vars.world.tile(x1 * block.size + sx, y1 * block.size + sy) ?: continue
                tile.setNet(block, player.team(), rotation)
            }
        }
    }
    // Writeup did not have this packet at all
    Vars.netServer.addPacketHandler("schemesize.brush") { player, data ->
        if (!Gamemode.adminCommands) return@addPacketHandler
        if (player.permissionLevel < 200) {
            Tl.send(player).done("{generic.checks.admin-action-permission}")
            return@addPacketHandler
        }
        val data = data.split(" ")
        if (data.size != 8) return@addPacketHandler
        if (!data[7].isEmpty()) return@addPacketHandler

        val block: Block? = Vars.content.block(data[0].toIntOrNull() ?: -1)
        val rotation: Int = data[1].toIntOrNull() ?: return@addPacketHandler
        val floor: Floor? = Vars.content.block(data[2].toIntOrNull() ?: -1)?.asFloor()
        val overlay: Floor? = Vars.content.block(data[3].toIntOrNull() ?: -1)?.asFloor()
        val radius: Int = data[4].toIntOrNull() ?: return@addPacketHandler
        val x: Int = data[5].toIntOrNull() ?: return@addPacketHandler
        val y: Int = data[6].toIntOrNull() ?: return@addPacketHandler

        Geometry.circle(x, y, radius) { cx, cy ->
            val tile = Vars.world.tile(cx, cy) ?: return@circle
            tile.setFloorNet(floor ?: tile.floor(), overlay ?: tile.overlay())
        }
        // TODO: Save config for better blocks placement.
        block?.let { block ->
            Geometry.circle(0, 0, radius.div(block.size)) { cx, cy ->
                val tile = Vars.world.tile(x + cx * block.size, y + cy * block.size) ?: return@circle
                tile.setNet(block, player.team(), rotation)
            }
        }
    }
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun unit(caller: Player, unit: UnitType, @Rest target: Player?) {
    val player = target ?: caller
    val unit = unit.create(player.team())
    unit.x = caller.x
    unit.y = caller.y
    unit.spawnedByCore = true
    unit.add()
    unit.controller(caller)
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun team(caller: Player, team: Team, @Rest target: Player?) { (target ?: caller).team(team) }

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun despawn(caller: Player, @Rest unit: Unit?) {
    if (unit == null) {
        Groups.unit.copy().each(Unit::kill)
    } else {
        unit.kill()
    }
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun tp(caller: Player, x: Float, y: Float, @Rest target: Player?) {
    val target = target ?: caller
    val unit = target.unit() ?: return
    ModifyWorld.teleport(unit, x * Vars.tilesize, y * Vars.tilesize)
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun setrule(caller: Player, rule: String, @Rest value: String) {
    try {
        val parent = JsonValue(JsonValue.ValueType.`object`)
        parent.addChild(rule, JsonReader().parse(value))
        JsonIO.json.readField(Vars.state.rules, rule, parent)
        Call.setRule(rule, value)
    } catch (_: Exception) {
        Tl.send(caller).done("{commands.setrule.failed}")
    }
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun core(caller: Player) {
    val unit = caller.unit() ?: run {
        Tl.send(caller).done("{commands.core.no-unit}")
        return
    }
    val tile = unit.tileOn() ?: run {
        Tl.send(caller).done("{commands.core.no-tile}")
        return
    }
    if (tile.block() is CoreBlock) tile.setNet(Blocks.air)
    else tile.setNet(Blocks.coreShard, caller.team(), 0)
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun spawn(caller: Player, kind: UnitType, count: UInt, team: Team?) {
    if (count > 500U) {
        Tl.send(caller).done("{commands.spawn.too-many-units}")
        return
    }

    val unit = caller.unit() ?: run {
        Tl.send(caller).done("{commands.spawn.no-unit}")
        return
    }
    val team = team ?: unit.team()

    for (i in 0U..<count) {
        kind.spawn(team, caller.x(), caller.y(), 0f) ?: run {
            Tl.send(caller).put("remaining", (count - i).toString()).done("{commands.spawn.spawn-failed}")
            return
        }
    }
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun effect(caller: Player, effect: StatusEffect, duration: Float, unit: Unit?) {
    val unit = unit ?: caller.unit() ?: run {
        Tl.send(caller).done("{commands.effect.no-unit}")
        return
    }

    if (duration == 0f) unit.clearStatuses()
    else unit.apply(effect, duration * 60f)
}

@Command
@RequiresPermission(PermLevels.admin)
@EnabledIf(AdminCommandsEnabled::class)
private fun give(caller: Player, item: Item, amount: Int, team: Team?) {
    val team = team ?: caller.unit()?.team ?: run {
        Tl.send(caller).done("{commands.give.no-team}")
        return
    }
    val core = team.core() ?: run {
        Tl.send(caller).done("{commands.give.no-core}")
        return
    }
    core.items.add(item, amount)
}
