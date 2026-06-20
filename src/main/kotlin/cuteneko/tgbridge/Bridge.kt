@file:OptIn(DelicateCoroutinesApi::class)

package cuteneko.tgbridge

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import cuteneko.tgbridge.tgbot.TgBot
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback // Изменено на v1 для 1.18.2
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.message.MessageType // Добавлено для 1.18.2 чата
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.LiteralText // Изменено для 1.18.2
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException

class Bridge : ModInitializer {
    override fun onInitialize() {
        LOGGER.info("Telegram bridge loaded!")
        INSTANCE = this
        CONFIG = ConfigLoader.load()
        ConfigLoader.save(CONFIG)

        if(CONFIG.botToken == "YOUR BOT TOKEN HERE") {
            LOGGER.info("Please edit config file!")
            return
        }

        try {
            LANG = ConfigLoader.getLang()
        } catch (e:FileNotFoundException) {
            LOGGER.error("lang.json not found! Read the document for more info")
            return
        }

        BOT = TgBot(LOGGER)
        GlobalScope.launch { BOT.startPolling() }

        // В 1.18.2 используется CommandRegistrationCallback из v1 с двумя параметрами
        CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(LiteralArgumentBuilder.literal<ServerCommandSource?>("tgbridge_reload")
                .requires {
                    it.hasPermissionLevel(4)
                }
                .executes {
                    if(RELOADING) {
                        it.source.sendMessage(LiteralText("A reload is already in progress!").formatted(Formatting.RED), Util.NIL_UUID)
                        return@executes 1
                    }
                    RELOADING = true
                    it.source.sendMessage(LiteralText("Reloading!"), Util.NIL_UUID)
                    CONFIG = ConfigLoader.load()
                    GlobalScope.launch {
                        try {
                            BOT.stop()
                            BOT = TgBot(LOGGER)
                            BOT.startPolling()
                            it.source.sendMessage(LiteralText("Reloaded!"), Util.NIL_UUID)
                        }
                        catch (e: Exception) {
                            it.source.sendMessage(LiteralText("Error occurred!").formatted(Formatting.RED), Util.NIL_UUID)
                            e.message?.let { msg -> it.source.sendMessage(LiteralText(msg), Util.NIL_UUID) }
                        }
                        finally {
                            RELOADING = false
                        }
                    }
                    0
                })
        }

        ServerLifecycleEvents.SERVER_STARTED.register {
            SERVER = it
            if(CONFIG.sendServerStarted) GlobalScope.launch { BOT.sendMessageToTelegram(CONFIG.serverStartedMessage)}
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            GlobalScope.launch {
                BOT.stop()
                if(CONFIG.sendServerStopping)  BOT.sendMessageToTelegram(CONFIG.serverStoppingMessage)
            }
        }

        // В 1.18.2 CHAT_MESSAGE принимает Text, ServerPlayerEntity и UUID
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            if(!CONFIG.sendChatMessage) return@register
            val senderName = sender.displayName.toPlainString()
            val msg = message.toPlainString()
            GlobalScope.launch { BOT.sendMessageToTelegram(msg.escapeHTML(), senderName.escapeHTML()) }
        }

        // В 1.18.2 GAME_MESSAGE принимает Text
        ServerMessageEvents.GAME_MESSAGE.register { message ->
            if(!CONFIG.sendGameMessage) return@register
            val msg = message.toPlainString()
            GlobalScope.launch { BOT.sendMessageToTelegram(msg) }
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
            SERVER.playerManager.playerList.forEach {
                // В 1.18.2 отправка сообщений игрокам требует тип сообщения и UUID
                it.sendMessage(text, MessageType.CHAT, Util.NIL_UUID)
            }
            // Отправка в консоль сервера
            SERVER.sendMessage(text, Util.NIL_UUID)
        }
    }
}