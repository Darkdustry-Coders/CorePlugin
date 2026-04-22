package mindurka.coreplugin.nativeimage

import arc.struct.BoolSeq
import arc.struct.ByteSeq
import arc.struct.FloatSeq
import arc.struct.IntMap
import arc.struct.IntSeq
import arc.struct.IntSet
import arc.struct.LongMap
import arc.struct.LongSeq
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.ShortSeq
import arc.util.Log
import arc.util.Reflect
import arc.util.serialization.Json
import mindurka.annotations.NetworkEvent
import mindurka.api.on
import mindurka.config.Serializers
import mindurka.coreplugin.PlayerData
import mindurka.coreplugin.messages.AddFirewallBan
import mindurka.coreplugin.messages.PermissionLevel
import mindurka.coreplugin.messages.PlayerJoined
import mindurka.coreplugin.messages.PlayerLeft
import mindurka.coreplugin.messages.ServerDown
import mindurka.coreplugin.messages.ServerInfo
import mindurka.coreplugin.messages.ServerMessage
import mindurka.coreplugin.messages.ServersRefresh
import mindurka.util.newSeq
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Bullets
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.content.Liquids
import mindustry.content.Loadouts
import mindustry.content.Planets
import mindustry.content.UnitTypes
import mindustry.content.Weathers
import mindustry.ctype.MappableContent
import mindustry.game.EventType
import mindustry.game.Rules
import mindustry.game.SpawnGroup
import mindustry.gen.Icon
import mindustry.gen.ServerBinaryPacketReliableCallPacket
import mindustry.gen.ServerBinaryPacketUnreliableCallPacket
import mindustry.gen.ServerPacketReliableCallPacket
import mindustry.gen.ServerPacketUnreliableCallPacket
import mindustry.gen.Sounds
import mindustry.gen.Unit
import mindustry.io.JsonIO
import mindustry.net.Administration
import mindustry.net.ArcNetProvider
import mindustry.net.NetConnection
import mindustry.type.ItemStack
import mindustry.type.Weather
import mindustry.world.consumers.ConsumeCoolant
import mindustry.world.consumers.ConsumeItemCharged
import mindustry.world.consumers.ConsumeItemDynamic
import mindustry.world.consumers.ConsumeItemExplode
import mindustry.world.consumers.ConsumeItemExplosive
import mindustry.world.consumers.ConsumeItemFlammable
import mindustry.world.consumers.ConsumeItemList
import mindustry.world.consumers.ConsumeItemRadioactive
import mindustry.world.consumers.ConsumeItems
import mindustry.world.consumers.ConsumeLiquid
import mindustry.world.consumers.ConsumeLiquidFlammable
import mindustry.world.consumers.ConsumeLiquids
import mindustry.world.consumers.ConsumeLiquidsDynamic
import mindustry.world.consumers.ConsumePayloadDynamic
import mindustry.world.consumers.ConsumePayloadFilter
import mindustry.world.consumers.ConsumePayloads
import mindustry.world.consumers.ConsumePowerCondition
import java.io.PrintWriter
import java.lang.reflect.Field
import kotlin.system.exitProcess

private val registeredClasses = ObjectSet<String>()
private val blacklistedClasses = arrayOf($$"arc.scene.ui.CheckBox$CheckBoxStyle")
private fun heatClass(o: Class<*>) {
    if (o.isPrimitive) return
    if (o.name.startsWith("java")) return
    if (o.name.startsWith("jdk")) return
    if (o.name.startsWith("com.sun")) return
    if (o.name.startsWith("sun")) return
    if (o.name in blacklistedClasses) return

    if (registeredClasses.contains(o.name)) return
    registeredClasses.add(o.name)

    o.fields.forEach { heatClass(it.type) }
    o.declaredFields.forEach { heatClass(it.type) }
    o.constructors.forEach { o.getConstructor(*it.parameters.map { it.type }.toTypedArray()) }
    o.declaredConstructors.forEach { o.getDeclaredConstructor(*it.parameters.map { it.type }.toTypedArray()) }
    o.methods.forEach { o.getMethod(it.name, *it.parameters.map { it.type }.toTypedArray()) }
    o.declaredMethods.forEach { o.getDeclaredMethod(it.name, *it.parameters.map { it.type }.toTypedArray()) }
    o.classes.forEach(::heatClass)
    o.declaredClasses.forEach(::heatClass)
    o.annotations.forEach { heatClass(it.annotationClass.java) }
    o.declaredAnnotations.forEach { heatClass(it.annotationClass.java) }

    if (o.superclass != null) heatClass(o.superclass)
}

