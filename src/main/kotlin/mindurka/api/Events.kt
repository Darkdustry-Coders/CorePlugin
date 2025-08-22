package mindurka.api

import arc.func.Cons
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import mindurka.annotations.PublicAPI
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Player
import arc.util.Log
import mindurka.util.any
import mindurka.annotations.NetworkEvent
import mindurka.coreplugin.RabbitMQ

/**
 * A player is having their team assigned.
 *
 * This event is usually fired by team assigner.
 */
@PublicAPI
data class PlayerTeamAssign(
    val player: Player,
    val players: Iterable<Player>,
    var team: Team,
)

/** A cancellable object. */
@PublicAPI
interface Cancellable {
    fun cancelled(): Boolean
    fun cancel()
}

/** A cancel callback. */
@PublicAPI
class Cancel(private val callback: Runnable) : Cancellable, Runnable {
    private var cancelled = false
    override fun cancelled(): Boolean = cancelled
    override fun cancel() {
        if (!cancelled) cancelled = true else return

        callback.run()
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
enum class Lifetime {
    /** Event handler lasts until it's fired or round ends */
    OnceOrRound,
    /** Event handler lasts until it's fired */
    Once,
    /** Event handler lasts until round ends */
    Round,
    /** Event handler lasts forever until canceled */
    Forever,
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
    listener: Cons<T>
): Cancel = Events.on(lifetime, priority, T::class.java, listener)

/**
 * Emit an event.
 *
 * This will handle both Mindustry and network events.
 */
@PublicAPI fun <T> emit(event: T) = Events.fire(event)

/**
 * Drop-in replacement for Mindustry's event handler.
 *
 * This supports both Mindustry and network events.
 */
@PublicAPI
object Events {
    private val eventHandlers = ObjectMap<Class<*>, ObjectMap<Lifetime, Seq<Seq<Cons<Any>>>>>()
    private val notCancelled = ObjectSet<Any>()

    /**
     * Register an event handler.
     *
     * @return Cancellation for that event handler.
     */
    @JvmStatic
    fun <T> on(event: Class<T>, listener: Cons<T>): Cancel =
            on(Lifetime.Forever, Priority.Normal, event, listener)
    /**
     * Register an event handler.
     *
     * @return Cancellation for that event handler.
     */
    @JvmStatic
    fun <T> on(lifetime: Lifetime, event: Class<T>, listener: Cons<T>): Cancel =
            on(lifetime, Priority.Normal, event, listener)
    /**
     * Register an event handler.
     *
     * @return Cancellation for that event handler.
     */
    @JvmStatic
    fun <T> on(priority: Priority, event: Class<T>, listener: Cons<T>): Cancel =
            on(Lifetime.Forever, priority, event, listener)

    /**
     * Register an event handler.
     *
     * @return Cancellation for that event handler.
     */
    @JvmStatic
    fun <T> on(lifetime: Lifetime, priority: Priority, cls: Class<T>, listener: Cons<T>): Cancel {
        val a =
            if (eventHandlers.containsKey(cls)) eventHandlers[cls]
            else {
                @Suppress("UNCHECKED_CAST")
                arc.Events.on(cls) { event ->
                    val a = eventHandlers[cls]
                    val handlers = Seq.with<Cons<Any>>()
                    for (priority in 0 ..< Priority.entries.size) {
                        handlers.clear()
                        for (lifetime in Lifetime.entries) {
                            if (a.containsKey(lifetime) && a[lifetime][priority] != null) {
                                handlers.addAll(a[lifetime][priority])
                            }
                        }
                        for (handle in handlers) handle[event]
                        if (event is Cancellable && event.cancelled()) break
                    }
                    a.remove(Lifetime.OnceOrRound)
                    a.remove(Lifetime.Once)
                }
                if (cls.annotations.any{ it.annotationClass == NetworkEvent::class } == true) RabbitMQ.recv<Any>(cls as Class<Any>) {
                    arc.Events.fire(it)
                }
                val handlers = ObjectMap<Lifetime, Seq<Seq<Cons<Any>>>>()
                eventHandlers.put(cls, handlers)
                Events.on(
                    Lifetime.Forever,
                    Priority.Before,
                    EventType.WorldLoadBeginEvent::class.java
                ) {
                    eventHandlers[cls].removeAll {
                        it.key === Lifetime.OnceOrRound || it.key == Lifetime.Once
                    }
                }
                handlers
            }

        val b =
            if (a.containsKey(lifetime)) a[lifetime]
            else {
                val handlers = Seq<Seq<Cons<Any>>>()
                (0..Priority.entries.size).forEach { handlers.add(Seq<Cons<Any>>()) }
                a.put(lifetime, handlers)
                handlers
            }

        val c = b[priority.ordinal]

        @Suppress("UNCHECKED_CAST") c.add(listener as Cons<*> as Cons<Any>)

        return Cancel {
            @Suppress("UNCHECKED_CAST")
            eventHandlers[cls][lifetime][priority.ordinal].remove(listener as Cons<*> as Cons<Any>)
        }
    }

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
        for (a in handlers.values()) {
            for (b in a) {
                b.removeAll { it === listener }
            }
        }
    }
}
