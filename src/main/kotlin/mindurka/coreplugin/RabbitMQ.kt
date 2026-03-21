package mindurka.coreplugin

import arc.Core
import arc.func.Cons
import arc.func.Prov
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import arc.util.Threads
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BasicProperties
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import kotlinx.coroutines.future.await
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer
import mindurka.annotations.NetworkEvent
import mindurka.annotations.PublicAPI
import mindurka.config.Serializers
import mindurka.config.SharedConfig
import mindurka.util.Mutex
import mindurka.util.debug
import mindurka.util.sha256
import mindurka.util.unreachable
import mindustry.Vars
import java.io.IOException
import java.net.ConnectException
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import kotlin.jvm.kotlin
import kotlin.reflect.full.createType
import kotlin.system.exitProcess
import kotlin.uuid.ExperimentalUuidApi

class RabbitMQLock internal constructor(private val worker: RabbitMQWorker, channel: Channel, queueName: String) {
    private var channel: Channel? = channel
    private var queueName: String? = queueName

    /**
     * Release the lock.
     *
     * Will not do anything on multiple triggers.
     */
    suspend fun release() {
        val channel = this.channel ?: return
        this.channel = null
        val queueName = this.queueName ?: return
        this.queueName = null

        worker.task<Unit> {
            channel.queueDelete(queueName)
            assert(worker.shared.mutb { it.acquiredLocks.remove(queueName) })
            channel.close()
            debug("[RabbitMQ] Released lock $queueName")
        }.await()
    }
}

internal class RabbitMQWorker() {
    @JvmField
    val connection: Connection = run {
        val factory = ConnectionFactory()
        factory.setUri(SharedConfig.i().rabbitMqUrl)
        factory.isAutomaticRecoveryEnabled = true

        while (true) {
            try {
                return@run factory.newConnection()
            } catch (_: ConnectException) {
                Log.err("Failed to connect to RabbitMQ, retrying in 5 seconds...")
                Thread.sleep(5000)
            }
        }
        unreachable()
    }
    @JvmField
    val mainChannel: Channel = connection.createChannel()

    @JvmField
    val shared = Mutex(SharedInfo())
    class SharedInfo {
        val tasks = ArrayDeque<Runnable>()
        val acquiredLocks = Seq<String>()
        val exchangesFor = ObjectMap<String, Seq<Cons<*>>>()
        var currentThread: Thread? = null
        var nextConsumerId: Long = 0L
    }

    init {
        mainChannel.basicQos(128)
        mainChannel.addShutdownListener {
            Log.err("RabbitMQ error: ${it.stackTraceToString()}")
        }
    }

    /**
     * Add a new tasks and return the thread it'll be running on.
     */
    fun resumeTasks(newTask: Runnable): Thread = shared.mut {
        it.tasks.addLast(newTask)
        it.currentThread?.let { return@mut it }
        val thread = Threads.thread("RabbitMQ Task Executor") {
            while (true) {
                val task = shared.mut {
                    val task = it.tasks.removeFirstOrNull()
                    if (task == null) {
                        it.currentThread = null
                    }
                    task
                } ?: break
                task.run()
            }
        }
        it.currentThread = thread
        thread
    }

