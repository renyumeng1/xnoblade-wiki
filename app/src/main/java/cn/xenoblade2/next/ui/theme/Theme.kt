package cn.xenoblade2.next.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.xenoblade2.next.data.Prefs

/**
 * 自研主题：暖纸底 + 墨色字 + 单一青绿强调色。
 *
 * 字段名沿用旧代码，避免全项目改名；语义已经换掉：
 * Core 是界面强调色，不再是原版 PyraRed；Gold / Aqua 等只留给数据标签。
 */
object Palette {
    private var dark by mutableStateOf(false)

    fun apply(darkTheme: Boolean) {
        if (dark != darkTheme) dark = darkTheme
    }

    val isDark: Boolean get() = dark

    private fun p(light: Color, darkColor: Color): Color = if (dark) darkColor else light

    val Bg get() = p(Color(0xFFF3EFE6), Color(0xFF141311))
    val Surface get() = p(Color(0xFFFFFCF8), Color(0xFF1E1C19))
    val SurfaceHigh get() = p(Color(0xFFFFFFFF), Color(0xFF26231F))
    val TopBar get() = p(Color(0xFFF3EFE6), Color(0xFF141311))
    val Text get() = p(Color(0xFF1A1714), Color(0xFFF4EFE6))
    val TextDim get() = p(Color(0xFF6B645C), Color(0xFFB7AFA4))
    val CardBorder get() = p(Color(0xFFE4DDD2), Color(0xFF3A342C))

    val Core get() = p(Color(0xFF0B6E6A), Color(0xFF7DCCC6))
    val Gold get() = p(Color(0xFF8A6A2F), Color(0xFFE3C27A))
    val Purple get() = p(Color(0xFF6C5A8A), Color(0xFFC9B8E8))
    val Aqua get() = p(Color(0xFF2F7F8C), Color(0xFF9BDDE4))
    val Cyan get() = p(Color(0xFF2F7F8C), Color(0xFF9BDDE4))
    val Flame get() = p(Color(0xFFA33A2B), Color(0xFFF08B7A))
    val Done get() = p(Color(0xFF3D7A4A), Color(0xFF8FD3A0))
    val Brown get() = p(Color(0xFF795E45), Color(0xFFD0B79A))
    val Green get() = p(Color(0xFF3F7A55), Color(0xFF9AD3AE))
    val Orange get() = p(Color(0xFFA9652B), Color(0xFFE8B27A))
    val Teal get() = Core

    /** 地图画布单独用深底，不跟随浅色主题反转。 */
    val MapCanvas get() = Color(0xFF101614)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8EBE8),
    onPrimaryContainer = Color(0xFF063A37),
    secondary = Color(0xFF8A6A2F),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6C5A8A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3EFE6),
    onBackground = Color(0xFF1A1714),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF1A1714),
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF6B645C),
    error = Color(0xFFA33A2B),
    outline = Color(0xFFE4DDD2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DCCC6),
    onPrimary = Color(0xFF063A37),
    primaryContainer = Color(0xFF0B4A47),
    onPrimaryContainer = Color(0xFFD8EBE8),
    secondary = Color(0xFFE3C27A),
    onSecondary = Color(0xFF3A2A0A),
    tertiary = Color(0xFFC9B8E8),
    onTertiary = Color(0xFF2A203A),
    background = Color(0xFF141311),
    onBackground = Color(0xFFF4EFE6),
    surface = Color(0xFF1E1C19),
    onSurface = Color(0xFFF4EFE6),
    surfaceVariant = Color(0xFF26231F),
    onSurfaceVariant = Color(0xFFB7AFA4),
    error = Color(0xFFF08B7A),
    outline = Color(0xFF3A342C),
)

private val Typo = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, letterSpacing = 0.2.sp),
)

@Composable
fun Xc2Theme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = Prefs.themeMode.resolvesToDark(systemDark)
    val view = LocalView.current
    SideEffect {
        Palette.apply(dark)
        // enableEdgeToEdge 默认只跟随系统 uiMode；应用内强制亮/暗色时状态栏图标会失配。
        if (!view.isInEditMode) {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typo,
        content = content,
    )
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
