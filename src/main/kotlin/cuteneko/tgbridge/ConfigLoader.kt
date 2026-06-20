package cuteneko.tgbridge

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import cuteneko.tgbridge.tgbot.I18n
import net.fabricmc.loader.api.FabricLoader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.*

object ConfigLoader {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    
    // ОПТИМИЗАЦИЯ: Кэшируем пути, чтобы не дергать FabricLoader при каждом чтении
    private val modConfigDir: Path by lazy {
        FabricLoader.getInstance().configDir.resolve(Bridge.MOD_ID).also {
            if (!it.exists()) it.createDirectory()
        }
    }
    
    private val configFile: Path get() = modConfigDir.resolve("config.json")
    private val langFile: Path get() = modConfigDir.resolve("lang.json")
    private val i18nFile: Path get() = modConfigDir.resolve("i18n.json")

    fun load(): Config {
        if (!configFile.exists()) return Config()
        // ОПТИМИЗАЦИЯ: .bufferedReader().use автоматически закроет файл после чтения
        return configFile.bufferedReader().use { reader ->
            gson.fromJson(reader, Config::class.java)
        }
    }

    fun save(config: Config) {
        val json = gson.toJson(config)
        configFile.writeText(json, Charsets.UTF_8)
    }

    fun getLang(): Map<String, String> {
        if (!langFile.exists()) {
            val stream = javaClass.classLoader.getResourceAsStream("assets/lang.json")
            InputStreamReader(stream!!).use { reader ->
                langFile.writeText(reader.readText())
            }
        }
        // ОПТИМИЗАЦИЯ: Читаем через Type Token для безопасного приведения типов в Map
        val type = object : TypeToken<Map<String, String>>() {}.type
        return langFile.bufferedReader().use { reader ->
            gson.fromJson(reader, type)
        }
    }

    fun getI18n(): I18n {
        if (!i18nFile.exists()) {
            i18nFile.writeText(gson.toJson(I18n()))
        }
        return i18nFile.bufferedReader().use { reader ->
            gson.fromJson(reader, I18n::class.java)
        }
    }
}