package mindurka.coreplugin

import arc.struct.ObjectMap
import buj.tl.Tl
import mindurka.annotations.Command
import mindurka.api.emit
import mindurka.api.interval
import mindurka.api.on
import mindurka.config.SharedConfig
import mindurka.coreplugin.messages.ServerDown
import mindurka.coreplugin.messages.ServerInfo
import mindurka.coreplugin.messages.ServersRefresh
import mindurka.util.Async
import mindurka.util.AsyncCall
import mindurka.util.checkOnCooldown
import mindurka.util.setCooldown
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration

private class HubServer(
    @JvmField var ip: String,
    @JvmField var refreshing: Boolean,
)
private val hubServers = ObjectMap<String, HubServer>(2)

private fun serverInfo(): ServerInfo? {
    if (!Vars.net.active()) return null

    return ServerInfo(
        name = Administration.Config.serverName.get() as String,
        motd = Administration.Config.desc.get() as String,
        gamemode = Config.i.gamemode,
        map = Vars.state.map.name(),
        players = Groups.player.size(),
        maxPlayers = Vars.netServer.admins.playerLimit,
        wave = if (Vars.state.rules.waves) Vars.state.wave else -1,
        maxWaves = if (Vars.state.rules.winWave > 0) Vars.state.rules.winWave else -1,
        ip = "${SharedConfig.i.serverIp}:${Administration.Config.port.get()}",
    )
}

// TODO: Increase rates on all of these.
internal fun initHubDiscovery() {
    on<ServersRefresh> { serverInfo()?.let { info -> Async.run { RabbitMQ.reply(it, info) } } }
    interval(30f) { serverInfo()?.let(::emit) }

    interval(30f) {
        hubServers.removeAll {
            if (it.value.refreshing) true
            else {
                it.value.refreshing = true
                RabbitMQ.send(ServersRefresh(), it.key)
                false
            }
        }
    }
    on<ServerInfo> { msg ->
        if (msg.gamemode != "hub") return@on
        val sender = RabbitMQ.sentBy(msg)
        var server = hubServers[sender]
        if (server == null) {
            server = HubServer(msg.ip, false)
            hubServers.put(sender, server)
        } else {
            server.ip = msg.ip
            server.refreshing = false
        }
    }
    on<ServerDown> { msg ->
        hubServers.remove(RabbitMQ.sentBy(msg))
    }
}

@Command
private fun hub(caller: Player) = Async.run {
    if (caller.checkOnCooldown("/hub")) return@run

    for (server in hubServers.copy()) {
        val i = server.value.ip.indexOf(':')
        val host = server.value.ip.substring(0, i)
        val port = server.value.ip.substring(i + 1).toInt()

        if (AsyncCall.connect(caller, host, port)) return@run
    }

    Tl.send(caller).done("{commands.hub.errors.hub-down}")

    caller.setCooldown("/hub", 10f)
}
