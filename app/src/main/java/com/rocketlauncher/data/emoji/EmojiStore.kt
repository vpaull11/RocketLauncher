package com.rocketlauncher.data.emoji

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.vdurmont.emoji.Emoji
import com.vdurmont.emoji.EmojiManager
import com.vdurmont.emoji.EmojiParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmojiStore"

data class EmojiEntry(
    val code: String,
    val unicode: String?,
    val category: String,
    val isCustom: Boolean = false,
    val imageUrl: String? = null
)

/** Одна строка стандартного эмодзи для пикера и текстового поиска по алиасам / тегам / описанию. */
data class EmojiPickerSearchRow(
    val shortcode: String,
    val unicode: String,
    val searchText: String
)

private data class EmojiPickerData(
    val categories: Map<String, List<Triple<String, String, String>>>,
    val searchRows: List<EmojiPickerSearchRow>
)

@Singleton
class EmojiStore @Inject constructor(
    private val apiProvider: ApiProvider
) {
    private val _customEmojis = MutableStateFlow<List<EmojiEntry>>(emptyList())
    val customEmojis: StateFlow<List<EmojiEntry>> = _customEmojis.asStateFlow()

    private val codeToUnicode = HashMap<String, String>(4096)
    @Volatile
    private var standardAliasesLoaded = false

    @Volatile
    private var pickerDataCache: EmojiPickerData? = null

    private fun ensureStandardAliasesLoaded() {
        if (standardAliasesLoaded) return
        synchronized(this) {
            if (standardAliasesLoaded) return
            try {
                for (emoji in EmojiManager.getAll()) {
                    val u = emoji.unicode
                    for (raw in emoji.aliases.filterIsInstance<String>()) {
                        val a = raw.trim().lowercase(Locale.ROOT)
                        if (a.isNotEmpty()) {
                            codeToUnicode[":$a:"] = u
                        }
                    }
                }
                standardAliasesLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "ensureStandardAliasesLoaded: ${e.message}", e)
            }
        }
    }

    suspend fun loadCustomEmojis(serverUrl: String) {
        try {
            ensureStandardAliasesLoaded()
            val api = apiProvider.getApi() ?: return
            val response = api.getCustomEmojis()
            if (response.success && response.emojis != null) {
                val base = serverUrl.trimEnd('/')
                val entries = response.emojis.update.map { dto ->
                    val url = "$base/emoji-custom/${dto.name}.${dto.extension}"
                    val entry = EmojiEntry(
                        code = ":${dto.name}:",
                        unicode = null,
                        category = "custom",
                        isCustom = true,
                        imageUrl = url
                    )
                    dto.aliases.forEach { alias ->
                        codeToUnicode[":$alias:"] = ":${dto.name}:"
                    }
                    entry
                }
                _customEmojis.value = entries
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadCustomEmojis: ${e.message}")
        }
    }

    /**
     * Shortcode / алиас → юникод или другое shortcode (цепочка кастомных эмодзи).
     */
    fun resolve(code: String): String {
        ensureStandardAliasesLoaded()
        return resolveInternal(code, 0)
    }

    private fun resolveInternal(code: String, depth: Int): String {
        if (depth > 12) return code
        val mapped = codeToUnicode[code]
        if (mapped != null) {
            if (mapped == code) {
                return parseShortcodeWithEmojiJava(code)
            }
            if (mapped.startsWith(":") && mapped.endsWith(":")) {
                return resolveInternal(mapped, depth + 1)
            }
            return mapped
        }
        return parseShortcodeWithEmojiJava(code)
    }

    private fun parseShortcodeWithEmojiJava(code: String): String {
        if (!code.startsWith(":") || !code.endsWith(":")) return code
        return try {
            val u = EmojiParser.parseToUnicode(code)
            if (u != code) u else code
        } catch (_: Throwable) {
            code
        }
    }

    fun unicodeForComposerInsert(selectionCode: String): String {
        ensureStandardAliasesLoaded()
        if (isCustomEmoji(selectionCode) || getCustomEmojiUrl(selectionCode) != null) {
            return selectionCode
        }
        val r = resolve(selectionCode)
        if (!r.startsWith(":")) return r
        return try {
            val u = EmojiParser.parseToUnicode(selectionCode)
            if (u != selectionCode) u else selectionCode
        } catch (_: Throwable) {
            selectionCode
        }
    }

    fun unicodeForReactionOrKey(key: String): String {
        ensureStandardAliasesLoaded()
        val t = key.trim()
        if (t.isEmpty()) return t
        val short = when {
            t.startsWith(":") && t.endsWith(":") -> t
            else -> ":${t.trim(':')}:"
        }
        val after = resolve(short)
        if (!after.startsWith(":")) return after
        val parsed = try {
            EmojiParser.parseToUnicode(short)
        } catch (_: Throwable) {
            short
        }
        if (parsed != short) return parsed
        return if (!t.startsWith(":")) t else t
    }

    fun customEmojiUrlForReactionKey(key: String): String? {
        val t = key.trim()
        if (t.startsWith(":") && t.endsWith(":")) {
            getCustomEmojiUrl(t)?.let { return it }
        }
        return getCustomEmojiUrl(":${t.trim(':')}:")
    }

    fun isCustomEmoji(code: String): Boolean {
        return _customEmojis.value.any { it.code == code }
    }

    fun getCustomEmojiUrl(code: String): String? {
        return _customEmojis.value.firstOrNull { it.code == code }?.imageUrl
    }

    /**
     * Полный набор стандартных эмодзи из emoji-java (~1.7k), сгруппированный по вкладкам пикера.
     */
    fun getStandardByCategory(): Map<String, List<Triple<String, String, String>>> {
        return ensurePickerData().categories
    }

    /** Плоский список стандартных эмодзи с полем [EmojiPickerSearchRow.searchText] для фильтрации в UI. */
    fun getStandardEmojiSearchRows(): List<EmojiPickerSearchRow> {
        return ensurePickerData().searchRows
    }

    private fun ensurePickerData(): EmojiPickerData {
        ensureStandardAliasesLoaded()
        pickerDataCache?.let { return it }
        synchronized(this) {
            pickerDataCache?.let { return it }
            val built = buildPickerDataFromEmojiJava()
            pickerDataCache = built
            return built
        }
    }

    private fun buildPickerDataFromEmojiJava(): EmojiPickerData {
        val buckets = linkedMapOf<String, MutableList<Triple<String, String, String>>>()
        PICKER_CATEGORY_ORDER.forEach { buckets[it] = mutableListOf() }
        val searchRows = ArrayList<EmojiPickerSearchRow>(2048)
        for (emoji in EmojiManager.getAll()) {
            val aliases = emoji.aliases.filterIsInstance<String>()
            val alias = aliases.minOrNull() ?: continue
            val code = ":$alias:"
            val unicode = emoji.unicode
            val cat = categorizeForPicker(emoji)
            buckets.getOrPut(cat) { mutableListOf() }
                .add(Triple(code, unicode, cat))
            searchRows.add(
                EmojiPickerSearchRow(
                    shortcode = code,
                    unicode = unicode,
                    searchText = buildEmojiSearchBlob(emoji, code)
                )
            )
        }
        val ordered = linkedMapOf<String, List<Triple<String, String, String>>>()
        for (key in PICKER_CATEGORY_ORDER) {
            val list = buckets[key]?.sortedBy { it.first }?.takeIf { it.isNotEmpty() } ?: continue
            ordered[key] = list
        }
        for ((k, v) in buckets) {
            if (k !in ordered && v.isNotEmpty()) {
                ordered[k] = v.sortedBy { it.first }
            }
        }
        searchRows.sortBy { it.shortcode }
        return EmojiPickerData(ordered, searchRows)
    }

    private fun buildEmojiSearchBlob(emoji: Emoji, shortcode: String): String {
        val parts = linkedSetOf<String>()
        parts.add(shortcode.trim(':', ':').lowercase(Locale.ROOT))
        emoji.aliases.filterIsInstance<String>().forEach {
            val a = it.trim().lowercase(Locale.ROOT)
            if (a.isNotEmpty()) parts.add(a)
        }
        emoji.tags.filterIsInstance<String>().forEach {
            parts.add(it.lowercase(Locale.ROOT))
        }
        emoji.description?.trim()?.lowercase(Locale.ROOT)?.let { d ->
            if (d.isNotEmpty()) parts.add(d)
        }
        return parts.joinToString(" ")
    }

    companion object {
        private val PICKER_CATEGORY_ORDER = listOf(
            "😀 Лица",
            "🧑 Люди",
            "👋 Руки и жесты",
            "🌿 Природа",
            "🍔 Еда",
            "⚽ Активность",
            "✈ Транспорт",
            "📦 Предметы",
            "🔣 Символы",
            "🏳 Флаги",
            "📎 Прочее"
        )

        private val EMOTION_TAGS = setOf(
            "smile", "happy", "joy", "sad", "cry", "laugh", "angry", "wink", "kiss", "love",
            "think", "sick", "cool", "tired", "sleep", "fear", "surprise", "proud", "bored",
            "hug", "haha", "tears", "mad", "scared", "hot", "cold", "dizzy", "woozy", "lying",
            "shush", "explode", "mind", "cowboy", "clown", "joker", "alien", "robot", "ghost",
            "skull", "poop", "devil", "angel", "heart", "eyes", "eye", "mouth", "ear", "nose",
            "tooth", "tongue", "brain", "bone"
        )

        private val PEOPLE_TAGS = setOf(
            "person", "people", "family", "baby", "child", "boy", "girl", "man", "woman",
            "couple", "bride", "santa", "mother", "father", "parent", "older", "adult",
            "teacher", "student", "worker", "detective", "guard", "pilot", "farmer", "cook",
            "doctor", "scientist", "judge", "prince", "princess", "superhero", "elf", "mage",
            "fairy", "vampire", "zombie", "massage", "haircut", "walking", "running", "dancing"
        )

        private val HAND_TAGS = setOf(
            "hand", "hands", "thumb", "fist", "wave", "clap", "pray", "muscle", "victory",
            "finger", "writing", "nail", "selfie", "shake", "ok", "pinch", "palm"
        )

        private val NATURE_TAGS = setOf(
            "animal", "dog", "cat", "bird", "fish", "bug", "nature", "flower", "tree", "plant",
            "weather", "leaf", "herb", "mushroom", "cactus", "earth", "moon", "sun", "star",
            "cloud", "rain", "snow", "ocean", "beach", "volcano", "comet", "fire", "droplet",
            "banana", "apple", "grape", "melon", "corn", "peanut", "chestnut", "blossom", "rose",
            "shell", "feather", "paw", "turkey", "chicken", "rabbit", "mouse", "cow", "pig",
            "horse", "monkey", "panda", "koala", "tiger", "lion", "frog", "whale", "dolphin",
            "shark", "octopus", "crab", "snail", "butterfly", "bee", "spider", "unicorn"
        )

        private val FOOD_TAGS = setOf(
            "food", "drink", "eat", "coffee", "pizza", "fruit", "cake", "beer", "wine", "meal",
            "cooking", "rice", "bread", "cheese", "meat", "egg", "bacon", "hamburger", "fries",
            "popcorn", "salt", "bento", "sushi", "dango", "noodle", "cookie", "candy", "honey",
            "tea", "juice", "milk", "ice", "chocolate", "donut", "croissant", "baguette", "pretzel",
            "salad", "shallow", "stew", "fondue", "tamale", "burrito", "taco", "flatbread"
        )

        private val ACTIVITY_TAGS = setOf(
            "sport", "ball", "game", "trophy", "medal", "music", "run", "swim", "ski", "art",
            "dance", "basketball", "football", "soccer", "baseball", "tennis", "volleyball",
            "rugby", "golf", "bowling", "fishing", "boxing", "skate", "target", "dart", "kite",
            "yarn", "thread", "knot", "sewing", "theater", "circus", "ticket", "artist", "palette"
        )

        private val TRAVEL_TAGS = setOf(
            "car", "plane", "train", "ship", "house", "building", "city", "beach", "mountain",
            "hotel", "church", "shop", "travel", "place", "map", "compass", "bridge", "tower",
            "castle", "statue", "fountain", "camping", "motorcycle", "bus", "truck", "tractor",
            "sled", "rocket", "satellite", "fuel", "parking", "customs", "baggage", "airplane",
            "sailboat", "canoe", "speedboat", "ferry", "motor", "scooter", "railway", "station"
        )

        private val OBJECT_TAGS = setOf(
            "computer", "phone", "watch", "money", "key", "lock", "mail", "book", "pen", "tool",
            "light", "gift", "bell", "camera", "video", "radio", "tv", "printer", "keyboard",
            "battery", "electric", "magnet", "microscope", "telescope", "chart", "calendar",
            "clipboard", "folder", "paperclip", "scissors", "paint", "crayon", "notebook",
            "briefcase", "backpack", "luggage", "soap", "sponge", "broom", "basket", "shopping",
            "bed", "couch", "chair", "toilet", "shower", "bathtub", "door", "window", "mirror",
            "candle", "clock", "hourglass", "alarm", "gem", "ring", "crown", "umbrella", "package"
        )

        private val SYMBOL_TAGS = setOf(
            "symbol", "sign", "mark", "button", "arrow", "number", "clock", "warning", "zodiac",
            "cross", "star", "sparkle", "circle", "square", "triangle", "heart", "peace",
            "infinity", "recycle", "check", "ballot", "x", "exclamation", "question", "percent",
            "currency", "yen", "dollar", "euro", "pound", "bitcoin", "atom", "fleur", "trident",
            "wheel", "khanda", "yin", "orthodox", "star and crescent", "peace symbol", "men",
            "women", "restroom", "baby", "wheelchair", "passport", "id", "pirate", "medical",
            "balance", "alembic", "fleur-de-lis", "part", "intersect", "union", "club", "spade",
            "diamond", "mahjong", "joker", "flower playing cards", "mute", "speaker", "volume"
        )

        private fun categorizeForPicker(emoji: Emoji): String {
            val tags = emoji.tags.filterIsInstance<String>().map { it.lowercase(Locale.ROOT) }.toSet()
            val d = (emoji.description ?: "").lowercase(Locale.ROOT)
            val tagHit: (Set<String>) -> Boolean = { set -> tags.any { t -> set.any { s -> t.contains(s) || s == t } } }

            if ("flag" in tags || d.contains("regional indicator")) return "🏳 Флаги"
            if (tagHit(HAND_TAGS) || d.contains("thumb") || d.contains("finger") || d.contains("fist")) {
                return "👋 Руки и жесты"
            }
            if (tagHit(EMOTION_TAGS) || d.contains(" face") || d.endsWith(" face") ||
                d.startsWith("face ") || d.contains("smil") || d.contains("grin") || d.contains("laugh")
            ) {
                return "😀 Лица"
            }
            if (tagHit(PEOPLE_TAGS) || d.contains(" person") || d.endsWith(" person") ||
                d.contains(" people") || d.contains("man ") || d.contains("woman ") || d.contains("child")
            ) {
                return "🧑 Люди"
            }
            if (tagHit(NATURE_TAGS)) return "🌿 Природа"
            if (tagHit(FOOD_TAGS)) return "🍔 Еда"
            if (tagHit(ACTIVITY_TAGS)) return "⚽ Активность"
            if (tagHit(TRAVEL_TAGS)) return "✈ Транспорт"
            if (tagHit(OBJECT_TAGS)) return "📦 Предметы"
            if (tagHit(SYMBOL_TAGS)) return "🔣 Символы"
            return "📎 Прочее"
        }
    }
}
