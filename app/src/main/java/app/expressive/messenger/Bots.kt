package app.expressive.messenger

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Bots {
    val groupMembers = listOf("Дима", "Лена", "Артём", "Оля")

    private val generic = listOf(
        "Понял тебя 👍",
        "Хорошо, давай так",
        "Ха-ха, точно!",
        "Интересно, расскажи подробнее",
        "Я сейчас занят, отвечу позже",
        "Согласен",
        "Окей, договорились",
        "Спасибо, что написал!",
        "Секунду, проверю",
    )

    private val jokes = listOf(
        "Программист уходит в магазин. Жена: «Купи батон, а если будут яйца — бери десять». Вернулся с десятью батонами.",
        "— Почему Java-разработчики носят очки? — Потому что не C#.",
        "Есть только 10 типов людей: те, кто понимает двоичную систему, и те, кто нет.",
    )

    fun reply(c: Chat, m: Message): String {
        val t = m.text.trim()
        val low = t.lowercase()
        if (c.type == ChatType.BOT) {
            return when {
                m.kind != MsgKind.TEXT -> "Получил ваше вложение!"
                low.startsWith("/start") -> "Привет! Я эхо-бот. Команды: /help, /joke, /time, /dice"
                low.startsWith("/help") -> "/joke — шутка\n/time — текущее время\n/dice — бросить кубик\nЛюбой другой текст я повторю"
                low.startsWith("/joke") -> jokes.random()
                low.startsWith("/time") -> SimpleDateFormat("HH:mm:ss, d MMMM yyyy", Locale("ru")).format(Date())
                low.startsWith("/dice") -> "Выпало: ${(1..6).random()}"
                else -> "Вы сказали: $t"
            }
        }
        return when {
            m.kind == MsgKind.PHOTO -> "Классное фото!"
            m.kind == MsgKind.VOICE -> "Послушаю чуть позже 🎧"
            m.kind == MsgKind.FILE -> "Файл получил, спасибо"
            m.kind == MsgKind.LOCATION -> "Ага, буду там"
            m.kind == MsgKind.CONTACT -> "Спасибо за контакт"
            low.contains("привет") || low.contains("здравствуй") -> "Привет! Как дела?"
            low.contains("как дела") -> "Отлично, а у тебя?"
            low.contains("спасибо") -> "Всегда пожалуйста!"
            low.contains("пока") -> "До встречи!"
            t.endsWith("?") -> listOf("Хороший вопрос, думаю, да", "Пожалуй, нет", "Надо подумать...").random()
            else -> generic.random()
        }
    }
}
