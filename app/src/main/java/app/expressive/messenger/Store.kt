package app.expressive.messenger

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dataFile = File(ctx.filesDir, "messenger.json")
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var saveJob: Job? = null
    private var bannerJob: Job? = null
    private var counter = System.currentTimeMillis()

    var chats by mutableStateOf(listOf<Chat>())
    var messages by mutableStateOf(mapOf<Long, List<Message>>())
    var typing by mutableStateOf(setOf<Long>())
    var banner by mutableStateOf<Banner?>(null)
    var openChat: Long? = null
    var newChatType by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>(Screen.Main)

    // settings
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic", true))
    var textScale by mutableFloatStateOf(prefs.getFloat("textScale", 1f))
    var bubbleRadius by mutableIntStateOf(prefs.getInt("bubble", 20))
    var enterSends by mutableStateOf(prefs.getBoolean("enter", false))
    var notifications by mutableStateOf(prefs.getBoolean("notif", true))
    var myName by mutableStateOf(prefs.getString("name", "Вы") ?: "Вы")
    var myUsername by mutableStateOf(prefs.getString("username", "me") ?: "me")

    init {
        load()
    }

    // ---------- settings ----------
    fun setTheme(v: Int) { themeMode = v; prefs.edit().putInt("theme", v).apply() }
    fun setDynamic(v: Boolean) { dynamicColor = v; prefs.edit().putBoolean("dynamic", v).apply() }
    fun setTextScale(v: Float) { textScale = v; prefs.edit().putFloat("textScale", v).apply() }
    fun setBubble(v: Int) { bubbleRadius = v; prefs.edit().putInt("bubble", v).apply() }
    fun setEnterSends(v: Boolean) { enterSends = v; prefs.edit().putBoolean("enter", v).apply() }
    fun setNotifications(v: Boolean) { notifications = v; prefs.edit().putBoolean("notif", v).apply() }
    fun setProfile(name: String, username: String) {
        myName = name
        myUsername = username
        prefs.edit().putString("name", name).putString("username", username).apply()
    }

    // ---------- navigation ----------
    fun push(s: Screen) { if (stack.last() != s) stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // ---------- queries ----------
    fun chat(id: Long): Chat? = chats.firstOrNull { it.id == id }
    fun msgs(id: Long): List<Message> = messages[id].orEmpty()
    fun last(id: Long): Message? = messages[id]?.lastOrNull()

    @Synchronized
    private fun nextId(): Long = counter++

    // ---------- persistence ----------
    private fun load() {
        try {
            if (dataFile.exists()) {
                val root = JSONObject(dataFile.readText())
                val ca = root.getJSONArray("chats")
                chats = (0 until ca.length()).map { chatFromJson(ca.getJSONObject(it)) }
                val mo = root.getJSONObject("messages")
                val map = HashMap<Long, List<Message>>()
                for (k in mo.keys()) {
                    val a = mo.getJSONArray(k)
                    map[k.toLong()] = (0 until a.length()).map { msgFromJson(a.getJSONObject(it)) }
                }
                messages = map
                counter = maxOf(counter, root.optLong("counter", 0L))
                return
            }
        } catch (e: Exception) {
            // fall through to seed
        }
        seed()
    }

    private fun save() {
        saveJob?.cancel()
        val root = JSONObject()
        val ca = JSONArray()
        chats.forEach { ca.put(it.toJson()) }
        root.put("chats", ca)
        val mo = JSONObject()
        for ((k, v) in messages) {
            val a = JSONArray()
            v.forEach { a.put(it.toJson()) }
            mo.put(k.toString(), a)
        }
        root.put("messages", mo)
        root.put("counter", counter)
        val text = root.toString()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(300)
            try { dataFile.writeText(text) } catch (e: Exception) { }
        }
    }

    private fun seed() {
        val now = System.currentTimeMillis()
        val minute = 60_000L
        chats = listOf(
            Chat(1, "Избранное", ChatType.SAVED, 5, pinned = true, bio = "Сохраняйте здесь сообщения, заметки и файлы"),
            Chat(2, "Анна Смирнова", ChatType.PRIVATE, 0, username = "anna_s", bio = "Дизайнер интерфейсов"),
            Chat(3, "Максим Орлов", ChatType.PRIVATE, 3, username = "maxorlov", bio = "Android-разработчик"),
            Chat(4, "Команда проекта", ChatType.GROUP, 2, members = 5, pinned = true, bio = "Рабочий чат по проекту"),
            Chat(5, "Новости Android", ChatType.CHANNEL, 5, members = 12840, username = "android_news_demo", bio = "Новости мобильной разработки", unread = 2),
            Chat(6, "Echo Bot", ChatType.BOT, 4, username = "echo_demo_bot", bio = "Эхо-бот. Напишите /help"),
            Chat(7, "Мемы дня", ChatType.CHANNEL, 6, members = 5320, muted = true, bio = "Смешные картинки и шутки", unread = 12),
        )
        val map = HashMap<Long, List<Message>>()
        fun add(chatId: Long, fromMe: Boolean, author: String, text: String, ago: Long, read: Boolean = true) {
            val m = Message(
                id = nextId(), chatId = chatId, fromMe = fromMe, author = author, text = text,
                time = now - ago * minute,
                status = if (read) MsgStatus.READ else MsgStatus.SENT,
            )
            map[chatId] = map[chatId].orEmpty() + m
        }
        add(1, true, "Вы", "Купить: молоко, хлеб, кофе", 2880)
        add(1, true, "Вы", "Идея: сделать свой мессенджер ✨", 600)
        add(2, false, "Анна Смирнова", "Привет! Посмотрела новый макет?", 190)
        add(2, true, "Вы", "Привет! Да, выглядит отлично 👍", 185)
        add(2, false, "Анна Смирнова", "Спасибо! Тогда отправляю в разработку", 180)
        add(3, true, "Вы", "Максим, как прогресс по сборке?", 1500)
        add(3, false, "Максим Орлов", "Всё зелёное, APK уже в релизах", 1490)
        add(4, false, "Дима", "Всем привет! Встреча в пятницу в 15:00", 300)
        add(4, false, "Лена", "Я буду", 295)
        add(4, true, "Вы", "Я тоже подключусь", 290)
        add(4, false, "Артём", "Отлично, тогда ждём всех", 240)
        add(5, false, "Новости Android", "Вышел новый Jetpack Compose с улучшенными анимациями", 720)
        add(5, false, "Новости Android", "Material 3 Expressive приходит в больше приложений", 360)
        add(6, false, "Echo Bot", "Напишите /start, чтобы начать", 4000)
        add(7, false, "Мемы дня", "Когда код заработал с первого раза 😎", 120)
        messages = map
        save()
    }

    // ---------- chat operations ----------
    private fun updateChat(id: Long, f: (Chat) -> Chat) {
        chats = chats.map { if (it.id == id) f(it) else it }
        save()
    }

    private fun updateMsgs(id: Long, f: (List<Message>) -> List<Message>) {
        messages = messages + (id to f(msgs(id)))
        save()
    }

    fun setPinned(id: Long, v: Boolean) = updateChat(id) { it.copy(pinned = v) }
    fun setMuted(id: Long, v: Boolean) = updateChat(id) { it.copy(muted = v) }
    fun setArchived(id: Long, v: Boolean) = updateChat(id) { it.copy(archived = v) }
    fun setBlocked(id: Long, v: Boolean) = updateChat(id) { it.copy(blocked = v) }
    fun setDraft(id: Long, text: String) {
        val c = chat(id) ?: return
        if (c.draft != text) updateChat(id) { it.copy(draft = text) }
    }

    fun markRead(id: Long) {
        val c = chat(id) ?: return
        if (c.unread > 0) updateChat(id) { it.copy(unread = 0) }
    }

    fun markUnread(id: Long) {
        val c = chat(id) ?: return
        if (c.unread == 0) updateChat(id) { it.copy(unread = 1) }
    }

    fun deleteChat(id: Long) {
        chats = chats.filter { it.id != id }
        messages = messages - id
        save()
    }

    fun clearHistory(id: Long) {
        messages = messages + (id to emptyList())
        updateChat(id) { it.copy(pinnedMessage = null, unread = 0) }
    }

    fun createChat(title: String, type: ChatType, bio: String, username: String): Long {
        val id = nextId()
        val c = Chat(
            id = id, title = title.trim(), type = type, seed = (title.hashCode() and 0x7fffffff) % 7,
            bio = bio.trim(), username = username.trim(),
            members = when (type) { ChatType.GROUP -> 1; ChatType.CHANNEL -> 1; else -> 0 },
            owner = type == ChatType.GROUP || type == ChatType.CHANNEL,
        )
        chats = chats + c
        messages = messages + (id to emptyList())
        save()
        return id
    }

    fun resetAll() {
        File(ctx.filesDir, "media").deleteRecursively()
        messages = emptyMap()
        chats = emptyList()
        seed()
    }

    // ---------- messages ----------
    fun send(
        chatId: Long,
        text: String,
        kind: MsgKind = MsgKind.TEXT,
        media: String? = null,
        duration: Int = 0,
        replyTo: Long? = null,
        forwardedFrom: String? = null,
    ) {
        val c = chat(chatId) ?: return
        val instantRead = c.type == ChatType.SAVED || c.type == ChatType.CHANNEL
        val m = Message(
            id = nextId(), chatId = chatId, fromMe = true, author = myName, kind = kind, text = text,
            time = System.currentTimeMillis(),
            status = if (instantRead) MsgStatus.READ else MsgStatus.SENT,
            replyTo = replyTo, forwardedFrom = forwardedFrom, media = media, duration = duration,
        )
        updateMsgs(chatId) { it + m }
        if (c.draft.isNotEmpty()) updateChat(chatId) { it.copy(draft = "") }
        scheduleReply(c, m)
    }

    private fun scheduleReply(c: Chat, m: Message) {
        if (c.type == ChatType.SAVED || c.type == ChatType.CHANNEL || c.blocked) return
        scope.launch {
            delay(700)
            updateMsgs(c.id) { list -> list.map { if (it.fromMe && it.status != MsgStatus.READ) it.copy(status = MsgStatus.READ) else it } }
            typing = typing + c.id
            val reply = Bots.reply(c, m)
            delay(900L + reply.length * 25L)
            typing = typing - c.id
            if (chat(c.id) == null) return@launch
            val author = if (c.type == ChatType.GROUP) Bots.groupMembers.random() else c.title
            receive(c.id, author, reply)
        }
    }

    fun receive(chatId: Long, author: String, text: String) {
        val m = Message(id = nextId(), chatId = chatId, fromMe = false, author = author, text = text, time = System.currentTimeMillis())
        updateMsgs(chatId) { it + m }
        val c = chat(chatId) ?: return
        if (openChat != chatId) {
            updateChat(chatId) { it.copy(unread = it.unread + 1) }
            if (!c.muted && notifications) {
                banner = Banner(chatId, author, text)
                bannerJob?.cancel()
                bannerJob = scope.launch { delay(3500); banner = null }
            }
        }
    }

    fun edit(chatId: Long, id: Long, text: String) =
        updateMsgs(chatId) { list -> list.map { if (it.id == id) it.copy(text = text, edited = true) else it } }

    fun delete(chatId: Long, ids: Set<Long>) {
        updateMsgs(chatId) { list -> list.filter { it.id !in ids } }
        val c = chat(chatId)
        if (c?.pinnedMessage != null && c.pinnedMessage in ids) updateChat(chatId) { it.copy(pinnedMessage = null) }
    }

    fun react(chatId: Long, id: Long, emoji: String) = updateMsgs(chatId) { list ->
        list.map { m ->
            if (m.id != id) m else {
                val r = m.reactions.toMutableMap()
                val my = m.myReaction
                if (my != null) {
                    val n = (r[my] ?: 1) - 1
                    if (n <= 0) r.remove(my) else r[my] = n
                }
                if (my == emoji) {
                    m.copy(reactions = r, myReaction = null)
                } else {
                    r[emoji] = (r[emoji] ?: 0) + 1
                    m.copy(reactions = r, myReaction = emoji)
                }
            }
        }
    }

    fun togglePin(chatId: Long, id: Long) {
        val c = chat(chatId) ?: return
        updateChat(chatId) { it.copy(pinnedMessage = if (c.pinnedMessage == id) null else id) }
    }

    fun forward(list: List<Message>, targetId: Long) {
        val target = chat(targetId) ?: return
        list.sortedBy { it.time }.forEach { m ->
            val origin = m.forwardedFrom ?: if (m.fromMe) myName else m.author
            val nm = Message(
                id = nextId(), chatId = targetId, fromMe = true, author = myName, kind = m.kind, text = m.text,
                time = System.currentTimeMillis(),
                status = if (target.type == ChatType.SAVED || target.type == ChatType.CHANNEL) MsgStatus.READ else MsgStatus.SENT,
                forwardedFrom = origin, media = m.media, duration = m.duration,
            )
            updateMsgs(targetId) { it + nm }
        }
    }

    fun attach(chatId: Long, uri: Uri, photo: Boolean, replyTo: Long?) {
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    var name = "file"
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                        val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && cur.moveToFirst()) name = cur.getString(i) ?: name
                    }
                    val dir = File(ctx.filesDir, "media")
                    dir.mkdirs()
                    val out = File(dir, "${nextId()}_$name")
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { o -> input.copyTo(o) }
                    }
                    Pair(out.absolutePath, name)
                } catch (e: Exception) {
                    null
                }
            }
            if (res != null) {
                send(
                    chatId,
                    if (photo) "" else res.second,
                    if (photo) MsgKind.PHOTO else MsgKind.FILE,
                    media = res.first,
                    replyTo = replyTo,
                )
            }
        }
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
