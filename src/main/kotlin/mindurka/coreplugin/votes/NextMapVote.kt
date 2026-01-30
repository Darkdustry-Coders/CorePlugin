package mindurka.coreplugin.votes

import buj.tl.L
import mindurka.api.Consts
import mindurka.api.Gamemode
import mindurka.api.MapHandle
import mindurka.util.SendMessage
import mindustry.gen.Player

class NextMapVote(val map: MapHandle, initiator: Player): SimpleVote("commands.vnm.vote", initiator, null) {
    override fun formatParams(l: L<*>) {
        l.put("map", map.name())
    }
    override fun done() {
        Gamemode.maps.setNext(map)
    }

    override val cancelsIfRoundChanged: Boolean = true
}