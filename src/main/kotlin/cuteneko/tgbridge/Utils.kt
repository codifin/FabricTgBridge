@file:OptIn(DelicateCoroutinesApi::class)

package cuteneko.tgbridge

import cuteneko.tgbridge.tgbot.User
import kotlinx.coroutines.DelicateCoroutinesApi
import net.minecraft.text.LiteralText // Изменено для 1.18.2
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText // Изменено для 1.18.2

fun Text?.toPlainString(formatted: Boolean = true): String {
    if (this == null) {
        return ""
    }
    var result = ""
    
    // В 1.18.2 вместо siblings используется children
    if (children.size == 0) {
        // В 1.18.2 проверяем сам объект Text, так как разделения на Content ещё нет
        result = when (this) {
            is LiteralText -> {
                // У LiteralText в 1.18.2 строка получается через метод rawString
                this.rawString.escapeHTML()
            }

            is TranslatableText -> {
                val lang = Bridge.LANG
                val key = this.key
                if (!lang.containsKey(key)) key
                
                // В 1.18.2 аргументы лежат в свойстве args
                val args = this.args.map {
                    if (it is Text) it.toPlainString()
                    else it.toString()
                }.toTypedArray()
                
                String.format(lang[key]!!.escapeHTML(), *args)
            }

            else -> {
                // На всякий случай для других типов (например, KeybindText)
                this.asString().escapeHTML()
            }
        }
    } else {
        // Проходимся по дочерним элементам через children
        children.forEach {
            result += it.toPlainString()
        }
    }

    if (!formatted) return result
    var format = ""
    if (style.isBold) format += "<b>"
    if (style.isItalic) format += "<i>"
    if (style.isUnderlined) format += "<u>"
    if (style.isStrikethrough) format += "<s>"
    if (style.isObfuscated) format += "<tg-spoiler>"
    format += "%s"
    if (style.isObfuscated) format += "</tg-spoiler>"
    if (style.isStrikethrough) format += "</s>"
    if (style.isUnderlined) format += "</u>"
    if (style.isItalic) format += "</i>"
    if (style.isBold) format += "</b>"

    return String.format(format, result)
}

fun String.escapeHTML(): String = this
    .replace("&", "&amp;")
    .replace(">", "&gt;")
    .replace("<", "&lt;")

fun User.rawUserMention(): String = firstName + (lastName?.let { " $it" } ?: "")