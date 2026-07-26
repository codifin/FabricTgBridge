package cuteneko.tgbridge

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import cuteneko.tgbridge.tgbot.TgBot
import kotlinx.coroutines.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
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
    
    // Используем SupervisorJob, чтобы ошибка в одной корутине не роняла весь Scope
    private val bridgeJob = SupervisorJob()
    private val bridgeScope = CoroutineScope(Dispatchers.Default + bridgeJob)

    override fun onInitialize() {
        LOGGER.info("Telegram-мост: Мод успешно загружен!")
        INSTANCE = this
        CONFIG = ConfigLoader.load()
        ConfigLoader.save(CONFIG)

        if (CONFIG.botToken == "YOUR BOT TOKEN HERE" || CONFIG.botToken.isBlank()) {
            LOGGER.warn("Telegram-мост: Пожалуйста, заполните токен бота в конфигурационном файле!")
            return
        }

        try {
            LANG = ConfigLoader.getLang()
        } catch (e: FileNotFoundException) {
            LOGGER.error("Telegram-мост: Файл локализации lang.json не найден!")
            return
        }

        BOT = TgBot(LOGGER)
        bridgeScope.launch { 
            try {
                BOT.startPolling() 
            } catch (e: Exception) {
                LOGGER.error("Ошибка при запуске поллинга бота: ", e)
            }
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            // Команда: /tgbridge_reload
            dispatcher.register(LiteralArgumentBuilder.literal<ServerCommandSource?>("tgbridge_reload")
                .requires { it.hasPermissionLevel(4) }
                .executes {
                    if (RELOADING) {
                        it.source.sendFeedback(LiteralText("Перезапуск моста уже выполняется!").formatted(Formatting.RED), false)
                        return@executes 1
                    }
                    RELOADING = true
                    it.source.sendFeedback(LiteralText("Перезапуск Telegram-моста...").formatted(Formatting.YELLOW), false)
                    CONFIG = ConfigLoader.load()
                    
                    bridgeScope.launch {
                        try {
                            BOT.stop()
                            BOT = TgBot(LOGGER)
                            BOT.startPolling()
                            it.source.sendFeedback(LiteralText("Telegram-мост успешно перезапущен!").formatted(Formatting.GREEN), false)
                        } catch (e: Exception) {
                            it.source.sendFeedback(LiteralText("Произошла ошибка при перезапуске моста!").formatted(Formatting.RED), false)
                            LOGGER.error("Ошибка перезапуска моста: ", e)
                        } finally {
                            RELOADING = false
                        }
                    }
                    0
                })

            // Команда: /tgbridge_test <сообщение>
            dispatcher.register(LiteralArgumentBuilder.literal<ServerCommandSource?>("tgbridge_test")
                .requires { it.hasPermissionLevel(4) } // Только для админов/консоли
                .then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("message", StringArgumentType.greedyString())
                    .executes { context ->
                        val message = StringArgumentType.getString(context, "message")
                        val senderName = context.source.name

                        context.source.sendFeedback(LiteralText("Тестовое сообщение отправлено в Telegram от имени $senderName!").formatted(Formatting.GREEN), false)
                        
                        // Запускаем сообщение ровно по тому же пути, по которому идут сообщения чата
                        onPlayerChat(senderName, message)
                        
                        1
                    }
                )
            )
        }

        // ==========================================
        // РЕГИСТРАЦИЯ СОБЫТИЯ ЧАТА ИЗ FABRIC API
        // ==========================================
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            val username = sender.name.string
            // На 1.18.2 могут быть особенности с типом message, берем строку текста напрямую
            val textContent = message.string 
            
            // Логируем, чтобы убедиться, что событие вообще срабатывает (потом можно убрать)
            LOGGER.info("Перехват чата: [$username] $textContent")
            
            onPlayerChat(username, textContent)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (CONFIG.sendGameMessage) {
                val username = handler.player.name.string
                bridgeScope.launch {
                    try { 
                        BOT.sendMessageToTelegram("<b>$username</b> зашёл на сервер.") 
                    } catch(e: Exception) {
                        LOGGER.error("Ошибка отправки сообщения о входе в ТГ: ", e)
                    }
                }
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (CONFIG.sendGameMessage) {
                val username = handler.player.name.string
                bridgeScope.launch {
                    try { 
                        BOT.sendMessageToTelegram("<b>$username</b> покинул сервер.") 
                    } catch(e: Exception) {
                        LOGGER.error("Ошибка отправки сообщения о выходе в ТГ: ", e)
                    }
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
                    LOGGER.error("Ошибка при остановке Telegram-бота: ${e.message}")
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
                // Используем общий пул моста, а не пул бота
                INSTANCE.bridgeScope.launch {
                    try {
                        BOT.sendMessageToTelegram(message, username)
                    } catch (e: Exception) {
                        LOGGER.error("Не удалось отправить сообщение из игры в Telegram: ", e)
                    }
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
                SERVER.sendSystemMessage(text, Util.NIL_UUID) // отправляем также в консоль сервера
            }
        }
    }
}