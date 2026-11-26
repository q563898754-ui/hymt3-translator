package com.hymt.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hymt.offline.data.Lang
import com.hymt.offline.data.Languages

/**
 * 语言选择器：宫格布局，常用语言在前，支持中英文与代码搜索。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(
    title: String,
    selected: String,
    allowAuto: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered: List<Lang> = remember(query) {
        if (query.isBlank()) {
            Languages.popular + Languages.others
        } else {
            Languages.all.filter {
                it.zh.contains(query, true) ||
                    it.en.contains(query, true) ||
                    it.code.contains(query, true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text("102 种语言", color = TextFaint, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(14.dp))
            SearchBox(query) { query = it }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allowAuto && query.isBlank()) {
                    item(key = "auto") {
                        LangCell(
                            zh = "自动检测",
                            sub = "AUTO",
                            selected = selected == Languages.AUTO
                        ) { onSelect(Languages.AUTO) }
                    }
                }
                items(filtered, key = { it.code }) { lang ->
                    LangCell(
                        zh = lang.zh,
                        sub = lang.code.uppercase(),
                        selected = selected == lang.code
                    ) { onSelect(lang.code) }
                }
            }
            if (filtered.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("没有匹配的语言", color = TextDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, PanelStroke, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text("搜索语言名称或代码", color = TextFaint, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
            cursorBrush = SolidColor(Mint),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LangCell(zh: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Mint.copy(alpha = 0.14f) else Panel)
            .border(1.dp, if (selected) Mint.copy(alpha = 0.45f) else PanelStroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp)
    ) {
        Text(
            text = zh,
            color = if (selected) Mint else TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            color = TextFaint,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
