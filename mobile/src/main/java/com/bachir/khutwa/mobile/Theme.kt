package com.bachir.khutwa.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_semibold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
)
val Doto = FontFamily(Font(R.font.doto_black, FontWeight.Black))

fun dotoStyle(size: TextUnit, color: Color) = TextStyle(
    fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = size, color = color,
    textDirection = TextDirection.Ltr,
)

// Mode colors — same as the design
val WalkBlue = Color(0xFF4FA3F7)
val RunOrange = Color(0xFFFF7A1A)
val HeartRed = Color(0xFFFF5A4E)

data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val invBg: Color,   // the black/white CTA
    val invText: Color,
)

val LightPalette = Palette(false, Color(0xFFE8E9EC), Color.White, Color(0xFF0B0B0C), Color(0xFF5F636A), Color(0xFFC9CBD0), Color(0xFF0B0B0C), Color.White)
val DarkPalette = Palette(true, Color(0xFF070708), Color(0xFF141518), Color.White, Color(0xFFA3A6AD), Color(0xFF3A3C42), Color.White, Color(0xFF0B0B0C))

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** A soft radial blob drawn behind content (fractions of the box size). */
data class Blob(val color: Color, val cx: Float, val cy: Float, val r: Float)

fun Modifier.blobs(vararg list: Blob) = drawBehind {
    list.forEach { b ->
        val c = Offset(size.width * b.cx, size.height * b.cy)
        val rad = size.minDimension * b.r
        drawCircle(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), c, rad), rad, c)
    }
}

/** Gradient card styles from the design, keyed by meaning. */
enum class CardTone(val base: Color, val blobs: List<Blob>, val linear: Brush? = null) {
    STEPS(Color(0xFF7B4F66), listOf(Blob(Color(0xCC1E0A28), 0.15f, 0.1f, 0.9f)),
        Brush.linearGradient(listOf(Color(0xFF4A3E5E), Color(0xFF7B4F66), Color(0xFFC77370)))),
    HEART(Color(0xFF2A2F2E), listOf(Blob(Color(0x8C96B4A0), 0.9f, 0.0f, 1.1f), Blob(Color(0xFF050505), 0.1f, 1.0f, 0.7f))),
    SLEEP(Color(0xFF1C2C8A), listOf(Blob(Color(0x995A78FF), 0.9f, 0.0f, 1.0f), Blob(Color(0xFF04061A), 0.25f, 0.45f, 0.6f))),
    MINUTES(Color(0xFFC95A9E), listOf(Blob(Color(0xFF5A0838), 0.4f, 0.4f, 0.55f))),
    MINUTES_LIGHT(Color(0xFF6B5A4A), listOf(Blob(Color(0xB35FA0DC), 0.1f, 0.05f, 0.75f)),
        Brush.linearGradient(listOf(Color(0xFF3A3F4A), Color(0xFF6B5A4A), Color(0xFFC9803E)))),
    WORKOUTS(Color(0xFFC4532E), listOf(Blob(Color(0xE63C50F0), 0.2f, 0.45f, 0.5f))),
    WALK(Color(0xFF2A6FD6), listOf(Blob(Color(0xFF071433), 0.25f, 0.3f, 0.75f), Blob(Color(0xCC8CBEFF), 1.0f, 1.1f, 0.7f))),
    RUN(Color(0xFFC4532E), listOf(Blob(Color(0xE63C50F0), 0.25f, 0.35f, 0.55f))),
    FOOTBALL(Color(0xFF2E4A3A), listOf(Blob(Color(0x8CA0D278), 0.9f, 0.0f, 1.1f), Blob(Color(0xFF050805), 0.1f, 1.0f, 0.7f))),
}

@Composable
fun ToneCard(tone: CardTone, modifier: Modifier = Modifier, radius: Dp = 34.dp, pad: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .then(if (tone.linear != null) Modifier.background(tone.linear) else Modifier.background(tone.base))
            .blobs(*tone.blobs.toTypedArray())
            .padding(pad),
        content = content,
    )
}

@Composable
fun PlainCard(modifier: Modifier = Modifier, radius: Dp = 30.dp, pad: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(p.card)
            .then(if (p.dark) Modifier.border(1.dp, Color(0xFF23252A), RoundedCornerShape(radius)) else Modifier)
            .padding(pad),
        content = content,
    )
}

/** Outlined circle icon, top corner of the cards. */
@Composable
fun CircleIcon(icon: ImageVector, tint: Color, ring: Color, size: Dp = 38.dp, filled: Color? = null) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (filled != null) Modifier.background(filled) else Modifier.border(1.dp, ring, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}
