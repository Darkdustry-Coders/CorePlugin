package mindurka.coreplugin

import mindustry.mod.Plugin
import mindurka.api.Gamemode
import mindurka.util.prefixed

class Main : Plugin() {
    override fun init() {
        Gamemode.init(javaClass.classLoader.prefixed("coreplugin"))
    }
}
