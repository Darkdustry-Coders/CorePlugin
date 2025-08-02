package mindurka.coreplugin

// Keeping those unwrapped for my own sanity.
import arc.Core
import arc.struct.Seq
import arc.util.Log
import buj.tl.Tl
import kotlinx.serialization.ExperimentalSerializationApi
import mindurka.annotations.Command
import mindurka.api.Events
import mindurka.api.Gamemode
import mindurka.api.PlayerTeamAssign
import mindurka.api.on
import mindurka.build.CommandImpl
import mindurka.build.CommandType
import mindurka.coreplugin.commands.metadataForCommand
import mindurka.coreplugin.commands.registerCommand
import mindurka.ui.handleUiEvent
import mindurka.util.K
import mindurka.util.Ref
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.server.ServerControl
import kotlin.math.ceil
import kotlin.math.roundToInt
import mindurka.annotations.Awaits
import mindurka.ui.openMenu
import kotlinx.coroutines.future.await
import mindurka.ui.openText
import arc.util.Timer
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.Terminal
import mindurka.util.*
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;

object CorePlugin {
    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun init(loader: ClassLoader) {
        Tl.init(loader)

        (loader.getResourceAsStream("META-INF/mindurka.coreplugin.commands")?.readAllBytes()?.toString(charset("UTF-8")) ?: "")
            .split("\n")
            .listIterator()
            .map { it.trim() }
            .filter { !it.isEmpty() }
            .forEach {
                @Suppress("UNCHECKED_CAST")
                val klass = loader.loadClass(it) as Class<CommandImpl>
                val init = klass.getConstructors().first()
                val cmd = init.newInstance() as CommandImpl
                registerCommand(cmd)
            }
    }
    @JvmStatic
    fun init(klass: Class<mindustry.mod.Plugin>) {
        init(klass.classLoader)
    }

    init {
        Log.info("Starting CorePlugin")

        val self = this
        val vanillaTeamAssigner = Vars.netServer.assigner
        Vars.netServer.assigner = object : NetServer.TeamAssigner {
            override fun assign(player: Player, players: Iterable<Player>): Team {
                val event = PlayerTeamAssign(
                    player,
                    players,
                    vanillaTeamAssigner.assign(player, players)
                )
                Events.fire(event)
                return event.team
            }
        }
        
        on<EventType.PlayerLeave> {
            handleUiEvent(it)
        }
        on<EventType.MenuOptionChooseEvent> {
            handleUiEvent(it)
        }
        on<EventType.TextInputEvent> {
            handleUiEvent(it)
        }

        val serverControl = Core.app.listeners.first { it is ServerControl } as ServerControl

        serverControl.serverInput = object : Runnable { override fun run() {
            val terminal = TerminalBuilder.builder().jna(true).system(true).dumb(true).build()
            val reader = LineReaderBuilder.builder().terminal(terminal).build()

            terminal.enterRawMode()

            while (true) {
                val line = reader.readLine("> ").trim()
                if (!line.isEmpty()) serverControl.handleCommandString(line)
            }
        } }
        
        RabbitMQ.noop()
    }
}

/** List commands */
@Command
private fun help(caller: Player, pageInit: UInt?) = Async.run {
    abstract class Page
    data class HelpMenu(var page: UInt) : Page() {}
    val SelectPage = object : Page() {}
    
    val commands = Vars.netServer.clientCommands.commandList.iterator()
        .filter {
            val commands = metadataForCommand(it.text, CommandType.Player).collect(Seq())
            commands.isEmpty() || !commands.iterator().all { it.hidden }
        }
        .map { Tl.fmt(caller).put("command", it.text).done("{commands.help.command}") }
        .collect(Seq())
    val maxPage = ceil(commands.size.toFloat().div(5)).roundToInt().toUInt()
    var page: Page = HelpMenu(run {
        var page = (pageInit ?: 1U)
        if (page == 0U) page = 1U
        if (page > maxPage) page = maxPage
        page
    })

    caller.openMenu<K> {
        val currentPage = page
        when (currentPage) {
            is HelpMenu -> {
                title("{commands.help.title-page}")
                message = commands.iterator().skip((currentPage.page - 1U) * 5U).take(5U).join("\n\n")
    
                group {
                    optionText("") {
                        if (currentPage.page != 1U) currentPage.page--
                        else currentPage.page = maxPage
                        rerenderDialog()
                    }
                    optionText("[white]${currentPage.page}/$maxPage") {
                        page = SelectPage
                        rerenderDialog()
                    }
                    optionText("") {
                        if (currentPage.page != maxPage) currentPage.page++
                        else currentPage.page = 1U
                        rerenderDialog()
                    }
                }
    
                option("{generic.close}") { K }
            }
            SelectPage -> {
                title("{commands.help.select-page.title}")
    
                var i = 0U
                while (i <= maxPage) {
                    group {
                        for (o in 0..<3) {
                            i++
                            val switchTo = i
                            if (i <= maxPage) optionText("$i") {
                                page = HelpMenu(switchTo)
                                rerenderDialog()
                            }
                            else optionText("")
                        }
                    }
                }
    
                option("{generic.close}") { K }
            }
            else -> throw UnreachableException()
        }
    }.await()
}

/** List commands. */
@Command
private fun help(caller: Player, command: String) {
    Tl.send(caller).put("command", command).done("{commands.help.title-cmd}")
}

/** List maps. */
@Command
private fun maps(caller: Player) {
    caller.sendMessage("De maps:")
    for (map in Gamemode.maps.maps()) {
        caller.sendMessage(map.name())
    }
}
