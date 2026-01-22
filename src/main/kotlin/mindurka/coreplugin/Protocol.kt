package mindurka.coreplugin

import arc.Core
import arc.func.Cons3
import arc.func.Prov
import arc.struct.ObjectIntMap
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import arc.util.Reflect
import arc.util.Time
import arc.util.io.Reads
import arc.util.io.ReusableByteOutStream
import mindurka.annotations.PublicAPI
import mindurka.api.emit
import mindurka.api.timer
import mindurka.config.SharedConfig
import mindurka.util.Async
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.gen.ServerBinaryPacketReliableCallPacket
import mindustry.gen.ServerBinaryPacketUnreliableCallPacket
import mindustry.gen.ServerPacketReliableCallPacket
import mindustry.gen.ServerPacketUnreliableCallPacket
import mindustry.net.Net
import mindustry.net.NetConnection
import mindustry.net.Packet
import mindustry.net.Packets
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import kotlin.reflect.KClass

class OServerBinaryPacketReliableCallPacket: ServerBinaryPacketReliableCallPacket() {
    override fun read(READ: Reads, LENGTH: Int) {
        super.read(READ, LENGTH)
        BAIS.setBytes(Reflect.get(ServerBinaryPacketReliableCallPacket::class.java, this, "DATA"))
        type = mindustry.io.TypeIO.readString(Packet.READ)
    }

    override fun getPriority(): Int {
        val priority = CorePlugin.protocol.priorities.get(type, Int.MIN_VALUE)
        return if (priority == Int.MIN_VALUE) super.getPriority() else priority
    }

    override fun handleServer(con: NetConnection) {
        if (con.kicked) return;

        val stage = CorePlugin.protocol.stages.get(type) ?: return super.handleServer(con)
        if (!stage.isInstance(CorePlugin.protocol.connectionStates[con])) {
            con.kick("Invalid protocol state")
            CorePlugin.protocol.connectionStates[con] = Protocol.PanicState
            return
        }

        val handle = CorePlugin.protocol.binary[type]
        if (handle != null) handle[con, contents, CorePlugin.protocol.connectionStates[con]]

        if (con.player != null) NetServer.serverBinaryPacketReliable(con.player, type, contents)
    }
}
class OServerBinaryPacketUnreliableCallPacket: ServerBinaryPacketUnreliableCallPacket() {
    override fun read(READ: Reads, LENGTH: Int) {
        super.read(READ, LENGTH)
        BAIS.setBytes(Reflect.get(ServerBinaryPacketUnreliableCallPacket::class.java, this, "DATA"))
        type = mindustry.io.TypeIO.readString(Packet.READ)
    }

    override fun getPriority(): Int {
        val priority = CorePlugin.protocol.priorities.get(type, Int.MIN_VALUE)
        return if (priority == Int.MIN_VALUE) super.getPriority() else priority
    }

    override fun handleServer(con: NetConnection) {
        if (con.kicked) return;

        val stage = CorePlugin.protocol.stages.get(type) ?: return super.handleServer(con)
        if (!stage.isInstance(CorePlugin.protocol.connectionStates[con])) {
            con.kick("Invalid protocol state")
            CorePlugin.protocol.connectionStates[con] = Protocol.PanicState
            return
        }

        val handle = CorePlugin.protocol.binary[type]
        if (handle != null) handle[con, contents, CorePlugin.protocol.connectionStates[con]]

        if (con.player != null) NetServer.serverBinaryPacketUnreliable(con.player, type, contents)
    }
}
class OServerPacketReliableCallPacket: ServerPacketReliableCallPacket() {
    override fun read(READ: Reads, LENGTH: Int) {
        super.read(READ, LENGTH)
        BAIS.setBytes(Reflect.get(ServerPacketReliableCallPacket::class.java, this, "DATA"))
        type = mindustry.io.TypeIO.readString(Packet.READ)
    }

    override fun getPriority(): Int {
        val priority = CorePlugin.protocol.priorities.get(type, Int.MIN_VALUE)
        return if (priority == Int.MIN_VALUE) super.getPriority() else priority
    }

