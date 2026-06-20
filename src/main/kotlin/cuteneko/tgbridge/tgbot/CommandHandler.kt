@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package cuteneko.tgbridge.tgbot

import cuteneko.tgbridge.Bridge
import cuteneko.tgbridge.escapeHTML
import cuteneko.tgbridge.toPlainString
import net.minecraft.server.command.CommandOutput
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID

class MyOutput(private val bot: TgBot) : CommandOutput {
    // В 1.18.2 интерфейс требует именно этот метод
    override fun sendSystemMessage(message: Text, sender: UUID) {
        val txt = message.toPlainString(false)
        if (txt.isBlank()) return
        bot.LOGGER.info(txt)
        
        GlobalScope.launch {
            bot.sendMessageToTelegram(txt)
        }
    }

    override fun shouldReceiveFeedback(): Boolean = true
    override fun shouldTrackOutput(): Boolean = true
    // Метод 1.18.2 вместо shouldBroadcastToOps
    override fun shouldBroadcastConsoleToOps(): Boolean = false
}

// Убрали явное упоминание несуществующего CmdHandler, Котлин сам выведет типы
val TgBot.commandMap
    get() = mapOf(
        "chat_id" to ::chatIdHandler,
        "list" to ::listHandler,
        "meow" to ::meowHandler,
        "cmd" to ::commandHandler
    )

suspend fun TgBot.chatIdHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    val chatId = msg.chat.id
    val text = "Chat ID: <code>$chatId</code>."
    this.sendMessageToTelegram(text, reply = msg.messageId)
}

suspend fun TgBot.listHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    if (msg.chat.id != Bridge.CONFIG.chatId) return
    val players = Bridge.SERVER.playerManager.playerList
    var list = players.joinToString("\n") { it.displayName.toPlainString().escapeHTML() }
    if (list.isBlank()) list = "No players online."
    this.sendMessageToTelegram(list, reply = msg.messageId)
}

suspend fun TgBot.meowHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    // Массив meow берется напрямую у инстанса TgBot (через ключевое слово this)
    this.sendMessageToTelegram(this.meow.shuffled()[0], reply = msg.messageId)
}

suspend fun TgBot.commandHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    var cmd = ctx.commandArgs.subList(1, ctx.commandArgs.size).joinToString(" ")
    
    if (!Bridge.CONFIG.admins.contains(msg.from?.username)) {
        this.sendMessageToTelegram(Bridge.CONFIG.noPermission, reply = msg.messageId)
        return
    }
    
    val cmdMgr = Bridge.SERVER.commandManager
    
    // В 1.18.2 выводим лог через sendFeedback у источника команд
    Bridge.SERVER.commandSource.sendFeedback(
        LiteralText("Executing command: /$cmd"), 
        false
    )
    
    val myOutput = MyOutput(this)
    val source = Bridge.SERVER.commandSource.withOutput(myOutput)
    
    cmd = cmd.removePrefix("/")
    // В 1.18.2 метод менеджера команд называется просто execute
    cmdMgr.execute(source, cmd)
}