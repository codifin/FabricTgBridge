@file:OptIn(DelicateCoroutinesApi::class)

package cuteneko.tgbridge

import cuteneko.tgbridge.tgbot.User
import kotlinx.coroutines.DelicateCoroutinesApi
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText

fun Text?.toPlainString(formatted: Boolean = true): String {
    if (this == null) {
        return ""
    }
    var result = ""
    
    if (siblings.isEmpty()) {
        result = when (this) {
            is LiteralText -> {
                this.rawString.escapeHTML()
            }

            is TranslatableText -> {
                val lang = Bridge.LANG
                val key = this.key
                if (!lang.containsKey(key)) key
                
                val args = this.args.map {
                    if (it is Text) it.toPlainString()
                    else it.toString()
                }.toTypedArray()
                
                String.format(lang[key]!!.escapeHTML(), *args)
            }

            else -> {
                this.asString().escapeHTML()
            }
        }
    } else {
        siblings.forEach {
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