package cuteneko.tgbridge

data class Config(
    // ОПТИМИЗАЦИЯ: Все переменные иммутабельны. Сообщения по умолчанию переведены на русский язык.
    val botToken: String = "YOUR BOT TOKEN HERE",
    val chatId: Long = 0,
    val telegramAPI: String = "api.telegram.org",
    val pollTimeout: Int = 5,
    val useHtmlFormat: Boolean = true,
    val sendChatMessage: Boolean = true,
    val sendGameMessage: Boolean = true,
    val sendTelegramMessage: Boolean = true,
    val messageTrim: Int = 20,
    val sendServerStarted: Boolean = true,
    val sendServerStopping: Boolean = true,
    val minecraftFormat: String = "<%1\$s> %2\$s",
    val telegramFormat: String = "<b>%1\$s</b> %2\$s",
    val serverStartedMessage: String = "Сервер запущен!",
    val serverStoppingMessage: String = "Сервер останавливается!",
    val admins: List<String> = emptyList(),
    val noPermission: String = "Недостаточно прав!",
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "localhost",
    val proxyPort: Int = 10809,
)