    override fun handleServer(con: NetConnection) {
        if (con.kicked) return;

        val stage = CorePlugin.protocol.stages.get(type) ?: return super.handleServer(con)
        if (!stage.isInstance(CorePlugin.protocol.connectionStates[con])) {
            con.kick("Invalid protocol state")
            CorePlugin.protocol.connectionStates[con] = Protocol.PanicState
            return
        }

        val handle = CorePlugin.protocol.text[type]
        if (handle != null) handle[con, contents, CorePlugin.protocol.connectionStates[con]]

        if (con.player != null) NetServer.serverPacketReliable(con.player, type, contents)
    }
}
class OServerPacketUnreliableCallPacket: ServerPacketUnreliableCallPacket() {
    override fun read(READ: Reads, LENGTH: Int) {
        super.read(READ, LENGTH)
        BAIS.setBytes(Reflect.get(ServerPacketUnreliableCallPacket::class.java, this, "DATA"))
        type = mindustry.io.TypeIO.readString(Packet.READ)
    }

    override fun getPriority(): Int {
        val priority = CorePlugin.protocol.priorities.get(type, Int.MIN_VALUE)
        return if (priority == Int.MIN_VALUE) super.getPriority() else priority
    }

    override fun handleServer(con: NetConnection) {
        if (con.kicked) return;

        val stage = CorePlugin.protocol.stages.get(type) ?: return super.handleServer(con)
        if (!stage.isInstance(CorePlugin.protocol.connectionStates[con])) {
            con.kick("Invalid protocol state")
            CorePlugin.protocol.connectionStates[con] = Protocol.PanicState
            return
        }

        val handle = CorePlugin.protocol.text[type]
        if (handle != null) handle[con, contents, CorePlugin.protocol.connectionStates[con]]

        if (con.player != null) NetServer.serverPacketUnreliable(con.player, type, contents)
    }
}

class Protocol {
    companion object {
        internal val AUTH_HEADER = byteArrayOf(43, 76, 12, 45)
    }

    private val outStream = ReusableByteOutStream()
    private val outData = DataOutputStream(outStream)

    interface NetState { val priority: Int get() = Packet.priorityNormal }
    object BeginState: NetState { override val priority: Int get() = Packet.priorityHigh }

    object VanillaClientState: NetState

    data class VerificationState(
        val key: PublicKey,
        val nonce: ByteArray,
        val time: Long,
    ): NetState { override val priority: Int get() = Packet.priorityHigh }
    data class PatchedClientState(val key: PublicKey, val mindurkaCompatVersion: Int): NetState

    private val prioritiesMap = ObjectIntMap<KClass<out NetState>>().apply {
        put(BeginState::class, Packet.priorityHigh)
        put(VerificationState::class, Packet.priorityHigh)
    }

    object PanicState: NetState

    internal val priorities = ObjectIntMap<String>()
    internal val stages = ObjectMap<String, KClass<out NetState>>()
    internal val binary = ObjectMap<String, Cons3<NetConnection, ByteArray, NetState>>()
    internal val text = ObjectMap<String, Cons3<NetConnection, String, NetState>>()

    internal val connectionStates = WeakHashMap<NetConnection, NetState>();

    inline fun <reified T, reified Y: T> overridePacket(prov: Prov<Y>) = overridePacket(T::class.java, Y::class.java, prov)
    fun <T, Y: T> overridePacket(klass: Class<T>, newKlass: Class<Y>, prov: Prov<Y>) {
        val packetToId = Reflect.get<ObjectIntMap<Class<*>>>(Net::class.java, null, "packetToId")
        val packetClasses = Reflect.get<Seq<Class<*>>>(Net::class.java, null, "packetClasses")
        val packetProvs = Reflect.get<Seq<Prov<*>>>(Net::class.java, null, "packetProvs")
        val id = packetToId.get(klass, -1)
        packetToId.put(newKlass, id)
        packetClasses.add(newKlass)
        packetProvs.replace(packetProvs[id], prov as Prov<*>)
    }

    inline fun <reified NS: NetState> addBinaryPacketHandler(type: String, handle: Cons3<NetConnection, ByteArray, NS>) = addBinaryPacketHandler(type, NS::class, handle)
    fun <NS: NetState> addBinaryPacketHandler(type: String, stage: KClass<out NetState>, handle: Cons3<NetConnection, ByteArray, NS>) {
        stages.put(type, stage)
        binary.put(type, handle as Cons3<NetConnection, ByteArray, NetState>)
        priorities.put(type, prioritiesMap.get(stage, Packet.priorityNormal))
    }

    fun publicKeyOf(player: Player) = publicKeyOf(player.con)
    fun publicKeyOf(con: NetConnection): PublicKey? {
        val state = connectionStates[con] ?: return null
        if (state is PatchedClientState) return state.key
        return null
    }

    fun hasMindurkaCompat(player: Player) = hasMindurkaCompat(player.con)
    fun hasMindurkaCompat(con: NetConnection): Boolean = connectionStates[con] is PatchedClientState

