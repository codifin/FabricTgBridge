package cuteneko.tgbridge

import cuteneko.tgbridge.tgbot.User
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText

fun Text?.toPlainString(formatted: Boolean = true): String {
    if (this == null) return ""
    
    val builder = StringBuilder()

    // 1. Парсим текст самого текущего компонента
    val currentText = when (this) {
        is LiteralText -> this.rawString.escapeHTML()
        is TranslatableText -> {
            val lang = Bridge.LANG
            val key = this.key
            if (!lang.containsKey(key)) {
                key.escapeHTML() // ИСПРАВЛЕНО: Если ключа перевода нет, возвращаем сам ключ
            } else {
                val args = this.args.map {
                    if (it is Text) it.toPlainString(formatted) else it.toString()
                }.toTypedArray()
                String.format(lang[key]!!.escapeHTML(), *args)
            }
        }
        else -> this.asString().escapeHTML()
    }
    builder.append(currentText)

    // 2. ИСПРАВЛЕНО: Безопасно и без лишних аллокаций памяти добавляем всех детей (siblings)
    for (sibling in siblings) {
        builder.append(sibling.toPlainString(formatted))
    }

    val result = builder.toString()
    if (!formatted) return result

    // 3. ОПТИМИЗАЦИЯ: Сборка HTML-тегов форматирования
    val style = this.style
    val prefix = StringBuilder()
    val suffix = StringBuilder()

    if (style.isBold) { prefix.append("<b>"); suffix.insert(0, "</b>") }
    if (style.isItalic) { prefix.append("<i>"); suffix.insert(0, "</i>") }
    if (style.isUnderlined) { prefix.append("<u>"); suffix.insert(0, "</u>") }
    if (style.isStrikethrough) { prefix.append("<s>"); suffix.insert(0, "</s>") }
    if (style.isObfuscated) { prefix.append("<tg-spoiler>"); suffix.insert(0, "</tg-spoiler>") }

    return "$prefix$result$suffix"
}

fun String.escapeHTML(): String {
    if (isEmpty()) return this
    // ОПТИМИЗАЦИЯ: Быстрая замена символов без компиляции тяжелых регулярных выражений
    return this.replace("&", "&amp;")
        .replace(">", "&gt;")
        .replace("<", "&lt;")
}

fun User.rawUserMention(): String {
    return if (lastName != null) "$firstName $lastName" else firstName
}