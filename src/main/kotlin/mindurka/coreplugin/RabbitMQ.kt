package mindurka.coreplugin

import arc.util.Log
import arc.Core
import arc.struct.ObjectMap
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP.BasicProperties.Builder as PropertiesBuilder
import mindurka.annotations.NetworkEvent
import mindurka.coreplugin.Config as CorePluginConfig
import mindurka.config.GlobalConfig
import mindurka.config.Serializers
import mindurka.api.Cancel
import kotlinx.serialization.serializer
import kotlin.jvm.kotlin
import java.net.ConnectException
import java.util.WeakHashMap
import kotlin.reflect.full.createType
import arc.func.Cons
import mindurka.annotations.NetworkRequest
import arc.math.Mathf
import arc.util.Timer
import java.util.concurrent.CompletableFuture
import mindurka.util.Async
import mindurka.util.Ref
import mindurka.util.nodecl

class MessageWrapper {}

class TookTooLongExeption(message: String): Exception(message)

object RabbitMQ {
    private val connection: Connection
    private val channel: Channel
    private val queues = HashSet<String>()

    private val sentBy = WeakHashMap<Any?, String?>()
    private val correlationId = WeakHashMap<Any?, String?>()

    init {
        Log.info("Starting RabbitMQ")
        val factory = ConnectionFactory()
        factory.setUri(GlobalConfig.i().rabbitMqUrl)
        factory.setAutomaticRecoveryEnabled(true)
        var con: Connection
        while (true) {
            try {
                con = factory.newConnection()
                break
            } catch (_: ConnectException) {
                Log.err("Connection refused! Retrying in 5s...")
                Thread.sleep(5000)
            }
        }
        connection = con
        channel = connection.createChannel()
        Log.info("Connected to RabbitMQ")
    }

