package cn.xenoblade2.next.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Core = Color(0xFF6FE3C4)      // 异刃核心青
    val CoreDim = Color(0xFF2E7D6B)
    val Flame = Color(0xFFE8603C)
    val Aqua = Color(0xFF3C8FE8)
    val Gold = Color(0xFFC9A227)
    val Bg = Color(0xFF0B0F17)
    val Surface = Color(0xFF141B25)
    val SurfaceHigh = Color(0xFF1D2734)
    val Text = Color(0xFFE6EDF5)
    val TextDim = Color(0xFF9FB0C4)
    val Done = Color(0xFF3FBF7F)
}

private val Colors = darkColorScheme(
    primary = Palette.Core,
    onPrimary = Color(0xFF06251E),
    primaryContainer = Palette.CoreDim,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Aqua,
    onSecondary = Color(0xFF07182B),
    tertiary = Palette.Gold,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    error = Palette.Flame,
    outline = Color(0xFF2C3A4C),
)

private val Typo = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun Xc2Theme(content: @Composable () -> Unit) {
    // 游戏本身就是深色 UI，跟随系统也不切成浅色，避免地图对比度崩掉
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = Colors, typography = Typo, content = content)
}
