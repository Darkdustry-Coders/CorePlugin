package mindurka.api

import arc.Core
import arc.util.io.ReusableByteOutStream
import mindurka.annotations.PublicAPI
import mindustry.Vars
import mindustry.server.ServerControl
import java.io.DataOutputStream

/**
 * Constants.
 *
 * You may use them, you may not. Probably should use other things instead.
 */
@PublicAPI
object Consts {
    /** Mindustry's server control. */
    @PublicAPI
    @JvmField
    val serverControl = Core.app.listeners.first { it is ServerControl } as ServerControl
    /** SyncStream for protocol stuff. */
    @PublicAPI
    @JvmField
    val syncStream = ReusableByteOutStream()
    /** Wrapper over SyncStream for protocol stuff. */
    @PublicAPI
    @JvmField
    val dataStream = DataOutputStream(syncStream)
    /** Wrapper over SyncStream for protocol stuff. */
    @PublicAPI
    @JvmField
    val legacyMechPad = Vars.content.block("legacy-mech-pad")
    @PublicAPI
    @JvmField
    val legacyUnitFactory = Vars.content.block("legacy-unit-factory")
    @PublicAPI
    @JvmField
    val legacyUnitFactoryAir = Vars.content.block("legacy-unit-factory-air")
    @PublicAPI
    @JvmField
    val legacyUnitFactoryGround = Vars.content.block("legacy-unit-factory-air")
    @PublicAPI
    @JvmField
    val legacyCommandCenter = Vars.content.block("command-center")
    @PublicAPI
    @JvmField
    val legacyBlocks = arrayOf(legacyMechPad, legacyUnitFactory, legacyUnitFactoryAir, legacyUnitFactoryGround, legacyCommandCenter)
}