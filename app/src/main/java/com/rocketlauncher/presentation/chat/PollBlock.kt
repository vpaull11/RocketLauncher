package com.rocketlauncher.presentation.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketlauncher.R
import org.json.JSONArray
import org.json.JSONObject

// ─── Модель данных ─────────────────────────────────────────────────────────────

/**
 * Данные одного варианта опроса, извлечённые из UIKit-блоков.
 */
private data class PollOption(
    val label: String,
    val resultText: String,
    val progress: Float,            // 0f..1f от сервера; -1f если нет данных
    val voteCount: Int,             // кол-во голосов; -1 если нет данных
    val isSelectedByServer: Boolean,// true если сервер пометил вариант как выбранный текущим юзером
    /** Данные для ui.blockAction */
    val appId: String,
    val blockId: String,
    val actionId: String,
    val value: String
)

// ─── Парсинг ────────────────────────────────────────────────────────────────────

/**
 * Парсит UIKit-блоки из [blocksJson] в список [PollOption].
 *
 * Структура блоков от Poll App:
 *   blocks[0] → section: вопрос (accessory = overflow "Finish poll")
 *   blocks[1] → divider
 *   blocks[2] → section: вариант 1 (accessory = button "Vote", value="0")
 *   blocks[3] → context: результат варианта 1 ("`████` 60.00% (3)")
 *   blocks[4] → section: вариант 2 ...
 *   blocks[5] → context: результат варианта 2 ...
 *   blocks[N] → context: итог "3 votes - username1, username2"
 */
private fun parsePollBlocks(blocksJson: String): Pair<String, List<PollOption>> {
    return try {
        val array = JSONArray(blocksJson)
        var question = ""
        var globalAppId = ""

        data class RawSection(val obj: JSONObject, val idx: Int)
        data class RawContext(val obj: JSONObject, val idx: Int)

        val sections = mutableListOf<RawSection>()
        val contexts  = mutableListOf<RawContext>()

        for (i in 0 until array.length()) {
            val block = array.optJSONObject(i) ?: continue
            val appId = block.optString("appId").takeIf { it.isNotBlank() }
            if (appId != null && globalAppId.isEmpty()) globalAppId = appId
            when (block.optString("type")) {
                "section" -> sections.add(RawSection(block, i))
                "context" -> contexts.add(RawContext(block, i))
            }
        }

        val questionSection = sections.firstOrNull { sec ->
            val accType = sec.obj.optJSONObject("accessory")?.optString("type") ?: ""
            accType == "overflow" || accType == ""
        }
        question = questionSection?.let { extractText(it.obj.opt("text")) } ?: ""

        val optionSections = sections.filter { sec ->
            val acc = sec.obj.optJSONObject("accessory")
            acc?.optString("type") == "button" && acc.optString("actionId") == "vote"
        }

        // Контексты, которые уже «взяты» — чтобы не присвоить один и тот же двум вариантам
        val takenContextIdxs = mutableSetOf<Int>()

        val options = optionSections.map { sec ->
            val acc      = sec.obj.optJSONObject("accessory")!!
            val label    = extractText(sec.obj.opt("text"))
            val blockId  = sec.obj.optString("blockId").takeIf { it.isNotBlank() }
                ?: acc.optString("blockId", "")
            val actionId = acc.optString("actionId").takeIf { it.isNotBlank() } ?: "vote"
            val value    = acc.optString("value").takeIf { it.isNotBlank() } ?: "0"
            val appId    = sec.obj.optString("appId").takeIf { it.isNotBlank() }
                ?: acc.optString("appId").takeIf { it.isNotBlank() }
                ?: globalAppId

            // Следующий контекст-блок, ещё не занятый другим вариантом
            val nextContext = contexts.firstOrNull { it.idx > sec.idx && it.idx !in takenContextIdxs }
            if (nextContext != null) takenContextIdxs += nextContext.idx
            val contextText = nextContext?.let { extractContextText(it.obj) } ?: ""
            val (progress, voteCount) = parseProgressFromContext(contextText)

            // Сервер помечает выбранный вариант дополнительным элементом в context (check emoji или «✓»)
            val isSelectedByServer = contextText.contains("✓") || contextText.contains("✅")
                    || acc.optString("style") == "primary"

            PollOption(
                label              = label,
                resultText         = contextText,
                progress           = progress,
                voteCount          = voteCount,
                isSelectedByServer = isSelectedByServer,
                appId              = appId,
                blockId            = blockId,
                actionId           = actionId,
                value              = value
            )
        }

        question to options
    } catch (_: Exception) {
        "" to emptyList()
    }
}

private fun extractText(raw: Any?): String = when (raw) {
    is JSONObject -> raw.optString("text", "")
    is String     -> raw
    else          -> ""
}

private fun extractContextText(contextBlock: JSONObject): String {
    val elements = contextBlock.optJSONArray("elements") ?: return ""
    for (i in 0 until elements.length()) {
        val el = elements.optJSONObject(i) ?: continue
        val text = el.optString("text").takeIf { it.isNotBlank() } ?: continue
        return text
    }
    return ""
}

private fun parseProgressFromContext(text: String): Pair<Float, Int> {
    val percentMatch = Regex("""(\d+(?:\.\d+)?)\s*%""").find(text)
    val percent = percentMatch?.groupValues?.get(1)?.toFloatOrNull()
        ?.div(100f)?.coerceIn(0f, 1f) ?: -1f
    val countMatch = Regex("""\((\d+)\)""").find(text)
    val count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
    return percent to count
}

// ─── Главный Composable ────────────────────────────────────────────────────────

