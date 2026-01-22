package mindurka.api

import arc.func.Cons
import arc.struct.ObjectMap
import arc.struct.Seq
import mindurka.annotations.PublicAPI
import mindurka.annotations.NetworkEvent
import mindurka.coreplugin.RabbitMQ
import mindurka.util.Ref
import mindurka.util.nodecl
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.gen.Unit
import mindustry.world.blocks.environment.Floor

/**
 * A player is having their team assigned.
 *
 * This event is usually fired by team assigner.
 */
@PublicAPI
data class PlayerTeamAssign (
    val player: Player,
    val players: Iterable<Player>,
    var team: Team,
)

/**
 * End of the round.
 *
 * This is emitted when either server has stopped or if
 * map is switched.
 *
 * This event is cached.
 *
 * Listening to this event with a lifetime that doesn't extend
 * beyond a round is a logic bug.
 */
@PublicAPI
object RoundEndEvent

/**
 * A block has been built.
 *
 * This event is used to potentially replace built blocks
 * without causing plugin incompatibilities.
 *
 * Cancelling this event will cancel block replacement, assuming
 * it was done via [mindurka.api.BuildEvent.replace].
 */
@PublicAPI
data class BuildEvent (
    val unit: Unit,
    val tile: Tile,

    var replacementBlock: Block? = null,
    var replacementOverlay: Block? = null,
    var replacementFloor: Floor? = null,
    var replacementHealth: Float? = null,
    var replacementRotation: Int = if (tile.build == null) 0 else tile.build.rotation,
    var replacementTeam: Team = tile.team(),
    var replacementCallback: Runnable? = null,
) {
    @PublicAPI
    @JvmOverloads
    fun replace(block: Block, team: Team = Team.derelict, rotation: Int = 0, callback: Runnable? = null) {
        replacementBlock = block
        replacementTeam = team
        replacementRotation = rotation
        replacementCallback = callback
    }
    @PublicAPI
    fun replaceAir() = replace(Blocks.air)
    @PublicAPI
    fun replaceOverlay(overlay: Block) {
        replacementOverlay = overlay
    }
    @PublicAPI
    fun replaceFloor(floor: Floor) {
        replacementFloor = floor
    }

    fun block(): Block =
        if (replacementBlock == null) tile.block()
        else replacementBlock as Block
    fun team(): Team = replacementTeam
    fun rotation(): Int = replacementRotation
    fun health(): Float =
        if (replacementHealth != null) replacementHealth as Float
        else if (replacementBlock != null)
            (replacementBlock as Block).health.toFloat() *
                Vars.state.rules.blockHealthMultiplier *
                Vars.state.rules.teams.get(team()).blockHealthMultiplier
        else tile.block().health.toFloat()
}

/**
 * A block has been built.
 *
 * This is emitted after the block has been modifier by listeners
 * of [BuildEvent].
 *
 * This event is cached.
 */
@PublicAPI
object BuildEventPost {
    lateinit var unit: Unit
    lateinit var tile: Tile
}

/** A cancellable object. */
@PublicAPI
interface Cancellable {
    /**
     * Bind a cancel event.
     *
     * It will be called alongside this cancel event.
     *
     * Calling this twice with the same arguments does nothing.
     */
    @PublicAPI
    fun bind(cancel: Cancellable)
    /**
     * Unbind a cancel event.
     *
     * Calling this twice with the same arguments does nothing.
     */
    @PublicAPI
    fun unbind(cancel: Cancellable)

    /**
     * Called by the binder upon [Cancellable.bind].
     *
     * When binders cancels, it should call [Cancellable.unbind]
     * on all backwards-bound cancellables.
     *
     * Forwards- and backwards- binding to a cancellable is a logic bug.
     */
    @PublicAPI
    fun backwardsBind(cancel: Cancellable)

    @PublicAPI
    fun cancelled(): Boolean
    @PublicAPI
    fun cancel()
}

/** A cancel callback. */
@PublicAPI
class Cancel(private val callback: Runnable) : Cancellable, Runnable {
    private var cancelled = false
    private val alsoCancel = Seq<Cancellable>()
    private val backwardsBound = Seq<Cancellable>()

    override fun bind(cancel: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot bind to a cancelled cancellable")
        if (backwardsBound.contains(cancel))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")

        alsoCancel.addUnique(cancel)
        cancel.backwardsBind(this)
    }
    override fun unbind(cancel: Cancellable) {
        alsoCancel.remove(cancel)
        backwardsBound.remove(cancel)
    }

    override fun backwardsBind(cancel: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot backwards bind to a cancelled cancellable")
        if (alsoCancel.contains(cancel))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")
        backwardsBound.add(cancel)
    }

    override fun cancelled(): Boolean = cancelled
    override fun cancel() {
        if (!cancelled) cancelled = true else return

        callback.run()
        alsoCancel.each(Cancellable::cancel)
        backwardsBound.each { it.unbind(this) }
    }
    override fun run() = cancel()
}

