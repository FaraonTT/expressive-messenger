@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)

package app.expressive.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MessengerApp(store: Store) {
    val dark = when (store.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MessengerTheme(dark, store.dynamicColor, store.textScale) {
        val top = store.stack.last()
        BackHandler(enabled = store.stack.size > 1) { store.pop() }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            AnimatedContent(
                targetState = top,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith fadeOut(tween(120))
                },
                label = "nav",
            ) { s ->
                when (s) {
                    Screen.Main -> MainScreen(store)
                    is Screen.ChatS -> ChatScreen(store, s.id)
                    is Screen.ProfileS -> ProfileScreen(store, s.id)
                    Screen.SettingsS -> SettingsScreen(store)
                    Screen.NewChatS -> NewChatScreen(store)
                    Screen.ArchiveS -> ArchiveScreen(store)
                }
            }
            val b = store.banner
            if (b != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clickable {
                            store.banner = null
                            store.push(Screen.ChatS(b.chatId))
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 8.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(b.title, b.title.hashCode(), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                b.title,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                b.text,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
