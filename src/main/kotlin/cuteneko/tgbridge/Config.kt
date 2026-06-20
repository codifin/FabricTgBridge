package cuteneko.tgbridge

data class Config(
    // ОПТИМИЗАЦИЯ: Все var изменены на val для обеспечения иммутабельности и потокобезопасности
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
    val serverStartedMessage: String = "Server has started!",
    val serverStoppingMessage: String = "Server is stopping!",
    val admins: List<String> = emptyList(), // Оптимизация: пустой синглтон-список
    val noPermission: String = "No permission!",
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "localhost",
    val proxyPort: Int = 10809,
)