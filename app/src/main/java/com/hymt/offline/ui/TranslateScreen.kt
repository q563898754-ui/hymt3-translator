package com.hymt.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hymt.offline.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(
    state: UiState,
    onSourceChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    onSwap: () -> Unit,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onMic: () -> Unit,
    onSpeak: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, BgGlow, Bg)))
    ) {
        val wide = maxWidth >= 760.dp
        // safeDrawing 已经包含输入法高度，这里只做读取，不再叠加 imePadding
        val imeVisible = WindowInsets.isImeVisible
        val panelHeight = ((maxHeight - 260.dp) / 2).coerceIn(150.dp, 360.dp)

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = if (wide) 26.dp else 14.dp, vertical = 10.dp)
        ) {
            AppHeader(state, onOpenSettings)
            Spacer(Modifier.height(12.dp))
            LanguageSelectorRow(state, onSwap, onPickSource, onPickTarget)
            Spacer(Modifier.height(12.dp))

            if (wide) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SourcePanel(
                        state = state,
                        modifier = Modifier.weight(1f),
                        onSourceChange = onSourceChange,
                        onMic = onMic,
                        onPaste = {
                            val pasted = clipboard.getText()?.text
                            if (!pasted.isNullOrBlank()) onSourceChange(pasted)
                        },
                        onClear = onClear
                    )
                    ActionColumn(state, onTranslate, onCancel)
                    ResultPanel(
                        state = state,
                        modifier = Modifier.weight(1f),
                        onCopy = {
                            if (state.output.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(state.output))
                            }
                        },
                        onSpeak = onSpeak,
                        onToggleAutoSpeak = onToggleAutoSpeak
                    )
                }
            } else {
                // 键盘弹出时整体可滚动，避免上下两个面板被压扁到看不见
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    SourcePanel(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (imeVisible) 200.dp else panelHeight),
                        onSourceChange = onSourceChange,
                        onMic = onMic,
                        onPaste = {
                            val pasted = clipboard.getText()?.text
                            if (!pasted.isNullOrBlank()) onSourceChange(pasted)
                        },
                        onClear = onClear
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryAction(
                        text = if (state.translating) "停止" else "翻译",
                        enabled = state.translating || state.sourceText.isNotBlank(),
                        busy = state.translating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.translating) onCancel() else onTranslate()
                    }
                    Spacer(Modifier.height(10.dp))
                    ResultPanel(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(panelHeight),
                        onCopy = {
                            if (state.output.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(state.output))
                            }
                        },
                        onSpeak = onSpeak,
                        onToggleAutoSpeak = onToggleAutoSpeak
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePanel(
    state: UiState,
    modifier: Modifier,
    onSourceChange: (String) -> Unit,
    onMic: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Panel(modifier) {
        SectionLabel("原文", "${state.sourceText.length}/${com.hymt.offline.llm.PromptBuilder.MAX_SOURCE_CHARS}")
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            BasicTextField(
                value = state.sourceText,
                onValueChange = onSourceChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(color = TextMain, fontSize = 17.sp, lineHeight = 26.sp),
                cursorBrush = SolidColor(Mint),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (state.sourceText.isEmpty()) {
                            Text(
                                text = if (state.listening) "正在聆听…" else "输入或粘贴要翻译的内容",
                                color = TextFaint,
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                }
            )
            if (state.listening && state.partial.isNotEmpty()) {
                Text(
                    text = state.partial,
                    color = Amber,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(
                icon = Icons.Filled.Mic,
                text = if (state.listening) "停止" else "说话",
                accent = state.listening,
                onClick = onMic
            )
            ActionChip(icon = Icons.Filled.ContentPaste, text = "粘贴", onClick = onPaste)
            Spacer(Modifier.weight(1f))
            ActionChip(
                icon = Icons.Filled.DeleteOutline,
                text = "清空",
                enabled = state.sourceText.isNotEmpty(),
                onClick = onClear
            )
        }
    }
}

@Composable
private fun ResultPanel(
    state: UiState,
    modifier: Modifier,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onToggleAutoSpeak: () -> Unit
) {
    val scroll = rememberScrollState()
    Panel(modifier) {
        SectionLabel("译文", state.stats)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
        ) {
            if (state.output.isEmpty()) {
                Text(
                    text = if (state.translating) "正在生成…" else "译文会逐字出现在这里",
                    color = TextFaint,
                    fontSize = 16.sp
                )
            } else {
                Text(
                    text = state.output + if (state.translating) "▍" else "",
                    color = TextMain,
                    fontSize = 17.sp,
                    lineHeight = 27.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(
                icon = Icons.Filled.ContentCopy,
                text = "复制",
                enabled = state.output.isNotEmpty(),
                onClick = onCopy
            )
            ActionChip(
                icon = Icons.Filled.VolumeUp,
                text = "朗读",
                enabled = state.output.isNotEmpty(),
                onClick = onSpeak
            )
            ActionChip(
                icon = if (state.autoSpeak) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                text = if (state.autoSpeak) "自动朗读" else "自动关",
                accent = state.autoSpeak,
                onClick = onToggleAutoSpeak
            )
            Spacer(Modifier.weight(1f))
            if (state.translating) BusyDots()
        }
    }
}

@Composable
private fun ActionColumn(state: UiState, onTranslate: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .width(136.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryAction(
            text = if (state.translating) "停止" else "翻译",
            enabled = state.translating || state.sourceText.isNotBlank(),
            busy = state.translating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.translating) onCancel() else onTranslate()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "全程离线\n无需网络",
            color = TextFaint,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}