@PublicAPI
enum class Priority {
    /**
     * Run before any other handler.
     *
     * Use with caution.
     */
    Before,
    Lowest,
    Low,
    Normal,
    High,
    Highest,
    /**
     * Run after any other handler.
     *
     * Use with caution.
     */
    After,
}

@PublicAPI
open class Lifetime(): Cancellable {
    companion object {
        /** Lasts forever. */
        @PublicAPI
        @JvmField
        val Forever = Lifetime()
        /** Lasts until round ends. */
        @PublicAPI
        @JvmField
        val Round = object : Lifetime() {
            override fun uponStart() {
                on<RoundEndEvent> {
                    cancel()
                }
            }
            override fun uponEnd() {
                cancelled = false
            }
        }
    }

    protected var cancelled: Boolean = false

    private val alsoCancel = Seq<Cancellable>()
    private val backwardsBound = Seq<Cancellable>()

    init {
        uponStart()
    }

    @PublicAPI
    protected open fun uponStart() {}
    @PublicAPI
    protected open fun uponEnd() {}

    override fun bind(cancel: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot bind to a cancelled cancellable")
        if (backwardsBound.contains(cancel))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")

        alsoCancel.addUnique(cancel)
        cancel.backwardsBind(this)
    }
    override fun unbind(cancel: Cancellable) {
        alsoCancel.remove(cancel)
        backwardsBound.remove(cancel)
    }

    override fun backwardsBind(cancel: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot backwards bind to a cancelled cancellable")
        if (alsoCancel.contains(cancel))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")
        backwardsBound.add(cancel)
    }

    override fun cancel() {
        cancelled = true
        alsoCancel.each(Cancellable::cancel)
        backwardsBound.each { it.unbind(this) }
        uponEnd()
    }

    override fun cancelled(): Boolean = cancelled
}

/**
 * Register an event handler.
 *
 * @return Cancellation for that event handler.
 */
@PublicAPI
inline fun <reified T> on(
    lifetime: Lifetime = Lifetime.Forever,
    priority: Priority = Priority.Normal,
    once: Boolean = false,
    listener: Cons<T>,
): Cancel = Events.on(lifetime, priority, once, T::class.java, listener)

/**
 * Emit an event.
 *
 * This will handle both Mindustry and network events.
 */
@PublicAPI fun <T> emit(event: T) = Events.fire(event)

private data class EventContainer (
    val cons: Cons<*>,
    val once: Boolean,
    val cancel: Cancel,
)

/**
 * Drop-in replacement for Mindustry's event handler.
 *
 * This supports both Mindustry and network events.
 */
@PublicAPI
object Events {
    private val eventHandlers = ObjectMap<Class<*>, Array<Seq<EventContainer>?>>()

    /**
     * Register an event handler.
     *
     * @return Cancellation for that event handler.
     */
    @JvmStatic
    @JvmOverloads
    fun <T> on(
        lifetime: Lifetime = Lifetime.Forever,
        priority: Priority = Priority.Normal,
        once: Boolean = false,
        cls: Class<T>, listener: Cons<T>): Cancel {
        val a =
            if (eventHandlers.containsKey(cls)) eventHandlers[cls]
            else {
                @Suppress("UNCHECKED_CAST")
                arc.Events.on(cls) { event ->
                    val priorityQueue = eventHandlers[cls]
                    var cancelled = false
                    for (prioritized in priorityQueue) {
                        prioritized?.removeAll {
                            (it.cons as Cons<Any?>)[event as Any?]
                            if (event is Cancellable && event.cancelled()) cancelled = true
                            if (it.once) {
                                it.cancel.cancel()
                                true
                            }
                            else false
                        }
                        if (cancelled) break
                    }
                }
                if (cls.annotations.any{ it.annotationClass == NetworkEvent::class })
                    RabbitMQ.recv(cls as Class<*>) {
                        arc.Events.fire(it)
                    }
                val array = Array<Seq<EventContainer>?>(Priority.entries.size) { null }
                eventHandlers.put(cls, array)
                array
            }
        val b =
            if (a[priority.ordinal] != null) a[priority.ordinal] as Seq<EventContainer>
            else {
                val s = Seq<EventContainer>()
                a[priority.ordinal] = s
                s
            }
        val container = Ref<EventContainer>(nodecl())
        val cancel = Cancel {
            b.remove(container.r)
        }
        container.r = EventContainer(
            listener,
            once,
            cancel,
        )
        b.add(container.r)
        lifetime.bind(cancel)

        return cancel
    }

    // TODO: Make this API thread-safe and remove `Bus`
    /** Emit an event. */
    @JvmStatic
    fun <T> fire(event: T) {
        if (event?.javaClass?.annotations?.any{ it.annotationClass == NetworkEvent::class } == true) RabbitMQ.send(event)
        else arc.Events.fire(event)
    }

    /** Remove an event handler. */
    @JvmStatic
    fun <T> remove(ty: Class<T>, listener: Cons<T>) {
        val handlers = eventHandlers.get(ty) ?: return
        for (a in handlers) {
            a?.removeAll {
                if (it.cons == listener) {
                    it.cancel.cancel()
                    true
                }
                else false
            }
        }
    }
}
