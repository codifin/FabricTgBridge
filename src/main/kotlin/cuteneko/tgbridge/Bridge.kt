package cuteneko.tgbridge

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import cuteneko.tgbridge.tgbot.TgBot
import kotlinx.coroutines.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents // ДОБАВЛЕН ИМПОРТ СОБЫТИЙ ЧАТА
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException

class Bridge : ModInitializer {
    
    private val bridgeJob = SupervisorJob()
    private val bridgeScope = CoroutineScope(Dispatchers.Default + bridgeJob)

    override fun onInitialize() {
        LOGGER.info("Telegram bridge loaded!")
        INSTANCE = this
        CONFIG = ConfigLoader.load()
        ConfigLoader.save(CONFIG)

        if (CONFIG.botToken == "YOUR BOT TOKEN HERE") {
            LOGGER.info("Please edit config file!")
            return
        }

        try {
            LANG = ConfigLoader.getLang()
        } catch (e: FileNotFoundException) {
            LOGGER.error("lang.json not found! Read the document for more info")
            return
        }

        BOT = TgBot(LOGGER)
        bridgeScope.launch { BOT.startPolling() }

        CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(LiteralArgumentBuilder.literal<ServerCommandSource?>("tgbridge_reload")
                .requires { it.hasPermissionLevel(4) }
                .executes {
                    if (RELOADING) {
                        it.source.sendFeedback(LiteralText("A reload is already in progress!").formatted(Formatting.RED), false)
                        return@executes 1
                    }
                    RELOADING = true
                    it.source.sendFeedback(LiteralText("Reloading!"), false)
                    CONFIG = ConfigLoader.load()
                    
                    bridgeScope.launch {
                        try {
                            BOT.stop()
                            BOT = TgBot(LOGGER)
                            BOT.startPolling()
                            it.source.sendFeedback(LiteralText("Reloaded!"), false)
                        } catch (e: Exception) {
                            it.source.sendFeedback(LiteralText("Error occurred!").formatted(Formatting.RED), false)
                            e.message?.let { msg -> it.source.sendFeedback(LiteralText(msg), false) }
                        } finally {
                            RELOADING = false
                        }
                    }
                    0
                })
        }

        // 1. Перехват сообщений из игрового чата в Telegram
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            if (CONFIG.sendChatMessage) {
                val text = message.content.toPlainString(false)
                val username = sender.name.string
                bridgeScope.launch {
                    BOT.sendMessageToTelegram(text, username)
                }
            }
        }

        // 2. Перехват входа игрока на сервер
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (CONFIG.sendGameMessage) {
                val username = handler.player.name.string
                bridgeScope.launch {
                    BOT.sendMessageToTelegram("$username вошел в игру.")
                }
            }
        }

        // 3. Перехват выхода игрока с сервера
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (CONFIG.sendGameMessage) {
                val username = handler.player.name.string
                bridgeScope.launch {
                    BOT.sendMessageToTelegram("$username покинул игру.")
                }
            }
        }

        // Старт сервера
        ServerLifecycleEvents.SERVER_STARTED.register {
            SERVER = it
            if (CONFIG.sendServerStarted) {
                bridgeScope.launch { BOT.sendMessageToTelegram(CONFIG.serverStartedMessage) }
            }
        }

        // Остановка сервера
        ServerLifecycleEvents.SERVER_STOPPING.register {
            runBlocking {
                try {
                    withTimeoutOrNull(2500) {
                        if (CONFIG.sendServerStopping) {
                            BOT.sendMessageToTelegram(CONFIG.serverStoppingMessage)
                        }
                    }
                    BOT.stop()
                } catch (e: Exception) {
                    LOGGER.error("Error while stopping bot: ${e.message}")
                } finally {
                    bridgeJob.cancelChildren()
                    bridgeJob.cancel()
                }
            }
        }
    }

    companion object {
        const val MOD_ID = "tgbridge"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var INSTANCE: Bridge
        lateinit var SERVER: MinecraftServer
        lateinit var CONFIG: Config
        lateinit var LANG: Map<String, String>
        lateinit var BOT: TgBot
        var RELOADING: Boolean = false
        
        fun sendMessage(text: Text?) {
            if (text == null) return
            if (!::SERVER.isInitialized) return

            SERVER.execute {
                SERVER.playerManager.playerList.forEach {
                    it.sendSystemMessage(text, Util.NIL_UUID)
                }
                SERVER.sendSystemMessage(text, Util.NIL_UUID)
            }
        }
    }
}