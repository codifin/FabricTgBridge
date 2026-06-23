package cuteneko.tgbridge

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import cuteneko.tgbridge.tgbot.TgBot
import kotlinx.coroutines.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
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
        LOGGER.info("Telegram мод успешно загружен!")
        INSTANCE = this
        CONFIG = ConfigLoader.load()
        ConfigLoader.save(CONFIG)

        if (CONFIG.botToken == "YOUR BOT TOKEN HERE") {
            LOGGER.info("Пожалуйста, заполните конфигурационный файл!")
            return
        }

        try {
            LANG = ConfigLoader.getLang()
        } catch (e: FileNotFoundException) {
            LOGGER.error("Файл lang.json не найден!")
            return
        }

        BOT = TgBot(LOGGER)
        bridgeScope.launch { BOT.startPolling() }

        CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(LiteralArgumentBuilder.literal<ServerCommandSource?>("tgbridge_reload")
                .requires { it.hasPermissionLevel(4) }
                .executes {
                    if (RELOADING) {
                        it.source.sendFeedback(LiteralText("Перезапуск уже выполняется!").formatted(Formatting.RED), false)
                        return@executes 1
                    }
                    RELOADING = true
                    it.source.sendFeedback(LiteralText("Перезапуск моста..."), false)
                    CONFIG = ConfigLoader.load()
                    
                    bridgeScope.launch {
                        try {
                            BOT.stop()
                            BOT = TgBot(LOGGER)
                            BOT.startPolling()
                            it.source.sendFeedback(LiteralText("Мост успешно перезапущен!"), false)
                        } catch (e: Exception) {
                            it.source.sendFeedback(LiteralText("Произошла ошибка при перезапуске!").formatted(Formatting.RED), false)
                            e.message?.let { msg -> it.source.sendFeedback(LiteralText(msg), false) }
                        } finally {
                            RELOADING = false
                        }
                    }
                    0
                })
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (CONFIG.sendGameMessage) {
                val username = handler.player.name.string
                bridgeScope.launch {
                    try { BOT.sendMessageToTelegram("$username вошел в игру.") } catch(_: Exception) {}
                }
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (CONFIG.sendGameMessage) {
                val username = handler.player.name.string
                bridgeScope.launch {
                    try { BOT.sendMessageToTelegram("$username покинул игру.") } catch(_: Exception) {}
                }
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register {
            SERVER = it
        }

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
                    LOGGER.error("Ошибка при остановке бота: ${e.message}")
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
        
        fun onPlayerChat(username: String, message: String) {
            if (!::BOT.isInitialized) return
            if (CONFIG.sendChatMessage) {
                BOT.getBotScope().launch {
                    try {
                        BOT.sendMessageToTelegram(message, username)
                    } catch (_: Exception) {}
                }
            }
        }

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