    fun <T> task(exec: Prov<T>): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        resumeTasks {
            try {
                val x = exec.get()
                Core.app.run { future.complete(x) }
            } catch (x: Throwable) {
                Core.app.run { future.completeExceptionally(x) }
            }
        }
        return future
    }

    @JvmField
    val metadata = WeakHashMap<Any, BasicProperties>()

    @OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
    fun <T: Any> collectAll(klass: Class<T>, newMessage: Cons<T>) {
        val annotation = klass.annotations.find { it is NetworkEvent } as NetworkEvent? ?: throw IllegalArgumentException("All network messages must be annotated with '@NetworkEvent'")
        if (shared.mutb {
            if (it.exchangesFor.containsKey(annotation.value)) {
                it.exchangesFor.get(annotation.value).add(newMessage)
                true
            } else {
                it.exchangesFor.put(annotation.value, Seq<Cons<*>>().add(newMessage))
                false
            }
        }) return

        val queueName = "${annotation.value}.mindustry.${Config.i.serverName}"
        val routingKey = Config.i.serverName
        val consumerKey = "${run {shared.mutl {
            val x = it.nextConsumerId
            it.nextConsumerId += 1
            x
        }}}.${annotation.value}"

        resumeTasks {
            try {
                mainChannel.exchangeDeclare(annotation.value, BuiltinExchangeType.DIRECT, /*durable*/false, /*autoDelete*/true, /*internal*/false, emptyMap())
                mainChannel.queueDeclare(queueName, /*durable*/false, /*exclusive*/false, /*autoDelete*/true, emptyMap())
                mainChannel.queueBind(queueName, annotation.value, routingKey)
                mainChannel.queueBind(queueName, annotation.value, "#")
                mainChannel.basicConsume(queueName, false, consumerKey, object : Consumer {
                    override fun handleConsumeOk(consumerTag: String) {}
                    override fun handleCancelOk(consumerTag: String) {}
                    override fun handleCancel(consumerTag: String) {}
                    override fun handleShutdownSignal(
                        consumerTag: String,
                        sig: ShutdownSignalException
                    ) {}
                    override fun handleRecoverOk(consumerTag: String) {}

                    override fun handleDelivery(
                        consumerTag: String,
                        envelope: Envelope,
                        properties: AMQP.BasicProperties,
                        body: ByteArray
                    ) {
                        debug{"[RabbitMQ] Recvd $queueName (mime=${properties.contentEncoding}, from=${properties.appId})"}
                        val ktype = klass.kotlin.createType(emptyList(), false, emptyList())

                        val o = when (properties.contentEncoding) {
                            "application/cbor" ->
                                try {
                                    Serializers.cbor.decodeFromByteArray(Serializers.cbor.serializersModule.serializer(ktype), body)
                                } catch (t: Throwable) {
                                    mainChannel.basicNack(envelope.deliveryTag, false, false)
                                    Core.app.run { Log.err("Failed to parse message (${queueName})", t) }
                                    return
                                }
                            "application/json" ->
                                try {
                                    Serializers.json.decodeFromString(Serializers.json.serializersModule.serializer(ktype), body.toString(Vars.charset))
                                } catch (t: Throwable) {
                                    mainChannel.basicNack(envelope.deliveryTag, false, false)
                                    Core.app.run { Log.err("Failed to parse message (${queueName})", t) }
                                    return
                                }
                            "application/toml" ->
                                try {
                                    Serializers.toml.decodeFromString(Serializers.toml.serializersModule.serializer(ktype), body.toString(Vars.charset))
                                } catch (t: Throwable) {
                                    mainChannel.basicNack(envelope.deliveryTag, false, false)
                                    Core.app.run { Log.err("Failed to parse message (${queueName})", t) }
                                    return
                                }
                            else -> {
                                mainChannel.basicNack(envelope.deliveryTag, false, false)
                                Core.app.run { Log.err("Failed to parse message (${queueName}): Unsupported format ${properties.contentEncoding}") }
                                return
                            }
                        } as T

                        mainChannel.basicAck(envelope.deliveryTag, false)

                        Core.app.run { try {
                            metadata[o] = properties
                            shared.mutv { (it.exchangesFor[annotation.value] as Seq<Cons<T>>?)?.each { it.get(o) } }
                        } catch (t: Throwable) {
                            Log.err("Fatal error", t)
                            exitProcess(1)
                        } }
                    }
                })
            } catch (err: Throwable) {
                Core.app.run {
                    Log.err("Fatal RabbitMQ error", err)
                    exitProcess(1)
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T: Any> broadcast(o: T) = send(o, "#")
    @OptIn(ExperimentalSerializationApi::class)
    fun <T: Any> send(o: T, routingKey: String) {
        resumeTasks {
            val annotation = o.javaClass.annotations.find { it is NetworkEvent } as NetworkEvent? ?: throw IllegalArgumentException("All network messages must be annotated with '@NetworkEvent'")
            val props = AMQP.BasicProperties.Builder()
            props.contentEncoding("application/cbor")
            props.appId(Config.i.serverName)

            debug{"[RabbitMQ] Sending ${annotation.value} (routingKey=$routingKey)"}

            val ktype = o.javaClass.kotlin.createType(emptyList(), false, emptyList())
            val arr = Serializers.cbor.encodeToByteArray(Serializers.cbor.serializersModule.serializer(ktype), o)

            mainChannel.basicPublish(annotation.value, routingKey, true, false, props.build(), arr)
        }
    }
    suspend fun lock(name: ByteArray): RabbitMQLock? {
        val name = "lock.${sha256(name)}"
        if (shared.mutb { it.acquiredLocks.contains(name) }) {
            debug("[RabbitMQ] Failed to acquire lock $name")
            return null
        }
        val future = CompletableFuture<RabbitMQLock?>()
        resumeTasks {
            try {
                val channel = connection.createChannel()
                channel.queueDeclare(name, /*durable*/false, /*exclusive*/true, /*autoDelete*/true, emptyMap())
                shared.mutv { it.acquiredLocks.add(name) }
                debug("[RabbitMQ] Acquired lock $name")
                future.complete(RabbitMQLock(this, channel, name))
            } catch (_: IOException) {
                debug("[RabbitMQ] Failed to acquire lock $name")
                future.complete(null)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return future.await()
    }

    fun close() {
        resumeTasks { connection.close() }.join()
    }
}

object RabbitMQ {
    private lateinit var worker: RabbitMQWorker

    @JvmStatic
    internal fun init() {
        worker = RabbitMQWorker()
    }

    /**
     * Collect all events of the provided type.
     *
     * You probably want to use [mindurka.api.Events] instead.
     */
    @PublicAPI
    @JvmStatic
    fun <T: Any> collectAll(klass: Class<T>, newMessage: Cons<T>) = worker.collectAll(klass, newMessage)
    /**
     * Collect all events of the provided type.
     *
     * You probably want to use [mindurka.api.Events] instead.
     */
    @PublicAPI
    @JvmStatic
    inline fun <reified T: Any> collectAll(newMessage: Cons<T>) = collectAll(T::class.java, newMessage)

    /**
     * Broadcast a message.
     *
     * You probably want to use [mindurka.api.Events] instead.
     */
    @PublicAPI
    @JvmStatic
    fun <T: Any> broadcast(message: T) = worker.broadcast(message)

    /**
     * Send a message to a particular target.
     */
    @PublicAPI
    @JvmStatic
    fun <T: Any> send(message: T, routingKey: String) = worker.send(message, routingKey)

    /**
     * Obtain message metadata.
     */
    @PublicAPI
    @JvmStatic
    fun <T: Any> metadata(message: T): BasicProperties? = worker.metadata[message]

    /**
     * Get the ID of the application that sent the message.
     */
    @PublicAPI
    @JvmStatic
    fun <T: Any> sentBy(message: T): String = worker.metadata[message]!!.appId

    /**
     * Send a message to a particular target.
     */
    @PublicAPI
    @JvmStatic
    fun <T: Any, Y: Any> reply(to: T, with: Y) = worker.send(with, sentBy(to))

    /**
     * Create a distributed lock.
     */
    @PublicAPI
    @JvmStatic
    suspend fun lock(name: String): RabbitMQLock? = worker.lock(name.toByteArray(Vars.charset))

    /**
     * Create a distributed lock.
     */
    @PublicAPI
    @JvmStatic
    suspend fun lock(name: ByteArray): RabbitMQLock? = worker.lock(name)

    @JvmStatic
    internal fun close() {
        worker.close()
    }
}