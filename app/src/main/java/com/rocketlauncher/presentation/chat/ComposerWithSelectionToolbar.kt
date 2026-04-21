package com.rocketlauncher.presentation.chat

import android.content.Context
import com.rocketlauncher.R
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

data class ComposerLinkDialogState(
    val replaceStart: Int,
    val replaceEnd: Int,
    val initialLabel: String
)

private const val MENU_BOLD = 10001
private const val MENU_ITALIC = 10002
private const val MENU_STRIKE = 10003
private const val MENU_INLINE_CODE = 10004
private const val MENU_CODE_BLOCK = 10005
private const val MENU_LINK = 10006

private class ProgrammaticUpdateGuard {
    var skip: Boolean = false
}

private class ComposerListenerHolder {
    lateinit var guard: ProgrammaticUpdateGuard
    var onComposerChange: (TextFieldValue) -> Unit = {}
    var onRequestLinkDialog: (ComposerLinkDialogState) -> Unit = { _ -> }
}

/** EditText с колбэком при смене только выделения (для @mention). */
class ComposerEditText(context: Context) : AppCompatEditText(context) {
    var onSelectionChangedCallback: (() -> Unit)? = null
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedCallback?.invoke()
    }
}

/**
 * Многострочный ввод через EditText: форматирование в системном меню выделения
 * (текстовые пункты рядом с «Копировать», «Вырезать», …).
 */
@Composable
fun ComposerWithSelectionToolbar(
    composer: TextFieldValue,
    onComposerChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onRequestLinkDialog: (ComposerLinkDialogState) -> Unit,
    hintText: String = "",
    trailingContent: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
    val textSizeSp = MaterialTheme.typography.bodyLarge.fontSize.value
    val minH = with(density) { 48.dp.roundToPx() }

    val guard = remember { ProgrammaticUpdateGuard() }
    val holder = remember { ComposerListenerHolder().apply { this.guard = guard } }

    val latestOnChange = rememberUpdatedState(onComposerChange)
    val latestOnLink = rememberUpdatedState(onRequestLinkDialog)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .heightIn(min = 48.dp),
                factory = { ctx ->
                    val et = ComposerEditText(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(0, 0, 0, 0)
                        minimumHeight = minH
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isClickable = true
                        isLongClickable = true
                        isCursorVisible = true
                        showSoftInputOnFocus = true
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        maxLines = 8
                        isVerticalScrollBarEnabled = true
                        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                        tag = holder
                    }
                    val textWatcher = object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            val h = et.tag as? ComposerListenerHolder ?: return
                            if (h.guard.skip) return
                            h.onComposerChange(
                                TextFieldValue(
                                    et.text?.toString().orEmpty(),
                                    TextRange(et.selectionStart, et.selectionEnd)
                                )
                            )
                        }
                    }
                    et.addTextChangedListener(textWatcher)
                    et.onSelectionChangedCallback = sel@{
                        val h = et.tag as? ComposerListenerHolder ?: return@sel
                        if (h.guard.skip) return@sel
                        h.onComposerChange(
                            TextFieldValue(
                                et.text?.toString().orEmpty(),
                                TextRange(et.selectionStart, et.selectionEnd)
                            )
                        )
                    }
                    et
                },
                update = { et ->
                    val h = et.tag as ComposerListenerHolder
                    h.onComposerChange = { latestOnChange.value(it) }
                    h.onRequestLinkDialog = { latestOnLink.value(it) }

                    et.isEnabled = enabled
                    et.hint = hintText
                    et.setTextColor(onSurfaceArgb)
                    et.setHintTextColor(hintArgb)
                    et.minimumHeight = minH
                    et.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

                    et.customSelectionActionModeCallback = buildSelectionCallback(
                        et = et,
                        holder = h
                    )

                    syncComposerToEditText(et, composer, h.guard)
                }
            )
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

