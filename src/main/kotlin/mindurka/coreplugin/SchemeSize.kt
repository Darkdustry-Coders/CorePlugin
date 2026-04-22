package mindurka.coreplugin

import mindurka.util.sendBinaryPacket
import mindustry.Vars
import mindustry.gen.Call

fun initSchemeSize() {
    Vars.netServer.addBinaryPacketHandler("schemesize.available") { player, _ ->
        player.sendBinaryPacket("schemesize.available", byteArrayOf(0))
    }
}