    /** Do nothing. This is here to force rabbitmq to load. */
    fun noop() {}

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T> send(`object`: T) {
        val `annotation` = `object`?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        if (queues.add(queueName)) {
            channel.exchangeDeclare(queueName, "direct", true, true, false, emptyMap())
            channel.queueDeclare(queueName, true, false, true, emptyMap())
            channel.queueBind(queueName, queueName, CorePluginConfig.i().serverName)
        }
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        channel.basicPublish(
            queueName,
            CorePluginConfig.i().serverName,
            true, false,
            PropertiesBuilder()
                .appId(CorePluginConfig.i().serverName)
                .contentEncoding("text/cbor")
                .build(),
            body)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T: Any> recv(klass: Class<T>, cb: (T) -> Unit): Cancel {
        val `annotation` = klass.annotations.
                           find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                           ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        if (queues.add(queueName)) {
            channel.exchangeDeclare(queueName, "direct", true, false, true, emptyMap())
            channel.queueDeclare(queueName, true, false, true, emptyMap())
            channel.queueBind(queueName, queueName, CorePluginConfig.i().serverName)
        }
        val tag = channel.basicConsume(queueName, true, queueName, true, false, emptyMap(), { _, msg ->
            if (msg.properties.replyTo != null && msg.properties.replyTo != CorePluginConfig.i().serverName) return@basicConsume
            if (msg.properties.contentEncoding != "text/cbor" && msg.properties.contentEncoding != null) return@basicConsume

            val `object`: T = Serializers.cbor.decodeFromByteArray(
                Serializers.cbor.serializersModule.serializer(klass.kotlin.createType(emptyList(), false, emptyList())),
                msg.body) as T

            sentBy[`object`] = msg.properties.appId
            correlationId[`object`] = msg.properties.correlationId

            cb(`object`)
        }, {}, { _, _ -> })

        return Cancel {
            channel.basicCancel(tag)
        }
    }

    fun<T> sentBy(`object`: T): String? = sentBy[`object`]
    fun<T> correlationId(`object`: T): String? = correlationId[`object`]

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<R, T> request(`object`: T, recv: Cons<R>) {
        val networkEvent = `object`?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val networkRequest = `object`.javaClass.annotations.
                                     find { it.annotationClass == NetworkRequest::class } as NetworkRequest?
                                     ?: throw IllegalArgumentException("can only send requests annotated with '@NetworkRequest'")
        val queueName = networkEvent.value
        if (queues.add(queueName)) {
            channel.exchangeDeclare(queueName, "direct", true, true, false, emptyMap())
            channel.queueDeclare(queueName, true, false, true, emptyMap())
            channel.queueBind(queueName, queueName, CorePluginConfig.i().serverName)
        }
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        val consumerTag = "temp-tag-${Mathf.random(Int.MAX_VALUE)}-${System.currentTimeMillis()}"
        val tag = channel.basicConsume(queueName, true, queueName, true, false, emptyMap(), { _, msg ->
            if (msg.properties.replyTo != null && msg.properties.replyTo != CorePluginConfig.i().serverName) return@basicConsume
            if (msg.properties.contentEncoding != "text/cbor" && msg.properties.contentEncoding != null) return@basicConsume

            val `object`: R = Serializers.cbor.decodeFromByteArray(
                Serializers.cbor.serializersModule.serializer(networkRequest.value.createType(emptyList(), false, emptyList())),
                msg.body) as R

            sentBy[`object`] = msg.properties.appId
            correlationId[`object`] = msg.properties.correlationId

            recv[`object`]
        }, {}, { _, _ -> })
        channel.basicPublish(
            queueName,
            CorePluginConfig.i().serverName,
            true, false,
            PropertiesBuilder()
                .appId(CorePluginConfig.i().serverName)
                .contentEncoding("text/cbor")
                .build(),
            body)
        Timer.schedule({ channel.basicCancel(tag) }, networkEvent.ttl.toFloat())
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T, R> requestOnce(`object`: T): CompletableFuture<R> {
        val future = CompletableFuture<R>()
        val timer = Ref<Timer.Task>(nodecl())
        val networkEvent = `object`?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val networkRequest = `object`.javaClass.annotations.
                                     find { it.annotationClass == NetworkRequest::class } as NetworkRequest?
                                     ?: throw IllegalArgumentException("can only send requests annotated with '@NetworkRequest'")
        val queueName = networkEvent.value
        if (queues.add(queueName)) {
            channel.exchangeDeclare(queueName, "direct", true, true, false, emptyMap())
            channel.queueDeclare(queueName, true, false, true, emptyMap())
            channel.queueBind(queueName, queueName, CorePluginConfig.i().serverName)
        }
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        val consumerTag = "temp-tag-${Mathf.random(Int.MAX_VALUE)}-${System.currentTimeMillis()}"
        val tag = Ref("")
        tag.r = channel.basicConsume(queueName, true, queueName, true, false, emptyMap(), { _, msg ->
            if (msg.properties.replyTo != null && msg.properties.replyTo != CorePluginConfig.i().serverName) return@basicConsume
            if (msg.properties.contentEncoding != "text/cbor" && msg.properties.contentEncoding != null) return@basicConsume

            val `object`: R = Serializers.cbor.decodeFromByteArray(
                Serializers.cbor.serializersModule.serializer(networkRequest.value.createType(emptyList(), false, emptyList())),
                msg.body) as R

            sentBy[`object`] = msg.properties.appId
            correlationId[`object`] = msg.properties.correlationId

            timer.r.cancel()
            channel.basicCancel(tag.r)
            future.complete(`object`)
        }, {}, { _, _ -> })
        channel.basicPublish(
            queueName,
            CorePluginConfig.i().serverName,
            true, false,
            PropertiesBuilder()
                .appId(CorePluginConfig.i().serverName)
                .contentEncoding("text/cbor")
                .build(),
            body)
        timer.r = Timer.schedule({
            channel.basicCancel(tag.r)
            future.completeExceptionally(TookTooLongExeption("could not receive an event in required time"))
        }, networkEvent.ttl.toFloat())
        return future
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<E, M> reply(event: E, message: M) {
        val sentBy = sentBy(event)
        val correlationId = correlationId(event)

        val `annotation` = message?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        if (queues.add(queueName)) {
            channel.exchangeDeclare(queueName, "direct", true, true, false, emptyMap())
            channel.queueDeclare(queueName, true, false, true, emptyMap())
            channel.queueBind(queueName, queueName, CorePluginConfig.i().serverName)
        }
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(message.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            message)
        channel.basicPublish(
            queueName,
            CorePluginConfig.i().serverName,
            true, false,
            PropertiesBuilder()
                .appId(CorePluginConfig.i().serverName)
                .contentEncoding("text/cbor")
                .replyTo(sentBy)
                .correlationId(correlationId)
                .build(),
            body)
    }
}
