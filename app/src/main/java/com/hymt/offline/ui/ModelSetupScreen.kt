package com.hymt.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hymt.offline.ModelStatus
import java.util.Locale

/**
 * 模型准备页：下载 / 导入 / 加载进度。
 */
@Composable
fun ModelSetupScreen(
    status: ModelStatus,
    freeSpaceGb: Double,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, BgGlow)))
    ) {
        // 顶部光晕
        Box(
            Modifier
                .size(460.dp)
                .offset(x = 90.dp, y = (-260).dp)
                .background(
                    Brush.radialGradient(listOf(Mint.copy(alpha = 0.13f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GridMark()
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "ZRC",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "本地翻译引擎",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (onClose != null) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Panel)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = TextDim,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(44.dp))

            Text(
                text = "把翻译模型\n装进口袋",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "腾讯混元 Hy-MT3 · 102 种语言 · 全离线推理",
                color = TextDim,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.height(28.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .clip(PanelShape)
                    .background(Brush.verticalGradient(listOf(PanelTop, Panel)))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hy-MT3-1.8B · Q4_K_M", color = TextMain, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("GGUF 1.35 GB · Apache-2.0", color = TextFaint, fontSize = 11.sp)
                    }
                    StatusDot(active = status is ModelStatus.Ready)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = when (status) {
                            is ModelStatus.Ready -> "已就绪"
                            is ModelStatus.Loading -> "加载中"
                            is ModelStatus.Downloading, is ModelStatus.Copying -> "准备中"
                            is ModelStatus.Failed -> "异常"
                            ModelStatus.Missing -> "未安装"
                        },
                        color = if (status is ModelStatus.Failed) Danger else TextDim,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (status) {
                    is ModelStatus.Downloading -> ProgressBlock(
                        title = "正在下载模型",
                        done = status.done,
                        total = status.total,
                        hint = "支持断点续传，可随时退出"
                    )

                    is ModelStatus.Copying -> ProgressBlock(
                        title = "正在导入本地模型",
                        done = status.done,
                        total = status.total,
                        hint = "请保持应用在前台"
                    )

                    is ModelStatus.Loading -> {
                        Text("正在把权重加载到内存…", color = TextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        BusyDots()
                    }

                    is ModelStatus.Failed -> {
                        Text(status.message, color = Danger, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionChip(Icons.Filled.Refresh, "重新下载", accent = true) { onDownload() }
                            ActionChip(Icons.Filled.FolderOpen, "导入文件") { onImport() }
                        }
                    }

                    is ModelStatus.Ready -> {
                        Text("模型已加载，返回即可开始翻译。", color = TextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        ActionChip(Icons.Filled.Delete, "删除本地模型") { onDelete() }
                    }

                    ModelStatus.Missing -> {
                        Text(
                            text = "首次使用需要准备模型文件（应用不内置，避免 APK 过大）。之后全程离线，不产生任何网络请求。",
                            color = TextDim,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryAction(
                            text = "下载模型 1.35 GB",
                            enabled = true,
                            busy = false,
                            modifier = Modifier.fillMaxWidth()
                        ) { onDownload() }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onImport)
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "已有 GGUF 文件？从本地导入",
                                color = Mint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.size(6.dp))
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Mint,
                                modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "剩余存储 " + String.format(Locale.US, "%.1f GB", freeSpaceGb) +
                                " · 建议使用 Wi-Fi 下载",
                            color = TextFaint,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "模型由腾讯混元开源（Apache-2.0），推理基于 llama.cpp",
                color = TextFaint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProgressBlock(title: String, done: Long, total: Long, hint: String) {
    val fraction = if (total > 0) (done.toFloat() / total.toFloat()) else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextMain, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (total > 0) {
                String.format(Locale.US, "%.0f%%", fraction * 100)
            } else {
                String.format(Locale.US, "%.2f GB", done / 1_000_000_000.0)
            },
            color = Mint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(Modifier.height(10.dp))
    ThinProgress(fraction)
    Spacer(Modifier.height(8.dp))
    Text(hint, color = TextFaint, fontSize = 11.sp)
}

/** 2×2 宫格品牌标记 */
@Composable
fun GridMark(size: Int = 26) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { index ->
                    Box(
                        Modifier
                            .size((size / 2 - 2).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index == 0) Mint else Mint.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}