    fun mindurkaCompatVersion(player: Player) = mindurkaCompatVersion(player.con)
    fun mindurkaCompatVersion(con: NetConnection): Int {
        val state = connectionStates[con] ?: return 0
        if (state is PatchedClientState) return state.mindurkaCompatVersion
        return 0
    }

    init {
        overridePacket<ServerBinaryPacketReliableCallPacket, OServerBinaryPacketReliableCallPacket> { OServerBinaryPacketReliableCallPacket() }
        overridePacket<ServerBinaryPacketUnreliableCallPacket, OServerBinaryPacketUnreliableCallPacket> { OServerBinaryPacketUnreliableCallPacket() }
        overridePacket<ServerPacketReliableCallPacket, OServerPacketReliableCallPacket> { OServerPacketReliableCallPacket() }
        overridePacket<ServerPacketUnreliableCallPacket, OServerPacketUnreliableCallPacket> { OServerPacketUnreliableCallPacket() }

        Vars.net.handleServer(Packets.Connect::class.java) { con, packet ->
            if (connectionStates.containsKey(con)) {
                con.close()
                return@handleServer
            }
            connectionStates[con] = BeginState;

            val connections = Seq.with(Vars.net.connections).retainAll { it.address == con.address }
            if (connections.size >= 2) {
                val address = con.address
                Vars.netServer.admins.blacklistDos(address)
                con.close()
                connectionStates[con] = PanicState
                connections.each(NetConnection::close)
                timer(60f * 60 * 60 * 24) { Vars.netServer.admins.unBlacklistDos(address) }
            }

            emit(EventType.ConnectionEvent(con))
        }

        addBinaryPacketHandler<BeginState>("mindurka.connect") { con, packet, _ ->
            try {
                val dataStream = DataInputStream(ByteArrayInputStream(packet))

                val length = dataStream.readShort();
                if (length <= 0) throw IllegalArgumentException("length is too small")
                val spec = X509EncodedKeySpec(dataStream.readNBytes(length.toInt()))
                val factory = KeyFactory.getInstance("RSA")
                val key = factory.generatePublic(spec)

                val rng = SecureRandom.getInstanceStrong()
                val nonce = ByteArray(32) { 0 }
                rng.nextBytes(nonce)
                val time = Time.millis()

                outStream.reset()
                outData.write(nonce)
                outData.writeLong(time)
                outData.close()

                Call.clientBinaryPacketReliable(con, "mindurka.confirmConnect", outStream.toByteArray())

                connectionStates[con] = VerificationState(key, nonce, time)
            } catch (e: Exception) {
                connectionStates[con] = PanicState
                con.kick("Protocol error")
                Log.err("Protocol error", e)
                return@addBinaryPacketHandler
            }
        }

        addBinaryPacketHandler<VerificationState>("mindurka.verifyKey") { con, packet, state ->
            val serverIp = "/${SharedConfig.i.serverIp}:${Core.settings.getInt("port", 0)}"

            val expectedData = run {
                outStream.reset()
                outData.write(AUTH_HEADER)
                outData.writeUTF(serverIp)
                outData.write(state.nonce)
                outData.writeLong(state.time)
                outData.close()
                outStream.toByteArray()
            }

            try {
                val dataStream = DataInputStream(ByteArrayInputStream(packet))

                val length = dataStream.readShort();
                if (length <= 0) throw IllegalArgumentException("length is too small")
                val encryptedData = dataStream.readNBytes(length.toInt())
                val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE, state.key)
                val data = cipher.doFinal(encryptedData)

                if (!expectedData.contentEquals(data)) {
                    try {
                        Log.debug("Data (${data.size}): [${data.joinToString(", ") { it.toString() }}]")
                        val dataStream = DataInputStream(ByteArrayInputStream(data))
                        try {
                            if (!dataStream.readNBytes(4).contentEquals(AUTH_HEADER)) {
                                throw IllegalArgumentException("Invalid auth header!")
                            }

                            val ip = dataStream.readUTF()
                            if (ip != serverIp) {
                                throw IllegalArgumentException("Invalid server IP! ($ip vs $serverIp)")
                            }

                            val nonce = dataStream.readNBytes(32)
                            if (!nonce.contentEquals(state.nonce)) {
                                throw IllegalArgumentException("Invalid nonce!")
                            }

                            val time = dataStream.readLong()
                            if (time != state.time) {
                                throw IllegalArgumentException("Invalid time! ($time vs ${state.time})")
                            }

                            throw IllegalArgumentException("Too much data was sent!")
                        } catch (ignored: IOException) {
                            throw IllegalArgumentException("Not enough data was sent (length: ${data.size})")
                        }
                    } catch (err: IllegalArgumentException) {
                        connectionStates[con] = PanicState
                        Log.err("Key validation error", err)
                        con.kick("Key validation failure")
                        return@addBinaryPacketHandler
                    }
                }

                val mcversion = dataStream.readInt()
                if (mcversion < 2) throw IllegalArgumentException("Mindurka Compat version must be >=2")

                val player = Player.create()
                player.con = con
                con.player = player
                player.name = dataStream.readUTF().trim()
                if (player.name.length > 128) {
                    connectionStates[con] = PanicState
                    con.kick("Player name is too long")
                    return@addBinaryPacketHandler
                }
                if (player.name.isEmpty()) {
                    connectionStates[con] = PanicState
                    con.kick("Cannot join with empty name")
                    return@addBinaryPacketHandler
                }
                if (player.name.length < 2) {
                    connectionStates[con] = PanicState
                    con.kick("Player name is too short")
                    return@addBinaryPacketHandler
                }

                val mods = Seq<String>()
                val modCount = dataStream.readShort()
                for (i in 0..<modCount) mods.add(dataStream.readUTF())

                con.mobile = dataStream.readBoolean()
                // TODO: Store this somewhere. Maybe.
                val versionType = dataStream.readUTF()
                player.color.set(dataStream.readInt())
                con.usid = dataStream.readUTF()
                con.uuid = dataStream.readUTF()
                player.locale = dataStream.readUTF()
                // TODO: Replace completely with key auth and forget this exists.
                player.admin = Vars.netServer.admins.isAdmin(con.uuid, con.usid)

                connectionStates[con] = PatchedClientState(state.key, mcversion)

                Async.run { login(player, mods, mcversion) }
            } catch (e: Exception) {
                connectionStates[con] = PanicState
                con.kick("Protocol error")
                Log.err("Protocol error", e)
                return@addBinaryPacketHandler
            }
        }

