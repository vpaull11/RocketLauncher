package com.rocketlauncher.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Оборачивает выделение в markdown ([MessageFormatter]): *жирный*, _курсив_, ~зачёркнутый~, `код`, блок ```.
 * При пустом выделении вставляет плейсхолдер и выделяет его.
 */
fun wrapMarkdownSelection(
    composer: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholderWhenEmpty: String = "текст"
): TextFieldValue {
    val text = composer.text
    val sel = composer.selection
    val start = minOf(sel.start, sel.end).coerceIn(0, text.length)
    val end = maxOf(sel.start, sel.end).coerceIn(0, text.length)
    val inner = if (start != end) text.substring(start, end) else placeholderWhenEmpty
    val newText = text.take(start) + prefix + inner + suffix + text.drop(end)
    return if (start != end) {
        val newPos = (start + prefix.length + inner.length + suffix.length).coerceIn(0, newText.length)
        TextFieldValue(newText, TextRange(newPos))
    } else {
        val selStart = start + prefix.length
        val selEnd = selStart + inner.length
        TextFieldValue(newText, TextRange(selStart, selEnd))
    }
}

fun insertMarkdownLink(
    composer: TextFieldValue,
    linkText: String,
    url: String,
    replaceStart: Int,
    replaceEnd: Int
): TextFieldValue {
    val text = composer.text
    val s = replaceStart.coerceIn(0, text.length)
    val e = replaceEnd.coerceIn(s, text.length)
    val md = "[$linkText]($url)"
    val newText = text.take(s) + md + text.drop(e)
    val newPos = (s + md.length).coerceIn(0, newText.length)
    return TextFieldValue(newText, TextRange(newPos))
}

@Composable
fun ComposerFormattingToolbar(
    enabled: Boolean,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onInlineCode: () -> Unit,
    onCodeBlock: () -> Unit,
    onLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.primary
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 2.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(onClick = onBold, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.FormatBold, contentDescription = "Жирный", tint = tint, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onItalic, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.FormatItalic, contentDescription = "Курсив", tint = tint, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onStrike, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.StrikethroughS, contentDescription = "Зачёркнутый", tint = tint, modifier = Modifier.size(22.dp))
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(22.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        IconButton(onClick = onInlineCode, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Code, contentDescription = "Код в строке", tint = tint, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onCodeBlock, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.DataObject, contentDescription = "Блок кода", tint = tint, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onLink, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Link, contentDescription = "Ссылка", tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
