package mindurka.util

import arc.struct.Seq
import arc.util.Log
import mindurka.util.Schematic.Options
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Block
import mindustry.world.Tiles
import mindustry.world.blocks.environment.Floor
import java.nio.ByteBuffer
import kotlin.math.min

private val DEFAULT: Options = Options()

class Schematic(val width: Int, val height: Int) {
    companion object {
        @JvmStatic
        val EMPTY: Schematic = Schematic(0, 0)

        fun of(tiles: Tiles, x: Int, y: Int, w: Int, h: Int): Schematic {
            return of(tiles, x, y, w, h, DEFAULT)
        }

        fun of(tiles: Tiles, x: Int, y: Int, w: Int, h: Int, options: Options): Schematic {
            var w = w
            var h = h
            if (x >= tiles.width || y >= tiles.height) return EMPTY
            w = min(tiles.width - x, w)
            h = min(tiles.height - y, h)
            if (w == 0 || h == 0) return EMPTY

            val schematic = Schematic(w, h)
            var cursor = 0
            for (dy in 0..<h) {
                var dx = 0
                while (dx < w) {
                    val tile = tiles.get(x + dx, y + dy)
                    schematic.overlays[cursor] = tile.overlay()
                    schematic.floors[cursor] = tile.floor()
                    schematic.data[cursor] = tile.packedData

                    if ((schematic.overlays[cursor] === Blocks.air || schematic.overlays[cursor] === Blocks.empty)
                        && options.skipNoOverlay) schematic.overlays[cursor] = null
                    if (schematic.floors[cursor] === Blocks.empty && options.skipEmpty) schematic.floors[cursor] = null

                    val block = tile.block()
                    a@while (!block.isMultiblock || tile.isCenter) {
                        val build = tile.build
                        if (build != null && options.skipBuildings) break@a

                        schematic.blocks[cursor] = tile.block()
                        if (build != null) {
                            schematic.build[cursor] = BuildData(build.rotation, build.config())
                        }
                        if (schematic.blocks[cursor] === Blocks.air && options.skipAir) schematic.blocks[cursor] = null
                        break
                    };
                    dx++
                    cursor++
                }
            }

            return schematic
        }

        @Throws(FormatException::class)
        fun of(data: String): Schematic {
            val read: StringRead = StringRead(data)
            try {
                if (read.i() != 2) throw FormatException("Invalid format")
            } catch (e: FormatException) {
                throw FormatException("Invalid format", e)
            }

            val width: Int = read.i()
            if (width < 0) throw FormatException("Invalid width ($width < 0)")
            val height: Int = read.i()
            if (height < 0) throw FormatException("Invalid height ($height < 0)")

            if (width == 0 || height == 0) return EMPTY

            val scheme = Schematic(width, height)

            val blockCount: Int = read.i()
            if (blockCount < 0) throw FormatException("Invalid block count ($blockCount < 0)")
            // I know exactly what I'm doing.
            val blocks: Array<Block> = arrayOfNulls<Block>(blockCount) as Array<Block>

            for (cursor in 0..<blockCount) {
                val name: String = read.sym()
                val block = Vars.content.block(name) ?: throw FormatException("Could not find block '$name'")
                blocks[cursor] = block
            }

            var cursor = 0
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    scheme.data[cursor] = read.l()

                    try {
                        if (!read.nil()) {
                            scheme.blocks[cursor] = blocks[read.i()]
                        }
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        throw FormatException("Invalid block index", e)
                    }

                    try {
                        if (!read.nil()) {
                            val block = blocks[read.i()]
                            if (!block.isFloor && block !== Blocks.air) throw FormatException("Not an overlay (${block.name})")
                            scheme.overlays[cursor] = block.asFloor()
                        }
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        throw FormatException("Invalid block index", e)
                    }

                    try {
                        if (!read.nil()) {
                            val block = blocks[read.i()]
                            if (!block.isFloor) throw FormatException("Not a floor")
                            scheme.floors[cursor] = block.asFloor()
                        }
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        throw FormatException("Invalid block index", e)
                    }

                    if (!read.nil()) throw FormatException("Block data is not yet implemented")
                    x++
                    cursor++
                }
                y++
            }

