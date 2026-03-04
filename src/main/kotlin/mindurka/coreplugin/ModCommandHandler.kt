package mindurka.coreplugin

import arc.util.CommandHandler
import arc.struct.Seq
import mindustry.gen.Player

class ModCommandHandler(private val parent: CommandHandler) : CommandHandler(parent.prefix) {
    override fun handleMessage(message_: String, params: Any): CommandResponse {
        var message = message_

        var spaceIdx = message.indexOf(" ")
        if (spaceIdx == -1) spaceIdx = message.length

        message = message.take(spaceIdx).lowercase() + message.substring(spaceIdx)

        if (message.matches(Regex("\\/t +\\/"))) message = message.substring(2).trimStart()

        if (params is Player && message.startsWith("/fuck")) {
            // Necessary due to CorePlugin commands being async
            carriedLastFailedCommand[params] = lastFailedCommand[params]
        }

        val resp = parent.handleMessage(message, params)

        if (params is Player) {
            lastFailedCommand.remove(params)
            lastCommandArgs[params] = message.substring(spaceIdx).trim()
        }

        return resp
    }

    override fun <T> register(text: String, params: String, description: String, runner: CommandRunner<T>): Command =
        parent.register(text, params, description, runner)

    override fun getCommandList(): Seq<Command> = parent.commandList

    override fun removeCommand(name: String) { parent.removeCommand(name) }
}
