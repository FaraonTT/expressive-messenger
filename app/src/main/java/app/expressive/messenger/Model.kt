package app.expressive.messenger

import org.json.JSONObject

enum class ChatType { PRIVATE, GROUP, CHANNEL, SAVED, BOT }
enum class MsgKind { TEXT, PHOTO, FILE, LOCATION, CONTACT, VOICE }
enum class MsgStatus { SENT, READ }

data class Message(
    val id: Long,
    val chatId: Long,
    val fromMe: Boolean,
    val author: String,
    val kind: MsgKind = MsgKind.TEXT,
    val text: String = "",
    val time: Long,
    val status: MsgStatus = MsgStatus.SENT,
    val replyTo: Long? = null,
    val edited: Boolean = false,
    val forwardedFrom: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
    val media: String? = null,
    val duration: Int = 0,
)

data class Chat(
    val id: Long,
    val title: String,
    val type: ChatType,
    val seed: Int,
    val bio: String = "",
    val username: String = "",
    val members: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
    val unread: Int = 0,
    val draft: String = "",
    val pinnedMessage: Long? = null,
    val owner: Boolean = false,
    val blocked: Boolean = false,
)

data class Banner(val chatId: Long, val title: String, val text: String)

sealed interface Screen {
    data object Main : Screen
    data class ChatS(val id: Long) : Screen
    data class ProfileS(val id: Long) : Screen
    data object SettingsS : Screen
    data object NewChatS : Screen
    data object ArchiveS : Screen
}

fun Message.toJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("chatId", chatId)
    o.put("fromMe", fromMe)
    o.put("author", author)
    o.put("kind", kind.name)
    o.put("text", text)
    o.put("time", time)
    o.put("status", status.name)
    if (replyTo != null) o.put("replyTo", replyTo)
    o.put("edited", edited)
    if (forwardedFrom != null) o.put("fwd", forwardedFrom)
    val r = JSONObject()
    for ((k, v) in reactions) r.put(k, v)
    o.put("reactions", r)
    if (myReaction != null) o.put("my", myReaction)
    if (media != null) o.put("media", media)
    o.put("duration", duration)
    return o
}

fun msgFromJson(o: JSONObject): Message {
    val r = HashMap<String, Int>()
    val ro = o.optJSONObject("reactions")
    if (ro != null) {
        for (k in ro.keys()) r[k] = ro.getInt(k)
    }
    return Message(
        id = o.getLong("id"),
        chatId = o.getLong("chatId"),
        fromMe = o.getBoolean("fromMe"),
        author = o.optString("author"),
        kind = MsgKind.valueOf(o.getString("kind")),
        text = o.optString("text"),
        time = o.getLong("time"),
        status = MsgStatus.valueOf(o.getString("status")),
        replyTo = if (o.has("replyTo")) o.getLong("replyTo") else null,
        edited = o.optBoolean("edited"),
        forwardedFrom = if (o.has("fwd")) o.getString("fwd") else null,
        reactions = r,
        myReaction = if (o.has("my")) o.getString("my") else null,
        media = if (o.has("media")) o.getString("media") else null,
        duration = o.optInt("duration"),
    )
}

fun Chat.toJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("title", title)
    o.put("type", type.name)
    o.put("seed", seed)
    o.put("bio", bio)
    o.put("username", username)
    o.put("members", members)
    o.put("pinned", pinned)
    o.put("muted", muted)
    o.put("archived", archived)
    o.put("unread", unread)
    o.put("draft", draft)
    if (pinnedMessage != null) o.put("pm", pinnedMessage)
    o.put("owner", owner)
    o.put("blocked", blocked)
    return o
}

fun chatFromJson(o: JSONObject): Chat = Chat(
    id = o.getLong("id"),
    title = o.getString("title"),
    type = ChatType.valueOf(o.getString("type")),
    seed = o.getInt("seed"),
    bio = o.optString("bio"),
    username = o.optString("username"),
    members = o.optInt("members"),
    pinned = o.optBoolean("pinned"),
    muted = o.optBoolean("muted"),
    archived = o.optBoolean("archived"),
    unread = o.optInt("unread"),
    draft = o.optString("draft"),
    pinnedMessage = if (o.has("pm")) o.getLong("pm") else null,
    owner = o.optBoolean("owner"),
    blocked = o.optBoolean("blocked"),
)
