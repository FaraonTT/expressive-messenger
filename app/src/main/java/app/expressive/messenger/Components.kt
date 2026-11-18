@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.expressive.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val RU: Locale = Locale("ru")

val avatarColors = listOf(
    Color(0xFFE17076), Color(0xFFFAA774), Color(0xFFA695E7), Color(0xFF7BC862),
    Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE),
)

fun avatarColor(seed: Int): Color = avatarColors[abs(seed % avatarColors.size)]

fun initials(title: String): String {
    val parts = title.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

fun fmtTime(t: Long): String = SimpleDateFormat("HH:mm", RU).format(Date(t))

fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

fun listTime(t: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(t, now) -> fmtTime(t)
        now - t < 6 * 86_400_000L -> SimpleDateFormat("EEE", RU).format(Date(t))
        else -> SimpleDateFormat("dd.MM.yy", RU).format(Date(t))
    }
}

fun dateLabel(t: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(t, now) -> "Сегодня"
        sameDay(t, now - 86_400_000L) -> "Вчера"
        else -> SimpleDateFormat("d MMMM", RU).format(Date(t))
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1 -> one
        m10 in 2..4 -> few
        else -> many
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "%.1f МБ".format(Locale.US, bytes / 1048576.0)
}

fun Message.preview(): String = when (kind) {
    MsgKind.TEXT -> text.replace('\n', ' ')
    MsgKind.PHOTO -> if (text.isBlank()) "Фото" else "Фото, $text"
    MsgKind.FILE -> "Файл: $text"
    MsgKind.LOCATION -> "Геопозиция"
    MsgKind.CONTACT -> "Контакт: $text"
    MsgKind.VOICE -> "Голосовое сообщение"
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}

fun decodeSampled(path: String, max: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > max * 2 || bounds.outHeight / sample > max * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
} catch (e: Exception) {
    null
}

val EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
    "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😋",
    "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😢",
    "😭", "😤", "😠", "🤯", "😳", "😱", "🤔", "🤗",
    "🙄", "😴", "👍", "👎", "👏", "🙌", "🙏", "💪",
    "👋", "🤝", "❤️", "🧡", "💛", "💚", "💙", "💜",
    "🔥", "🎉", "✨", "⭐", "🌈", "☀️", "🌙", "🍕",
)

val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉")

@Composable
fun Avatar(title: String, seed: Int, size: Dp = 52.dp, icon: String? = null, modifier: Modifier = Modifier) {
    val c = avatarColor(seed)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.75f), c))),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            SIcon(icon, tint = Color.White, size = size * 0.5f)
        } else {
            Text(initials(title), color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun UnreadBadge(count: Int, muted: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        modifier = Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp),
    ) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                if (count > 99) "99+" else count.toString(),
                color = MaterialTheme.colorScheme.surface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun DateChip(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SheetAction(icon: String, label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    ListItem(
        headlineContent = { Text(label, color = c) },
        leadingContent = { SIcon(icon, tint = c) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}
