package mindurka.util

import arc.util.io.Writes
import mindurka.api.Consts
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.SetFloorCallPacket
import mindustry.gen.SetOverlayCallPacket
import mindustry.gen.SetTileCallPacket
import mindustry.net.NetConnection
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.environment.Floor

object ModifyWorld {
    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun syncBuild(con: NetConnection, build: Building) {
        Consts.syncStream.reset()
        Consts.dataStream.writeInt(build.pos())
        Consts.dataStream.writeShort(build.block.id.toInt())
        build.writeAll(Writes(Consts.dataStream))
        Consts.dataStream.close()
        val bytes = Consts.syncStream.bytes
        Call.blockSnapshot(con, 1, bytes)
    }
    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun syncBuild(build: Building) {
        Consts.syncStream.reset()
        Consts.dataStream.writeInt(build.pos())
        Consts.dataStream.writeShort(build.block.id.toInt())
        build.writeAll(Writes(Consts.dataStream))
        Consts.dataStream.close()
        val bytes = Consts.syncStream.bytes
        Call.blockSnapshot(1, bytes)
    }

    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun netBlock(con: NetConnection, tile: Tile, block: Block, team: Team, rotation: Int) {
        val packet = SetTileCallPacket();
        packet.tile = tile;
        packet.block = block;
        packet.team = team;
        packet.rotation = rotation;
        con.send(packet, true)
    }
    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun netBlock(tile: Tile, block: Block, team: Team, rotation: Int) {
        val packet = SetTileCallPacket();
        packet.tile = tile;
        packet.block = block;
        packet.team = team;
        packet.rotation = rotation;
        Vars.net.send(packet, true)
    }

    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun netOverlay(con: NetConnection, tile: Tile, overlay: Block) {
        val packet = SetOverlayCallPacket();
        packet.tile = tile;
        packet.overlay = overlay;
        con.send(packet, true)
    }
    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun netOverlay(tile: Tile, overlay: Block) {
        val packet = SetOverlayCallPacket();
        packet.tile = tile;
        packet.overlay = overlay;
        Vars.net.send(packet, true);
    }

    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun netFloor(con: NetConnection, tile: Tile, floor: Block, overlay: Block) {
        val packet = SetFloorCallPacket();
        packet.tile = tile;
        packet.floor = floor;
        packet.overlay = overlay;
        con.send(packet, true)
    }
    /**
     * Synchronize a building over the network.
     */
    @JvmStatic
    fun netFloor(tile: Tile, floor: Block, overlay: Block) {
        val packet = SetFloorCallPacket();
        packet.tile = tile;
        packet.floor = floor;
        packet.overlay = overlay;
        Vars.net.send(packet, true);
    }

    /**
     * Placement check that actually checks placement.
     */
    @JvmStatic
    fun canPlaceOn(block: Block, tile: Tile?, team: Team, rotation: Int): Boolean {
        val tile = tile ?: return false

        if (!block.canPlaceOn(tile, team, rotation)) return false
        if (block.isFloor) return true

        block.eachBlockOffset(tile) { x, y ->
            val tile = Vars.world.tile(x, y) ?: return@eachBlockOffset
            if (!tile.floor().hasSurface()) return false
            // TODO: Replace blocks that Mindustry allows replacing.
            if (tile.block() != Blocks.air && !tile.block().alwaysReplace) return false
        }

        return true
    }
}

fun Block.canActuallyPlaceOn(tile: Tile?, team: Team, rotation: Int): Boolean = ModifyWorld.canPlaceOn(this, tile, team, rotation)
