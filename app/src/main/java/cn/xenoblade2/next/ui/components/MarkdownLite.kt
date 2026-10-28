package cn.xenoblade2.next.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cn.xenoblade2.next.ui.theme.Palette

/**
 * 极简 Markdown 降级渲染：只认 **粗体**、`行内代码`、行首 # 标题、行首「- 」与「* 」列表。
 * 模型回复几乎一定是 Markdown，这里把它转成带样式的纯文本，
 * 避免界面上直接露出 ** 和反引号。解析不了的语法原样保留，不会丢字。
 */
fun markdownLite(raw: String, codeColor: Color = Palette.Aqua): AnnotatedString = buildAnnotatedString {
    val text = raw.replace("\r\n", "\n")
    var i = 0
    var lineStart = true
    while (i < text.length) {
        val c = text[i]

        if (lineStart) {
            lineStart = false
            var h = i
            while (h < text.length && text[h] == '#') h++
            if (h > i && h < text.length && text[h] == ' ') {
                val end = text.indexOf('\n', h).let { if (it < 0) text.length else it }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.Core))
                append(text.substring(h + 1, end))
                pop()
                i = end
                continue
            }
            if ((c == '-' || c == '*' || c == '+') && i + 1 < text.length && text[i + 1] == ' ') {
                append("· ")
                i += 2
                continue
            }
        }

        if (c == '\n') {
            append('\n')
            i++
            lineStart = true
            continue
        }

        if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(text.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }

        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor))
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }

        append(c)
        i++
    }
}
