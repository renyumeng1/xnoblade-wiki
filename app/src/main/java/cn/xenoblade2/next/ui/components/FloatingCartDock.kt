package cn.xenoblade2.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * 原版全局悬浮按钮组 `FloatingCartDock`（FloatingCartDockKt.java:92 起）：
 * - 右下角 `padding(end = 18.dp, bottom = 64.dp)`，Column `spacedBy(10.dp)`
 * - 红色 52dp 圆形按钮（PyraRed，30% 红阴影）回首页，仅非首页显示
 * - 金色 54dp 圆形按钮（MythraGold，28% 金阴影）进购物车，右上角红色数字角标
 * - 整组可拖动，默认略高于底部导航
 *
 * 原版挂在 MainActivity 的 Scaffold 上，所以这里也做成全局 overlay。
 */
@Composable
fun FloatingCartDockOverlay(
    entryCount: Int,
    showsHome: Boolean,
    onCartClick: () -> Unit,
    onHomeClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val endPad = with(density) { 18.dp.toPx() }
        val bottomPad = with(density) { 64.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        var dockSize by remember { mutableStateOf(IntSize.Zero) }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(end = 18.dp, bottom = 64.dp)
                .onSizeChanged { dockSize = it }
                .pointerInput(maxW, maxH) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val minX = -(maxW - dockSize.width - endPad).coerceAtLeast(0f)
                        val minY = -(maxH - dockSize.height - bottomPad).coerceAtLeast(0f)
                        offsetX = (offsetX + drag.x).coerceIn(minX, 0f)
                        offsetY = (offsetY + drag.y).coerceIn(minY, 0f)
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (showsHome) {
                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(10.dp, CircleShape, ambientColor = Palette.Core.copy(alpha = 0.3f), spotColor = Palette.Core.copy(alpha = 0.3f))
                        .background(Palette.Core, CircleShape),
                ) {
                    Icon(Icons.Rounded.Dashboard, contentDescription = "返回首页", tint = androidx.compose.ui.graphics.Color.White)
                }
            }

            Box(Modifier.size(54.dp)) {
                IconButton(
                    onClick = onCartClick,
                    modifier = Modifier
                        .matchParentSize()
                        .shadow(10.dp, CircleShape, ambientColor = Palette.Core.copy(alpha = 0.18f), spotColor = Palette.Core.copy(alpha = 0.18f))
                        .background(Palette.SurfaceHigh, CircleShape),
                ) {
                    Icon(
                        Icons.Rounded.ShoppingCart,
                        contentDescription = if (entryCount == 0) "购物车" else "购物车，$entryCount 个目标",
                        tint = Palette.Core,
                    )
                }
                if (entryCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(18.dp),
                        shape = CircleShape,
                        color = Palette.Core,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (entryCount > 99) "99+" else "$entryCount",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
