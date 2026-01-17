package mindurka.coreplugin

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
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
import arc.util.Log
import arc.util.Timer
import com.rabbitmq.client.AMQP.BasicProperties.Builder
import mindurka.annotations.PublicAPI
import java.util.concurrent.CompletableFuture
import mindurka.util.Ref
import mindurka.util.nodecl

class TookTooLongExeption(message: String): Exception(message)

object RabbitMQ {
    private val connection: Connection
    private val channel: Channel
    private val queues = HashSet<String>()

    private val sentBy = WeakHashMap<Any?, String?>()
    private val correlationId = WeakHashMap<Any?, String?>()

    init {
        val factory = ConnectionFactory()
        factory.setUri(GlobalConfig.i().rabbitMqUrl)
        factory.isAutomaticRecoveryEnabled = true
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
        channel.basicQos(100);
        channel.addShutdownListener {
            Log.err("The world is on fire", it)
        }
    }

    /** Do nothing. This is here to force rabbitmq to load. */
    fun noop() {}

    private fun exchange(queueName: String) = "main.$queueName"

    private fun queue(queueName: String) = "main.$queueName.${CorePluginConfig.i.serverName}"

    private fun ensureExchange(queueName: String) {
        if (queues.add(queueName)) {
            channel.exchangeDeclare(exchange(queueName), "fanout", true, false, false, emptyMap())
            channel.queueDeclare(queue(queueName), true, false, true, emptyMap())
            channel.queueBind(queue(queueName), exchange(queueName), CorePluginConfig.i.serverName)
        }
    }

    private fun publish(queueName: String, body: ByteArray, routingKey: String = "#", props: BasicProperties.Builder = PropertiesBuilder()) {
        channel.basicPublish(
            exchange(queueName),
            routingKey,
            true, false,
            props
                .appId(CorePluginConfig.i().serverName)
                .contentEncoding("text/cbor")
                .build(),
            body)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T> send(`object`: T, routingKey: String = "#") {
        val `annotation` = `object`?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        ensureExchange(queueName)
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        publish(queueName, body, routingKey)
    }

    @PublicAPI
    @JvmStatic
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T> sendTo(`object`: T, to: String, routingKey: String = "#") {
        val `annotation` = `object`?.javaClass?.annotations?.
        find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
            ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        ensureExchange(queueName)
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        publish(queueName, body, routingKey, props = PropertiesBuilder().replyTo(to))
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T: Any> recv(klass: Class<T>, cb: (T) -> Unit): Cancel {
        val `annotation` = klass.annotations.
                           find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                           ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        ensureExchange(queueName)
        val consumerTag = "temp-tag-${Mathf.random(Int.MAX_VALUE / 2)}-${System.currentTimeMillis()}"
        val tag = channel.basicConsume(queue(queueName), true, consumerTag, true, false, emptyMap(), { _, msg ->
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

    @PublicAPI
    @JvmStatic
    fun<T> sentBy(`object`: T): String? = sentBy[`object`]
    @PublicAPI
    @JvmStatic
    fun<T> correlationId(`object`: T): String? = correlationId[`object`]

    @PublicAPI
    @JvmStatic
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<R, T> request(`object`: T, timeout: Float, recv: Cons<R>) {
        val networkEvent = `object`?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val networkRequest = `object`.javaClass.annotations.
                                     find { it.annotationClass == NetworkRequest::class } as NetworkRequest?
                                     ?: throw IllegalArgumentException("can only send requests annotated with '@NetworkRequest'")
        val queueName = networkEvent.value
        ensureExchange(queueName)
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        val consumerTag = "temp-tag-${Mathf.random(Int.MAX_VALUE / 2)}-${System.currentTimeMillis()}"
        val tag = channel.basicConsume("Q$queueName-${CorePluginConfig.i.serverName}", true, consumerTag, true, false, emptyMap(), { _, msg ->
            if (msg.properties.replyTo != null && msg.properties.replyTo != CorePluginConfig.i().serverName) return@basicConsume
            if (msg.properties.contentEncoding != "text/cbor" && msg.properties.contentEncoding != null) return@basicConsume

            val `object`: R = Serializers.cbor.decodeFromByteArray(
                Serializers.cbor.serializersModule.serializer(networkRequest.value.createType(emptyList(), false, emptyList())),
                msg.body) as R

            sentBy[`object`] = msg.properties.appId
            correlationId[`object`] = msg.properties.correlationId

            recv[`object`]
        }, {}, { _, _ -> })
        publish(queueName, body)
        channel.basicPublish(
            "Ex$queueName",
            CorePluginConfig.i().serverName,
            true, false,
            PropertiesBuilder()
                .appId(CorePluginConfig.i().serverName)
                .contentEncoding("text/cbor")
                .build(),
            body)
        Timer.schedule({ channel.basicCancel(tag) }, timeout)
    }

    @PublicAPI
    @JvmStatic
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<T, R> requestOnce(`object`: T, timeout: Float): CompletableFuture<R> {
        val future = CompletableFuture<R>()
        val timer = Ref<Timer.Task>(nodecl())
        val networkEvent = `object`?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val networkRequest = `object`.javaClass.annotations.
                                     find { it.annotationClass == NetworkRequest::class } as NetworkRequest?
                                     ?: throw IllegalArgumentException("can only send requests annotated with '@NetworkRequest'")
        val queueName = networkEvent.value
        ensureExchange(queueName)
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(`object`.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            `object`)
        val consumerTag = "temp-tag-${Mathf.random(Int.MAX_VALUE / 2)}-${System.currentTimeMillis()}"
        val tag = Ref("")
        tag.r = channel.basicConsume("Q$queueName-${CorePluginConfig.i.serverName}", true, consumerTag, true, false, emptyMap(), { _, msg ->
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
            "Ex$queueName",
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
        }, timeout)
        return future
    }

    @PublicAPI
    @JvmStatic
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun<E, M> reply(event: E, message: M) {
        val sentBy = sentBy(event)
        val correlationId = correlationId(event)

        val `annotation` = message?.javaClass?.annotations?.
                                   find { it.annotationClass == NetworkEvent::class } as NetworkEvent?
                                   ?: throw IllegalArgumentException("can only send objects annotated with '@NetworkEvent'")
        val queueName = `annotation`.value
        ensureExchange(queueName)
        val body = Serializers.cbor.encodeToByteArray(
            Serializers.cbor.serializersModule.serializer(message.javaClass.kotlin.createType(emptyList(), false, emptyList())),
            message)
        publish(queueName, body, props = PropertiesBuilder().replyTo(sentBy).correlationId(correlationId))
    }

    @PublicAPI
    fun flush() {
        // TODO: Somehow make this work properly, idk
        Thread.sleep(200);
    }
}
