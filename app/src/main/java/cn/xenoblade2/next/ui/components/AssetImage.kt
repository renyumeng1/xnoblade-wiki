package cn.xenoblade2.next.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 assets 读图（异刃头像、物品分类图标）。
 * 图标只有几十 KB，解码放 IO 线程，按路径缓存到 Compose 状态里。
 */
@Composable
fun rememberAssetBitmap(path: String?): ImageBitmap? {
    val assets = LocalContext.current.assets
    val state by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            path?.takeIf { it.isNotBlank() }?.let { p ->
                runCatching {
                    assets.open(p).use { s -> BitmapFactory.decodeStream(s)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }
    return state
}

/** path 为空或缺失时什么也不画，由调用方决定用文字/色块兜底。 */
@Composable
fun AssetImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bmp = rememberAssetBitmap(path) ?: return
    Image(
        bitmap = bmp,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
