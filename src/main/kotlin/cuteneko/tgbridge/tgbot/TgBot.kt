package cuteneko.tgbridge.tgbot

import cuteneko.tgbridge.Bridge
import cuteneko.tgbridge.rawUserMention
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import net.minecraft.text.Text
import net.minecraft.text.LiteralText
import okhttp3.OkHttpClient
import org.slf4j.Logger
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

class TgBot(val LOGGER: Logger) {
    private val config get() = Bridge.CONFIG
    
    private val proxy get() = if (!config.proxyEnabled) Proxy.NO_PROXY else Proxy(Proxy.Type.HTTP, InetSocketAddress(config.proxyHost, config.proxyPort))
    
    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(proxy)
            .readTimeout(Duration.ZERO)
            .build()
    }
        
    internal val api by lazy {
        Retrofit.Builder()
            .baseUrl("https://${config.telegramAPI}/bot${config.botToken}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TgApi::class.java)
    }

    private val updateChan = Channel<Update>(Channel.BUFFERED)
    
    private val botJob = SupervisorJob()
    private val botScope = CoroutineScope(Dispatchers.IO + botJob)
    
    private var pollJob: Job? = null
    private var handlerJob: Job? = null
    private var currentOffset: Long = -1
    private var me: User? = null

    val meow = arrayOf("мяу~", "мур!", "фрр...") // Русифицировано мяуканье :)

    fun getBotScope(): CoroutineScope = botScope

    private suspend fun initialize() {
        try {
            me = api.getMe().result!!
            val commands = arrayOf(
                BotCommand("chat_id", "Получить ID текущего чата."),
                BotCommand("cmd", "Выполнить консольную команду."),
                BotCommand("list", "Показать список игроков онлайн."),
                BotCommand("meow", "Мяукнуть!")
            )
            api.setMyCommands(SetCommands(commands.toList()))
            api.deleteWebhook(true)
        } catch (e: HttpException) {
            e.response()?.errorBody()?.string()?.let {
                LOGGER.error("Ошибка Telegram API при инициализации: $it")
            }
        }
    }

    suspend fun startPolling() {
        try {
            initialize()
        } catch(e: Exception) {
            Bridge.LOGGER.error("Не удалось инициализировать бота! Проверьте сеть и токен!")
            Bridge.LOGGER.error(e.message)
        }
        pollJob = initPolling()
        handlerJob = initHandler()
    }

    suspend fun stop() {
        botJob.cancelChildren()
        botJob.cancel()
        pollJob?.cancelAndJoin()
        handlerJob?.join()
        updateChan.close()
    }

    private fun initPolling() = botScope.launch {
        loop@while (isActive) {
            try {
                val response = api.getUpdates(
                    offset = currentOffset,
                    timeout = config.pollTimeout,
                )
                val updates = response.result
                if (updates != null && updates.isNotEmpty()) {
                    for (update in updates) {
                        if (!isActive) break@loop
                        updateChan.send(update)
                    }
                    currentOffset = updates.last().updateId + 1
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> break@loop
                    else -> {
                        Bridge.LOGGER.error("Ошибка получения обновлений (поллинг): ${e.message}")
                        delay(2000)
                        continue@loop
                    }
                }
            }
        }
    }

    private fun initHandler() = botScope.launch {
        updateChan.consumeEach {
            if (!isActive) return@launch
            try {
                handleUpdate(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleUpdate(update: Update) {
        val msg = update.message ?: return
        if (msg.chat.type != "group" && msg.chat.type != "supergroup") return
        
        val ctx = HandlerContext(update, msg, msg.chat)
        
        if (msg.text?.startsWith("/") == true) {
            val args = msg.text.split(" ")
            val cmd = if (args[0].contains("@")) {
                val cmds = args[0].split("@")
                if (cmds[1] != me?.username) return
                cmds[0].substring(1)
            } else args[0].substring(1)
            
            commandMap[cmd]?.invoke(this, ctx.copy(commandArgs = args))
            return
        }
        onMessageHandler(ctx)
    }

    private fun onMessageHandler(ctx: HandlerContext) {
        val msg = ctx.message!!
        if (config.chatId != msg.chat.id) return
        
        // Русифицировано дефолтное имя
        val authorName = msg.from?.rawUserMention() ?: msg.senderChat?.title ?: "Пользователь Telegram"
        
        val formattedPattern = config.minecraftFormat.replace("%1\$s", authorName)
        val parts = formattedPattern.split("%2\$s")
        
        val text = LiteralText("")
        if (parts.size > 1) {
            text.append(LiteralText(parts[0]))
            text.append(msg.toText(config.messageTrim))
            text.append(LiteralText(parts[1]))
        } else {
            text.append(msg.toText(config.messageTrim))
        }

        try {
            val server = Bridge.SERVER
            server.execute {
                Bridge.sendMessage(text)
            }
        } catch (e: UninitializedPropertyAccessException) {
        }
    }

    suspend fun sendMessageToTelegram(text: String, username: String? = null, reply: Long? = null) {
        withContext(Dispatchers.IO) {
            val formatted = username?.let {
                String.format(config.telegramFormat, username, text)
            } ?: text
            
            try {
                if (config.useHtmlFormat) {
                    sendHtmlMessage(formatted, reply)
                } else {
                    sendPlainMessage(formatted, reply)
                }
            } catch (e: HttpException) {
                e.response()?.errorBody()?.string()?.let {
                    LOGGER.error("Ошибка Telegram API: $it")
                    LOGGER.error("Текст, который не удалось отправить: $formatted")
                }
            } catch (e: Exception) {
                LOGGER.error("Сетевая ошибка при отправке в Telegram: ${e.message}")
            }
        }
    }

    private suspend fun sendHtmlMessage(formatted: String, reply: Long?) {
        val response = api.sendMessage(config.chatId, formatted, reply)
        if (!response.ok) {
            LOGGER.error(response.description ?: "Неизвестная ошибка API")
        }
    }

    private suspend fun sendPlainMessage(formatted: String, reply: Long?) {
        val response = api.sendMessageWithoutParse(config.chatId, formatted, reply)
        if (!response.ok) {
            LOGGER.error(response.description ?: "Неизвестная ошибка API")
        }
    }
}