private fun syncComposerToEditText(
    et: ComposerEditText,
    composer: TextFieldValue,
    guard: ProgrammaticUpdateGuard
) {
    val text = composer.text
    val viewText = et.text?.toString().orEmpty()
    val ss = composer.selection.start.coerceIn(0, text.length)
    val se = composer.selection.end.coerceIn(0, text.length)
    guard.skip = true
    if (viewText != text) {
        // Текст изменился снаружи (очистка после отправки, вставка @, эмодзи)
        et.setText(text)
        et.setSelection(ss, se)
    } else if (!et.hasFocus()) {
        // Пока поле в фокусе, не перезаписывать selection из отстающего Compose state — иначе курсор
        // «отскакивает» и IME не открывается.
        if (et.selectionStart != ss || et.selectionEnd != se) {
            et.setSelection(ss, se)
        }
    }
    guard.skip = false
}

private fun populateComposerSelectionMenu(menu: Menu, ctx: Context) {
    menu.clear()
    menu.add(0, android.R.id.selectAll, 0, ctx.getString(R.string.action_select_all))
    menu.add(0, android.R.id.cut, 1, ctx.getString(R.string.action_cut))
    menu.add(0, android.R.id.copy, 2, ctx.getString(R.string.action_copy))
    menu.add(0, android.R.id.paste, 3, ctx.getString(R.string.action_paste))
    menu.add(0, MENU_BOLD, 10, ctx.getString(R.string.action_bold))
    menu.add(0, MENU_ITALIC, 11, ctx.getString(R.string.action_italic))
    menu.add(0, MENU_STRIKE, 12, ctx.getString(R.string.action_strikethrough))
    menu.add(0, MENU_INLINE_CODE, 13, ctx.getString(R.string.action_code))
    menu.add(0, MENU_CODE_BLOCK, 14, ctx.getString(R.string.action_code_block))
    menu.add(0, MENU_LINK, 15, ctx.getString(R.string.action_link))
}

private fun buildSelectionCallback(
    et: ComposerEditText,
    holder: ComposerListenerHolder
): ActionMode.Callback = object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        if (menu == null) return false
        populateComposerSelectionMenu(menu, et.context)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.let { populateComposerSelectionMenu(it, et.context) }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        if (item == null) return false
        return when (item.itemId) {
            MENU_BOLD -> applyMarkdownWrap(et, holder, mode, "*", "*", et.context.getString(R.string.composer_placeholder_text))
            MENU_ITALIC -> applyMarkdownWrap(et, holder, mode, "_", "_", et.context.getString(R.string.composer_placeholder_text))
            MENU_STRIKE -> applyMarkdownWrap(et, holder, mode, "~", "~", et.context.getString(R.string.composer_placeholder_text))
            MENU_INLINE_CODE -> applyMarkdownWrap(et, holder, mode, "`", "`", et.context.getString(R.string.composer_placeholder_code))
            MENU_CODE_BLOCK -> applyMarkdownWrap(et, holder, mode, "```\n", "\n```", et.context.getString(R.string.composer_placeholder_code))
            MENU_LINK -> {
                val s = minOf(et.selectionStart, et.selectionEnd)
                val e = maxOf(et.selectionStart, et.selectionEnd)
                val str = et.text?.toString().orEmpty()
                val label = if (s != e && s in str.indices && e <= str.length) str.substring(s, e)
                    else et.context.getString(R.string.composer_placeholder_text)
                mode?.finish()
                holder.onRequestLinkDialog(ComposerLinkDialogState(s, e, label))
                true
            }
            else -> {
                val handled = et.onTextContextMenuItem(item.itemId)
                if (handled) {
                    mode?.finish()
                    holder.onComposerChange(
                        TextFieldValue(
                            et.text?.toString().orEmpty(),
                            TextRange(et.selectionStart, et.selectionEnd)
                        )
                    )
                }
                handled
            }
        }
    }

    override fun onDestroyActionMode(mode: ActionMode?) {}
}

private fun applyMarkdownWrap(
    et: ComposerEditText,
    holder: ComposerListenerHolder,
    mode: ActionMode?,
    prefix: String,
    suffix: String,
    placeholderWhenEmpty: String
): Boolean {
    val current = TextFieldValue(
        et.text?.toString().orEmpty(),
        TextRange(et.selectionStart, et.selectionEnd)
    )
    val updated = wrapMarkdownSelection(current, prefix, suffix, placeholderWhenEmpty)
    holder.guard.skip = true
    et.setText(updated.text)
    et.setSelection(updated.selection.start, updated.selection.end)
    holder.guard.skip = false
    holder.onComposerChange(updated)
    mode?.finish()
    return true
}
