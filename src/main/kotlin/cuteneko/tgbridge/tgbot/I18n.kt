package cuteneko.tgbridge.tgbot

data class I18n(
    // ОПТИМИЗАЦИЯ: Все var изменены на val для обеспечения потокобезопасности и иммутабельности данных
    val reply: String = "[Replying to %1\$s: \"%2\$s\"] ",
    val forwarded: String = "[Forwarded] ",
    val forwardedFromUser: String = "Forwarded from user %s ",
    val forwardedFromChannel: String = "Forwarded from channel %s ",
    val forwardedFromGroup: String = "Forwarded from group %s ",
    val photo: String = "[Photo] ",
    val sticker: String = "[%sSticker] ",
    val stickerFrom: String = "From sticker pack %s ",
    val document: String = "[File %s] ",
    val voice: String = "[Voice %ss] ",
    val audio: String = "[Audio %ss] ",
    val video: String = "[Video %ss] ",
    val poll: String = "[Poll %s] ",
    val message: String = "[Message] ",
    val more: String = "[More]"
)