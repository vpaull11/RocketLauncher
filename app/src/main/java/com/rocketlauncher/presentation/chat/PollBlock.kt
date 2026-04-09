package com.rocketlauncher.presentation.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Данные одного варианта опроса, извлечённые из UIKit-блоков.
 */
private data class PollOption(
    val label: String,
    val resultText: String,        // текст с результатом от сервера, напр. "Kotlin [3] 60%"
    val progress: Float,           // 0f..1f, парсится из текста (если не удалось — -1f)
    val voteCount: Int,            // кол-во голосов (если распарсилось), иначе -1
    /** Данные для ui.blockAction */
    val appId: String,
    val blockId: String,
    val actionId: String,
    val value: String
)

/**
 * Парсит UIKit-блоки из [blocksJson] в список [PollOption].
 *
 * Реальная структура блоков от Poll App (версии с context-блоками):
 *   blocks[0] → section: вопрос (accessory = overflow "Finish poll")
 *   blocks[1] → divider
 *   blocks[2] → section: вариант 1 (accessory = button "Vote", value="0", blockId=...)
 *   blocks[3] → context: результат варианта 1 (mrkdwn "`████` 60.00% (3)")
 *   blocks[4] → section: вариант 2 ...
 *   blocks[5] → context: результат варианта 2 ...
 *   blocks[N] → context: итог "3 votes - username1, username2"
 */
private fun parsePollBlocks(blocksJson: String): Pair<String, List<PollOption>> {
    return try {
        val array = JSONArray(blocksJson)
        var question = ""
        // appId берём из блока верхнего уровня
        var globalAppId = ""

        // Сначала собираем все блоки по типу
        data class RawSection(val obj: JSONObject, val idx: Int)
        data class RawContext(val obj: JSONObject, val idx: Int)

        val sections = mutableListOf<RawSection>()
        val contexts = mutableListOf<RawContext>()

        for (i in 0 until array.length()) {
            val block = array.optJSONObject(i) ?: continue
            val appId = block.optString("appId").takeIf { it.isNotBlank() }
            if (appId != null && globalAppId.isEmpty()) globalAppId = appId
            when (block.optString("type")) {
                "section" -> sections.add(RawSection(block, i))
                "context" -> contexts.add(RawContext(block, i))
            }
        }

        // Первая секция — вопрос (accessory = overflow или нет кнопки vote)
        val questionSection = sections.firstOrNull { sec ->
            val accType = sec.obj.optJSONObject("accessory")?.optString("type") ?: ""
            accType == "overflow" || accType == ""
        }
        question = questionSection?.let { extractText(it.obj.opt("text")) } ?: ""

        // Секции с вариантами — все у кого accessory = button с actionId "vote"
        val optionSections = sections.filter { sec ->
            val acc = sec.obj.optJSONObject("accessory")
            acc?.optString("type") == "button" && acc.optString("actionId") == "vote"
        }

        // Для каждой секции-варианта ищем следующий по индексу context блок
        val options = optionSections.map { sec ->
            val acc = sec.obj.optJSONObject("accessory")!!
            val label    = extractText(sec.obj.opt("text"))
            val blockId  = sec.obj.optString("blockId").takeIf { it.isNotBlank() }
                ?: acc.optString("blockId", "")
            val actionId = acc.optString("actionId").takeIf { it.isNotBlank() } ?: "vote"
            val value    = acc.optString("value").takeIf { it.isNotBlank() } ?: "0"
            val appId    = sec.obj.optString("appId").takeIf { it.isNotBlank() }
                ?: acc.optString("appId").takeIf { it.isNotBlank() }
                ?: globalAppId

            // Ищем context блок который следует за этим section
            val nextContext = contexts.firstOrNull { it.idx > sec.idx }
            val contextText = nextContext?.let { extractContextText(it.obj) } ?: ""
            val (progress, voteCount) = parseProgressFromContext(contextText)

            PollOption(
                label     = label,
                resultText = contextText,
                progress  = progress,
                voteCount = voteCount,
                appId     = appId,
                blockId   = blockId,
                actionId  = actionId,
                value     = value
            )
        }

        question to options
    } catch (_: Exception) {
        "" to emptyList()
    }
}

/**
 * Достаёт текст из поля UIKit — может быть объектом `{type, text}` или строкой.
 */
private fun extractText(raw: Any?): String = when (raw) {
    is JSONObject -> raw.optString("text", "")
    is String     -> raw
    else          -> ""
}

/**
 * Извлекает текст из context-блока (elements[0].text для mrkdwn).
 * Пример: "`████████████████████` 100.00% (1)"
 */
private fun extractContextText(contextBlock: JSONObject): String {
    val elements = contextBlock.optJSONArray("elements") ?: return ""
    for (i in 0 until elements.length()) {
        val el = elements.optJSONObject(i) ?: continue
        val text = el.optString("text").takeIf { it.isNotBlank() } ?: continue
        // Убираем backtick-блоки с прогресс-барами для чистого чтения процентов
        return text
    }
    return ""
}

/**
 * Парсит процент и кол-во голосов из context-текста.
 * Форматы: "`████` 60.00% (3)"  или  "`   ` 0.00% (0)"
 */
private fun parseProgressFromContext(text: String): Pair<Float, Int> {
    // Процент: "60.00%"
    val percentMatch = Regex("""(\d+(?:\.\d+)?)\s*%""").find(text)
    val percent = percentMatch?.groupValues?.get(1)?.toFloatOrNull()
        ?.div(100f)?.coerceIn(0f, 1f) ?: -1f
    // Кол-во в скобках: "(3)"
    val countMatch = Regex("""\((\d+)\)""").find(text)
    val count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
    return percent to count
}

/** Устаревший — оставлен для совместимости, не используется. */
private fun parseProgressFromText(text: String): Pair<Float, Int> = parseProgressFromContext(text)



/**
 * Compose-компонент для отображения опроса из UIKit-блоков.
 *
 * @param blocksJson  JSON-строка из [MessageEntity.blocksJson]
 * @param onVote      колбэк при нажатии на кнопку голосования
 * @param votedValues множество значений, уже проголосованных пользователем (ключ — value кнопки)
 */
@Composable
fun PollBlock(
    blocksJson: String,
    onVote: (appId: String, blockId: String, actionId: String, value: String) -> Unit,
    votedValues: Set<String> = emptySet()
) {
    val (question, options) = remember(blocksJson) { parsePollBlocks(blocksJson) }

    if (question.isEmpty() && options.isEmpty()) {
        // Не удалось распарсить — показываем заглушку
        Text(
            text = "[Poll]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
        return
    }

    val hasVoted = options.any { it.value in votedValues }

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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Заголовок опроса
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { opt ->
                        PollOptionRow(
                            option = opt,
                            isVoted = opt.value in votedValues,
                            // Показываем результаты если сервер прислал данные (progress >= 0)
                            // ИЛИ если пользователь уже проголосовал локально
                            showResults = opt.progress >= 0f || hasVoted,
                            onVote = { onVote(opt.appId, opt.blockId, opt.actionId, opt.value) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    option: PollOption,
    isVoted: Boolean,
    showResults: Boolean,
    onVote: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isVoted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isVoted) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (option.voteCount >= 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (option.progress >= 0) "${(option.progress * 100).toInt()}%"
                           else "${option.voteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (option.progress >= 0f) {
            LinearProgressIndicator(
                progress = option.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isVoted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }

        // Кнопка голосования — всегда доступна
        if (true) {
            Button(
                onClick = onVote,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    Icons.Default.HowToVote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Голосовать",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
