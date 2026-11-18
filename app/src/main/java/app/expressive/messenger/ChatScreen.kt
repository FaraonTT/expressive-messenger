@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.expressive.messenger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

fun chatSubtitle(c: Chat): String = when (c.type) {
    ChatType.PRIVATE -> if (c.seed % 2 == 0) "в сети" else "был(а) недавно"
    ChatType.GROUP -> "${c.members} ${plural(c.members, "участник", "участника", "участников")}"
    ChatType.CHANNEL -> "${c.members} ${plural(c.members, "подписчик", "подписчика", "подписчиков")}"
    ChatType.BOT -> "бот"
    ChatType.SAVED -> "заметки и файлы"
}

@Composable
fun ChatScreen(store: Store, chatId: Long) {
    val chat = store.chat(chatId)
    if (chat == null) {
        LaunchedEffect(Unit) { store.pop() }
        return
    }
    val cs = MaterialTheme.colorScheme
    val msgs = store.msgs(chatId)
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var input by remember { mutableStateOf(chat.draft) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var menuMsg by remember { mutableStateOf<Message?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var forwardIds by remember { mutableStateOf<List<Long>?>(null) }
    var deleteIds by remember { mutableStateOf<Set<Long>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var calling by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    val latestInput by rememberUpdatedState(input)
    val latestEditing by rememberUpdatedState(editing)
    DisposableEffect(chatId) {
        store.openChat = chatId
        onDispose {
            if (store.openChat == chatId) store.openChat = null
            if (latestEditing == null) store.setDraft(chatId, latestInput)
        }
    }
    LaunchedEffect(chatId, msgs.size) { store.markRead(chatId) }

    val canWrite = !(chat.type == ChatType.CHANNEL && !chat.owner)
    val canModerate = chat.type != ChatType.CHANNEL || chat.owner
    val shown = if (searching && query.isNotBlank()) msgs.filter { it.text.contains(query, ignoreCase = true) } else msgs
    val reversed = shown.asReversed()
    val byId = remember(msgs) { msgs.associateBy { it.id } }
    val listState = rememberLazyListState()
    val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    LaunchedEffect(msgs.size) { if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            store.attach(chatId, uri, true, replyTo?.id)
            replyTo = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            store.attach(chatId, uri, false, replyTo?.id)
            replyTo = null
        }
    }

    fun say(text: String) { scope.launch { snack.showSnackbar(text) } }
    fun jump(id: Long) {
        val idx = reversed.indexOfFirst { it.id == id }
        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
    }
    fun doSend() {
        val text = input.trim()
        if (text.isEmpty()) return
        val e = editing
        if (e != null) {
            store.edit(chatId, e.id, text)
            editing = null
        } else {
            store.send(chatId, text, replyTo = replyTo?.id)
            replyTo = null
        }
        input = ""
    }
    fun startReply(m: Message) {
        if (canWrite) { replyTo = m; editing = null }
    }
    fun startEdit(m: Message) {
        editing = m; replyTo = null; input = m.text
    }
    fun toggleSel(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    BackHandlerCompat(selected.isNotEmpty() || searching || showEmoji) {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            searching -> { searching = false; query = "" }
            showEmoji -> showEmoji = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            when {
                selected.isNotEmpty() -> {
                    val sel = msgs.filter { it.id in selected }.sortedBy { it.time }
                    TopAppBar(
                        title = { Text("${selected.size}") },
                        navigationIcon = { SIconButton("close", { selected = emptySet() }) },
                        actions = {
                            if (selected.size == 1 && canWrite) {
                                SIconButton("reply", { sel.firstOrNull()?.let { startReply(it) }; selected = emptySet() })
                            }
                            SIconButton("copy", {
                                copyText(ctx, sel.filter { it.kind == MsgKind.TEXT }.joinToString("\n") { it.text })
                                say("Скопировано")
                                selected = emptySet()
                            })
                            SIconButton("forward", { forwardIds = sel.map { it.id } })
                            if (sel.all { it.fromMe || canModerate }) {
                                SIconButton("delete", { deleteIds = selected })
                            }
                        },
                    )
                }
                searching -> {
                    val fr = remember { FocusRequester() }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                    TopAppBar(
                        title = {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Поиск в чате") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().focusRequester(fr),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        },
                        navigationIcon = { SIconButton("arrow_back", { searching = false; query = "" }) },
                        actions = { if (query.isNotEmpty()) SIconButton("close", { query = "" }) },
                    )
                }
                else -> {
                    TopAppBar(
                        title = {
                            Row(
                                Modifier.clickable { store.push(Screen.ProfileS(chatId)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(chat.title, chat.seed, 40.dp, icon = if (chat.type == ChatType.SAVED) "bookmark" else null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    val typingNow = chatId in store.typing
                                    Text(
                                        chat.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        if (typingNow) "печатает…" else chatSubtitle(chat),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (typingNow) cs.primary else cs.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        navigationIcon = { SIconButton("arrow_back", { store.pop() }) },
                        actions = {
                            if (chat.type == ChatType.PRIVATE) SIconButton("phone", { calling = true })
                            SIconButton("search", { searching = true })
                            Box {
                                SIconButton("more_vert", { menuOpen = true })
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Профиль") },
                                        leadingIcon = { SIcon("info") },
                                        onClick = { menuOpen = false; store.push(Screen.ProfileS(chatId)) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (chat.muted) "Включить звук" else "Без звука") },
                                        leadingIcon = { SIcon(if (chat.muted) "notifications" else "volume_off") },
                                        onClick = { menuOpen = false; store.setMuted(chatId, !chat.muted) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("В архив") },
                                        leadingIcon = { SIcon("archive") },
                                        onClick = { menuOpen = false; store.setArchived(chatId, true); store.pop() },
                                    )
                                    if (chat.type == ChatType.PRIVATE) {
                                        DropdownMenuItem(
                                            text = { Text(if (chat.blocked) "Разблокировать" else "Заблокировать") },
                                            leadingIcon = { SIcon("block") },
                                            onClick = { menuOpen = false; store.setBlocked(chatId, !chat.blocked) },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Очистить историю") },
                                        leadingIcon = { SIcon("delete") },
                                        onClick = { menuOpen = false; confirm = "clear" },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить чат", color = cs.error) },
                                        leadingIcon = { SIcon("close", tint = cs.error) },
                                        onClick = { menuOpen = false; confirm = "delete" },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding(),
        ) {
            val pm = chat.pinnedMessage?.let { byId[it] }
            if (pm != null) {
                Surface(color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable { jump(pm.id) }) {
                    Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SIcon("pin", tint = cs.primary, size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Закреплённое сообщение", color = cs.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(pm.preview(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (canModerate) SIconButton("close", { store.togglePin(chatId, pm.id) })
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(cs.surface, cs.primaryContainer.copy(alpha = 0.22f)))),
            ) {
                if (reversed.isEmpty()) {
                    Text(
                        if (searching) "Ничего не найдено" else "Сообщений пока нет",
                        modifier = Modifier.align(Alignment.Center),
                        color = cs.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(reversed, key = { _, m -> m.id }) { i, m ->
                        val older = reversed.getOrNull(i + 1)
                        Column(Modifier.fillMaxWidth().animateItem()) {
                            if (older == null || !sameDay(older.time, m.time)) DateChip(dateLabel(m.time))
                            MessageBubble(
                                store = store,
                                m = m,
                                chat = chat,
                                replied = m.replyTo?.let { byId[it] },
                                selected = m.id in selected,
                                onClick = { if (selected.isNotEmpty()) toggleSel(m.id) },
                                onLong = { if (selected.isNotEmpty()) toggleSel(m.id) else menuMsg = m },
                                onReact = { store.react(chatId, m.id, it) },
                                onJump = { jump(it) },
                                onSwipeReply = { startReply(m) },
                            )
                        }
                    }
                }
                if (showDown) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) { SIcon("arrow_down") }
                }
            }

            when {
                selected.isNotEmpty() -> {}
                chat.blocked -> {
                    Surface(color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Пользователь заблокирован", modifier = Modifier.weight(1f).padding(start = 8.dp))
                            TextButton(onClick = { store.setBlocked(chatId, false) }) { Text("Разблокировать") }
                        }
                    }
                }
                !canWrite -> {
                    Box(Modifier.fillMaxWidth().padding(8.dp)) {
                        FilledTonalButton(onClick = { store.setMuted(chatId, !chat.muted) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (chat.muted) "Включить звук" else "Отключить звук")
                        }
                    }
                }
                else -> {
                    Column {
                        val rt = replyTo
                        val ed = editing
                        if (rt != null || ed != null) {
                            Surface(color = cs.surfaceContainer) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SIcon(if (ed != null) "edit" else "reply", tint = cs.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        val who = if (rt != null) (if (rt.fromMe) "Вы" else rt.author) else ""
                                        Text(
                                            if (ed != null) "Редактирование" else "Ответ $who",
                                            color = cs.primary,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text((ed ?: rt)!!.preview(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    SIconButton("close", {
                                        replyTo = null
                                        if (editing != null) { editing = null; input = "" }
                                    })
                                }
                            }
                        }
                        InputBar(
                            input = input,
                            onInput = { input = it },
                            onSend = { doSend() },
                            onAttach = { focus.clearFocus(); showAttach = true },
                            emojiOpen = showEmoji,
                            onEmoji = {
                                if (showEmoji) showEmoji = false else { focus.clearFocus(); showEmoji = true }
                            },
                            onFieldFocus = { showEmoji = false },
                            editing = editing != null,
                            enterSends = store.enterSends,
                            onVoice = { secs ->
                                store.send(chatId, "", MsgKind.VOICE, duration = secs, replyTo = replyTo?.id)
                                replyTo = null
                            },
                            onHint = { say("Удерживайте кнопку для записи голосового") },
                        )
                        if (showEmoji) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(8),
                                modifier = Modifier.fillMaxWidth().height(240.dp).background(cs.surfaceContainer),
                                contentPadding = PaddingValues(8.dp),
                            ) {
                                gridItems(EMOJIS) { e ->
                                    Box(
                                        Modifier.aspectRatio(1f).clickable { input += e },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(e, fontSize = 26.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ----- message actions -----
    val mm = menuMsg
    if (mm != null) {
        ModalBottomSheet(onDismissRequest = { menuMsg = null }) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                REACTIONS.forEach { e ->
                    Surface(
                        onClick = { store.react(chatId, mm.id, e); menuMsg = null },
                        shape = CircleShape,
                        color = if (mm.myReaction == e) cs.primaryContainer else cs.surfaceContainerHigh,
                    ) { Text(e, fontSize = 24.sp, modifier = Modifier.padding(10.dp)) }
                }
            }
            if (canWrite) SheetAction("reply", "Ответить") { startReply(mm); menuMsg = null }
            if (mm.kind == MsgKind.TEXT) SheetAction("copy", "Копировать") { copyText(ctx, mm.text); menuMsg = null; say("Скопировано") }
            SheetAction("forward", "Переслать") { forwardIds = listOf(mm.id); menuMsg = null }
            if (canModerate) SheetAction("pin", if (chat.pinnedMessage == mm.id) "Открепить" else "Закрепить") { store.togglePin(chatId, mm.id); menuMsg = null }
            if (mm.fromMe && mm.kind == MsgKind.TEXT) SheetAction("edit", "Изменить") { startEdit(mm); menuMsg = null }
            SheetAction("check_circle", "Выбрать") { selected = setOf(mm.id); menuMsg = null }
            if (mm.fromMe || canModerate) SheetAction("delete", "Удалить", cs.error) { deleteIds = setOf(mm.id); menuMsg = null }
            Spacer(Modifier.height(16.dp))
        }
    }

    val fw = forwardIds
    if (fw != null) {
        ModalBottomSheet(onDismissRequest = { forwardIds = null }) {
            Text("Переслать", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(store.chats.filter { it.type != ChatType.CHANNEL || it.owner }, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(if (c.type == ChatType.SAVED) "Избранное" else c.title) },
                        leadingContent = { Avatar(c.title, c.seed, 40.dp, icon = if (c.type == ChatType.SAVED) "bookmark" else null) },
                        modifier = Modifier.clickable {
                            store.forward(msgs.filter { it.id in fw }, c.id)
                            forwardIds = null
                            selected = emptySet()
                            say("Переслано: ${c.title}")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAttach) {
        ModalBottomSheet(onDismissRequest = { showAttach = false }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachItem("image", "Фото") { showAttach = false; photoPicker.launch("image/*") }
                AttachItem("file", "Файл") { showAttach = false; filePicker.launch("*/*") }
                AttachItem("location", "Геопозиция") {
                    showAttach = false
                    store.send(chatId, "55.7558, 37.6173", MsgKind.LOCATION, replyTo = replyTo?.id)
                    replyTo = null
                }
                AttachItem("person", "Контакт") {
                    showAttach = false
                    store.send(chatId, "Иван Петров", MsgKind.CONTACT, media = "+7 900 123-45-67", replyTo = replyTo?.id)
                    replyTo = null
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val di = deleteIds
    if (di != null) {
        AlertDialog(
            onDismissRequest = { deleteIds = null },
            title = { Text("Удалить ${di.size} ${plural(di.size, "сообщение", "сообщения", "сообщений")}?") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(chatId, di)
                    selected = emptySet()
                    deleteIds = null
                }) { Text("Удалить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleteIds = null }) { Text("Отмена") } },
        )
    }

    val cf = confirm
    if (cf != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (cf == "clear") "Очистить историю?" else "Удалить чат?") },
            text = { Text(if (cf == "clear") "Все сообщения будут удалены." else "Чат будет удалён вместе с историей.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (cf == "clear") store.clearHistory(chatId) else store.deleteChat(chatId)
                }) { Text("Подтвердить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }

    if (calling) CallDialog(chat) { calling = false }
}

@Composable
fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
fun AttachItem(icon: String, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SIcon(icon, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun CallDialog(chat: Chat, onEnd: () -> Unit) {
    var sec by remember { mutableIntStateOf(-3) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            sec++
        }
    }
    AlertDialog(
        onDismissRequest = onEnd,
        icon = { Avatar(chat.title, chat.seed, 72.dp) },
        title = { Text(chat.title) },
        text = { Text(if (sec < 0) "Вызов…" else "%d:%02d".format(sec / 60, sec % 60)) },
        confirmButton = {
            Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                SIcon("phone", size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Завершить")
            }
        },
    )
}

@Composable
fun InputBar(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    emojiOpen: Boolean,
    onEmoji: () -> Unit,
    onFieldFocus: () -> Unit,
    editing: Boolean,
    enterSends: Boolean,
    onVoice: (Int) -> Unit,
    onHint: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var recording by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) {
        if (recording) {
            secs = 0
            while (true) {
                delay(1000)
                secs++
            }
        }
    }
    val voiceCb by rememberUpdatedState(onVoice)
    val hintCb by rememberUpdatedState(onHint)
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
            if (recording) {
                Row(Modifier.height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(cs.error))
                    Spacer(Modifier.width(10.dp))
                    Text("%d:%02d".format(secs / 60, secs % 60))
                    Spacer(Modifier.weight(1f))
                    Text("Отпустите, чтобы отправить", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    SIconButton("emoji", onEmoji, Modifier.height(56.dp), tint = if (emojiOpen) cs.primary else Color.Unspecified)
                    TextField(
                        value = input,
                        onValueChange = onInput,
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFieldFocus() },
                        placeholder = { Text(if (editing) "Изменить сообщение" else "Сообщение") },
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (enterSends) ImeAction.Send else ImeAction.Default,
                        ),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                    )
                    if (!editing) SIconButton("attach", onAttach, Modifier.height(56.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (input.isNotBlank()) {
            Surface(onClick = onSend, shape = CircleShape, color = cs.primary, modifier = Modifier.size(56.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SIcon(if (editing) "check" else "send", tint = cs.onPrimary)
                }
            }
        } else if (!editing) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (recording) cs.error else cs.primary)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            recording = true
                            val t0 = System.currentTimeMillis()
                            tryAwaitRelease()
                            recording = false
                            val s = ((System.currentTimeMillis() - t0) / 1000).toInt()
                            if (s >= 1) voiceCb(s) else hintCb()
                        })
                    },
                contentAlignment = Alignment.Center,
            ) { SIcon("mic", tint = cs.onPrimary) }
        }
    }
}

@Composable
fun MessageBubble(
    store: Store,
    m: Message,
    chat: Chat,
    replied: Message?,
    selected: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onReact: (String) -> Unit,
    onJump: (Long) -> Unit,
    onSwipeReply: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val r = store.bubbleRadius.dp
    val bg = if (m.fromMe) cs.primaryContainer else cs.surfaceContainerHigh
    val fg = if (m.fromMe) cs.onPrimaryContainer else cs.onSurface
    var dx by remember { mutableFloatStateOf(0f) }
    val swipe by rememberUpdatedState(onSwipeReply)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (chat.type == ChatType.GROUP && !m.fromMe) {
            Avatar(m.author, m.author.hashCode(), 32.dp)
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(
                topStart = r,
                topEnd = r,
                bottomStart = if (m.fromMe) r else 4.dp,
                bottomEnd = if (m.fromMe) 4.dp else r,
            ),
            modifier = Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .widthIn(max = 300.dp)
                .pointerInput(m.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dx < -90f) swipe()
                            dx = 0f
                        },
                        onDragCancel = { dx = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dx = (dx + amount).coerceIn(-120f, 0f)
                        },
                    )
                }
                .combinedClickable(onClick = onClick, onLongClick = onLong),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (chat.type == ChatType.GROUP && !m.fromMe) {
                    Text(
                        m.author,
                        color = avatarColor(m.author.hashCode()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (m.forwardedFrom != null) {
                    Text(
                        "Переслано от ${m.forwardedFrom}",
                        color = cs.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (replied != null) ReplyQuote(replied) { onJump(replied.id) }
                when (m.kind) {
                    MsgKind.TEXT -> Text(m.text, style = MaterialTheme.typography.bodyLarge)
                    MsgKind.PHOTO -> {
                        PhotoBody(m.media)
                        if (m.text.isNotBlank()) Text(m.text, modifier = Modifier.padding(top = 4.dp))
                    }
                    MsgKind.FILE -> FileBody(m)
                    MsgKind.LOCATION -> LocationBody(m)
                    MsgKind.CONTACT -> ContactBody(m)
                    MsgKind.VOICE -> VoiceBody(m)
                }
                if (m.reactions.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.reactions.forEach { (emoji, count) ->
                            val mine = m.myReaction == emoji
                            Surface(
                                onClick = { onReact(emoji) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (mine) cs.primary else cs.secondaryContainer,
                                contentColor = if (mine) cs.onPrimary else cs.onSecondaryContainer,
                            ) {
                                Text(
                                    "$emoji $count",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    val metaColor = LocalContentColor.current.copy(alpha = 0.6f)
                    if (m.edited) Text("изм. ", style = MaterialTheme.typography.labelSmall, color = metaColor)
                    Text(fmtTime(m.time), style = MaterialTheme.typography.labelSmall, color = metaColor)
                    if (m.fromMe) {
                        Spacer(Modifier.width(3.dp))
                        SIcon(
                            if (m.status == MsgStatus.READ) "done_all" else "check",
                            tint = if (m.status == MsgStatus.READ) cs.primary else metaColor,
                            size = 15.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyQuote(m: Message, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(cs.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                if (m.fromMe) "Вы" else m.author,
                color = cs.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(m.preview(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PhotoBody(path: String?) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { if (path == null) null else decodeSampled(path, 800) }
    }
    val b = bmp
    val ratio = if (b != null) (b.width.toFloat() / b.height.toFloat()).coerceIn(0.6f, 1.8f) else 1.3f
    Box(
        Modifier
            .width(240.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) {
            Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            SIcon("image", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
        }
    }
}

@Composable
fun FileBody(m: Message) {
    val cs = MaterialTheme.colorScheme
    val size = remember(m.media) { m.media?.let { File(it).length() } ?: 0L }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = cs.primary, modifier = Modifier.size(44.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SIcon("file", tint = cs.onPrimary) }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(m.text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(formatSize(size), style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun LocationBody(m: Message) {
    val cs = MaterialTheme.colorScheme
    val lineColor = cs.outline.copy(alpha = 0.35f)
    Box(
        Modifier
            .size(220.dp, 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(cs.secondaryContainer, cs.tertiaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 1..5) {
                val x = size.width * i / 6f
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 2f)
            }
            for (j in 1..3) {
                val y = size.height * j / 4f
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2f)
            }
        }
        SIcon("location", tint = cs.error, size = 40.dp)
    }
    Text(m.text, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
}

@Composable
fun ContactBody(m: Message) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(m.text, m.text.hashCode(), 44.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(m.text, fontWeight = FontWeight.Medium)
            Text(m.media ?: "", style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun VoiceBody(m: Message) {
    val cs = MaterialTheme.colorScheme
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            val total = maxOf(m.duration, 1) * 1000L
            var t = (progress * total).toLong()
            while (t < total) {
                delay(100)
                t += 100
                progress = t / total.toFloat()
            }
            playing = false
            progress = 0f
        }
    }
    val bars = remember(m.id) {
        val rnd = java.util.Random(m.id)
        List(28) { 0.25f + rnd.nextFloat() * 0.75f }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = { playing = !playing },
            shape = CircleShape,
            color = cs.primary,
            modifier = Modifier.size(44.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SIcon(if (playing) "pause" else "play", tint = cs.onPrimary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Canvas(Modifier.width(140.dp).height(28.dp)) {
                val n = bars.size
                val step = size.width / n
                val bw = step * 0.62f
                bars.forEachIndexed { i, h ->
                    val played = i.toFloat() / n < progress
                    val bh = size.height * h
                    drawRoundRect(
                        color = if (played) cs.primary else cs.primary.copy(alpha = 0.35f),
                        topLeft = Offset(i * step, (size.height - bh) / 2f),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(bw / 2f),
                    )
                }
            }
            Text(
                "%d:%02d".format(m.duration / 60, m.duration % 60),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
    }
}
