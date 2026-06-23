package cuteneko.tgbridge.tgbot

data class I18n(
    val reply: String = "[Ответ на %1\$s: \"%2\$s\"] ",
    val forwarded: String = "[Переслано] ",
    val forwardedFromUser: String = "Переслано от пользователя %s ",
    val forwardedFromChannel: String = "Переслано из канала %s ",
    val forwardedFromGroup: String = "Переслано из группы %s ",
    val photo: String = "[Фото] ",
    val sticker: String = "[Стикер %s] ",
    val stickerFrom: String = "Из набора стикеров %s ",
    val document: String = "[Файл %s] ",
    val voice: String = "[Голосовое сообщение %s с.] ",
    val audio: String = "[Аудиозапись %s с.] ",
    val video: String = "[Видеозапись %s с.] ",
    val poll: String = "[Опрос: %s] ",
    val message: String = "[Сообщение] ",
    val more: String = "[Читать далее]"
)