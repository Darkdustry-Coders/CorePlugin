package mindurka.coreplugin

import arc.util.Log
import buj.tl.Tl
import kotlinx.coroutines.future.await
import mindurka.coreplugin.database.Database
import mindurka.ui.openText
import mindurka.util.Async
import mindurka.util.FormatException
import mindurka.util.stringToDuration
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.AdminRequestCallPacket
import mindustry.gen.Player
import mindustry.net.Packets
import kotlin.time.Duration

internal fun initModActions() {
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

                    Tl.broadcast()
                        .put("admin", admin.coloredName())
                        .put("target", target.coloredName())
                        .done("{generic.admin.kick}")
                    Database.kick(target, admin, info.duration, info.reason)
                }
            }
            Packets.AdminAction.ban -> {
                if (adminChecks(target)) return@handleServer

                Async.run {
                    val info = moderationDialog(admin, target, "commands.ban.dialog") ?: return@run

                    if (!admin.admin) {
                        Tl.send(admin).done("{generic.checks.admin-action-not-admin}")
                        return@run
                    }
                    if (adminChecks(target)) return@run

                    Tl.broadcast()
                        .put("admin", admin.coloredName())
                        .put("target", target.coloredName())
                        .done("{generic.admin.ban}")
                    Database.ban(target, admin, info.duration, info.reason)
                }
            }
            Packets.AdminAction.wave -> {
                Tl.broadcast().put("admin", admin.coloredName()).done("{generic.admin.wave}")
                Vars.logic.skipWave()
            }
            Packets.AdminAction.switchTeam -> {
                if (adminChecks(target)) return@handleServer
                if (packet.params !is Team) return@handleServer
                target.team(packet.params as Team)
            }
            else -> {}
        }
    }
}

private data class ModerationDialogOutput(
    val reason: String,
    val duration: Duration,
)

private enum class DialogOutput {
    Continue,
    Cancel,
    Done,
}

private suspend fun moderationDialog(admin: Player, target: Player, prefix: String): ModerationDialogOutput? {
    var reason: String? = null
    var duration: Duration? = null
    var state = DialogOutput.Continue

    while (true) {
        if (state == DialogOutput.Cancel) return null
        if (state == DialogOutput.Done) return ModerationDialogOutput(
            reason!!,
            duration!!
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
                Duration.parseOrNull(value)?.let {
                    duration = it
                    state = DialogOutput.Done
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