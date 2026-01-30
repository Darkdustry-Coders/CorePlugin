package mindurka.util

import buj.tl.LCtx
import buj.tl.LVoidDone
import buj.tl.Lc
import buj.tl.Script
import buj.tl.Tl
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player

interface SendMessage {
    object All: SendMessage {
        override fun send(): LVoidDone<*> = Tl.broadcast()
    }
    data class One(val player: Player): SendMessage {
        override fun send(): LVoidDone<*> = Tl.send(player)
    }
    data class Multi(val team: Team): SendMessage {
        override fun send(): LVoidDone<*> {
            class O: LVoidDone<O> {
                val ctx = LCtx()

                override fun put(key: String, text: String): O {
                    ctx.put(key, text)
                    return this
                }

                override fun put(key: String, value: Script): O {
                    ctx.put(key, value)
                    return this
                }

                override fun done(key: String) {
                    Groups.player.each({ it.team() == team }) { Lc(it, ctx).done(key) }
                }
            }
            return O()
        }
    }

    fun send(): LVoidDone<*>
}