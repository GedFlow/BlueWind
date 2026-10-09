package com.example.bluewind.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 화면 가장자리 여백. 모든 화면이 같은 값을 써서 좌상단 버튼 위치가 같게 한다. */
val SCREEN_PADDING = 8.dp

/** 좌상단 버튼 크기 (메인: 키보드, 키보드·프레젠테이션: 닫기) */
val CORNER_BUTTON_WIDTH = 96.dp
val CORNER_BUTTON_HEIGHT = 44.dp

@Composable
fun PanelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
    labelStyle: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) color else color.copy(alpha = 0.4f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = labelStyle, textAlign = TextAlign.Center, maxLines = 1)
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

/** 좌상단 고정 버튼. 화면마다 같은 자리, 같은 크기로 둔다. */
@Composable
fun CornerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PanelButton(
        label = label,
        onClick = onClick,
        modifier = modifier.size(CORNER_BUTTON_WIDTH, CORNER_BUTTON_HEIGHT),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
