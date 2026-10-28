package cn.xenoblade2.next.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.WikiShots
import cn.xenoblade2.next.ui.theme.Palette

/**
 * 计划步骤里的「现场截图」：按需从 xenoblade2.cn 取该采集点的游戏内实景图。
 * 图片缓存在 cacheDir/wiki-shots，不占安装包体积。
 */
@Composable
fun WikiShotPanel(fileName: String?, modifier: Modifier = Modifier) {
    if (fileName.isNullOrBlank()) return
    var open by remember(fileName) { mutableStateOf(false) }
    var bitmap by remember(fileName) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(fileName) { mutableStateOf(false) }
    var failed by remember(fileName) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(fileName, open) {
        if (!open || bitmap != null || loading) return@LaunchedEffect
        loading = true
        bitmap = WikiShots.load(context, fileName)?.asImageBitmap()
        failed = bitmap == null
        loading = false
    }

    Column(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Core.copy(alpha = 0.06f))
                .border(1.dp, Palette.Core.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (open) "收起现场截图" else "看现场截图", color = Palette.Core, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("wiki 实景", color = Palette.TextDim, fontSize = 10.sp)
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            when {
                loading -> Text("正在从 wiki 取图…", color = Palette.TextDim, fontSize = 11.sp)
                failed -> Text("没有取到截图（wiki 未收录或网络不可用）", color = Palette.TextDim, fontSize = 11.sp)
                bitmap != null -> Image(
                    bitmap = bitmap!!,
                    contentDescription = "游戏内实景截图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 230.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "图源：乐园数据管理室 xenoblade2.cn · CC BY-NC-SA 4.0",
                color = Palette.TextDim.copy(alpha = 0.75f),
                fontSize = 9.sp,
            )
        }
    }
}
