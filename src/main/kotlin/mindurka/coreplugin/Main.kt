package mindurka.coreplugin

import mindustry.mod.Plugin
import mindurka.api.Gamemode

class Main : Plugin() {
    override fun init() {
        Gamemode.init(javaClass)
    }
}
