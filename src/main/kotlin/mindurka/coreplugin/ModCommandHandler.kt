package mindurka.coreplugin

import arc.util.CommandHandler
import arc.struct.Seq

class ModCommandHandler(private val parent: CommandHandler) : CommandHandler(parent.prefix) {
    override fun handleMessage(message_: String, params: Any): CommandHandler.CommandResponse {
        var message = message_

        var spaceIdx = message.indexOf(" ")
        if (spaceIdx == -1) spaceIdx = message.length

        message = message.take(spaceIdx).lowercase() + message.substring(spaceIdx)

        if (message.matches(Regex("\\/t +\\/"))) message = message.substring(2).trimStart()

        return parent.handleMessage(message, params)
    }

    override fun <T> register(text: String, params: String, description: String, runner: CommandRunner<T>): CommandHandler.Command =
        parent.register(text, params, description, runner)

    override fun getCommandList(): Seq<Command> =
        parent.getCommandList()

    override fun removeCommand(name: String) { parent.removeCommand(name) }
}
