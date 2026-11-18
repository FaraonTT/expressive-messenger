package app.expressive.messenger

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Loads icons from assets/icons/<name>.svg (24x24 viewBox) and draws their <path d="..."> data. */
object SvgIcons {
    private val cache = HashMap<String, Path>()
    private val dRegex = Regex("\\sd=\"([^\"]+)\"")

    fun load(ctx: Context, name: String): Path = synchronized(cache) {
        cache.getOrPut(name) {
            val result = Path()
            try {
                val svg = ctx.assets.open("icons/$name.svg").bufferedReader().use { it.readText() }
                dRegex.findAll(svg).forEach { match ->
                    result.addPath(PathParser().parsePathString(match.groupValues[1]).toPath())
                }
            } catch (e: Exception) {
                // missing or invalid icon: draw nothing
            }
            result
        }
    }
}

@Composable
fun SIcon(name: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, size: Dp = 24.dp) {
    val ctx = LocalContext.current
    val path = remember(name) { SvgIcons.load(ctx, name) }
    val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

@Composable
fun SIconButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconButton(onClick = onClick, modifier = modifier) {
        SIcon(name, tint = tint)
    }
}