/**
 * Compose-компонент для отображения опроса.
 *
 * Три визуальных режима:
 *  1. **SELECTING** — чекбоксы, кнопка «Проголосовать» (disabled пока ничего не выбрано)
 *  2. **SELECTING + выбрано** — чекбокс отмечен, кнопка «Проголосовать» активна
 *  3. **RESULTS** — % + прогресс-бар + счётчик, кнопки «Посмотреть голоса» / «Переголосовать»
 *
 * @param blocksJson   JSON из MessageEntity.blocksJson
 * @param onVote       коллбэк отправки голоса
 * @param votedValues  Set<"msgId:value"> — проголосованные варианты в текущей сессии
 */
@Composable
fun PollBlock(
    blocksJson: String,
    onVote: (appId: String, blockId: String, actionId: String, value: String) -> Unit,
    votedValues: Set<String> = emptySet()
) {
    val (question, options) = remember(blocksJson) { parsePollBlocks(blocksJson) }

    if (question.isEmpty() && options.isEmpty()) {
        Text(
            text = "[Poll]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
        return
    }

    // Обогащаем варианты флагом локального голоса
    val enriched = options.map { opt ->
        opt.copy(isSelectedByServer = opt.isSelectedByServer || opt.value in votedValues.map { it.substringAfter(":") })
    }

    val hasServerResults = enriched.any { it.progress >= 0f }
    val hasLocalVote     = enriched.any { it.isSelectedByServer }
    val showingResults   = hasServerResults || hasLocalVote

    // Локально выбранный вариант (до нажатия «Проголосовать»)
    var selectedValue by remember(blocksJson) { mutableStateOf<String?>(null) }
    // «Переголосовать» — возвращает пользователя в режим выбора
    var isRevoting by remember { mutableStateOf(false) }

    val inResultsMode = showingResults && !isRevoting

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .animateContentSize(animationSpec = tween(300)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Заголовок ──
            PollHeader(question = question)

            Spacer(Modifier.height(12.dp))

            // ── Варианты ──
            if (inResultsMode) {
                PollResultsView(options = enriched)
            } else {
                PollSelectingView(
                    options       = enriched,
                    selectedValue = selectedValue,
                    onSelect      = { selectedValue = it }
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // ── Кнопки внизу ──
            if (inResultsMode) {
                PollResultsFooter(
                    totalVotes = enriched.maxOfOrNull { it.voteCount.coerceAtLeast(0) }
                        ?.let { enriched.sumOf { o -> o.voteCount.coerceAtLeast(0) } } ?: 0,
                    onRevote = {
                        selectedValue = null
                        isRevoting = true
                    }
                )
            } else {
                PollSubmitButton(
                    enabled = selectedValue != null,
                    onClick = {
                        val sel = selectedValue ?: return@PollSubmitButton
                        val opt = enriched.firstOrNull { it.value == sel } ?: return@PollSubmitButton
                        isRevoting = false
                        onVote(opt.appId, opt.blockId, opt.actionId, opt.value)
                    }
                )
            }
        }
    }
}

// ─── Подкомпоненты ─────────────────────────────────────────────────────────────

@Composable
private fun PollHeader(question: String) {
    Column {
        Text(
            text = question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.poll_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Режим выбора: чекбоксы ─────────────────────────────────────────────────────

@Composable
private fun PollSelectingView(
    options: List<PollOption>,
    selectedValue: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { opt ->
            val isChecked = opt.value == selectedValue
            PollCheckboxRow(
                label     = opt.label,
                isChecked = isChecked,
                onClick   = { onSelect(opt.value) }
            )
        }
    }
}

@Composable
private fun PollCheckboxRow(
    label: String,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (isChecked) primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isChecked) primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Режим результатов: прогресс-бары ──────────────────────────────────────────

@Composable
private fun PollResultsView(options: List<PollOption>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            PollResultRow(option = opt)
        }
    }
}

@Composable
private fun PollResultRow(option: PollOption) {
    val primary  = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted    = MaterialTheme.colorScheme.onSurfaceVariant

    val progressPct = if (option.progress >= 0f) option.progress else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressPct,
        animationSpec = tween(600),
        label = "pollProgress"
    )
    val pctText = if (option.progress >= 0f) "${(option.progress * 100).toInt()}%" else "0%"
    val isVoted = option.isSelectedByServer

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Строка: [%] [Текст варианта] [✓] [N]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Процент — фиксированная ширина
            Text(
                text = pctText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isVoted) primary else muted,
                fontWeight = if (isVoted) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(42.dp)
            )

            // Текст варианта
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isVoted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isVoted) primary else onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.width(8.dp))

            // Галочка у выбранного варианта
            if (isVoted) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            // Счётчик голосов
            if (option.voteCount >= 0) {
                Text(
                    text = "${option.voteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isVoted) primary else muted,
                    fontSize = 12.sp
                )
            }
        }

        // Прогресс-бар на всю ширину
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isVoted) 5.dp else 3.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isVoted) primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    }
}

// ── Нижняя панель режима результатов ──────────────────────────────────────────

@Composable
private fun PollResultsFooter(
    totalVotes: Int,
    onRevote: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // «Посмотреть голоса (N)»
        TextButton(
            onClick = { /* TODO: показать список проголосовавших */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.HowToVote,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.poll_view_votes, totalVotes),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // «Переголосовать»
        TextButton(
            onClick = onRevote,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.poll_revote_button),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Кнопка «Проголосовать» ────────────────────────────────────────────────────

@Composable
private fun PollSubmitButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledContentColor   = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text  = stringResource(R.string.poll_submit_button),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
