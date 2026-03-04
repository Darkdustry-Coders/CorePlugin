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
import mindurka.build.CommandResult
import mindurka.coreplugin.lastFailedCommand
import mindurka.util.Async
import mindurka.util.map
import mindurka.util.fold
import kotlin.math.max
import mindurka.util.filter
import mindurka.util.unreachable

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
            handle.register(command.command[0], "[args...]", command.doc) { args, caller: Any? -> Async.run {
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

                var cause: CommandResult? = null

                for (cmd in commandList.iterator().filter { it.command[0] == command.command[0] }) {
                    when (val result = cmd.parse(caller, if (args.size > 0) args[0] else "")) {
                        CommandResult.Complete -> return@run
                        else -> if (cause?.let { it.ordinal > result.ordinal } != false) cause = result
                    }
                }

                val suffix = if (caller is Player) "" else "-console"
                val message = run {
                    val formatter = Tl.fmt(if (caller is Player) caller.locale else "c")
                    formatter.put("command", command.command[0])
                    when (cause) {
                        null -> {}
                        CommandResult.Complete -> unreachable()
                        CommandResult.TooMuchData -> {
                            formatter.put("cause", Tl.parse("{generic.command.too-many-arguments}"))
                        }
                        is CommandResult.Missing -> {
                            formatter.put("cause", Tl.parse("{generic.command.missing}"))
                            formatter.put("param", cause.argument)
                        }
                        is CommandResult.Invalid -> {
                            formatter.put("cause", Tl.parse(if (caller == null) cause.message.replace("}", "-console}") else cause.message))
                            formatter.put("param", cause.argument)
                        }
                    }
                    formatter.done("{generic.checks.invalid-arguments$suffix")
                }

                if (caller is Player) caller.sendMessage(message)
                else Log.err(message)
            } }
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
