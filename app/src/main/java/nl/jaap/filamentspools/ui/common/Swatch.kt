package nl.jaap.filamentspools.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Parses "#RRGGBB" or "#RRGGBBAA"; null for anything else. */
fun parseHexColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6 && h.length != 8) return null
    val v = h.toLongOrNull(16) ?: return null
    return if (h.length == 6) Color(0xFF000000 or v) else Color(((v and 0xFF) shl 24) or (v shr 8))
}

/** Filament color dot, split diagonally for two-color filaments; grey with "?" when unknown. */
@Composable
fun Swatch(colorHex: String?, colorHex2: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val first = parseHexColor(colorHex)
    val second = parseHexColor(colorHex2)
    val outline = MaterialTheme.colorScheme.outline
    val unknown = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val d = this.size.minDimension
            if (first != null && second != null) {
                drawArc(first, startAngle = 135f, sweepAngle = 180f, useCenter = true, size = Size(d, d))
                drawArc(second, startAngle = -45f, sweepAngle = 180f, useCenter = true, size = Size(d, d))
            } else {
                drawCircle(first ?: unknown, radius = d / 2)
            }
            drawCircle(outline, radius = d / 2 - 0.5.dp.toPx(), center = Offset(d / 2, d / 2), style = Stroke(1.dp.toPx()))
        }
        if (first == null) Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
