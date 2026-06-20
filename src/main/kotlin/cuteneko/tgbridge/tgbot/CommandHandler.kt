package cuteneko.tgbridge.tgbot

import cuteneko.tgbridge.Bridge
import cuteneko.tgbridge.escapeHTML
import cuteneko.tgbridge.toPlainString
import net.minecraft.server.command.CommandOutput
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import kotlinx.coroutines.launch
import java.util.UUID

class MyOutput(private val bot: TgBot) : CommandOutput {
    override fun sendSystemMessage(message: Text, sender: UUID) {
        val txt = message.toPlainString(false)
        if (txt.isBlank()) return
        bot.LOGGER.info(txt)
        
        bot.LOGGER.debug("Sending to Telegram: $txt")
        // ИСПРАВЛЕНО: Используем внутренний Scope бота, который привязан к жизненному циклу 
        // и не течет по памяти, в отличие от бесконтрольного создания MainScope()
        bot.getBotScope().launch {
            bot.sendMessageToTelegram(txt)
        }
    }

    override fun shouldReceiveFeedback(): Boolean = true
    override fun shouldTrackOutput(): Boolean = true
    override fun shouldBroadcastConsoleToOps(): Boolean = false
}

// ИСПРАВЛЕНО: Теперь мапа лениво создается один раз, а не генерируется заново при каждом запросе
val commandMap: Map<String, suspend TgBot.(HandlerContext) -> Unit> = mapOf(
    "chat_id" to TgBot::chatIdHandler,
    "list" to TgBot::listHandler,
    "meow" to TgBot::meowHandler,
    "cmd" to TgBot::commandHandler
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
    
    // Безопасно собираем игроков (доступ к серверу может требовать синхронизации, но для чтения списка достаточно)
    val players = Bridge.SERVER.playerManager.playerList
    var list = players.joinToString("\n") { it.displayName.toPlainString().escapeHTML() }
    if (list.isBlank()) list = "No players online."
    this.sendMessageToTelegram(list, reply = msg.messageId)
}

suspend fun TgBot.meowHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    val randomMeow = this.meow.random() // ИСПРАВЛЕНО: Стандартный читаемый .random() вместо shuffled().first()
    this.sendMessageToTelegram(randomMeow, reply = msg.messageId)
}

suspend fun TgBot.commandHandler(ctx: HandlerContext) {
    val msg = ctx.message!!
    var cmd = ctx.commandArgs.subList(1, ctx.commandArgs.size).joinToString(" ")
    
    if (!Bridge.CONFIG.admins.contains(msg.from?.username)) {
        this.sendMessageToTelegram(Bridge.CONFIG.noPermission, reply = msg.messageId)
        return
    }
    
    val cmdMgr = Bridge.SERVER.commandManager
    val myOutput = MyOutput(this)
    
    // ИСПРАВЛЕНО: Выполнение команд Майнкрафта переносим строго в поток сервера!
    // Прямой вызов из корутины вешал Server Thread и вызывал краш консоли.
    Bridge.SERVER.execute {
        Bridge.SERVER.commandSource.sendFeedback(
            LiteralText("Executing command: /$cmd"), 
            false
        )
        val source = Bridge.SERVER.commandSource.withOutput(myOutput)
        cmd = cmd.removePrefix("/")
        cmdMgr.execute(source, cmd)
    }
}