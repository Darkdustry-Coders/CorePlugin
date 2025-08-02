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
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.serializer
import kotlin.jvm.kotlin
import java.net.ConnectException
import java.util.WeakHashMap
import kotlin.reflect.full.createType

class MessageWrapper {}

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
            channel.exchangeDeclare(queueName, "direct", true, false, true, emptyMap())
            channel.queueDeclare(queueName, true, false, true, emptyMap())
            channel.queueBind(queueName, queueName, CorePluginConfig.i().serverName)
        }
        val body = Serializers.cbor.encodeToByteArray<Any?>(`object`)
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
            val `object`: T = Serializers.cbor.decodeFromByteArray(
                Serializers.cbor.serializersModule.serializer(klass.kotlin.createType(emptyList(), false, emptyList())),
                msg.body) as T
        }, {}, { _, _ -> })

        return Cancel {
            channel.basicCancel(tag)
        }
    }

    fun<T> sentBy(`object`: T): String? = sentBy[`object`]
    fun<T> correlationId(`object`: T): String? = correlationId[`object`]
}