private inline fun <reified T: Any> serde(value: T) {
    try {
        val field = value.javaClass.getDeclaredField("Companion")
        val instance = field.get(null)
        instance.javaClass.getDeclaredMethod("serializer")
        value.javaClass.getAnnotation(NetworkEvent::class.java)
    } catch (e: Throwable) {
        throw RuntimeException("Failed to get serializer for ${value.javaClass.name}", e)
    }
    Serializers.json.decodeFromString<T>(Serializers.json.encodeToString(value))
}

fun nativeImageHeatUp() {
    heatClass(Rules.TeamRules::class.java)

    // Will this record all of them? Ofc not!

    Reflect.get<ObjectMap<Class<*>, *>>(Json::class.java, JsonIO.json, "typeToFields").each { clazz, _ -> heatClass(clazz) }
    Reflect.get<ObjectMap<Class<*>, *>>(Json::class.java, JsonIO.json, "classToTag").each { clazz, _ -> heatClass(clazz) }
    Reflect.get<ObjectMap<Class<*>, *>>(Json::class.java, JsonIO.json, "classToSerializer").each { clazz, _ -> heatClass(clazz) }
    Reflect.get<ObjectMap<Class<*>, *>>(Json::class.java, JsonIO.json, "classToDefaultValues").each { clazz, _ -> heatClass(clazz) }

    heatClass(BoolSeq::class.java)
    heatClass(ByteSeq::class.java)
    heatClass(IntSeq::class.java)
    heatClass(ShortSeq::class.java)
    heatClass(LongSeq::class.java)
    heatClass(FloatSeq::class.java)

    heatClass(IntSet::class.java)

    heatClass(IntMap::class.java)
    heatClass(LongMap::class.java)

    heatClass(NetConnection::class.java)
    heatClass(ArcNetProvider::class.java)
    heatClass(Class.forName($$"mindustry.net.ArcNetProvider$ArcConnection"))

    heatClass(Blocks::class.java)
    heatClass(UnitTypes::class.java)
    heatClass(Bullets::class.java)
    heatClass(Fx::class.java)
    heatClass(Items::class.java)
    heatClass(Liquids::class.java)
    heatClass(Loadouts::class.java)
    heatClass(Planets::class.java)
    heatClass(Weathers::class.java)

    heatClass(Unit::class.java)
    heatClass(MappableContent::class.java)

    heatClass(ConsumeCoolant::class.java)
    heatClass(ConsumeItemCharged::class.java)
    heatClass(ConsumeItemDynamic::class.java)
    heatClass(ConsumeItemExplode::class.java)
    heatClass(ConsumeItemExplosive::class.java)
    heatClass(ConsumeItemFlammable::class.java)
    heatClass(ConsumeItemList::class.java)
    heatClass(ConsumeItemRadioactive::class.java)
    heatClass(ConsumeItems::class.java)
    heatClass(ConsumeLiquid::class.java)
    heatClass(ConsumeLiquidFlammable::class.java)
    heatClass(ConsumeLiquids::class.java)
    heatClass(ConsumeLiquidsDynamic::class.java)
    heatClass(ConsumePayloadFilter::class.java)
    heatClass(ConsumePayloads::class.java)
    heatClass(ConsumePowerCondition::class.java)
    heatClass(ConsumePayloadDynamic::class.java)

    heatClass(Vars::class.java)
    heatClass(PlayerData::class.java)
    heatClass(Administration.PlayerInfo::class.java)
    heatClass(Rules.TeamRule::class.java)
    heatClass(SpawnGroup::class.java)
    heatClass(ItemStack::class.java)
    heatClass(Sounds::class.java)
    heatClass(Icon::class.java)
    heatClass(Weather.WeatherEntry::class.java)
    heatClass(Class.forName("mindurka.coreplugin.extern.HeaderI"))

    heatClass(ServerBinaryPacketReliableCallPacket::class.java)
    heatClass(ServerBinaryPacketUnreliableCallPacket::class.java)
    heatClass(ServerPacketReliableCallPacket::class.java)
    heatClass(ServerPacketUnreliableCallPacket::class.java)

    serde(AddFirewallBan(""))
    serde(PermissionLevel("", 0))
    serde(ServerMessage("", "", "", "", ""))
    serde(ServerInfo("", "", "", "", 0, 0, 0, 0, ""))
    serde(ServerDown())
    serde(ServersRefresh())
    serde(PlayerLeft("", ""))
    serde(PlayerJoined("", ""))

    Vars.content.each { heatClass(it.javaClass) }
    Vars.content.blocks().each { heatClass(it.buildType.get().javaClass) }
    Vars.content.units().each {
        it.abilities.each { heatClass(it.javaClass) }
        it.weapons.each { heatClass(it.javaClass) }
        it.parts.each { heatClass(it.javaClass) }
        it.engines.each { heatClass(it.javaClass) }
    }

    Vars.mods.scripts.runConsole("")

    on<EventType.ServerLoadEvent> { _ ->
        exitProcess(0);
    }
}