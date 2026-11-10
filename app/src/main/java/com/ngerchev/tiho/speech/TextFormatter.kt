package com.ngerchev.tiho.speech

/** Small text helpers: spoken punctuation ("запетая" → ",") and joining phrases into a draft. */
object TextFormatter {

    private fun rule(words: String, out: String): Pair<Regex, String> =
        Regex(
            "\\s*(?<![\\p{L}\\p{N}])(?:$words)(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        ) to Regex.escapeReplacement(out)

    // Order matters: longer phrases first ("точка и запетая" before "точка").
    private val BG = listOf(
        rule("точка и запетая", ";"),
        rule("въпросителен знак|въпросителна", "?"),
        rule("удивителен знак|удивителна", "!"),
        rule("запетая", ","),
        rule("двоеточие", ":"),
        rule("нов ред|нов абзац", "\n"),
        rule("точка", ".")
    )

    private val EN = listOf(
        rule("question mark", "?"),
        rule("exclamation mark|exclamation point", "!"),
        rule("semicolon", ";"),
        rule("comma", ","),
        rule("colon", ":"),
        rule("new line|new paragraph", "\n"),
        rule("full stop|period", ".")
    )

    private val SPACE_AFTER_NEWLINE = Regex("\\n[ \\t]+")
    private val SENTENCE_START = Regex("([.!?]\\s+|\\n)(\\p{Ll})")

    fun applySpokenPunctuation(text: String, languageTag: String): String {
        var out = text
        val rules = if (languageTag.startsWith("bg")) BG else EN
        for ((regex, replacement) in rules) {
            out = regex.replace(out, replacement)
        }
        out = SPACE_AFTER_NEWLINE.replace(out, "\n")
        out = SENTENCE_START.replace(out) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
        return out
    }

    // ---- automatic punctuation ---------------------------------------------------------
    // The phone's recognizer returns Bulgarian almost without punctuation, so the usual
    // rules are applied here: capital first letter, comma before common conjunctions,
    // and a full stop or question mark at the end. A heuristic, not a grammar checker.

    private val BG_PAIRS = setOf(
        "така че", "въпреки че", "макар че", "освен че", "след като",
        "преди да", "без да", "вместо да", "тъй като", "за да"
    )
    private val BG_SINGLE = setOf(
        "че", "защото", "но", "обаче", "ама", "ако", "когато", "докато", "където", "понеже", "а"
    )
    private val BG_RELATIVE = setOf("който", "която", "което", "които", "когото")
    private val BG_PREPOSITIONS = setOf(
        "с", "със", "в", "във", "на", "за", "от", "до", "при", "по", "към", "без", "през",
        "след", "пред", "над", "под", "между", "върху", "срещу", "чрез", "заради", "според"
    )
    private val BG_BLOCKERS = setOf(
        "и", "а", "но", "или", "само", "даже", "дори", "едва", "чак", "както", "ами"
    )
    private val BG_GREETINGS = setOf(
        "здравей", "здравейте", "привет", "благодаря", "извинявай", "извинявайте",
        "добър ден", "добро утро", "добър вечер", "лека нощ"
    )
    private val BG_QUESTION_WORDS = setOf(
        "кой", "коя", "кое", "кои", "кого", "какво", "какъв", "каква", "какви", "къде",
        "кога", "защо", "как", "колко", "чий", "чия", "чие", "чии", "дали", "нали",
        "накъде", "откъде", "докога", "откога"
    )

    private val EN_SINGLE = setOf("but", "although", "though", "whereas")
    private val EN_GREETINGS = setOf(
        "hello", "hi", "hey", "thanks", "sorry", "okay", "ok", "well", "yes", "no",
        "thank you", "good morning", "good afternoon", "good evening"
    )
    private val EN_QUESTION_WORDS = setOf(
        "who", "what", "where", "when", "why", "how", "which", "whose", "is", "are", "am",
        "was", "were", "do", "does", "did", "can", "could", "will", "would", "should",
        "shall", "have", "has", "may", "might"
    )

    private fun bare(word: String): String = word.lowercase().trim { !it.isLetterOrDigit() }

    private fun endsWithMark(word: String): Boolean =
        word.isNotEmpty() && word.last() in ",.;:!?…"

    private fun addCommas(text: String, bulgarian: Boolean): String {
        val words = text.split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (words.size < 2) return text
        val pairs = if (bulgarian) BG_PAIRS else emptySet()
        val single = if (bulgarian) BG_SINGLE else EN_SINGLE
        val greetings = if (bulgarian) BG_GREETINGS else EN_GREETINGS

        // "Здравейте, ..." — a comma after an opening greeting.
        val firstTwo = bare(words[0]) + " " + bare(words[1])
        if (words.size > 2 && firstTwo in greetings) {
            if (!endsWithMark(words[1])) words[1] = words[1] + ","
        } else if (bare(words[0]) in greetings && !endsWithMark(words[0])) {
            words[0] = words[0] + ","
        }

        fun commaBefore(index: Int) {
            if (index < 1) return
            val previous = words[index - 1]
            if (endsWithMark(previous)) return
            if (bare(previous) in BG_BLOCKERS && bulgarian) return
            words[index - 1] = "$previous,"
        }

        var i = 1
        while (i < words.size) {
            val word = bare(words[i])
            val next = if (i + 1 < words.size) bare(words[i + 1]) else ""
            when {
                "$word $next" in pairs -> {
                    commaBefore(i)
                    i++ // the second word of the pair is not a conjunction on its own
                }

                bulgarian && word in BG_RELATIVE -> {
                    // "човекът, с който говорих": the comma goes before the preposition.
                    if (bare(words[i - 1]) in BG_PREPOSITIONS) commaBefore(i - 1) else commaBefore(i)
                }

                word in single -> commaBefore(i)
            }
            i++
        }
        return words.joinToString(" ")
    }

    private fun isQuestion(text: String, bulgarian: Boolean): Boolean {
        val words = text.split(' ').map { bare(it) }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        return if (bulgarian) {
            words.first() in BG_QUESTION_WORDS || "ли" in words || words.last() == "нали"
        } else {
            words.first() in EN_QUESTION_WORDS
        }
    }

    /** Adds the capital letter, commas and the closing mark to one recognized phrase. */
    fun autoPunctuate(input: String, languageTag: String): String {
        var text = input.trim(' ')
        if (text.isBlank()) return input
        val bulgarian = languageTag.startsWith("bg")
        // When the recognizer (or spoken punctuation) already put marks inside, trust them.
        val alreadyMarked = text.any { it in ",;:" } || text.dropLast(1).any { it in ".!?" }
        if (!alreadyMarked) text = addCommas(text, bulgarian)
        text = text.replaceFirstChar { it.uppercase() }
        if (text.last() !in ".!?…:;,\n") {
            text += if (isQuestion(text, bulgarian)) "?" else "."
        }
        return text
    }

    private val SENTENCE_BREAK = Regex("(?<=[.!?…])\\s+")

    /** Splits text into sentences, so each one can become its own message. */
    fun splitSentences(text: String): List<String> =
        SENTENCE_BREAK.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }

    /** Appends a recognized phrase to the draft with sensible spacing and capitalisation. */
    fun join(existing: String, addition: String): String {
        val add = addition.trim(' ')
        if (add.isEmpty()) return existing
        val base = existing.trimEnd(' ')
        val startsSentence = base.isEmpty() || base.last() in ".!?\n"
        val piece = if (startsSentence) add.replaceFirstChar { it.uppercase() } else add
        val separator = when {
            base.isEmpty() -> ""
            base.last() == '\n' -> ""
            piece.first() in ".,!?;:\n" -> ""
            else -> " "
        }
        return base + separator + piece
    }
}
