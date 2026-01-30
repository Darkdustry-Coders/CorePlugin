package mindurka.coreplugin.commands

import mindurka.build.CommandImpl
import arc.struct.Seq
import mindustry.Vars
import mindurka.build.CommandType
import arc.util.CommandHandler
import arc.Core
import mindustry.server.ServerControl
import mindurka.coreplugin.ModCommandHandler
import mindustry.gen.Player
import arc.util.Log
import buj.tl.Tl
import mindurka.util.map
import mindurka.util.fold
import kotlin.math.max
import mindurka.util.filter

private class CommandRegistrationContext(private val handle: CommandHandler) {
    private var sorted = true
    private val commandList = Seq<CommandImpl>()

    companion object {
        private val playerCtx = CommandRegistrationContext(run {
            Vars.netServer.clientCommands = ModCommandHandler(Vars.netServer.clientCommands)
            Vars.netServer.clientCommands
        })
        private val consoleCtx = CommandRegistrationContext((Core.app.listeners.first { it is ServerControl } as ServerControl).handler)

        operator fun get(type: CommandType): CommandRegistrationContext = when (type) {
            CommandType.Player -> playerCtx
            CommandType.Console -> consoleCtx
        }
    }

    fun register(command: CommandImpl) {
        if (!commandList.any { it.command[0] == command.command[0] }) {
            handle.register(command.command[0], "[args...]", command.doc) { args, caller: Any? ->
                if (!sorted) {
                    val maxDepth = commandList.iterator().map { it.priority.size }.fold(0) { a, b -> max(a, b) }
                    val maxCommandLen = commandList.iterator().map { it.command.size }.fold(0) { a, b -> max(a, b) }
                    for (i in 0..<maxDepth) {
                        val depth = maxDepth - i - 1
                        commandList.sort { if (depth < it.priority.size) -it.priority[depth].toFloat() else 0f }
                    }
                    commandList.sort { -(maxCommandLen - it.command.size).toFloat() }
                    sorted = true
                }

                for (cmd in commandList.iterator().filter { it.command[0] == command.command[0] }) {
                    (cmd.parse(caller, if (args.size > 0) args[0] else "") ?: continue)()
                    return@register
                }

                if (caller is Player) Tl.send(caller).put("command", command.command[0]).done("{generic.checks.invalid-arguments}")
                else Log.info("Invalid command arguments!")
            }
        }
        commandList.add(command)
        sorted = false
    }

    fun all(command: String): Iterator<CommandImpl> = commandList.iterator().filter { it.command[0] == command }
}

fun metadataForCommand(command: String, type: CommandType) =
    CommandRegistrationContext[type].all(command)

fun registerCommand(command: CommandImpl) {
    CommandRegistrationContext[command.type].register(command)
}
