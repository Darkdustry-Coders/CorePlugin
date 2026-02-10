package mindurka.coreplugin

import arc.util.Log
import buj.tl.Tl
import kotlinx.coroutines.future.await
import mindurka.ui.openText
import mindurka.util.Async
import mindurka.util.FormatException
import mindurka.util.stringToDuration
import mindustry.Vars
import mindustry.gen.AdminRequestCallPacket
import mindustry.gen.Player
import mindustry.net.Packets

internal fun modActionsInit() {
    Vars.net.handleServer(AdminRequestCallPacket::class.java) { con, packet ->
        val admin = con.player
        val target = packet.other

        if (!admin.admin) {
            Tl.send(admin).done("{generic.checks.admin-action-not-admin}")
            return@handleServer
        }

        fun adminChecks(target: Player?): Boolean {
            if (target == null) {
                Tl.send(admin).done("{generic.checks.admin-action-no-target}")
                return true
            }

            if (target.admin) {
                Tl.send(admin).done("{generic.checks.admin-action-target-admin}")
                return true
            }

            return false
        }

        when (packet.action) {
            Packets.AdminAction.kick -> {
                if (adminChecks(target)) return@handleServer

                Async.run {
                    val info = moderationDialog(admin, target, "commands.kick.dialog") ?: return@run

                    if (!admin.admin) {
                        Tl.send(admin).done("{generic.checks.admin-action-not-admin}")
                        return@run
                    }
                    if (adminChecks(target)) return@run


                }
            }
            else -> {}
        }
    }
}

private data class ModerationDialogOutput(
    val reason: String,
    val duration: Float,
)

private enum class DialogOutput {
    Continue,
    Cancel,
    Done,
}

private suspend fun moderationDialog(admin: Player, target: Player, prefix: String): ModerationDialogOutput? {
    var reason: String? = null
    var duration = 0f
    var state = DialogOutput.Continue

    while (true) {
        if (state == DialogOutput.Cancel) return null
        if (state == DialogOutput.Done) return ModerationDialogOutput(
            reason!!,
            duration
        )

        if (reason == null) reason = admin.openText {
            title("{$prefix.title}").put("player", target.coloredName())
            message("{$prefix.reason}")

            onComplete { out ->
                out.trim().ifEmpty { null }
            }

            onExit {
                state = DialogOutput.Cancel
                null
            }
            onClose {
                state = DialogOutput.Cancel
                null
            }
        }.await()
        else admin.openText {
            title("{$prefix.title}").put("player", target.coloredName())
            message("{$prefix.duration}")
            textLength = 40

            onComplete { value ->
                try {
                    duration = stringToDuration(value)
                    state = DialogOutput.Done
                } catch (f: FormatException) {
                    Log.err("Failed to parse duration", f)
                }
                null
            }

            onExit {
                state = DialogOutput.Cancel
                null
            }
            onClose {
                reason = null
                null
            }
        }.await()
    }
}