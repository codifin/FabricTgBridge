@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package cuteneko.tgbridge.tgbot

import cuteneko.tgbridge.Bridge
import cuteneko.tgbridge.escapeHTML
import cuteneko.tgbridge.toPlainString
import net.minecraft.server.command.CommandOutput
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.util.Util
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID

class MyOutput(private val ctx: HandlerContext) : CommandOutput {
    // В 1.18.2 метод принимает Text и UUID
    override fun sendSystemMessage(message: Text, sender: UUID) {
        val txt = message.toPlainString(false)
        if (txt.isBlank()) return
        ctx.bot.LOGGER.info(txt)
        
        // Отправка обратно в Telegram
        GlobalScope.launch {
            ctx.bot.sendMessageToTelegram(txt)
        }
    }

    override fun shouldReceiveFeedback(): Boolean = true
    override fun shouldTrackOutput(): Boolean = true
    override fun shouldBroadcastToOps(): Boolean = false
}

val TgBot.commandMap: Map<String?, CmdHandler>
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
    this.sendMessageToTelegram(meow.shuffled()[0], reply = msg.messageId)
}

suspend fun TgBot.commandHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    var cmd = ctx.commandArgs.subList(1, ctx.commandArgs.size).joinToString(" ")
    
    if (!Bridge.CONFIG.admins.contains(msg.from?.username)) {
        this.sendMessageToTelegram(Bridge.CONFIG.noPermission, reply = msg.messageId)
        return
    }
    
    val cmdMgr = Bridge.SERVER.commandManager
    
    // В 1.18.2 выводим лог выполнения команды через sendSystemMessage консоли
    Bridge.SERVER.commandSource.sendSystemMessage(
        LiteralText("Executing command: /$cmd"), 
        Util.NIL_UUID
    )
    
    // Создаем вывод, привязанный к текущему контексту сообщения Telegram
    val myOutput = MyOutput(ctx)
    val source = Bridge.SERVER.commandSource.withOutput(myOutput)
    
    // В 1.18.2 метод ожидает чистую команду (без '/' в начале)
    cmd = cmd.removePrefix("/")
    cmdMgr.executeWithPrefix(source, cmd)
}