            return scheme
        }
    }

    data class BuildData(
        val rotation: Int = 0,
        val config: Any? = null,
    )

    class Options {
        /** Skip air overlays.  */
        var skipNoOverlay: Boolean = false

        /** Skip air blocks.  */
        var skipAir: Boolean = false

        /** Skip empty floors.  */
        var skipEmpty: Boolean = false

        /** Skip buildings.  */
        var skipBuildings: Boolean = false

        /** Use net updates.  */
        var updateNet: Boolean = true

        /** Team to use when placing blocks.  */
        var team: Team? = Team.derelict

        // /** Mask to select affected blocks. Pasting only. */
        // public Schematic mask = null;
        // /** Starting x position on the mask. */
        // public int maskX = 0;
        // /** Starting y position on the mask. */
        // public int maskY = 0;
        /** Skip air blocks.  */
        fun skipAir(): Options {
            skipAir = true
            return this
        }

        /** Skip air overlays.  */
        fun skipNoOverlay(): Options {
            skipNoOverlay = true
            return this
        }

        /** Skip empty floors.  */
        fun skipEmpty(): Options {
            skipEmpty = true
            return this
        }

        /** Skip buildings.  */
        fun skipBuildings(): Options {
            skipBuildings = true
            return this
        }

        /** Disable network updates.  */
        fun noNet(): Options {
            updateNet = false
            return this
        }

        /** Team to use when placing blocks.  */
        fun team(team: Team?): Options {
            this.team = team
            return this
        }
        // /** Set mask. */
        // public Options mask(Schematic mask) { return mask(mask, 0, 0); }
        // /** Set mask. */
        // public Options mask(Schematic mask, int x, int y) {
        //     this.mask = mask;
        //     maskX = x;
        //     maskY = y;
        //     return this;
        // }
        /** Reset all fields to defaults.  */
        fun reset(): Options {
            skipAir = false
            skipEmpty = false
            skipNoOverlay = false
            skipBuildings = false
            updateNet = true
            team = Team.derelict
            // mask = null;
            // maskX = 0;
            // maskY = 0;
            return this
        }
    }

    val overlays = Array<Floor?>(width * height) { null }
    val blocks = Array<Block?>(width * height) { null }
    val floors = Array<Floor?>(width * height) { null }
    var data = LongArray(width * height)
    var build = Array<BuildData?>(width * height) { null }

    fun paste(dst: Tiles, dstx: Int, dsty: Int) {
        paste(0, 0, width, height, dst, dstx, dsty, DEFAULT)
    }

    fun paste(dst: Tiles, dstx: Int, dsty: Int, options: Options) {
        paste(0, 0, width, height, dst, dstx, dsty, options)
    }

    fun paste(x: Int, y: Int, w: Int, h: Int, dst: Tiles, dstx: Int, dsty: Int) {
        paste(x, y, w, h, dst, dstx, dsty, DEFAULT)
    }

    fun paste(x: Int, y: Int, w: Int, h: Int, dst: Tiles, dstx: Int, dsty: Int, options: Options) {
        var x = x
        var y = y
        var w = w
        var h = h
        var dstx = dstx
        var dsty = dsty
        if (dstx >= dst.width) return
        if (dsty >= dst.height) return

        if (dstx < 0) {
            x += dstx
            w -= dstx
            dstx = 0
        }
        if (dsty < 0) {
            y += dsty
            h -= dsty
            dsty = 0
        }

        if (x < 0) {
            dstx -= x
            x = 0
        }
        if (y < 0) {
            dsty -= y
            y = 0
        }

        w = min(w, dst.width - dstx)
        w = min(w, width - x)

        h = min(h, dst.height - dsty)
        h = min(h, height - y)

        if (w == 0 || h == 0) return
        if (x >= width) return
        if (y >= height) return

        for (dx in 0..<w) for (dy in 0..<h) {
            val idx = x + dx + (y + dy) * width

            if (idx >= blocks.size) {
                throw RuntimeException("Bailing out! Schematic (" + width + "x" + height + "): x=" + x + ", y=" + y + ", w=" + w + ", h=" + h + ", dstx=" + dstx + ", dsty=" + dsty + ", idx=" + idx + ", dx=" + dx + ", dy=" + dy)
            }

            val tile = dst.get(dstx + dx, dsty + dy) ?: continue

            val data = this.build[idx]
            block@while (blocks[idx] != null && !(blocks[idx] === Blocks.air && options.skipAir)) {
                if (options.skipBuildings && tile.build != null || this.build[idx] != null) break@block
                if (options.updateNet) tile.setNet(blocks[idx], options.team, data?.rotation ?: 0)
                else tile.setBlock(blocks[idx], options.team, data?.rotation ?: 0)
                break
            }
            val floorChanged = floors[idx] != null && !(blocks[idx] === Blocks.empty && options.skipEmpty)
            val overlayChanged = overlays[idx] != null && (!options.skipNoOverlay || blocks[idx] !== Blocks.air && blocks[idx] !== Blocks.empty)
            if (floorChanged && overlayChanged) {
                if (options.updateNet) tile.setFloorNet(floors[idx], overlays[idx])
                else {
                    tile.setOverlay(overlays[idx])
                    tile.setFloor(floors[idx])
                }
            } else if (floorChanged) {
                if (options.updateNet) tile.setFloorNet(floors[idx], tile.overlay())
                else tile.setFloor(floors[idx])
            } else if (overlayChanged) {
                if (options.updateNet) tile.setOverlayNet(overlays[idx])
                else tile.setOverlay(overlays[idx])
            }

            val dataChanged = tile.data.toLong() != this.data[idx]

            tile.setPackedData(this.data[idx])
            if (options.updateNet && dataChanged) {
                val buf = ByteBuffer.allocateDirect(16)
                buf.putInt(0, x + dx)
                buf.putInt(4, y + dy)
                buf.putLong(8, this.data[idx])

                Call.serverBinaryPacketReliable("mindurka.setData", buf.array())
            }
        }
    }

    private class RefTable {
        var refTableBuilder: StringBuilder = StringBuilder()
        var refTable: Seq<String?> = Seq<String?>()

        fun ref(id: String?): Int {
            var pos = refTable.indexOf(id)
            if (pos == -1) {
                pos = refTable.size
                refTableBuilder.append(id).append(',')
                refTable.add(id)
            }
            return pos
        }
    }

    fun serialize(): String {
        val refs = RefTable()
        val body = StringBuilderWrite(StringBuilder())

        var cursor = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                body.l(data[cursor])

                var block = blocks[cursor]
                if (block == null) body.nil()
                else body.i(refs.ref(block.name))

                block = overlays[cursor]
                if (block == null) body.nil()
                else body.i(refs.ref(block.name))

                block = floors[cursor]
                if (block == null) body.nil()
                else body.i(refs.ref(block.name))

                body.nil()
                x++
                cursor++
            }
            y++
        }

        return "2," + width + ',' + height + ',' + refs.refTable.size + ',' + refs.refTableBuilder + body.builder
    }

}