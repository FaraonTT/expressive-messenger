@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.expressive.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun MainScreen(store: Store) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var folder by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val folders = listOf("Все", "Личные", "Группы", "Каналы", "Боты", "Непрочитанные")

    BackHandler(drawer.currentValue == DrawerValue.Open) { scope.launch { drawer.close() } }
    BackHandler(searching) { searching = false; query = "" }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }

    fun inFolder(c: Chat, f: Int): Boolean = when (f) {
        0 -> true
        1 -> c.type == ChatType.PRIVATE || c.type == ChatType.SAVED
        2 -> c.type == ChatType.GROUP
        3 -> c.type == ChatType.CHANNEL
        4 -> c.type == ChatType.BOT
        else -> c.unread > 0
    }

    val active = store.chats.filter { !it.archived }
    val ordered = active.sortedWith(
        compareByDescending<Chat> { it.pinned }.thenByDescending { store.last(it.id)?.time ?: 0L },
    )
    val visible = ordered.filter { inFolder(it, folder) }
    val archivedCount = store.chats.count { it.archived }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp)) {
                    Avatar(store.myName, 7, 64.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(store.myName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("@${store.myUsername}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                NavigationDrawerItem(
                    label = { Text("Новая группа") },
                    selected = false,
                    icon = { SIcon("group") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; store.newChatType = 1; store.push(Screen.NewChatS) },
                )
                NavigationDrawerItem(
                    label = { Text("Новый канал") },
                    selected = false,
                    icon = { SIcon("channel") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; store.newChatType = 2; store.push(Screen.NewChatS) },
                )
                NavigationDrawerItem(
                    label = { Text("Новый контакт") },
                    selected = false,
                    icon = { SIcon("person_add") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; store.newChatType = 0; store.push(Screen.NewChatS) },
                )
                NavigationDrawerItem(
                    label = { Text("Избранное") },
                    selected = false,
                    icon = { SIcon("bookmark") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; store.push(Screen.ChatS(1)) },
                )
                NavigationDrawerItem(
                    label = { Text(if (archivedCount > 0) "Архив ($archivedCount)" else "Архив") },
                    selected = false,
                    icon = { SIcon("archive") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; store.push(Screen.ArchiveS) },
                )
                NavigationDrawerItem(
                    label = { Text("Настройки") },
                    selected = false,
                    icon = { SIcon("settings") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; store.push(Screen.SettingsS) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                if (selected.isNotEmpty()) {
                    val allPinned = selected.all { store.chat(it)?.pinned == true }
                    val allMuted = selected.all { store.chat(it)?.muted == true }
                    TopAppBar(
                        title = { Text("${selected.size}") },
                        navigationIcon = { SIconButton("close", { selected = emptySet() }) },
                        actions = {
                            SIconButton("pin", { selected.forEach { store.setPinned(it, !allPinned) }; selected = emptySet() })
                            SIconButton("volume_off", { selected.forEach { store.setMuted(it, !allMuted) }; selected = emptySet() })
                            SIconButton("done_all", { selected.forEach { store.markRead(it) }; selected = emptySet() })
                            SIconButton("archive", { selected.forEach { store.setArchived(it, true) }; selected = emptySet() })
                            SIconButton("delete", { confirmDelete = true })
                        },
                    )
                } else if (searching) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    TopAppBar(
                        title = {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Поиск") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
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
                } else {
                    TopAppBar(
                        title = { Text("Сообщения", fontWeight = FontWeight.Bold) },
                        navigationIcon = { SIconButton("menu", { scope.launch { drawer.open() } }) },
                        actions = { SIconButton("search", { searching = true }) },
                    )
                }
            },
            floatingActionButton = {
                if (selected.isEmpty() && !searching) {
                    FloatingActionButton(
                        onClick = { store.newChatType = 0; store.push(Screen.NewChatS) },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { SIcon("edit") }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (searching) {
                    SearchResults(store, query)
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(folders) { i, name ->
                            val n = active.count { inFolder(it, i) && it.unread > 0 }
                            FilterChip(
                                selected = folder == i,
                                onClick = { folder = i },
                                label = { Text(if (n > 0 && i != 5) "$name $n" else name) },
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Нет чатов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(visible, key = { it.id }) { chat ->
                                ChatRow(
                                    store = store,
                                    chat = chat,
                                    selected = chat.id in selected,
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            selected = if (chat.id in selected) selected - chat.id else selected + chat.id
                                        } else {
                                            store.push(Screen.ChatS(chat.id))
                                        }
                                    },
                                    onLong = { selected = if (chat.id in selected) selected - chat.id else selected + chat.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить чаты?") },
            text = { Text("Выбранные чаты (${selected.size}) будут удалены вместе с историей переписки.") },
            confirmButton = {
                TextButton(onClick = {
                    selected.forEach { store.deleteChat(it) }
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun SearchResults(store: Store, query: String) {
    if (query.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Введите запрос для поиска по чатам и сообщениям", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val chatHits = store.chats.filter { it.title.contains(query, ignoreCase = true) || it.username.contains(query, ignoreCase = true) }
    val msgHits = store.chats.flatMap { c ->
        store.msgs(c.id).filter { it.kind == MsgKind.TEXT && it.text.contains(query, ignoreCase = true) }.map { c to it }
    }.sortedByDescending { it.second.time }.take(50)
    if (chatHits.isEmpty() && msgHits.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (chatHits.isNotEmpty()) {
            item { SectionTitle("Чаты") }
            items(chatHits, key = { "c${it.id}" }) { c ->
                ChatRow(store, c, false, { store.push(Screen.ChatS(c.id)) }, {})
            }
        }
        if (msgHits.isNotEmpty()) {
            item { SectionTitle("Сообщения") }
            items(msgHits, key = { "m${it.second.id}" }) { (c, m) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { store.push(Screen.ChatS(c.id)) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.title, c.seed, 44.dp, icon = if (c.type == ChatType.SAVED) "bookmark" else null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(listTime(m.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            m.text.replace('\n', ' '),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatRow(store: Store, chat: Chat, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val last = store.last(chat.id)
    val typing = chat.id in store.typing
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(chat.title, chat.seed, 56.dp, icon = if (chat.type == ChatType.SAVED) "bookmark" else null)
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(cs.surface),
                    contentAlignment = Alignment.Center,
                ) { SIcon("check_circle", tint = cs.primary, size = 20.dp) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (chat.type) {
                    ChatType.GROUP -> SIcon("group", tint = cs.onSurfaceVariant, size = 16.dp)
                    ChatType.CHANNEL -> SIcon("channel", tint = cs.onSurfaceVariant, size = 16.dp)
                    ChatType.BOT -> SIcon("bot", tint = cs.onSurfaceVariant, size = 16.dp)
                    else -> {}
                }
                if (chat.type == ChatType.GROUP || chat.type == ChatType.CHANNEL || chat.type == ChatType.BOT) Spacer(Modifier.width(4.dp))
                Text(
                    chat.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.muted) {
                    Spacer(Modifier.width(4.dp))
                    SIcon("volume_off", tint = cs.onSurfaceVariant, size = 14.dp)
                }
                Spacer(Modifier.weight(1f))
                if (last != null) {
                    if (last.fromMe) {
                        SIcon(if (last.status == MsgStatus.READ) "done_all" else "check", tint = cs.primary, size = 16.dp)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(listTime(last.time), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when {
                        typing -> Text("печатает…", color = cs.primary, maxLines = 1)
                        chat.draft.isNotBlank() -> Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = cs.error)) { append("Черновик: ") }
                                append(chat.draft.replace('\n', ' '))
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = cs.onSurfaceVariant,
                        )
                        last != null -> {
                            val prefix = when {
                                last.fromMe -> "Вы: "
                                chat.type == ChatType.GROUP -> last.author + ": "
                                else -> ""
                            }
                            Text(
                                buildAnnotatedString {
                                    if (prefix.isNotEmpty()) withStyle(SpanStyle(color = cs.primary)) { append(prefix) }
                                    append(last.preview())
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        else -> Text("Нет сообщений", color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (chat.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(chat.unread, chat.muted)
                } else if (chat.pinned) {
                    Spacer(Modifier.width(8.dp))
                    SIcon("pin", tint = cs.onSurfaceVariant, size = 18.dp)
                }
            }
        }
    }
}

@Composable
fun ArchiveScreen(store: Store) {
    val archived = store.chats.filter { it.archived }
        .sortedByDescending { store.last(it.id)?.time ?: 0L }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив") },
                navigationIcon = { SIconButton("arrow_back", { store.pop() }) },
            )
        },
    ) { pad ->
        if (archived.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Архив пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.padding(pad)) {
                Text(
                    "Долгое нажатие — вернуть из архива",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn {
                    items(archived, key = { it.id }) { c ->
                        ChatRow(store, c, false, { store.push(Screen.ChatS(c.id)) }, { store.setArchived(c.id, false) })
                    }
                }
            }
        }
    }
}
