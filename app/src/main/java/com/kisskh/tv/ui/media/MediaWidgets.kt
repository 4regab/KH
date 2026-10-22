package com.kisskh.tv.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.khFocusBorder
import com.kisskh.tv.ui.theme.rememberKhFocusProgress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * A one-line text field with the KH focus ring. The hint is also its spoken label. On a remote, Up and
 * Down always leave the field, so it never traps focus. Left and Right move the cursor, and leave the
 * field only while it is empty. [onAction] runs for the keyboard's action key ([imeAction]).
 * [leading] draws an icon before the text (the search glass).
 */
@Composable
fun KhField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    leading: Int? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = rememberKhFocusProgress(focused)
    val focusManager = LocalFocusManager.current
    val shape = KhTheme.shapes.action
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = KhTheme.typography.field.copy(color = KhColors.TextPrimary),
        cursorBrush = SolidColor(KhColors.TextPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = hint }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    Key.DirectionLeft -> value.isEmpty() && focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> value.isEmpty() && focusManager.moveFocus(FocusDirection.Right)
                    else -> false
                }
            }
            .khFocusBorder({ progress.value }, shape)
            .background(KhColors.Elevated, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Icon(
                        painterResource(leading),
                        contentDescription = null,
                        tint = KhColors.TextSecondary,
                        modifier = Modifier.padding(end = 10.dp).size(20.dp),
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(hint, style = KhTheme.typography.field, color = KhColors.TextSecondary, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}
