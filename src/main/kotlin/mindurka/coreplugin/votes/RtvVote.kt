package mindurka.coreplugin.votes

import buj.tl.L
import mindurka.api.Consts
import mindurka.api.MapHandle
import mindurka.util.SendMessage
import mindustry.gen.Player

class RtvVote(val map: MapHandle, initiator: Player): SimpleVote("commands.rtv.vote", initiator, null) {
    override fun formatParams(l: L<*>) {
        l.put("map", map.name())
    }
    override fun done() {
        Consts.serverControl.play(map::rtv)
    }

    override val cancelsIfRoundChanged: Boolean = false
}