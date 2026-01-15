package fi.sulku.hytale

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.permissions.PermissionsModule
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.util.Config

class TinyMessagePlugin(init: JavaPluginInit) : JavaPlugin(init) {

    private val _config: Config<Configuration> = withConfig(Configuration.CODEC)

    val config: Configuration
        get() = _config.get()

    override fun setup() {
        super.setup()
        _config.save()
    }

    override fun start() {
        super.start()

        if (config.isChatColorEnabled) {
            eventRegistry.register(PlayerChatEvent::class.java) { event: PlayerChatEvent ->
                val permissions = PermissionsModule.get()
                val sender = event.sender

                if (!config.needsPermission || permissions.hasPermission(sender.uuid, config.chatColorPerm)) {
                    event.formatter = PlayerChatEvent.Formatter { _, msg ->
                        TinyMsg.parse(msg)
                    }
                }
            }
        }
    }

    class Configuration {
        var isChatColorEnabled: Boolean = true
            private set
        var needsPermission: Boolean = false
            private set
        var chatColorPerm: String = "tinymsg.chat.color"
            private set

        companion object {
            val CODEC: BuilderCodec<Configuration> =
                BuilderCodec.builder<Configuration>(Configuration::class.java) { Configuration() }
                    .append(
                        KeyedCodec("IsChatColorEnabled", Codec.BOOLEAN),
                        { config: Configuration, value: Boolean -> config.isChatColorEnabled = value },
                        { config: Configuration -> config.isChatColorEnabled }
                    ).add()
                    .append(
                        KeyedCodec("NeedsPermission", Codec.BOOLEAN),
                        { config: Configuration, value: Boolean -> config.needsPermission = value },
                        { config: Configuration -> config.needsPermission }
                    ).add()
                    .append(
                        KeyedCodec("ChatColorPerm", Codec.STRING),
                        { config: Configuration, value: String -> config.chatColorPerm = value },
                        { config: Configuration -> config.chatColorPerm }
                    ).add()
                    .build()
        }
    }
}