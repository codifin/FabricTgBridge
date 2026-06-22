package cuteneko.tgbridge.tgbot

import cuteneko.tgbridge.Bridge
import cuteneko.tgbridge.ConfigLoader
import cuteneko.tgbridge.rawUserMention
import cuteneko.tgbridge.toPlainString
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.LiteralText
import net.minecraft.util.Formatting

private val i18n get() = ConfigLoader.getI18n()

fun Message.toText(trim: Int = 0, showMore: Boolean = true): Text {
    val text = LiteralText("") 

    replyToMessage?.let {
        val replyRawText = it.text ?: it.caption ?: "[Медиа]"
        val replyClean = if (replyRawText.length > 20) "${replyRawText.take(20)}..." else replyRawText

        text.append(
            LiteralText(
                i18n.reply.format(
                    it.from?.rawUserMention() ?: "Пользователь", // ИСПРАВЛЕНО: Русская заглушка
                    replyClean
                )
            ).setStyle(
                Style.EMPTY
                    .withColor(Formatting.GOLD)
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, LiteralText(replyRawText)))
            )
        )
    }

    forwardFrom?.let {
        val info = LiteralText("")
        info.append(i18n.forwardedFromUser.format(it.rawUserMention()))
        it.username?.let { username -> info.append("\n@$username") }
        text.append(
            LiteralText(i18n.forwarded)
                .setStyle(
                    Style.EMPTY
                        .withColor(Formatting.GOLD)
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, info))
                )
        )
    }

    forwardFromChat?.let {
        val info = LiteralText("")
        when (it.type) {
            "channel" -> info.append(i18n.forwardedFromChannel.format(it.title))
            "group", "supergroup" -> info.append(i18n.forwardedFromGroup.format(it.title))
            else -> info.append(i18n.forwarded) // ИСПРАВЛЕНО: Безопасный фоллбек для неизвестных типов
        }
        text.append(
            LiteralText(i18n.forwarded)
                .setStyle(
                    Style.EMPTY
                        .withColor(Formatting.GOLD)
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, info))
                )
        )
    }

    if (!photo.isNullOrEmpty()) {
        text.append(
            LiteralText(i18n.photo)
                .setStyle(Style.EMPTY.withColor(Formatting.BLUE))
        )
    }

    sticker?.let {
        text.append(
            LiteralText(i18n.sticker.format(it.emoji ?: ""))
                .setStyle(
                    Style.EMPTY
                        .withColor(Formatting.BLUE)
                        .withHoverEvent(
                            HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                LiteralText(i18n.stickerFrom.format(it.setName ?: "неизвестного"))
                            )
                        )
                )
        )
    }

    document?.let {
        text.append(
            LiteralText(i18n.document.format(it.fileName))
                .setStyle(Style.EMPTY.withColor(Formatting.BLUE))
        )
    }

    voice?.let {
        text.append(
            LiteralText(i18n.voice.format(it.duration))
                .setStyle(Style.EMPTY.withColor(Formatting.BLUE))
        )
    }

    audio?.let {
        text.append(
            LiteralText(i18n.audio.format(it.duration))
                .setStyle(Style.EMPTY.withColor(Formatting.BLUE))
        )
    }

    video?.let {
        text.append(
            LiteralText(i18n.video.format(it.duration))
                .setStyle(Style.EMPTY.withColor(Formatting.BLUE))
        )
    }

    poll?.let {
        text.append(
            LiteralText(i18n.poll.format(it.question.trimMessage(trim, showMore).toPlainString(false)))
                .setStyle(Style.EMPTY.withColor(Formatting.BLUE))
        )
    }

    this.text?.let {
        text.append(it.trimMessage(trim, showMore))
    }

    if (text.siblings.isEmpty()) {
        text.append(
            LiteralText(i18n.message)
                .setStyle(Style.EMPTY.withColor(Formatting.DARK_GREEN))
        )
    }

    caption?.let {
        text.append(it.trimMessage(trim, showMore))
    }

    return text
}

private fun String.trimMessage(size: Int, showMore: Boolean = true): Text {
    val msg = this.replace('\n', ' ')
    val text = LiteralText("")
    
    if (size == 0 || msg.length <= size) {
        text.append(msg)
        return text
    }
    
    text.append("${msg.substring(0, size)}...")
    if (showMore) {
        text.append(
            LiteralText(i18n.more)
                .setStyle(
                    Style.EMPTY
                        .withColor(Formatting.GOLD)
                        .withHoverEvent(
                            HoverEvent(HoverEvent.Action.SHOW_TEXT, LiteralText(this))
                        )
                )
        )
    }
    return text
}