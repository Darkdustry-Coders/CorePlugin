package mindurka.api

import arc.func.Cons
import arc.struct.ObjectMap
import arc.struct.Seq
import mindurka.annotations.PublicAPI
import mindurka.annotations.NetworkEvent
import mindurka.coreplugin.RabbitMQ
import mindurka.util.Ref
import mindurka.util.UnsafeNull
import mindurka.util.nodecl
import mindurka.util.unreachable
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.gen.Unit
import mindustry.world.blocks.environment.Floor
import org.jline.utils.Log

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

/**
 * Marks the internals of [Cancellable].
 *
 * Generally, only the implementors of [Cancellable] will ever need to opt into this.
 */
@PublicAPI
@RequiresOptIn(message = "This is an internal API. Calling it manually may cause problems at runtime.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class CancellableInternals

/** A cancellable object. */
@PublicAPI
interface Cancellable {
    /**
     * Cancel another [Cancellable] when this [Cancellable] gets canceled.
     */
    @PublicAPI
    fun alsoCancel(other: Cancellable)
    /**
     * Unbind a [Cancellable].
     *
     * This removes both forwards and backwards binds. Caller should always set [recursive]
     * to `true` or use the method without the extra argument.
     *
     * @param cancel [Cancellable] to unbind from.
     * @param recursive If `true`, must call [Cancellable.unbind] on [cancel].
     */
    @PublicAPI
    @CancellableInternals
    fun unbind(cancel: Cancellable, recursive: Boolean)

    /**
     * Unbind a [Cancellable].
     *
     * This removes both forwards and backwards binds.
     *
     * @param cancel [Cancellable] to unbind from.
     */
    @PublicAPI
    @OptIn(CancellableInternals::class)
    fun unbind(cancel: Cancellable) = unbind(cancel, true)

    /**
     * Called by the binder upon [Cancellable.alsoCancel].
     *
     * When a binder cancels, it should call [Cancellable.unbind]
     * on all backwards-bound [Cancellable]s.
     *
     * This is used for optimization purposes.
     *
     * This method should not be called manually.
     *
     * Forwards- and backwards- binding to a cancellable is a logic bug.
     */
    @PublicAPI
    @CancellableInternals
    fun backwardsBind(cancel: Cancellable)

    /**
     * Check if this [Cancellable] is cancelled.
     *
     * If `true`, [Cancellable.cancel] will do nothing.
     */
    @PublicAPI
    fun cancelled(): Boolean
    /**
     * Cancel this [Cancellable].
     *
     * Implementors must ensure that calling this method multiple times only
     * cancels once.
     */
    @PublicAPI
    fun cancel()
}

/**
 * A cancel callback.
 *
 * Instances of [Cancel] are internally cached.
 */
@PublicAPI
class Cancel private constructor(callback: Runnable) : Cancellable, Runnable {
    companion object {
        private var bigCacheWarning = false
        private val cache = Seq<Cancel>()
        private val nullRunnable = Runnable { unreachable("This callback should have never been called") }

        /**
         * Obtain a new [Cancel] from the cache.
         */
        @PublicAPI
        @JvmStatic
        fun get(callback: Runnable): Cancel {
            if (cache.isEmpty) return Cancel(callback)

            val cancel = cache.pop()
            cancel.callback = callback

            return cancel
        }

        /**
         * Obtain a new [Cancel] from the cache.
         */
        @PublicAPI
        @JvmStatic
        fun get(parent: Cancellable, callback: Runnable): Cancel {
            if (cache.isEmpty) return Cancel(callback)

            val cancel = cache.pop()
            cancel.callback = callback
            parent.alsoCancel(cancel)

            return cancel
        }

        operator fun invoke(callback: Runnable) = get(callback)
        operator fun invoke(parent: Cancellable, callback: Runnable) = get(parent, callback)
    }

    /** Callback. `null` state is reserved for canceled state. */
    private var callback: Runnable? = callback
    private val alsoCancel = Seq<Cancellable>()
    private val backwardsBound = Seq<Cancellable>()

    @OptIn(CancellableInternals::class)
    override fun alsoCancel(other: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot bind to a cancelled cancellable")
        if (backwardsBound.contains(other))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")

        alsoCancel.addUnique(other)
        other.backwardsBind(this)
    }
    @CancellableInternals
    override fun unbind(cancel: Cancellable, recursive: Boolean) {
        alsoCancel.remove(cancel)
        backwardsBound.remove(cancel)
        if (recursive) cancel.unbind(this, false)
    }

    @CancellableInternals
    override fun backwardsBind(cancel: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot backwards bind to a cancelled cancellable")
        if (alsoCancel.contains(cancel))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")
        backwardsBound.add(cancel)
    }

    override fun cancelled(): Boolean = callback == null
    /**
     * Cancel this [Cancellable].
     *
     * Implementors must ensure that calling this method multiple times only
     * cancels once.
     *
     * If you can ensure that
     */
    override fun cancel() {
        val cbold = callback ?: return

        cbold.run()
        callback = null

        alsoCancel.each(Cancellable::cancel)
        backwardsBound.each { it.unbind(this) }

        alsoCancel.clear()
        backwardsBound.clear()
        callback = nullRunnable
    }
    override fun run() = cancel()

    /**
     * Release this [Cancel].
     *
     * Caller must ensure that nothing holds a reference to this object before
     * calling this method. If this cannot be guaranteed, [Cancel.cancel] should
     * be used instead.
     *
     * Calls [Cancel.cancel] under the hood.
     */
    fun release() {
        cancel()
        if (cache.size < 64) cache.add(this)
        else if (!bigCacheWarning) {
            bigCacheWarning = true
            Log.warn("Attempted to cache more than 64 `Cancel`s, is there a memory leak?")
        }
    }
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

/**
 * Lifetimes of long-lived objects.
 *
 * For short-lived durations use [Cancel].
 */
@PublicAPI
open class Lifetime(parent: Cancellable? = null): Cancellable {
    companion object {
        /**
         * Lasts for the entire lifetime of the application.
         *
         * May get cancelled before the application quits, but not necessarily so.
         */
        @PublicAPI
        @JvmField
        val Forever = Lifetime()
        /**
         * Lasts until round ends.
         *
         * Importantly, this does not guarantee availability of the world in which
         * this lifetime had been started.
         *
         * The object is reused for all rounds.
         */
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
        parent?.let(::alsoCancel)
        uponStart()
    }

    @PublicAPI
    protected open fun uponStart() {}
    @PublicAPI
    protected open fun uponEnd() {}

    @OptIn(CancellableInternals::class)
    override fun alsoCancel(other: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot bind to a cancelled cancellable")
        if (backwardsBound.contains(other))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")

        this.alsoCancel.addUnique(other)
        other.backwardsBind(this)
    }
    @CancellableInternals
    override fun unbind(cancel: Cancellable, recursive: Boolean) {
        this.alsoCancel.remove(cancel)
        backwardsBound.remove(cancel)
        if (recursive) cancel.unbind(this, false)
    }

    @CancellableInternals
    override fun backwardsBind(cancel: Cancellable) {
        if (cancelled())
            throw IllegalStateException("cannot backwards bind to a cancelled cancellable")
        if (this.alsoCancel.contains(cancel))
            throw IllegalArgumentException("having a cancellable both forwards- and backwards- bound is a logic error")
        backwardsBound.add(cancel)
    }

    override fun cancel() {
        cancelled = true

        this.alsoCancel.each(Cancellable::cancel)
        backwardsBound.each { it.unbind(this) }

        this.alsoCancel.clear()
        backwardsBound.clear()

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
        @OptIn(UnsafeNull::class)
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
        lifetime.alsoCancel(cancel)

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