        // This logic will be a bit of a mess due to how many things this needs to handle.
        //
        // TODO: Public key auth.
        Vars.net.handleServer(Packets.ConnectPacket::class.java) { con, packet ->
            // You really should not rely on this.
            //
            // This event is not fired for MindurkaCompat connections.
            emit(EventType.ConnectPacketEvent(con, packet))

            if (connectionStates[con] === VanillaClientState) {
                con.kick("Invalid protocol state")
                connectionStates[con] = PanicState
                return@handleServer
            }
            if (connectionStates[con] !== BeginState) return@handleServer
            connectionStates[con] = VanillaClientState

            val player = Player.create()
            player.con = con
            con.player = player
            con.uuid = packet.uuid
            con.usid = packet.usid
            con.mobile = packet.mobile
            player.name = packet.name.trim()
            if (player.name.length > 128) {
                connectionStates[con] = PanicState
                con.kick("Player name is too long")
                return@handleServer
            }
            if (player.name.isEmpty()) {
                connectionStates[con] = PanicState
                con.kick(Packets.KickReason.nameEmpty)
                return@handleServer
            }
            // TODO: Replace completely with key auth and forget this exists.
            player.admin = Vars.netServer.admins.isAdmin(packet.uuid, packet.usid)
            player.color.set(packet.color).a(1f)
            player.locale = packet.locale

            Async.run { login(player, packet.mods, 0) }
        }
    }

    private suspend fun login(player: Player, mods: Seq<String>, mindurkaCompatVersion: Int) {
        // TODO: Checks

        // In case a gamemode overrides that. Although idk if it should be an option. It probably should be.
        player.team(Vars.netServer.assignTeam(player))

        try {
            Reflect.get<ReusableByteOutStream>(Vars.netServer, "writeBuffer").reset()
            player.write(Reflect.get(Vars.netServer, "outputBuffer"))
        } catch (t: Throwable) {
            player.con.kick(Packets.KickReason.nameEmpty)
            Log.err(t)
            return
        }

        Vars.netServer.sendWorldData(player)
        emit(EventType.PlayerConnect(player))
    }
}

/**
 * Obtain player's public key.
 *
 * Will only be present if the player has MindurkaCompat installed.
 */
@PublicAPI val Player.publicKey get() = CorePlugin.protocol.publicKeyOf(this)
/**
 * Check if player has MindurkaCompat installed
 */
@PublicAPI val Player.hasMindurkaCompat get() = CorePlugin.protocol.hasMindurkaCompat(this)
/**
 * Check if player has MindurkaCompat installed
 */
@PublicAPI val Player.mindurkaCompatVersion get() = CorePlugin.protocol.mindurkaCompatVersion(this)
