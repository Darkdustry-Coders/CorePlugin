package mindurka.coreplugin

import mindurka.config.GlobalConfig
import arc.util.Log
import com.rabbitmq.client.ConnectionFactory
import arc.Core
import com.rabbitmq.client.Connection

class RabbitMQ {
    val connection: Connection

    init {
        Log.info("Starting RabbitMQ")
        val factory = ConnectionFactory()
        factory.setUri(GlobalConfig.i().rabbitMqUrl)
        connection = factory.newConnection()
        Log.info("Connected to RabbitMQ")
    }
}

val rabbitmq = RabbitMQ()
